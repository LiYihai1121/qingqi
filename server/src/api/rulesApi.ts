import { config as defaultConfig, type Config } from "../config";
import * as defaultStore from "../storage/store";
import { createAuth } from "../middleware/auth";
import * as defaultLimiter from "../middleware/rateLimit";
import { createHttpUtils, type Handler } from "../utils/httpUtil";
import { createValidator } from "../utils/validate";
import { createSigner } from "../utils/sign";
import { createReportsApi } from "./reportsApi";

export function createRulesApi(
  config: Config = defaultConfig,
  store: ReturnType<typeof defaultStore.createStore> = defaultStore,
  limiter: ReturnType<typeof defaultLimiter.createRateLimiter> = defaultLimiter
) {
  const { getRules, saveRules } = store;
  const { requireAdmin } = createAuth(config);
  const { limitRead, limitWrite } = limiter;
  const { jsonResponse, errorJson, statusResponse, readBody, safeJsonParse, errorResponseFrom } = createHttpUtils(config);
  const { cleanRules, isValidPackage } = createValidator(config);
  const { RULES_SIGNATURE_HEADER, signRulesBody } = createSigner(config);
  const reportsApi = createReportsApi(config, store, limiter);

  /**
   * 规则下发响应：对「即将发出的字节串原文」做 HMAC-SHA256 签名。
   */
  function signedRulesJson(
    payload: unknown,
    extra: Record<string, string> = {}
  ): Response {
    const body = JSON.stringify(payload);
    const headers: Record<string, string> = {
      "Content-Type": "application/json; charset=utf-8",
      ...extra,
    };
    const sig = signRulesBody(body);
    if (sig) headers[RULES_SIGNATURE_HEADER] = sig;
    return new Response(body, { status: 200, headers });
  }

  const v0_latest: Handler = (req, _url, ctx) => {
    if (!limitRead(req, ctx.ip)) return errorJson(429, "rate limited");
    const r = getRules();
    return signedRulesJson({
      version: r.version,
      hash: r.hash,
      updatedAt: r.updatedAt,
      keywords: r.keywords,
      viewIds: r.viewIds,
      selectors: r.selectors,
      packages: r.packages,
    });
  };

  const v1_latest: Handler = (req, _url, ctx) => {
    if (!limitRead(req, ctx.ip)) return errorJson(429, "rate limited");
    const rules = getRules();
    const ifNoneMatch = req.headers.get("if-none-match");
    if (ifNoneMatch && ifNoneMatch === rules.hash) return statusResponse(304);
    return signedRulesJson(
      {
        schemaVersion: rules.schemaVersion,
        version: rules.version,
        hash: rules.hash,
        updatedAt: rules.updatedAt,
        rules: rules.rules,
      },
      { ETag: rules.hash }
    );
  };

  const v0_publish: Handler = async (req, _url, ctx) => {
    if (!limitWrite(req, ctx.ip)) return errorJson(429, "rate limited");
    const auth = requireAdmin(req);
    if (!auth.ok) return errorJson(auth.status, auth.error);
    let body: string;
    try {
      body = await readBody(req);
    } catch (e: any) {
      return errorResponseFrom(e);
    }
    let parsed: any;
    try {
      parsed = safeJsonParse(body, config.MAX_APPS * 8 + 20);
    } catch (e: any) {
      return errorResponseFrom(e);
    }
    const cleaned = cleanRules(parsed);
    if (!cleaned) return errorJson(400, "invalid rules payload");
    const expected = req.headers.get("if-match");
    if (expected && expected !== getRules().hash && expected !== "*") {
      return errorJson(412, "rules changed; reload before publishing");
    }
    const version = saveRules(cleaned);
    return jsonResponse({ ok: true, version, hash: getRules().hash });
  };

  const v1_publish: Handler = async (req, _url, ctx) => {
    if (!limitWrite(req, ctx.ip)) return errorJson(429, "rate limited");
    const auth = requireAdmin(req);
    if (!auth.ok) return errorJson(auth.status, auth.error);
    let body: string;
    try {
      body = await readBody(req);
    } catch (e: any) {
      return errorResponseFrom(e);
    }
    let parsed: any;
    try {
      parsed = safeJsonParse(body, config.MAX_APPS * 8 + 20);
    } catch (e: any) {
      return errorResponseFrom(e);
    }
    const cleaned = cleanRules(parsed);
    if (!cleaned) return errorJson(400, "invalid rules payload");
    const expected = req.headers.get("if-match");
    if (expected && expected !== getRules().hash && expected !== "*") {
      return errorJson(412, "rules changed; reload before publishing");
    }
    const version = saveRules(cleaned);
    return jsonResponse({ ok: true, version, hash: getRules().hash });
  };

  /** 简单选择器：`[key]`、`[key op "value"]`；op ∈ `=` `*` `^` `$`（与客户端文法一致） */
  const SIMPLE_SELECTOR_RE = /\[(text|desc|vid|click)(?:([*^$]?=)"([^"]*)")?\]/g;
  const SELECTOR_MAX_VALUE_LEN = 64;

  type SelectorVerdict = { verdict: "hit" | "miss" | "unevaluable"; reason: string };

  function evalSelector(expr: string, sample: string, viewId: string): SelectorVerdict {
    const simples = [...expr.matchAll(SIMPLE_SELECTOR_RE)];
    if (simples.length === 0) {
      return { verdict: "unevaluable", reason: "表达式里没有可识别的简单选择器" };
    }
    if (simples.map((m) => m[0]).join("") !== expr.trim()) {
      return { verdict: "unevaluable", reason: "含组合符（后代/子/前序兄弟）或非选择器字符，需要节点树才能判定" };
    }

    for (const m of simples) {
      const key = m[1]!;
      const op = m[2] === undefined ? undefined : m[2][0];
      const value = m[3];
      if (key === "desc") {
        return { verdict: "unevaluable", reason: "desc 通道需要节点描述文本，本接口未提供" };
      }
      if (key === "click") {
        return { verdict: "unevaluable", reason: "click 通道需要节点可点击状态，本接口未提供" };
      }
      const target = key === "vid" ? viewId : sample;
      if (!target) {
        return { verdict: "unevaluable", reason: `未提供${key === "vid" ? " ViewID" : "样本文本"}，无法判定` };
      }
      const actual = target.trim().toLowerCase();
      if (key !== "vid" && actual.length > SELECTOR_MAX_VALUE_LEN) {
        return { verdict: "miss", reason: "样本文本超过 64 字符，客户端按设计不参与 text/desc 匹配" };
      }
      const expected = (value ?? "").toLowerCase();
      const ok =
        op === undefined
          ? actual.trim().length > 0
          : op === "="
            ? actual === expected
            : op === "*"
              ? actual.includes(expected)
              : op === "^"
                ? actual.startsWith(expected)
                : actual.endsWith(expected);
      if (!ok) {
        return { verdict: "miss", reason: `${key} 不满足 ${op ?? "存在性"}「${value ?? ""}」` };
      }
    }
    return { verdict: "hit", reason: "text/vid 通道全部满足" };
  }

  const v1_testRule: Handler = async (req, _url, ctx) => {
    if (!limitRead(req, ctx.ip)) return errorJson(429, "rate limited");
    const auth = requireAdmin(req);
    if (!auth.ok) return errorJson(auth.status, auth.error);
    let body: string;
    try {
      body = await readBody(req);
    } catch (e: any) {
      return errorResponseFrom(e);
    }
    let parsed: any;
    try {
      parsed = safeJsonParse(body);
    } catch (e: any) {
      return errorResponseFrom(e);
    }
    const pkgRaw = typeof parsed.pkg === "string" ? parsed.pkg.trim() : "";
    const pkg = isValidPackage(pkgRaw) ? pkgRaw : "";
    const sample = String(parsed.text ?? "").toLowerCase();
    const vid = String(parsed.viewId ?? "").toLowerCase();
    const rules = getRules();
    const app = pkg ? rules.rules.apps[pkg] : undefined;
    const disabled =
      app?.disabled === true || (pkg ? rules.rules.disabled.includes(pkg) : false);
    const keywords = [...rules.rules.globalKeywords, ...(app?.keywords ?? [])];
    const viewIdRules = [...rules.rules.globalViewIds, ...(app?.viewIds ?? [])];
    const selectorRules = [...rules.rules.globalSelectors, ...(app?.selectors ?? [])];
    const hits: Array<{ match: string; keyword?: string; rule?: string; field?: string }> = [];
    const selectorNotes: Array<{ rule: string; reason: string }> = [];
    for (const kw of keywords) {
      if (sample.includes(kw.toLowerCase())) {
        hits.push({ match: "keyword", keyword: kw, field: "text" });
      }
    }
    for (const rule of viewIdRules) {
      if (rule.length >= 3 && vid.includes(rule.toLowerCase())) {
        hits.push({ match: "viewId", rule });
      }
    }
    for (const selector of selectorRules) {
      const v = evalSelector(selector, sample, vid);
      if (v.verdict === "hit") {
        hits.push({ match: "selector", rule: selector, field: "text" });
      } else if (v.verdict === "unevaluable") {
        selectorNotes.push({ rule: selector, reason: v.reason });
      }
    }
    return jsonResponse({ hits, hit: !disabled && hits.length > 0, disabled, selectorNotes });
  };

  return {
    v0_latest,
    v1_latest,
    v0_publish,
    v1_publish,
    v0_skip: reportsApi.v0_skip,
    v1_batchReport: reportsApi.v1_batchReport,
    v1_testRule,
  };
}

export const { v0_latest, v1_latest, v0_publish, v1_publish, v0_skip, v1_batchReport, v1_testRule } = createRulesApi();
