import { jsonResponse, errorJson } from "../utils/httpUtil";
import { createAuth } from "../middleware/auth";
import * as defaultLimiter from "../middleware/rateLimit";
import * as defaultAccess from "../middleware/accessLog";
import { createRulesApi } from "./rulesApi";
import { createStatsApi } from "./statsApi";
import { createHealthApi } from "./healthApi";
import * as defaultStore from "../storage/store";
import { config as defaultConfig, type Config } from "../config";


export function createApi(config: Config = defaultConfig, store: ReturnType<typeof defaultStore.createStore> = defaultStore, limiter: ReturnType<typeof defaultLimiter.createRateLimiter> = defaultLimiter, access: ReturnType<typeof defaultAccess.createAccessLog> = defaultAccess) {
  const rulesApi = createRulesApi(config, store, limiter);
  const statsApi = createStatsApi(config, store, limiter);
  const healthApi = createHealthApi(store, limiter);
  const { requireAdmin } = createAuth(config);
  const { limitRead } = limiter;
  const { recentAccess } = access;
  /** API 路由分发（v1 优先，回退 v0；均精确匹配 pathname） */
  async function handleApi(
    req: Request,
    url: URL,
    ctx: { ip: string }
  ): Promise<Response> {
    const method = req.method;
    const p = url.pathname;

    if (method === "GET" && p === "/api/v1/rules/latest") {
      return rulesApi.v1_latest(req, url, ctx);
    }
    if (method === "PUT" && p === "/api/v1/rules") {
      return rulesApi.v1_publish(req, url, ctx);
    }
    if (method === "POST" && p === "/api/v1/rules/test") {
      return rulesApi.v1_testRule(req, url, ctx);
    }
    if (method === "POST" && p === "/api/v1/reports/batch") {
      return rulesApi.v1_batchReport(req, url, ctx);
    }
    if (method === "GET" && p === "/api/v1/stats/summary") {
      return statsApi.summary(req, url, ctx);
    }
    if (method === "GET" && p === "/api/v1/health") {
      return healthApi.health(req, url, ctx);
    }
    if (method === "GET" && p === "/api/v1/admin/logs") {
      // 限流先于鉴权：未认证尝试也计入读限额，避免 token 暴力破解零成本
      if (!limitRead(req, ctx.ip)) return errorJson(429, "rate limited");
      const auth = requireAdmin(req);
      if (!auth.ok) return errorJson(auth.status, auth.error);
      return jsonResponse({ entries: recentAccess() });
    }
    if (method === "GET" && p === "/api/rules/latest") {
      return rulesApi.v0_latest(req, url, ctx);
    }
    if (method === "PUT" && p === "/api/rules") {
      return rulesApi.v0_publish(req, url, ctx);
    }
    if (method === "POST" && p === "/api/skip") {
      return rulesApi.v0_skip(req, url, ctx);
    }
    if (method === "GET" && p === "/api/stats/summary") {
      return statsApi.summary(req, url, ctx);
    }

    return new Response(JSON.stringify({ error: "not found" }), {
      status: 404,
      headers: { "Content-Type": "application/json; charset=utf-8" },
    });
  }

  return { handleApi };
}

export const { handleApi } = createApi();
