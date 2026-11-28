import { config as defaultConfig, type Config } from "../config";
import * as defaultStore from "../storage/store";
import * as defaultLimiter from "../middleware/rateLimit";
import { createHttpUtils, type Handler } from "../utils/httpUtil";
import { createValidator } from "../utils/validate";

export function createReportsApi(
  config: Config = defaultConfig,
  store: ReturnType<typeof defaultStore.createStore> = defaultStore,
  limiter: ReturnType<typeof defaultLimiter.createRateLimiter> = defaultLimiter
) {
  const { recordBatch } = store;
  const { limitWrite, limitReport, probeReportIp } = limiter;
  const { jsonResponse, errorJson, readBody, safeJsonParse, errorResponseFrom } = createHttpUtils(config);
  const { cleanBatchReport, cleanReportEvent, cleanLabel } = createValidator(config);

  const v0_skip: Handler = async (req, _url, ctx) => {
    if (!limitWrite(req, ctx.ip)) return errorJson(429, "rate limited");
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
    const ev = cleanReportEvent(parsed);
    if (!ev) return errorJson(400, "invalid skip payload");
    // label 会持久化并经统计接口下发到所有端：cleanReportEvent 已做截断/回退，
    // 再过 cleanLabel 剥控制字符（跨端 UI/日志 注入防护）后入库
    recordBatch("legacy", [{ ...ev, label: cleanLabel(ev.label), eventId: undefined }]);
    return jsonResponse({ ok: true });
  };

  const v1_batchReport: Handler = async (req, _url, ctx) => {
    // 读体之前先按来源 IP 探测容量（不扣减），阻断未认证的大请求体内存放大
    if (!probeReportIp(req, ctx.ip)) return errorJson(429, "rate limited");
    let body: string;
    try {
      body = await readBody(req);
    } catch (e: any) {
      return errorResponseFrom(e);
    }
    let parsed: any;
    try {
      parsed = safeJsonParse(body, config.MAX_BATCH_EVENTS * 8 + 20);
    } catch (e: any) {
      return errorResponseFrom(e);
    }
    const cleaned = cleanBatchReport(parsed);
    if (!cleaned) return errorJson(400, "invalid batch report");
    if (!limitReport(req, ctx.ip, cleaned.deviceId)) {
      return errorJson(429, "rate limited");
    }
    const accepted = recordBatch(cleaned.deviceId, cleaned.events);
    return jsonResponse({ ok: true, accepted, duplicates: cleaned.events.length - accepted });
  };

  return { v0_skip, v1_batchReport };
}

export const { v0_skip, v1_batchReport } = createReportsApi();
