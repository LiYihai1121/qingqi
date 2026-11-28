import { test, expect } from "bun:test";
import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import { config } from "../src/config";
import { createStore } from "../src/storage/store";

/**
 * A3 事件时间归日 + 钳制（DESIGN-SERVER-MOBILE-READINESS §5.1 A3 / §9）。
 *
 * 关键断言：
 *  - 23:59 发生、00:01 到达的事件归入前一日（不错日）
 *  - ts=0 / 未来 1 年被钳制回服务端当前时间
 *  - 单请求不产生窗口外日分片（伪造 ts 不能造出海量分片文件）
 *  - STATS_EVENT_TIME=off 回退旧行为（忽略 ts）
 *  - STATS_TZ 决定「今天」的边界
 */

function tmpConfig(overrides: Partial<typeof config> = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "qingqi-ts-"));
  return {
    dir,
    cfg: { ...config, STATS_DIR: path.join(dir, "stats"), ...overrides },
  };
}

test("23:59 event arriving after midnight is filed under the event day", () => {
  const { dir, cfg } = tmpConfig();
  const store = createStore(cfg);
  try {
    // 上海时区 2026-11-20 23:59:30 发生的事件，2026-11-21 00:01:30 才到达
    const eventTs = Date.UTC(2026, 10, 20, 15, 59, 30); // = 上海 23:59:30
    const now = Date.UTC(2026, 10, 20, 16, 1, 30); //   = 上海 11-21 00:01:30
    const resolved = store._internal.resolveEventTime(eventTs, now);
    expect(resolved.clamped).toBe(false);
    expect(resolved.day).toBe("2026-11-20");
    expect(resolved.ts).toBe(eventTs);
  } finally {
    store.dispose();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("ts=0 and far-future ts are clamped back to server now", () => {
  const { dir, cfg } = tmpConfig();
  const store = createStore(cfg);
  try {
    const now = Date.UTC(2026, 10, 21, 4, 0, 0);
    for (const bad of [0, now + 365 * 24 * 3600 * 1000, now - 30 * 24 * 3600 * 1000]) {
      const resolved = store._internal.resolveEventTime(bad, now);
      expect(resolved.clamped).toBe(true);
      expect(resolved.ts).toBe(now);
      expect(resolved.day).toBe(store._internal.getDayKey(new Date(now)));
    }
    // 窗口内（6 天前）的离线补报不被钳制
    const ok = store._internal.resolveEventTime(now - 6 * 24 * 3600 * 1000, now);
    expect(ok.clamped).toBe(false);
  } finally {
    store.dispose();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("forged ts cannot create out-of-window day shards via recordBatch", () => {
  const { dir, cfg } = tmpConfig();
  const store = createStore(cfg);
  try {
    const now = Date.now();
    // 一批混合：正常事件 + 伪造远古/未来 ts + 缺 ts
    store.recordBatch("device-ts-001", [
      { pkg: "com.a.app", label: "A", channel: "text", ts: now - 3600_000 },
      { pkg: "com.b.app", label: "B", channel: "text", ts: 0 },
      { pkg: "com.c.app", label: "C", channel: "text", ts: now + 365 * 24 * 3600 * 1000 },
      { pkg: "com.d.app", label: "D", channel: "text" },
    ]);
    store.flush();
    const days = store._internal.listStatsDays();
    const todayKey = store._internal.getDayKey(new Date(now));
    // 1 小时前的事件可能落在昨天或今天（取决于当前时刻），其余三个必须落在今天
    expect(days.every((d) => d <= todayKey)).toBe(true);
    expect(days.length).toBeLessThanOrEqual(2);
    expect(days).toContain(todayKey);
    const summary = store.statsSummary();
    expect(summary.total).toBe(4);
  } finally {
    store.dispose();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("STATS_EVENT_TIME=off ignores client ts entirely", () => {
  const { dir, cfg } = tmpConfig({ STATS_EVENT_TIME: false });
  const store = createStore(cfg);
  try {
    const now = Date.now();
    const yesterdayTs = now - 24 * 3600 * 1000;
    store.recordBatch("device-ts-002", [
      { pkg: "com.a.app", label: "A", channel: "text", ts: yesterdayTs },
    ]);
    store.flush();
    const days = store._internal.listStatsDays();
    expect(days).toEqual([store._internal.getDayKey(new Date(now))]);
  } finally {
    store.dispose();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("STATS_TZ decides the day boundary", () => {
  const { dir, cfg } = tmpConfig({ STATS_TZ: "UTC" });
  const store = createStore(cfg);
  try {
    // 2026-11-21 00:30 上海时间 = 2026-11-20 16:30 UTC：
    // Asia/Shanghai 口径是 11-21，UTC 口径是 11-20
    const instant = Date.UTC(2026, 10, 20, 16, 30, 0);
    expect(store._internal.getDayKey(new Date(instant))).toBe("2026-11-20");
  } finally {
    store.dispose();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("invalid STATS_TZ fails fast at store creation", () => {
  const { dir, cfg } = tmpConfig({ STATS_TZ: "Not/AZone" });
  expect(() => createStore(cfg)).toThrow(/STATS_TZ/);
  fs.rmSync(dir, { recursive: true, force: true });
});
