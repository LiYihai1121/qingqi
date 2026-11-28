import * as crypto from "node:crypto";
import * as fs from "node:fs";

/**
 * L2 规则源（Issue #84 隔离原型）。
 *
 * DESIGN-ADS-FILTERING.md §3 要求来源、许可证、更新时间、版本、校验值可追溯。
 * 本实现拒绝「只有域名列表」的规则源，也拒绝未声明许可证或默认开启的配置：
 * Phase 0 未通过时，任何可下发的规则集都必须显式标注为「默认关闭、未获批准」。
 *
 * 种子文件刻意**不捆绑任何第三方规则**：许可证、署名与再分发条件尚未复核，
 * 在评审通过前分发外部规则集会引入独立的合规风险（§6）。
 */
export interface FilterRuleSource {
  schemaVersion: 1;
  source: string;
  license: string;
  version: string;
  updatedAt: string;
  sha256: string;
  domains: string[];
  allowlist: string[];
  enabledByDefault: false;
  phase0Approved: false;
  note: string;
}

const DOMAIN_LABEL = "[a-z0-9](?:[a-z0-9-]*[a-z0-9])?";
const DOMAIN_RE = new RegExp(`^${DOMAIN_LABEL}(?:\\.${DOMAIN_LABEL})+$`);
const HEX_256 = /^[a-f0-9]{64}$/;
const MAX_DOMAINS = 50_000;

/** 对规范化后的规则内容计算 SHA-256；载荷形状变动必须同步更新本函数。 */
export function computeRuleDigest(input: object): string {
  const raw = { ...(input as Record<string, unknown>) };
  const canonical = JSON.stringify({
    schemaVersion: raw.schemaVersion,
    source: raw.source,
    license: raw.license,
    version: raw.version,
    updatedAt: raw.updatedAt,
    domains: Array.isArray(raw.domains) ? [...raw.domains].sort() : [],
    allowlist: Array.isArray(raw.allowlist) ? [...raw.allowlist].sort() : [],
    enabledByDefault: raw.enabledByDefault,
    phase0Approved: raw.phase0Approved,
    note: raw.note,
  });
  return crypto.createHash("sha256").update(canonical).digest("hex");
}

function requireText(value: unknown, field: string): string {
  if (typeof value !== "string" || value.trim() === "") {
    throw new Error(`filter rule source: ${field} must be a non-empty string`);
  }
  return value;
}

function parseDomains(value: unknown, field: string): string[] {
  if (!Array.isArray(value) || value.length > MAX_DOMAINS) {
    throw new Error(`filter rule source: ${field} must be an array of at most ${MAX_DOMAINS} entries`);
  }
  for (const entry of value) {
    if (typeof entry !== "string" || entry.length > 253 || !DOMAIN_RE.test(entry)) {
      throw new Error(`filter rule source: invalid domain in ${field}`);
    }
  }
  return [...new Set(value as string[])].sort();
}

/** 读取并校验规则源；校验和不匹配、许可证缺失、非法域名或默认开启都视为损坏。 */
export function loadFilterRuleSource(filePath: string): FilterRuleSource {
  let parsed: unknown;
  try {
    parsed = JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch (error) {
    throw new Error(`filter rule source: cannot read seed (${String(error)})`);
  }
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    throw new Error("filter rule source: seed must be a JSON object");
  }
  const raw = parsed as Record<string, unknown>;
  if (raw.schemaVersion !== 1) throw new Error("filter rule source: unsupported schemaVersion");
  if (raw.enabledByDefault !== false) throw new Error("filter rule source: must be disabled by default");
  if (raw.phase0Approved !== false) throw new Error("filter rule source: phase 0 is not approved");

  const source = requireText(raw.source, "source");
  const license = requireText(raw.license, "license");
  const version = requireText(raw.version, "version");
  const updatedAt = requireText(raw.updatedAt, "updatedAt");
  const note = requireText(raw.note, "note");
  const domains = parseDomains(raw.domains, "domains");
  const allowlist = parseDomains(raw.allowlist, "allowlist");

  const sha256 = requireText(raw.sha256, "sha256");
  if (!HEX_256.test(sha256)) throw new Error("filter rule source: sha256 must be 64 hex chars");
  if (sha256 !== computeRuleDigest(raw)) {
    throw new Error("filter rule source: checksum mismatch; refusing a possibly tampered rule set");
  }

  return {
    schemaVersion: 1,
    source,
    license,
    version,
    updatedAt,
    sha256,
    domains,
    allowlist,
    enabledByDefault: false,
    phase0Approved: false,
    note,
  };
}
