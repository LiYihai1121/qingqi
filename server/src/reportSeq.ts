import type { Config } from "./config";
import { config as defaultConfig } from "./config";

/**
 * 上报幂等：`(deviceId, epoch)` + `seq` 高水位（DESIGN-SERVER-MOBILE-READINESS §5.1 A2）。
 *
 * 状态规模 O(设备数) 而非 O(事件数)：每个 (deviceId, epoch) 只存一个 lastSeq。
 * epoch 是客户端安装期随机 UUID——设备重装后 seq 从 1 重来，epoch 变化让
 * 「重装」成为一次干净的身份切换，旧高水位不会误杀新装设备的事件。
 *
 * 判定规则（按到达顺序逐条处理）：
 *  - `seq <= lastSeq`        → duplicate（重试/重复投递）
 *  - `seq === lastSeq + 1`   → accepted，推进高水位
 *  - `seq > lastSeq + 1`     → accepted 但**不推进**——队列丢最旧会产生合法
 *    空洞，把它当异常会误杀正常数据；只计入 gaps 供观测
 *  - 首个 seq               → accepted，lastSeq = seq
 *
 * 内存态：重启即丢（§7-4 取舍），重启窗口内的重复量级可接受。
 * 容量 50 000 条 + 24h TTL，与限流桶同构的「Map 插入序淘汰 + 定时回收」。
 */

export interface SeqFilterResult<T> {
  /** 通过高水位判定、应进入存储层的事件（保持原顺序） */
  events: T[];
  /** seq <= lastSeq 的重复事件数 */
  duplicates: number;
  /** seq > lastSeq + 1 的空洞事件数（已接受但未推进高水位） */
  gaps: number;
}

interface Watermark {
  lastSeq: number;
  seenAt: number;
}

const MAX_KEYS = 50_000;
const TTL_MS = 24 * 60 * 60 * 1000;

export function createReportSeqTracker(cfg: Config = defaultConfig) {
  const marks = new Map<string, Watermark>();

  function evict(): void {
    const now = Date.now();
    // TTL 回收：先扫一遍过期项（Map 规模有上限，扫描成本可控）
    for (const [k, w] of marks) {
      if (now - w.seenAt > TTL_MS) marks.delete(k);
    }
    // 容量回收：插入序即近似 LRU，删最旧直到留出余量
    while (marks.size >= MAX_KEYS) {
      const oldest = marks.keys().next().value;
      if (oldest === undefined) break;
      marks.delete(oldest);
    }
  }

  const gcTimer = setInterval(evict, 10 * 60 * 1000);
  gcTimer.unref?.();

  /**
   * 过滤一批事件。`getSeq` 从事件上取 seq；seq 缺失/非法的事件不参与
   * 高水位判定，直接放行（调用方保证旧客户端路径不走到这里）。
   */
  function filter<T>(
    deviceId: string,
    epoch: string,
    events: T[],
    getSeq: (e: T) => number
  ): SeqFilterResult<T> {
    const key = `${deviceId}:${epoch}`;
    let mark = marks.get(key);
    if (!mark) {
      evict();
      mark = { lastSeq: -1, seenAt: Date.now() };
      marks.set(key, mark);
    }
    mark.seenAt = Date.now();

    const accepted: T[] = [];
    let duplicates = 0;
    let gaps = 0;
    for (const e of events) {
      const seq = getSeq(e);
      if (seq <= mark.lastSeq) {
        duplicates++;
        continue;
      }
      if (mark.lastSeq >= 0 && seq > mark.lastSeq + 1) gaps++;
      // 连续序列推进高水位；空洞只接受不推进（后续连续段仍能对齐）
      if (seq === mark.lastSeq + 1) mark.lastSeq = seq;
      else if (mark.lastSeq < 0) mark.lastSeq = seq;
      accepted.push(e);
    }
    return { events: accepted, duplicates, gaps };
  }

  function dispose(): void {
    clearInterval(gcTimer);
    marks.clear();
  }

  /** 仅供测试：清空水位 */
  function _resetForTests(): void {
    marks.clear();
  }

  return { filter, dispose, _resetForTests };
}
