import * as fs from "node:fs";
import * as path from "node:path";
import * as crypto from "node:crypto";
import { config as defaultConfig, type Config } from "../config";
import type {
  AppStat,
  CleanedRules,
  RulesPackage,
  SkipEvent,
  StatsDay,
  StatsSummary,
} from "../types/rules";


export function createStore(config: Config = defaultConfig, options: { flushDelayMs?: number } = {}) {
  let disposed = false;
  const failedDays = new Set<string>();
  const retryAttempts = new Map<string, number>();
  let summaryDay = "";
  const eventIndex = new Map<string, string>();
  let indexLoaded = false;
  const statsCache = new Map<string, { data: StatsDay; dirty: boolean; timer: ReturnType<typeof setTimeout> | null }>();
  const FLUSH_DELAY_MS = options.flushDelayMs ?? 5000;

  /** 汇总缓存：recordSkip / 过期清理时失效，避免每次 summary 全量读盘 */
  let summaryCache: StatsSummary | null = null;
  let rulesCache: RulesPackage | null = null;

  type CachedDay = (typeof statsCache) extends Map<string, infer V> ? V : never;

  function readJson(file: string, fallback: any): any {
    try {
      const raw = fs.readFileSync(file, "utf8");
      return JSON.parse(raw);
    } catch {
      return fallback;
    }
  }

  function writeJson(file: string, obj: any): void {
    const dir = path.dirname(file);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    const tmp = file + ".tmp";
    const fd = fs.openSync(tmp, "w");
    try { fs.writeFileSync(fd, JSON.stringify(obj, null, 2)); fs.fsyncSync(fd); }
    finally { fs.closeSync(fd); }
    fs.renameSync(tmp, file);
  }

  function dirOrFileExists(file: string): boolean {
    try {
      fs.accessSync(file);
      return true;
    } catch {
      return false;
    }
  }

  function defaultRules(): any {
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

  function computeHash(rules: any): string {
    const content = JSON.stringify({
      schemaVersion: rules.schemaVersion,
      rules: rules.rules || {},
    });
    return "sha256:" + crypto.createHash("sha256").update(content).digest("hex").slice(0, 16);
  }

  /**
   * 兼容 v0/v1 两种形状：补全缺失字段，并把 legacy 的
   * keywords/viewIds/packages 重新指向 v1 的同一份引用。
   */
  function ensureCompatShape(input: unknown): RulesPackage {
    let rules: any = input;
    if (!rules || typeof rules !== "object") rules = defaultRules();
    // 存量 rules.json 可能停在旧 schema；载荷已带 selectors，故单向补齐到当前版本。
    // hash 的计算输入含 schemaVersion（computeHash），客户端会因此多同步一次，符合预期。
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

  function getRules(): RulesPackage {
    if (rulesCache) return rulesCache;
    // 运行时文件缺失/损坏时回退到入库种子，两者都不可用才走内置默认
    let raw = readJson(config.RULES_FILE, null);
    if (raw === null) raw = readJson(config.SEED_RULES_FILE, null);
    const shaped = ensureCompatShape(raw);
    shaped.hash = computeHash(shaped);
    rulesCache = shaped;
    return shaped;
  }

  function saveRules(cleaned: CleanedRules): number {
    rotateBackup();
    const prev = getRules();
    const version = (typeof prev.version === "number" ? prev.version : 1) + 1;

    const apps: Record<string, any> = {};
    const disabled: string[] = [];
    for (const [pkg, rule] of Object.entries(cleaned.packages)) {
      apps[pkg] = {
        keywords: rule.keywords,
        viewIds: rule.viewIds,
        selectors: rule.selectors,
        disabled: rule.disabled === true,
        // 包级手势放权必须原样下发，客户端据此决定是否放行无可点击路径的目标。
        gestureFallback: rule.gestureFallback === true,
      };
      if (rule.disabled === true) disabled.push(pkg);
    }

    const rules = {
      schemaVersion: config.SCHEMA_VERSION,
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
    const compat = ensureCompatShape(rules);
    compat.hash = computeHash(compat);
    writeJson(config.RULES_FILE, compat);
    rulesCache = compat;
    return version;
  }

  /**
   * 备份文件名用的时间戳。
   *
   * 纯毫秒时间戳不够：同一毫秒内的多次轮转会生成同名文件互相覆盖
   * （轮转保留份数静默变少，曾让 test 偶发红）。补齐进程内单调序号 +
   * 随机后缀，保证同一进程任意速率轮转都不撞名。
   */
  let backupSeq = 0;
  function backupStamp(): string {
    backupSeq = (backupSeq + 1) % 1_000_000;
    const rand = crypto.randomBytes(2).toString("hex");
    const iso = new Date().toISOString().replace(/[:.]/g, "-");
    return `${iso}-${backupSeq}-${rand}`;
  }

  function rotateBackup(): void {
    try {
      if (!dirOrFileExists(config.RULES_FILE)) return;
      const stamp = backupStamp();
      const name = `rules-${stamp}.json`;
      const dest = path.join(config.BACKUP_DIR, name);
      fs.mkdirSync(config.BACKUP_DIR, { recursive: true });
      fs.copyFileSync(config.RULES_FILE, dest);

      const files = fs
        .readdirSync(config.BACKUP_DIR)
        .filter((f) => f.startsWith("rules-") && f.endsWith(".json"))
        .sort()
        .reverse();
      for (const f of files.slice(config.BACKUP_COUNT)) {
        try {
          fs.unlinkSync(path.join(config.BACKUP_DIR, f));
        } catch {
          /* ignore */
        }
      }
    } catch (e) {
      console.warn("[store] backup rotation failed:", e);
    }
  }

  /** 备份文件名的进程内单调序号：同毫秒连续备份若只靠时间戳会撞名互相覆盖 */
  function rotateStatsBackup(day: string): void {
    try {
      const src = path.join(config.STATS_DIR, `${day}.json`);
      if (!dirOrFileExists(src)) return;
      const stamp = backupStamp();
      const dest = path.join(config.STATS_DIR, "backups", `stats-${day}-${stamp}.json`);
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      fs.copyFileSync(src, dest);

      const dir = path.dirname(dest);
      const files = fs
        .readdirSync(dir)
        .filter((f) => f.startsWith(`stats-${day}-`) && f.endsWith(".json"))
        .sort()
        .reverse();
      for (const f of files.slice(config.STATS_BACKUP_COUNT)) {
        try {
          fs.unlinkSync(path.join(dir, f));
        } catch {
          /* ignore */
        }
      }
    } catch (e) {
      console.warn("[store] stats backup rotation failed:", e);
    }
  }

  /**
   * 日键时区：STATS_TZ（IANA）。「今天」是产品语义，跟部署地一致；
   * 固定偏移在 DST 地区会错一小时，UTC 则与用户直觉错位 8 小时（R2）。
   * 非法时区名在 store 创建期快速失败，而不是在首次上报时炸掉。
   */
  const dayFormatter = (() => {
    try {
      return new Intl.DateTimeFormat("en-US", {
        timeZone: config.STATS_TZ,
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      });
    } catch {
      throw new Error(`STATS_TZ must be a valid IANA timezone, got ${JSON.stringify(config.STATS_TZ)}`);
    }
  })();

  function getDayKey(date: Date = new Date()): string {
    const parts = dayFormatter.formatToParts(date);
    const y = parts.find((p) => p.type === "year")?.value ?? "0000";
    const m = parts.find((p) => p.type === "month")?.value ?? "00";
    const d = parts.find((p) => p.type === "day")?.value ?? "00";
    return `${y}-${m}-${d}`;
  }

  /** 事件时间归日的钳制窗口：离线队列上限 7 天，对齐客户端队列语义 */
  const EVENT_TS_PAST_MS = 7 * 24 * 3600 * 1000;
  const EVENT_TS_FUTURE_MS = 5 * 60 * 1000;

  /**
   * 解析上报事件的归日时间（A3）。
   *
   * 上报端点无鉴权，客户端自报 ts 不可信：窗口内（now-7d ~ now+5min）
   * 才采信事件时间，越界一律回退到服务端当前时间并计 clamped——
   * 既防止伪造 ts 造出海量日分片（磁盘 DoS），也保证离线补报不错日。
   * STATS_EVENT_TIME=off 时整体回退旧行为（忽略 ts）。
   */
  function resolveEventTime(
    eventTs: number | undefined,
    now: number = Date.now()
  ): { ts: number; day: string; clamped: boolean } {
    const todayKey = getDayKey(new Date(now));
    const fallback = { ts: now, day: todayKey, clamped: false };
    if (!config.STATS_EVENT_TIME) return fallback;
    if (typeof eventTs !== "number" || !Number.isFinite(eventTs)) return fallback;
    const windowMs = Math.min(EVENT_TS_PAST_MS, config.STATS_RETENTION_DAYS * 24 * 3600 * 1000);
    if (windowMs <= 0) return fallback;
    if (eventTs < now - windowMs || eventTs > now + EVENT_TS_FUTURE_MS) {
      return { ts: now, day: todayKey, clamped: true };
    }
    const day = getDayKey(new Date(eventTs));
    // 未来 5 分钟跨零点时 day 会落到「明天」：分片只允许 [today-retention, today]，
    // 越界回退今天，防止单个请求制造窗口外日键。
    if (day > todayKey) return { ts: now, day: todayKey, clamped: true };
    return { ts: eventTs, day, clamped: false };
  }

  function loadDayStats(day: string): StatsDay {
    const file = path.join(config.STATS_DIR, `${day}.json`);
    return readJson(file, { day, byApp: {}, events: [] } as StatsDay);
  }

  function getCachedDay(day: string): CachedDay {
    let c = statsCache.get(day);
    if (!c) {
      c = { data: loadDayStats(day), dirty: false, timer: null };
      statsCache.set(day, c);
    }
    return c;
  }

  function scheduleFlush(day: string): void {
    const c = statsCache.get(day);
    if (!c || c.timer || disposed) return;
    c.timer = setTimeout(() => {
      try { flushDay(day); }
      catch (error) { console.error("[store] flush failed; retry scheduled", error); }
    }, Math.min(60000, FLUSH_DELAY_MS * 2 ** Math.min(retryAttempts.get(day) ?? 0, 6)));
    c.timer.unref?.();
  }

  /** 当日 byApp 条目数到顶后，超限上报聚合到该保留键（不是合法包名，不会与真实条目冲突） */
  const OVERFLOW_APP_KEY = "_other";

  /**
   * 写入一条跳过记录到指定日分片。
   * eventTsMs 必须是已钳制的事件时间（resolveEventTime 的产物），
   * SkipEvent.ts 记录事件发生时间而非到达时间，recent 列表才不失真。
   */
  function recordSkipOnDay(
    pkgRaw: string,
    labelRaw: string,
    channelRaw: string,
    day: string,
    eventTsMs: number
  ): void {
    const c = getCachedDay(day);
    const pkg = String(pkgRaw).slice(0, 256);
    const label = String(labelRaw ?? "").slice(0, 256);
    let channel = String(channelRaw ?? "text").slice(0, 32) || "text";

    // 安全契约：上报端点未认证，包名可被伪造出无限种合法形状；byApp 条目数
    // 必须封顶（超限聚合进 "_other"），否则内存 + 当日分片 + 备份会被持续放大。
    const appKey =
      c.data.byApp[pkg] || Object.keys(c.data.byApp).length < config.MAX_STATS_APPS_PER_DAY
        ? pkg
        : OVERFLOW_APP_KEY;
    if (!c.data.byApp[appKey]) c.data.byApp[appKey] = { label, count: 0, byChannel: {} };
    const entry = c.data.byApp[appKey];
    entry.label = label;
    entry.count += 1;
    entry.byChannel[channel] = (entry.byChannel[channel] ?? 0) + 1;

    c.data.events.unshift({ ts: new Date(eventTsMs).toISOString(), pkg, label, channel });
    if (c.data.events.length > config.RECENT_CAP) {
      c.data.events.length = config.RECENT_CAP;
    }
    c.dirty = true;
    summaryCache = null;
    scheduleFlush(day);
  }

  /** 单条上报入口：事件时间缺失/越界时回退服务端当前时间（见 resolveEventTime） */
  function recordSkip(pkgRaw: string, labelRaw: string, channelRaw: string, eventTs?: number): void {
    const resolved = resolveEventTime(eventTs);
    recordSkipOnDay(pkgRaw, labelRaw, channelRaw, resolved.day, resolved.ts);
  }

  function listStatsDays(): string[] {
    const set = new Set<string>();
    try {
      for (const f of fs.readdirSync(config.STATS_DIR)) {
        const m = /^(\d{4}-\d{2}-\d{2})\.json$/.exec(f);
        if (m) set.add(m[1]);
      }
    } catch {
      /* ignore */
    }
    for (const k of statsCache.keys()) set.add(k);
    return Array.from(set).sort();
  }

  function statsSummary(): StatsSummary {
    if (summaryCache && summaryDay === getDayKey()) return summaryCache;
    const days = listStatsDays();
    let total = 0;
    const byAppMap: Record<string, { label: string; count: number }> = {};
    const byDay: Array<{ day: string; count: number }> = [];
    const dayData = new Map<string, StatsDay>();

    for (const day of days) {
      const c = statsCache.get(day);
      const data: StatsDay = c ? c.data : loadDayStats(day);
      dayData.set(day, data);
      let dayCount = 0;
      for (const [pkg, info] of Object.entries(data.byApp)) {
        dayCount += info.count;
        total += info.count;
        const cur = byAppMap[pkg] ?? { label: info.label, count: 0 };
        cur.count += info.count;
        cur.label = info.label;
        byAppMap[pkg] = cur;
      }
      byDay.push({ day, count: dayCount });
    }

    byDay.sort((a, b) => (a.day < b.day ? -1 : 1));
    const byApp = Object.entries(byAppMap)
      .map(([pkg, v]) => ({ pkg, label: v.label, count: v.count }))
      .sort((a, b) => b.count - a.count);

    // recent 跨天取最近 50 条（days 升序，从最新一天往前收集；天内 events 已是新→旧）
    const recent: SkipEvent[] = [];
    for (let i = days.length - 1; i >= 0 && recent.length < 50; i--) {
      for (const ev of dayData.get(days[i])!.events) {
        recent.push(ev);
        if (recent.length >= 50) break;
      }
    }

    const summary: StatsSummary = {
      total,
      today: byDay.find((d) => d.day === getDayKey())?.count ?? 0,
      byDay: byDay.slice(-14),
      byApp,
      recent,
    };
    summaryCache = summary;
    summaryDay = getDayKey();
    return summary;
  }

  function cleanupOldStats(now = new Date()): void {
    const cutoff = getDayKey(new Date(now.getTime() - config.STATS_RETENTION_DAYS * 24 * 3600 * 1000));
    for (const [day, cached] of statsCache) {
      if (day < cutoff) {
        if (cached.timer) clearTimeout(cached.timer);
        statsCache.delete(day);
        failedDays.delete(day);
        retryAttempts.delete(day);
        for (const [id, receivedDay] of eventIndex) if (receivedDay === day) eventIndex.delete(id);
        summaryCache = null;
      }
    }
    try {
      for (const f of fs.readdirSync(config.STATS_DIR)) {
        const m = /^(\d{4}-\d{2}-\d{2})\.json$/.exec(f);
        if (!m) continue;
        if (m[1] < cutoff) {
          try {
            fs.unlinkSync(path.join(config.STATS_DIR, f));
            summaryCache = null;
          } catch {
            /* ignore */
          }
        }
      }
    } catch {
      /* ignore */
    }
  }

  // 过期分片定期清理：启动清理只跑一次，长驻进程需要周期执行
  const statsCleanupTimer = setInterval(cleanupOldStats, 6 * 60 * 60 * 1000);
  statsCleanupTimer.unref?.();

  /** 仅供测试：清空汇总缓存 */
  function _resetSummaryCacheForTests(): void {
    summaryCache = null;
  }

  /** 仅供测试：清空按天统计缓存（隔离跨测试的 config.STATS_DIR 切换） */
  function _resetStatsCacheForTests(): void {
    for (const cached of statsCache.values()) if (cached.timer) clearTimeout(cached.timer);
    statsCache.clear();
    eventIndex.clear();
    indexLoaded = false;
    failedDays.clear();
    retryAttempts.clear();
    summaryCache = null;
  }

  /** 仅供测试：清空规则缓存（隔离种子兜底测试的 config.RULES_FILE 切换） */
  function _resetRulesCacheForTests(): void {
    rulesCache = null;
  }

  /** 仅供测试：触发统计分片备份轮转（验证 rotateStatsBackup 行为） */
  function _rotateStatsBackupForTests(day: string): void {
    rotateStatsBackup(day);
  }

  function flushDay(day: string): void {
    const c = statsCache.get(day);
    if (!c) return;
    if (c.timer) {
      clearTimeout(c.timer);
      c.timer = null;
    }
    if (!c.dirty) return;
    try {
      rotateStatsBackup(day);
      writeJson(path.join(config.STATS_DIR, `${day}.json`), c.data);
      c.dirty = false;
      failedDays.delete(day);
      retryAttempts.delete(day);
    } catch (error) {
      failedDays.add(day);
      retryAttempts.set(day, (retryAttempts.get(day) ?? 0) + 1);
      scheduleFlush(day);
      throw error;
    }
  }

  function flush(): void {
    for (const day of statsCache.keys()) flushDay(day);
  }

  function storageHealthy(): boolean { return failedDays.size === 0; }
  function dispose(): void {
    disposed = true;
    clearInterval(statsCleanupTimer);
    for (const c of statsCache.values()) if (c.timer) clearTimeout(c.timer);
  }

  /**
   * Persist counters and optional device-scoped dedup IDs in the daily shards.
   *
   * 事件按各自 ts 归日（resolveEventTime 钳制后），一个批次可能跨多个日分片
   * （离线补报的典型形态）。按日分组后逐日「快照 → 写入 → flushDay → 更新索引」，
   * 单日失败只回滚该日；已成功的日分片在重试时经 eventIndex 去重，不会双计。
   */
  function recordBatch(
    deviceId: string,
    events: Array<{ pkg: string; label: string; channel: string; ts?: number; eventId?: string }>
  ): number {
    if (!indexLoaded) {
      for (const existingDay of listStatsDays()) {
        for (const id of getCachedDay(existingDay).data.eventIds ?? []) eventIndex.set(id, existingDay);
      }
      indexLoaded = true;
    }
    // 先统一解析归日：同一批内 now 只取一次，避免跨零点时同批事件拆到不同「今天」
    const now = Date.now();
    const byDay = new Map<string, Array<{ pkg: string; label: string; channel: string; ts: number; eventId?: string }>>();
    for (const event of events) {
      const resolved = resolveEventTime(event.ts, now);
      const item = { pkg: event.pkg, label: event.label, channel: event.channel, ts: resolved.ts, eventId: event.eventId };
      const list = byDay.get(resolved.day);
      if (list) list.push(item);
      else byDay.set(resolved.day, [item]);
    }
    let accepted = 0;
    for (const [day, dayEvents] of byDay) {
      const current = getCachedDay(day);
      const before = structuredClone(current.data);
      const wasDirty = current.dirty;
      const newKeys = new Set<string>();
      try {
        for (const event of dayEvents) {
          const key = event.eventId
            ? crypto.createHash("sha256").update(JSON.stringify([deviceId, event.eventId])).digest("hex")
            : undefined;
          if (key && (eventIndex.has(key) || newKeys.has(key))) continue;
          current.data.eventIds ??= [];
          if (key && current.data.eventIds.length >= 20000) throw Object.assign(new Error("daily event ID capacity exceeded"), { statusCode: 503 });
          recordSkipOnDay(event.pkg, event.label, event.channel, day, event.ts);
          if (key) { current.data.eventIds.push(key); newKeys.add(key); }
          accepted++;
        }
        flushDay(day);
        for (const key of newKeys) eventIndex.set(key, day);
      } catch (error) {
        current.data = before;
        current.dirty = wasDirty;
        summaryCache = null;
        throw error;
      }
    }
    return accepted;
  }
  const _internal = { computeHash, listStatsDays, getDayKey, resolveEventTime };

  return { computeHash, getRules, saveRules, recordSkip, recordBatch, storageHealthy, dispose, statsSummary, cleanupOldStats, _resetSummaryCacheForTests, _resetStatsCacheForTests, _resetRulesCacheForTests, _rotateStatsBackupForTests, flush, _internal };
}

export const { computeHash, getRules, saveRules, recordSkip, recordBatch, storageHealthy, dispose, statsSummary, cleanupOldStats, _resetSummaryCacheForTests, _resetStatsCacheForTests, _resetRulesCacheForTests, _rotateStatsBackupForTests, flush, _internal } = createStore();
