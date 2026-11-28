import { Database } from "bun:sqlite";
import * as fs from "node:fs";
import * as path from "node:path";
import * as crypto from "node:crypto";
import type { Config } from "../config";
import type { AppStat, SkipEvent, StatsDay, StatsSummary } from "../types/rules";
import { migrateLegacyData } from "./migration";

const OVERFLOW_APP_KEY = "_other";

export class StatsRepository {
  private summaryCache: StatsSummary | null = null;
  private summaryDay = "";
  private failedDays = new Set<string>();
  private retryAttempts = new Map<string, number>();
  private dirtyDays = new Set<string>();
  private flushTimers = new Map<string, ReturnType<typeof setTimeout>>();
  private backupSeq = 0;
  private disposed = false;
  private lastLoadedStatsDir = "";
  private readonly flushDelayMs: number;

  constructor(
    private db: Database,
    private config: Config,
    options: { flushDelayMs?: number } = {}
  ) {
    this.flushDelayMs = options.flushDelayMs ?? 5000;
    this.lastLoadedStatsDir = config.STATS_DIR;
  }

  private checkStatsDirSync(): void {
    if (this.lastLoadedStatsDir !== this.config.STATS_DIR) {
      this._resetStatsCacheForTests();
      this.lastLoadedStatsDir = this.config.STATS_DIR;
    }
  }

  public getDayKey(date: Date = new Date()): string {
    try {
      return new Intl.DateTimeFormat("en-CA", {
        timeZone: this.config.STATS_TZ || "Asia/Shanghai",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      }).format(date);
    } catch {
      return date.toISOString().slice(0, 10);
    }
  }

  public backupStamp(): string {
    this.backupSeq = (this.backupSeq + 1) % 1_000_000;
    const rand = crypto.randomBytes(2).toString("hex");
    const iso = new Date().toISOString().replace(/[:.]/g, "-");
    return `${iso}-${this.backupSeq}-${rand}`;
  }

  public recordSkip(
    pkgRaw: string,
    labelRaw: string,
    channelRaw: string,
    day = this.getDayKey()
  ): void {
    this.checkStatsDirSync();
    this.doRecordSkip(pkgRaw, labelRaw, channelRaw, day);
    this.summaryCache = null;
    this.dirtyDays.add(day);
    this.scheduleFlush(day);
  }

  private doRecordSkip(
    pkgRaw: string,
    labelRaw: string,
    channelRaw: string,
    day: string
  ): void {
    const pkg = String(pkgRaw).slice(0, 256);
    const label = String(labelRaw ?? "").slice(0, 256);
    const channel = String(channelRaw ?? "text").slice(0, 32) || "text";

    // 检查包名条目上限
    const existingRow = this.db
      .query("SELECT label, count, by_channel FROM stats_daily_apps WHERE day = ? AND pkg = ?;")
      .get(day, pkg) as { label: string; count: number; by_channel: string } | undefined;

    let appKey = pkg;
    if (!existingRow) {
      const countRow = this.db
        .query("SELECT COUNT(*) as c FROM stats_daily_apps WHERE day = ?;")
        .get(day) as { c: number };
      if (countRow.c >= this.config.MAX_STATS_APPS_PER_DAY) {
        appKey = OVERFLOW_APP_KEY;
      }
    }

    const targetRow =
      appKey === pkg
        ? existingRow
        : (this.db
            .query("SELECT label, count, by_channel FROM stats_daily_apps WHERE day = ? AND pkg = ?;")
            .get(day, appKey) as { label: string; count: number; by_channel: string } | undefined);

    let byChannel: Record<string, number> = {};
    if (targetRow) {
      try {
        byChannel = JSON.parse(targetRow.by_channel);
      } catch {}
      byChannel[channel] = (byChannel[channel] ?? 0) + 1;

      this.db.run(
        `UPDATE stats_daily_apps
         SET label = ?, count = count + 1, by_channel = ?
         WHERE day = ? AND pkg = ?;`,
        [label, JSON.stringify(byChannel), day, appKey]
      );
    } else {
      byChannel[channel] = 1;
      this.db.run(
        `INSERT INTO stats_daily_apps (day, pkg, label, count, by_channel)
         VALUES (?, ?, ?, 1, ?);`,
        [day, appKey, label, JSON.stringify(byChannel)]
      );
    }

    this.db.run(
      `INSERT INTO stats_events (day, ts, pkg, label, channel)
       VALUES (?, ?, ?, ?, ?);`,
      [day, new Date().toISOString(), pkg, label, channel]
    );
  }

