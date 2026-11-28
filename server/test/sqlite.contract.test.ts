import { test, expect, describe } from "bun:test";
import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import { Database } from "bun:sqlite";
import { config as defaultConfig } from "../src/config";
import { createStore } from "../src/storage/store";

describe("SQLite storage contract", () => {
  test("initializes SQLite database with WAL mode and expected schema", () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-sqlite-init-"));
    const dbFile = path.join(dir, "adskip.db");
    const cfg = { ...defaultConfig, DATA_DIR: dir, DB_FILE: dbFile };
    const store = createStore(cfg);

    try {
      expect(fs.existsSync(dbFile)).toBe(true);
      const db = new Database(dbFile);
      try {
        const walRow = db.query("PRAGMA journal_mode;").get() as { journal_mode: string };
        expect(walRow.journal_mode.toLowerCase()).toBe("wal");

        const tables = db
          .query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name;")
          .all() as Array<{ name: string }>;
        const tableNames = tables.map((t) => t.name);
        expect(tableNames).toContain("rules");
        expect(tableNames).toContain("rules_backup");
        expect(tableNames).toContain("stats_daily_apps");
        expect(tableNames).toContain("stats_events");
        expect(tableNames).toContain("stats_dedup");
      } finally {
        db.close();
      }
    } finally {
      store.dispose();
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });

  test("persists rules in SQLite and records backups", () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-sqlite-rules-"));
    const dbFile = path.join(dir, "adskip.db");
    const cfg = { ...defaultConfig, DATA_DIR: dir, DB_FILE: dbFile, BACKUP_COUNT: 3 };
    const store = createStore(cfg);

    try {
      const initial = store.getRules();
      const initialVersion = initial.version;

      const vNext = store.saveRules({
        keywords: ["跳过", "关闭"],
        viewIds: ["skip_view"],
        selectors: ["[text='跳过']"],
        packages: {
          "com.example.app": {
            keywords: ["app_skip"],
            viewIds: [],
            selectors: [],
            disabled: false,
            gestureFallback: true,
          },
        },
      });
      expect(vNext).toBe(initialVersion + 1);

      const current = store.getRules();
      expect(current.version).toBe(initialVersion + 1);
      expect(current.keywords).toContain("关闭");
      expect(current.packages["com.example.app"].gestureFallback).toBe(true);

      const db = new Database(dbFile);
      try {
        const ruleRow = db.query("SELECT * FROM rules WHERE id = 1;").get() as any;
        expect(ruleRow.version).toBe(initialVersion + 1);
        expect(ruleRow.hash).toBe(current.hash);

        const backups = db.query("SELECT * FROM rules_backup ORDER BY id ASC;").all() as any[];
        expect(backups.length).toBeGreaterThan(0);
      } finally {
        db.close();
      }
    } finally {
      store.dispose();
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });

  test("records skip events and calculates statsSummary accurately via SQLite", () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-sqlite-stats-"));
    const dbFile = path.join(dir, "adskip.db");
    const cfg = { ...defaultConfig, DATA_DIR: dir, DB_FILE: dbFile };
    const store = createStore(cfg);

    try {
      store.recordSkip("com.example.a", "App A", "text", "2026-11-20");
      store.recordSkip("com.example.a", "App A", "text", "2026-11-20");
      store.recordSkip("com.example.b", "App B", "selector", "2026-11-21");

      const summary = store.statsSummary();
      expect(summary.total).toBe(3);

      const appA = summary.byApp.find((a) => a.pkg === "com.example.a");
      const appB = summary.byApp.find((a) => a.pkg === "com.example.b");
      expect(appA?.count).toBe(2);
      expect(appB?.count).toBe(1);

      const day20 = summary.byDay.find((d) => d.day === "2026-11-20");
      const day21 = summary.byDay.find((d) => d.day === "2026-11-21");
      expect(day20?.count).toBe(2);
      expect(day21?.count).toBe(1);

      expect(summary.recent.length).toBe(3);
      expect(summary.recent[0].pkg).toBe("com.example.b");
    } finally {
      store.dispose();
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });

  test("deduplicates event batch in SQLite durably across instances", () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-sqlite-dedup-"));
    const dbFile = path.join(dir, "adskip.db");
    const cfg = { ...defaultConfig, DATA_DIR: dir, DB_FILE: dbFile };

    const store1 = createStore(cfg);
    try {
      const accepted1 = store1.recordBatch("device-1", [
        { eventId: "ev-1", pkg: "com.app1", label: "App 1", channel: "text" },
        { eventId: "ev-2", pkg: "com.app2", label: "App 2", channel: "selector" },
        { eventId: "ev-1", pkg: "com.app1", label: "App 1", channel: "text" }, // duplicate in same batch
      ]);
      expect(accepted1).toBe(2);
      expect(store1.statsSummary().total).toBe(2);
    } finally {
      store1.dispose();
    }

    // 第二个 store 实例重新加载同一 SQLite 文件：持久化去重仍然有效
    const store2 = createStore(cfg);
    try {
      const accepted2 = store2.recordBatch("device-1", [
        { eventId: "ev-1", pkg: "com.app1", label: "App 1", channel: "text" }, // already recorded
        { eventId: "ev-3", pkg: "com.app3", label: "App 3", channel: "text" }, // new
      ]);
      expect(accepted2).toBe(1);
      expect(store2.statsSummary().total).toBe(3);
    } finally {
      store2.dispose();
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });

  test("migrates legacy JSON rules and daily stats into SQLite on startup", () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-sqlite-mig-"));
    const statsDir = path.join(dir, "stats");
    const dbFile = path.join(dir, "adskip.db");
    fs.mkdirSync(statsDir, { recursive: true });

    // 写入旧版 rules.json
    const oldRules = {
      schemaVersion: 2,
      version: 99,
      updatedAt: "2026-10-01T00:00:00.000Z",
      rules: {
        globalKeywords: ["旧规则跳过"],
        globalViewIds: ["old_skip"],
        globalSelectors: [],
        apps: {
          "com.old.pkg": { keywords: ["old_pkg"], viewIds: [], selectors: [], disabled: false },
        },
        disabled: [],
      },
    };
    fs.writeFileSync(path.join(dir, "rules.json"), JSON.stringify(oldRules));

    // 写入旧版 stats 分片
    const oldStats = {
      day: "2026-10-01",
      byApp: {
        "com.old.pkg": { label: "Old App", count: 12, byChannel: { text: 12 } },
      },
      events: [
        { ts: "2026-10-01T12:00:00.000Z", pkg: "com.old.pkg", label: "Old App", channel: "text" },
      ],
      eventIds: ["hash-dedup-1"],
    };
    fs.writeFileSync(path.join(statsDir, "2026-10-01.json"), JSON.stringify(oldStats));

    const cfg = {
      ...defaultConfig,
      DATA_DIR: dir,
      DB_FILE: dbFile,
      RULES_FILE: path.join(dir, "rules.json"),
      STATS_DIR: statsDir,
    };
    const store = createStore(cfg);

    try {
      const rules = store.getRules();
      expect(rules.version).toBe(99);
      expect(rules.keywords).toContain("旧规则跳过");

      const summary = store.statsSummary();
      expect(summary.total).toBe(12);
      expect(summary.byApp[0].pkg).toBe("com.old.pkg");
      expect(summary.byApp[0].count).toBe(12);
      expect(summary.recent.length).toBe(1);
    } finally {
      store.dispose();
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });

  test("published rules survive restart from SQLite even if rules.json snapshot is lost", () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-sqlite-restart-"));
    const cfg = { ...defaultConfig, DATA_DIR: dir, DB_FILE: path.join(dir, "adskip.db"), RULES_FILE: path.join(dir, "rules.json") };
    const store1 = createStore(cfg);
    let hash = "";
    try {
      store1.saveRules({ keywords: ["持久化"], viewIds: [], selectors: [], packages: {} });
      hash = store1.getRules().hash;
    } finally {
      store1.dispose();
    }
    fs.rmSync(cfg.RULES_FILE, { force: true });
    const store2 = createStore(cfg);
    try {
      const rules = store2.getRules();
      expect(rules.keywords).toEqual(["持久化"]);
      expect(rules.hash).toBe(hash);
    } finally {
      store2.dispose();
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });

  test("cleans up old stats by cutoff date in SQLite", () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-sqlite-clean-"));
    const dbFile = path.join(dir, "adskip.db");
    const cfg = { ...defaultConfig, DATA_DIR: dir, DB_FILE: dbFile, STATS_RETENTION_DAYS: 7 };
    const store = createStore(cfg);

    try {
      store.recordSkip("com.old", "Old", "text", "2026-10-01");
      store.recordSkip("com.new", "New", "text", "2026-11-20");

      expect(store.statsSummary().total).toBe(2);

      // 按 2026-11-20 清理 7 天前的数据（2026-10-01 会被清理）
      store.cleanupOldStats(new Date("2026-11-20T00:00:00.000Z"));

      const summary = store.statsSummary();
      expect(summary.total).toBe(1);
      expect(summary.byApp[0].pkg).toBe("com.new");
    } finally {
      store.dispose();
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });
});
