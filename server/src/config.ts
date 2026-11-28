import * as path from "node:path";

// config.ts 位于 src/ 目录，向上一级才是 server 根目录
export const ROOT = path.join(import.meta.dir, "..");

export interface Config {
  PORT: number;
  HOST: string;
  ROOT: string;
  DATA_DIR: string;
  RULES_FILE: string;
  /** 入库的初始规则种子；运行时 RULES_FILE 缺失时兜底（不随 dataDir 注入改变） */
  SEED_RULES_FILE: string;
  STATS_DIR: string;
  PUBLIC_DIR: string;
  APK_FILE: string;
  MAX_BODY: number;
  RECENT_CAP: number;
  STATS_RETENTION_DAYS: number;
  STATS_DIR_CLEANUP_ON_START: boolean;
  BACKUP_DIR: string;
  BACKUP_COUNT: number;
  /** 统计分片保留的备份份数（rotateStatsBackup 轮转） */
  STATS_BACKUP_COUNT: number;
  ADMIN_TOKEN: string;
  /** 统计汇总是否要求 admin 鉴权（STATS_READ_AUTH=1 开启；默认匿名可读，行为与旧版一致） */
  STATS_READ_AUTH: boolean;
  /**
   * 规则签名密钥（HMAC-SHA256，env RULES_SIGNING_KEY）。
   *
   * 置空时 v0/v1 的 rules latest 端点不下发签名头——客户端配了签名密钥会
   * 因此拒绝载入云端规则（失败关闭）；两端都置空则退回明文生态（历史行为，
   * 不推荐）。读取端点本身无鉴权，签名是防「规则被中间人改写」的唯一完整性手段。
   */
  RULES_SIGNING_KEY: string;
  /**
   * 受信反向代理 IP 列表（env TRUSTED_PROXIES，逗号分隔）。
   * 只有来自这些 IP 的请求才采信 X-Forwarded-For；空 = 任何人伪造的 XFF 都不认，
   * 限流一律以 socket 对端 IP 为键（B 链修复：否则所有限流可被绕过）。
   */
  TRUSTED_PROXIES: string[];
  CORS_ORIGINS: string[] | null;
  RATE_LIMIT_READ_PER_MIN: number;
  RATE_LIMIT_REPORT_PER_MIN: number;
  RATE_LIMIT_WRITE_PER_MIN: number;
  SCHEMA_VERSION: number;
  SCHEMA_VERSION_MIN: number;
  MAX_KEYWORD_LEN: number;
  MAX_VIEWID_LEN: number;
  MAX_VIEWID_RULE_LEN: number;
  MAX_SELECTOR_LEN: number;
  MAX_SELECTORS_PER_LIST: number;
  MAX_RULES_PER_APP: number;
  MAX_APPS: number;
  /** 当日统计 byApp 条目数上限（未认证上报可伪造任意包名，超限聚合进 "_other"） */
  MAX_STATS_APPS_PER_DAY: number;
  MAX_BATCH_EVENTS: number;
  MAX_BODY_KEYS: number;
  MAX_BODY_DEPTH: number;
  /** TLS 证书路径（空字符串表示禁用 HTTPS）。 */
  TLS_CERT: string;
  /** TLS 私钥路径（空字符串表示禁用 HTTPS）。 */
  TLS_KEY: string;
  /**
   * 统计日键的 IANA 时区（env STATS_TZ，默认 Asia/Shanghai）。
   * 「今天」是产品语义，必须跟部署地一致；固定偏移在 DST 地区会错一小时。
   */
  STATS_TZ: string;
  /**
   * 事件时间归日开关（env STATS_EVENT_TIME=off 关闭）。
   * 开启时上报事件按客户端 ts（钳制到 7 天窗口）归入事件发生日；
   * 关闭时回退到服务端到达时间（旧行为）。
   */
  STATS_EVENT_TIME: boolean;
  /**
   * 上报幂等开关（env REPORT_IDEMPOTENCY=off 关闭）。
   * 开启时按 (deviceId, epoch) + seq 高水位去重；关闭时走旧路径
   * （仅 eventId 内容去重，无 seq 语义）。
   */
  REPORT_IDEMPOTENCY: boolean;
}