  public recordBatch(
    deviceId: string,
    events: Array<{ pkg: string; label: string; channel: string; eventId?: string }>
  ): number {
    this.checkStatsDirSync();
    const day = this.getDayKey();
    const newKeys = new Set<string>();
    let accepted = 0;

    const findDedup = this.db.prepare("SELECT 1 FROM stats_dedup WHERE dedup_key = ?;");
    const countDedup = this.db.prepare("SELECT COUNT(*) as c FROM stats_dedup WHERE day = ?;");
    const insertDedup = this.db.prepare(
      "INSERT INTO stats_dedup (dedup_key, day, created_at) VALUES (?, ?, ?);"
    );

    const now = new Date().toISOString();

    const runBatch = this.db.transaction(() => {
      for (const event of events) {
        let key: string | undefined;
        if (event.eventId) {
          key = crypto
            .createHash("sha256")
            .update(JSON.stringify([deviceId, event.eventId]))
            .digest("hex");

          if (newKeys.has(key) || findDedup.get(key)) {
            continue;
          }

          const c = (countDedup.get(day) as { c: number }).c;
          if (c >= 20000) {
            throw Object.assign(new Error("daily event ID capacity exceeded"), { statusCode: 503 });
          }
        }

        this.doRecordSkip(event.pkg, event.label, event.channel, day);

        if (key) {
          insertDedup.run(key, day, now);
          newKeys.add(key);
        }
        accepted++;
      }
      this.flushDay(day);
    });

    try {
      runBatch();
      if (!this.db.inTransaction) {
        try {
          this.db.run("PRAGMA wal_checkpoint(PASSIVE);");
        } catch {}
      }
    } catch (e) {
      this.summaryCache = null;
      throw e;
    } finally {
      findDedup.finalize();
      countDedup.finalize();
      insertDedup.finalize();
    }

    this.summaryCache = null;
    this.dirtyDays.add(day);

    return accepted;
  }

  public statsSummary(): StatsSummary {
    this.checkStatsDirSync();
    const today = this.getDayKey();
    if (this.summaryCache && this.summaryDay === today) {
      return this.summaryCache;
    }

    const totalRow = this.db.query("SELECT COALESCE(SUM(count), 0) as total FROM stats_daily_apps;").get() as {
      total: number;
    };
    const total = totalRow.total;

    const byDayRows = this.db
      .query("SELECT day, SUM(count) as count FROM stats_daily_apps GROUP BY day ORDER BY day ASC;")
      .all() as Array<{ day: string; count: number }>;

    const byAppRows = this.db
      .query(
        `SELECT pkg, label, SUM(count) as count
         FROM stats_daily_apps
         GROUP BY pkg
         ORDER BY count DESC;`
      )
      .all() as Array<{ pkg: string; label: string; count: number }>;

    const recentRows = this.db
      .query(
        `SELECT ts, pkg, label, channel
         FROM stats_events
         ORDER BY day DESC, id DESC
         LIMIT 50;`
      )
      .all() as SkipEvent[];

    const todayCount = byDayRows.find((d) => d.day === today)?.count ?? 0;

    const summary: StatsSummary = {
      total,
      today: todayCount,
      byDay: byDayRows.slice(-14),
      byApp: byAppRows,
      recent: recentRows,
    };

    this.summaryCache = summary;
    this.summaryDay = today;
    return summary;
  }

  public cleanupOldStats(now = new Date()): void {
    const cutoff = this.getDayKey(
      new Date(now.getTime() - this.config.STATS_RETENTION_DAYS * 24 * 3600 * 1000)
    );

    this.db.run("DELETE FROM stats_daily_apps WHERE day < ?;", [cutoff]);
    this.db.run("DELETE FROM stats_events WHERE day < ?;", [cutoff]);
    this.db.run("DELETE FROM stats_dedup WHERE day < ?;", [cutoff]);

    try {
      if (fs.existsSync(this.config.STATS_DIR)) {
        for (const f of fs.readdirSync(this.config.STATS_DIR)) {
          const m = /^(\d{4}-\d{2}-\d{2})\.json$/.exec(f);
          if (m && m[1] < cutoff) {
            try {
              fs.unlinkSync(path.join(this.config.STATS_DIR, f));
            } catch {}
          }
        }
      }
    } catch {}

    this.summaryCache = null;
  }

  public scheduleFlush(day: string): void {
    if (this.flushTimers.has(day) || this.disposed) return;
    const timer = setTimeout(() => {
      this.flushTimers.delete(day);
      try {
        this.flushDay(day);
      } catch (error) {
        console.error("[statsRepo] flush failed; retry scheduled", error);
      }
    }, Math.min(60000, this.flushDelayMs * 2 ** Math.min(this.retryAttempts.get(day) ?? 0, 6)));

    timer.unref?.();
    this.flushTimers.set(day, timer);
  }

