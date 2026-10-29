import * as defaultLimiter from "../middleware/rateLimit";
import * as defaultStore from "../storage/store";
import { jsonResponse, errorJson, type Handler } from "../utils/httpUtil";

export function createHealthApi(store: ReturnType<typeof defaultStore.createStore> = defaultStore, limiter: ReturnType<typeof defaultLimiter.createRateLimiter> = defaultLimiter) {
  const { limitRead } = limiter;
  const health: Handler = (req, _url, ctx) => {
    if (!limitRead(req, ctx.ip)) return errorJson(429, "rate limited");
    const healthy = store.storageHealthy();
    return jsonResponse({ status: healthy ? "ok" : "degraded", timestamp: new Date().toISOString() }, healthy ? 200 : 503);
  };
  return { health };
}
export const { health } = createHealthApi();