/** env 布尔开关：默认开，置 "off"/"0"/"false"/"no" 关闭 */
function envFlagOn(name: string): boolean {
  const v = (process.env[name] ?? "").toLowerCase();
  return !(v === "off" || v === "0" || v === "false" || v === "no");
}

/**
 * PORT 解析：非法值在启动期快速失败并给出可读原因。
 * 此前 `Number("abc")` → NaN 会在 Bun.serve 深处抛错，堆栈不指向配置。
 */
function parsePort(raw: string | undefined): number {
  const n = Number(raw ?? 3210);
  if (!Number.isInteger(n) || n < 1 || n > 65535) {
    throw new Error(
      `PORT must be an integer in 1..65535, got ${JSON.stringify(raw ?? "3210")}`
    );
  }
  return n;
}

export const config: Config = {
  PORT: parsePort(process.env.PORT),
  HOST: process.env.HOST ?? "127.0.0.1",
  ROOT,
  DATA_DIR: path.join(ROOT, "data"),
  RULES_FILE: path.join(ROOT, "data", "rules.json"),
  SEED_RULES_FILE: path.join(ROOT, "seed", "rules.json"),
  STATS_DIR: path.join(ROOT, "data", "stats"),
  PUBLIC_DIR: path.join(ROOT, "public"),
  APK_FILE: path.join(ROOT, "..", "AdSkip-latest.apk"),
  MAX_BODY: 1024 * 1024,
  RECENT_CAP: 500,
  STATS_RETENTION_DAYS: 90,
  STATS_DIR_CLEANUP_ON_START: true,
  BACKUP_DIR: path.join(ROOT, "data", "backups"),
  BACKUP_COUNT: 5,
  STATS_BACKUP_COUNT: 5,
  ADMIN_TOKEN: process.env.ADMIN_TOKEN ?? "",
  STATS_READ_AUTH: ["1", "true", "yes"].includes(
    (process.env.STATS_READ_AUTH ?? "").toLowerCase()
  ),
  RULES_SIGNING_KEY: process.env.RULES_SIGNING_KEY ?? "",
  TRUSTED_PROXIES: process.env.TRUSTED_PROXIES
    ? process.env.TRUSTED_PROXIES.split(",").map((s) => s.trim()).filter(Boolean)
    : [],
  CORS_ORIGINS: process.env.CORS_ORIGINS
    ? process.env.CORS_ORIGINS.split(",").map((s) => s.trim())
    : null,
  RATE_LIMIT_READ_PER_MIN: 120,
  RATE_LIMIT_REPORT_PER_MIN: 30,
  RATE_LIMIT_WRITE_PER_MIN: 10,
  // 2 = 载荷含选择器通道（rules.globalSelectors / apps.*.selectors）；
  // MIN 保持 1，旧客户端（不识 selectors）对 schema 2 载荷行为不变。
  SCHEMA_VERSION: 2,
  SCHEMA_VERSION_MIN: 1,
  MAX_KEYWORD_LEN: 12,
  MAX_VIEWID_LEN: 256,
  MAX_VIEWID_RULE_LEN: 256,
  MAX_SELECTOR_LEN: 256,
  MAX_SELECTORS_PER_LIST: 128,
  MAX_RULES_PER_APP: 512,
  MAX_APPS: 2000,
  MAX_STATS_APPS_PER_DAY: 2000,
  MAX_BATCH_EVENTS: 50,
  MAX_BODY_KEYS: 100,
  MAX_BODY_DEPTH: 5,
  TLS_CERT: process.env.TLS_CERT ?? "",
  TLS_KEY: process.env.TLS_KEY ?? "",
  STATS_TZ: process.env.STATS_TZ ?? "Asia/Shanghai",
  STATS_EVENT_TIME: envFlagOn("STATS_EVENT_TIME"),
  REPORT_IDEMPOTENCY: envFlagOn("REPORT_IDEMPOTENCY"),
};
