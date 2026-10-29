import { config as defaultConfig, type Config } from "../config";
import * as defaultStore from "../storage/store";
import { createAuth } from "../middleware/auth";
import * as defaultLimiter from "../middleware/rateLimit";
import { jsonResponse, errorJson, type Handler } from "../utils/httpUtil";

export function createStatsApi(config: Config = defaultConfig, store: ReturnType<typeof defaultStore.createStore> = defaultStore, limiter: ReturnType<typeof defaultLimiter.createRateLimiter> = defaultLimiter) {
  const { statsSummary } = store;
  const { requireAdmin } = createAuth(config);
  const { limitRead } = limiter;
  const summary: Handler = (req, _url, ctx) => {
    // 限流先于鉴权：失败的鉴权尝试同样消耗令牌桶，阻断 token 暴力猜测
    if (!limitRead(req, ctx.ip)) return errorJson(429, "rate limited");
    // STATS_READ_AUTH 是真实的访问控制开关：开启后统计汇总（含各应用跳过量、
    // 设备事件记录）只允许 admin 令牌读取，避免匿名侧信道收集使用画像。
    if (config.STATS_READ_AUTH) {
      const auth = requireAdmin(req);
      if (!auth.ok) return errorJson(auth.status, auth.error);
    }
    return jsonResponse(statsSummary());
  };
  return { summary };
}
export const { summary } = createStatsApi();
