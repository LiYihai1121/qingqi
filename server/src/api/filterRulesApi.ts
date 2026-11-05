import * as path from "node:path";
import { config as defaultConfig, type Config } from "../config";
import * as defaultLimiter from "../middleware/rateLimit";
import { createHttpUtils, type Handler } from "../utils/httpUtil";
import { loadFilterRuleSource, type FilterRuleSource } from "../utils/filterRuleSource";

/**
 * GET /api/v1/filter-rules —— L2 隔离原型的规则源下发（只读）。
 *
 * 边界（Issue #84 / DESIGN-ADS-FILTERING.md §3）：
 * - 只读：规则发布走既有 admin 流程，原型不开放任何写入口；
 * - 默认关闭、标注 Phase 0 未批准，客户端不得据此自动启用 VPN；
 * - 不接收、不保存、不返回任何用户 DNS 查询明细，只做汇总计数于客户端本地；
 * - 规则源损坏时继续使用最后一份有效规则；从未成功加载则返回 503 而不是空规则。
 */
const DEFAULT_SEED = path.join(import.meta.dir, "..", "..", "seed", "filter-rules.json");

export function createFilterRulesApi(
  config: Config = defaultConfig,
  limiter: ReturnType<typeof defaultLimiter.createRateLimiter> = defaultLimiter,
  seedPath: string = DEFAULT_SEED,
) {
  const { errorJson, statusResponse } = createHttpUtils(config);
  const { limitRead } = limiter;
  let lastGood: FilterRuleSource | null = null;

  const latest: Handler = (req, _url, ctx) => {
    if (!limitRead(req, ctx.ip)) return errorJson(429, "rate limited");

    let source: FilterRuleSource;
    try {
      source = loadFilterRuleSource(seedPath);
      lastGood = source;
    } catch {
      // 规则源损坏/过期：优先使用最后一份有效规则，避免一次更新把网络打穿
      if (!lastGood) return errorJson(503, "filter rules unavailable");
      source = lastGood;
    }

    if (req.headers.get("if-none-match") === source.sha256) return statusResponse(304);
    const body = JSON.stringify({ ...source, prototypeOnly: true });
    return new Response(body, {
      status: 200,
      headers: {
        "Content-Type": "application/json; charset=utf-8",
        ETag: source.sha256,
        "Cache-Control": "no-store",
      },
    });
  };

  return { latest };
}
