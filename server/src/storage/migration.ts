import { Database } from "bun:sqlite";
import * as fs from "node:fs";
import * as path from "node:path";
import * as crypto from "node:crypto";
import type { Config } from "../config";
import type { RulesPackage, StatsDay } from "../types/rules";

export function defaultRules(config: Config): RulesPackage {
  const now = new Date().toISOString();
  return {
    schemaVersion: config.SCHEMA_VERSION,
    version: 1,
    updatedAt: now,
    hash: "",
    rules: {
      globalKeywords: ["跳过", "跳過", "skip", "跳过广告", "关闭广告"],
      globalViewIds: ["skip", "jump"],
      globalSelectors: [],
      apps: {},
      disabled: [],
    },
    keywords: ["跳过", "跳過", "skip", "跳过广告", "关闭广告"],
    viewIds: ["skip", "jump"],
    selectors: [],
    packages: {},
  };
}

export function computeHash(rules: any): string {
  const content = JSON.stringify({
    schemaVersion: rules.schemaVersion,
    rules: rules.rules || {},
  });
  return "sha256:" + crypto.createHash("sha256").update(content).digest("hex").slice(0, 16);
}

export function ensureCompatShape(input: unknown, config: Config): RulesPackage {
  let rules: any = input;
  if (!rules || typeof rules !== "object") rules = defaultRules(config);
  if (typeof rules.schemaVersion !== "number" || rules.schemaVersion < config.SCHEMA_VERSION) {
    rules.schemaVersion = config.SCHEMA_VERSION;
  }
  if (!rules.rules || typeof rules.rules !== "object") rules.rules = {};
  const r = rules.rules;
  const legacyKw: string[] = Array.isArray(rules.keywords) ? rules.keywords : [];
  const legacyVid: string[] = Array.isArray(rules.viewIds) ? rules.viewIds : [];

  if (!Array.isArray(r.globalKeywords)) r.globalKeywords = legacyKw.slice();
  if (!Array.isArray(r.globalViewIds)) r.globalViewIds = legacyVid.slice();
  if (!Array.isArray(r.globalSelectors)) r.globalSelectors = Array.isArray(rules.selectors) ? rules.selectors.slice() : [];
  if (!r.apps || typeof r.apps !== "object" || Array.isArray(r.apps)) r.apps = {};
  if (!Array.isArray(r.disabled)) r.disabled = [];

  for (const [pkg, rule] of Object.entries(r.apps as Record<string, any>)) {
    if (!rule || typeof rule !== "object" || Array.isArray(rule)) continue;
    if (!Array.isArray(rule.selectors)) rule.selectors = [];
  }

  if (!Array.isArray(rules.keywords)) rules.keywords = r.globalKeywords.slice();
  if (!Array.isArray(rules.viewIds)) rules.viewIds = r.globalViewIds.slice();
  if (!Array.isArray(rules.selectors)) rules.selectors = r.globalSelectors.slice();
  if (!rules.packages || typeof rules.packages !== "object" || Array.isArray(rules.packages)) {
    rules.packages = {};
  }

  rules.keywords = r.globalKeywords;
  rules.viewIds = r.globalViewIds;
  rules.selectors = r.globalSelectors;
  rules.packages = r.apps;

  if (typeof rules.version !== "number") rules.version = 1;
  if (typeof rules.updatedAt !== "string") rules.updatedAt = new Date().toISOString();
  return rules;
}

function readJsonSafe(filePath: string): any {
  try {
    const raw = fs.readFileSync(filePath, "utf8");
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function migrateLegacyData(db: Database, config: Config): void {
  // 1. 迁移规则数据
  const rulesCount = (db.query("SELECT COUNT(*) as c FROM rules;").get() as { c: number }).c;
  if (rulesCount === 0) {
    let raw = readJsonSafe(config.RULES_FILE);
    if (raw === null) raw = readJsonSafe(config.SEED_RULES_FILE);
    const shaped = ensureCompatShape(raw, config);
    shaped.hash = computeHash(shaped);

    db.run(
      `INSERT INTO rules (id, schema_version, version, updated_at, hash, content)
       VALUES (1, ?, ?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET
         schema_version = excluded.schema_version,
         version = excluded.version,
         updated_at = excluded.updated_at,
         hash = excluded.hash,
         content = excluded.content;`,
      [shaped.schemaVersion, shaped.version, shaped.updatedAt, shaped.hash, JSON.stringify(shaped)]
    );
  }

  // 2. 迁移按天统计数据
  const statsCount = (db.query("SELECT COUNT(*) as c FROM stats_daily_apps;").get() as { c: number }).c;
  if (statsCount === 0 && fs.existsSync(config.STATS_DIR)) {
    try {
      const files = fs
        .readdirSync(config.STATS_DIR)
        .filter((f) => /^(\d{4}-\d{2}-\d{2})\.json$/.test(f))
        .sort();

      if (files.length > 0) {
        const insertAppStmt = db.prepare(
          `INSERT OR REPLACE INTO stats_daily_apps (day, pkg, label, count, by_channel)
           VALUES (?, ?, ?, ?, ?);`
        );
        const insertEventStmt = db.prepare(
          `INSERT INTO stats_events (day, ts, pkg, label, channel)
           VALUES (?, ?, ?, ?, ?);`
        );
        const insertDedupStmt = db.prepare(
          `INSERT OR IGNORE INTO stats_dedup (dedup_key, day, created_at)
           VALUES (?, ?, ?);`
        );

        try {
          const migrateAll = db.transaction(() => {
            for (const file of files) {
              const filePath = path.join(config.STATS_DIR, file);
              const data: StatsDay & { eventIds?: string[] } = readJsonSafe(filePath);
              if (!data || typeof data !== "object") continue;
              const day = data.day || file.replace(/\.json$/, "");

              if (data.byApp && typeof data.byApp === "object") {
                for (const [pkg, info] of Object.entries(data.byApp)) {
                  if (!info) continue;
                  const byChannel = JSON.stringify(info.byChannel ?? {});
                  insertAppStmt.run(day, pkg, info.label ?? "", info.count ?? 0, byChannel);
                }
              }

              if (Array.isArray(data.events)) {
                // events 在 JSON 分片中是最新记录在前，倒序插入保证 SQLite id 与时间正序一致
                for (let i = data.events.length - 1; i >= 0; i--) {
                  const ev = data.events[i];
                  if (!ev || !ev.pkg) continue;
                  insertEventStmt.run(
                    day,
                    ev.ts ?? new Date().toISOString(),
                    ev.pkg,
                    ev.label ?? "",
                    ev.channel ?? "text"
                  );
                }
              }

              if (Array.isArray(data.eventIds)) {
                const now = new Date().toISOString();
                for (const id of data.eventIds) {
                  if (typeof id === "string" && id) {
                    insertDedupStmt.run(id, day, now);
                  }
                }
              }
            }
          });

          migrateAll();
        } finally {
          insertAppStmt.finalize();
          insertEventStmt.finalize();
          insertDedupStmt.finalize();
        }
      }
    } catch (e) {
      console.warn("[storage:migration] stats migration failed:", e);
    }
  }
}