  public flushDay(day: string): void {
    const timer = this.flushTimers.get(day);
    if (timer) {
      clearTimeout(timer);
      this.flushTimers.delete(day);
    }

    try {
      // 1. WAL Checkpoint (仅在非事务中执行，避免 SQLITE_LOCKED)
      if (!this.db.inTransaction) {
        try {
          this.db.run("PRAGMA wal_checkpoint(PASSIVE);");
        } catch {
          /* ignore checkpoint errors */
        }
      }

      // 2. 过渡期同步写回 JSON 快照
      const apps = this.db
        .query("SELECT pkg, label, count, by_channel FROM stats_daily_apps WHERE day = ?;")
        .all(day) as Array<{ pkg: string; label: string; count: number; by_channel: string }>;

      const events = this.db
        .query("SELECT ts, pkg, label, channel FROM stats_events WHERE day = ? ORDER BY id DESC LIMIT 500;")
        .all(day) as SkipEvent[];

      const dedupRows = this.db
        .query("SELECT dedup_key FROM stats_dedup WHERE day = ?;")
        .all(day) as Array<{ dedup_key: string }>;

      const byApp: Record<string, { label: string; count: number; byChannel: Record<string, number> }> = {};
      for (const a of apps) {
        let byChannel: Record<string, number> = {};
        try {
          byChannel = JSON.parse(a.by_channel);
        } catch {}
        byApp[a.pkg] = { label: a.label, count: a.count, byChannel };
      }

      const dayData: StatsDay & { eventIds?: string[] } = {
        day,
        byApp,
        events,
        eventIds: dedupRows.map((r) => r.dedup_key),
      };

      this.rotateStatsBackup(day);
      this.writeJson(path.join(this.config.STATS_DIR, `${day}.json`), dayData);

      this.dirtyDays.delete(day);
      this.failedDays.delete(day);
      this.retryAttempts.delete(day);
    } catch (error) {
      this.failedDays.add(day);
      this.retryAttempts.set(day, (this.retryAttempts.get(day) ?? 0) + 1);
      this.scheduleFlush(day);
      throw error;
    }
  }

  public flush(): void {
    const daysToFlush = new Set([...this.dirtyDays, ...this.flushTimers.keys(), this.getDayKey()]);
    for (const day of daysToFlush) {
      this.flushDay(day);
    }
  }

  public rotateStatsBackup(day: string): void {
    try {
      const src = path.join(this.config.STATS_DIR, `${day}.json`);
      if (!this.dirOrFileExists(src)) return;
      const stamp = this.backupStamp();
      const backupDir = path.join(this.config.STATS_DIR, "backups");
      const dest = path.join(backupDir, `stats-${day}-${stamp}.json`);
      fs.mkdirSync(backupDir, { recursive: true });
      fs.copyFileSync(src, dest);

      const files = fs
        .readdirSync(backupDir)
        .filter((f) => f.startsWith(`stats-${day}-`) && f.endsWith(".json"))
        .sort()
        .reverse();
      for (const f of files.slice(this.config.STATS_BACKUP_COUNT)) {
        try {
          fs.unlinkSync(path.join(backupDir, f));
        } catch {
          /* ignore */
        }
      }
    } catch (e) {
      console.warn("[statsRepo] stats backup rotation failed:", e);
    }
  }

  public storageHealthy(): boolean {
    return this.failedDays.size === 0;
  }

  public dispose(): void {
    this.disposed = true;
    for (const timer of this.flushTimers.values()) {
      clearTimeout(timer);
    }
    this.flushTimers.clear();
  }

  public _resetSummaryCacheForTests(): void {
    this.summaryCache = null;
  }

  public _resetStatsCacheForTests(): void {
    for (const timer of this.flushTimers.values()) clearTimeout(timer);
    this.flushTimers.clear();
    this.dirtyDays.clear();
    this.failedDays.clear();
    this.retryAttempts.clear();
    this.summaryCache = null;
    try {
      this.db.run("DELETE FROM stats_daily_apps;");
      this.db.run("DELETE FROM stats_events;");
      this.db.run("DELETE FROM stats_dedup;");
      migrateLegacyData(this.db, this.config);
    } catch {}
  }

  public listStatsDays(): string[] {
    const set = new Set<string>();
    const rows = this.db.query("SELECT DISTINCT day FROM stats_daily_apps;").all() as Array<{ day: string }>;
    for (const r of rows) set.add(r.day);
    return Array.from(set).sort();
  }

  private writeJson(file: string, obj: any): void {
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
