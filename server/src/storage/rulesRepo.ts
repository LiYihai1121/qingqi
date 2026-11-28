import { Database } from "bun:sqlite";
import * as fs from "node:fs";
import * as path from "node:path";
import * as crypto from "node:crypto";
import type { Config } from "../config";
import type { CleanedRules, RulesPackage } from "../types/rules";
import { computeHash, ensureCompatShape, defaultRules } from "./migration";

export class RulesRepository {
  private rulesCache: RulesPackage | null = null;
  private backupSeq = 0;
  private lastLoadedRulesFile = "";
  private lastLoadedSeedFile = "";

  constructor(private db: Database, private config: Config) {
    // 记录初始路径：仅当运行期（测试）切换规则文件时才清空 DB 规则，避免首次读取误删已持久化的规则
    this.lastLoadedRulesFile = config.RULES_FILE;
    this.lastLoadedSeedFile = config.SEED_RULES_FILE;
  }

  public backupStamp(): string {
    this.backupSeq = (this.backupSeq + 1) % 1_000_000;
    const rand = crypto.randomBytes(2).toString("hex");
    const iso = new Date().toISOString().replace(/[:.]/g, "-");
    return `${iso}-${this.backupSeq}-${rand}`;
  }

  public resetCache(): void {
    this.rulesCache = null;
    try {
      this.db.run("DELETE FROM rules;");
    } catch {}
  }

  public getRules(): RulesPackage {
    if (
      this.lastLoadedRulesFile !== this.config.RULES_FILE ||
      this.lastLoadedSeedFile !== this.config.SEED_RULES_FILE
    ) {
      this.resetCache();
      this.lastLoadedRulesFile = this.config.RULES_FILE;
      this.lastLoadedSeedFile = this.config.SEED_RULES_FILE;
    }

    if (this.rulesCache) return this.rulesCache;

    const row = this.db.query("SELECT content FROM rules WHERE id = 1;").get() as
      | { content: string }
      | undefined;

    if (row && row.content) {
      try {
        const parsed = JSON.parse(row.content);
        const shaped = ensureCompatShape(parsed, this.config);
        shaped.hash = computeHash(shaped);
        this.rulesCache = shaped;
        return shaped;
      } catch {
        /* fallback to disk if DB row corrupted */
      }
    }

    // DB 为空时回退到规则文件或种子
    let raw = this.readJson(this.config.RULES_FILE, null);
    if (raw === null) raw = this.readJson(this.config.SEED_RULES_FILE, null);
    const shaped = ensureCompatShape(raw, this.config);
    shaped.hash = computeHash(shaped);

    this.db.run(
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

    this.rulesCache = shaped;
    return shaped;
  }

  public saveRules(cleaned: CleanedRules): number {
    this.rotateBackup();
    const prev = this.getRules();
    const version = (typeof prev.version === "number" ? prev.version : 1) + 1;

    const apps: Record<string, any> = {};
    const disabled: string[] = [];
    for (const [pkg, rule] of Object.entries(cleaned.packages)) {
      apps[pkg] = {
        keywords: rule.keywords,
        viewIds: rule.viewIds,
        selectors: rule.selectors,
        disabled: rule.disabled === true,
        gestureFallback: rule.gestureFallback === true,
      };
      if (rule.disabled === true) disabled.push(pkg);
    }

    const rules = {
      schemaVersion: this.config.SCHEMA_VERSION,
      version,
      updatedAt: new Date().toISOString(),
      rules: {
        globalKeywords: cleaned.keywords,
        globalViewIds: cleaned.viewIds,
        globalSelectors: cleaned.selectors,
        apps,
        disabled,
      },
    };

    const compat = ensureCompatShape(rules, this.config);
    compat.hash = computeHash(compat);
    const serialized = JSON.stringify(compat);

    // 1. 备份旧规则到 SQLite rules_backup 表
    const stamp = this.backupStamp();
    this.db.run(
      `INSERT INTO rules_backup (stamp, version, content, created_at)
       VALUES (?, ?, ?, ?);`,
      [stamp, prev.version ?? 1, JSON.stringify(prev), new Date().toISOString()]
    );

    // 修剪 rules_backup 数量
    this.db.run(
      `DELETE FROM rules_backup
       WHERE id NOT IN (
         SELECT id FROM rules_backup ORDER BY id DESC LIMIT ?
       );`,
      [this.config.BACKUP_COUNT]
    );

    // 2. 更新 SQLite rules 表
    this.db.run(
      `INSERT INTO rules (id, schema_version, version, updated_at, hash, content)
       VALUES (1, ?, ?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET
         schema_version = excluded.schema_version,
         version = excluded.version,
         updated_at = excluded.updated_at,
         hash = excluded.hash,
         content = excluded.content;`,
      [compat.schemaVersion, compat.version, compat.updatedAt, compat.hash, serialized]
    );

    // 3. 过渡期同步写 rules.json 快照
    this.writeJson(this.config.RULES_FILE, compat);

    this.rulesCache = compat;
    return version;
  }

  public rotateBackup(): void {
    try {
      if (!this.dirOrFileExists(this.config.RULES_FILE)) return;
      const stamp = this.backupStamp();
      const name = `rules-${stamp}.json`;
      const dest = path.join(this.config.BACKUP_DIR, name);
      fs.mkdirSync(this.config.BACKUP_DIR, { recursive: true });
      fs.copyFileSync(this.config.RULES_FILE, dest);

      const files = fs
        .readdirSync(this.config.BACKUP_DIR)
        .filter((f) => f.startsWith("rules-") && f.endsWith(".json"))
        .sort()
        .reverse();
      for (const f of files.slice(this.config.BACKUP_COUNT)) {
        try {
          fs.unlinkSync(path.join(this.config.BACKUP_DIR, f));
        } catch {
          /* ignore */
        }
      }
    } catch (e) {
      console.warn("[rulesRepo] backup rotation failed:", e);
    }
  }

  private readJson(file: string, fallback: any): any {
    try {
      const raw = fs.readFileSync(file, "utf8");
      return JSON.parse(raw);
    } catch {
      return fallback;
    }
  }

  private writeJson(file: string, obj: any): void {
    try {
      const dir = path.dirname(file);
      if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
      const tmp = file + ".tmp";
      const fd = fs.openSync(tmp, "w");
      try {
        fs.writeFileSync(fd, JSON.stringify(obj, null, 2));
        fs.fsyncSync(fd);
      } finally {
        fs.closeSync(fd);
      }
      fs.renameSync(tmp, file);
    } catch (e) {
      /* ignore file sync error during testing if blocked */
    }
  }

  private dirOrFileExists(file: string): boolean {
    try {
      fs.accessSync(file);
      return true;
    } catch {
      return false;
    }
  }
}
