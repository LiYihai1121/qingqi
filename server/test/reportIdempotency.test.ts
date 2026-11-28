import { test, expect } from "bun:test";
import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import { startServer } from "../server";

/**
 * A2 幂等：epoch + seq 高水位（DESIGN-SERVER-MOBILE-READINESS §5.1 A2 / §9）。
 *
 * 关键断言：
 *  - 重复 seq 只计一次（duplicates 计数）
 *  - 空洞不推进高水位（队列丢最旧的合法形态不被误杀）
 *  - epoch 变化后旧 seq 重新可用（重装 = 干净的身份切换）
 *  - 无 epoch/seq 的旧载荷行为不变
 */

const EPOCH = "epoch-test-0001";
const DEVICE = "device-idem-001";

function reportBody(events: Array<Record<string, unknown>>, epoch: string | undefined = EPOCH) {
  return JSON.stringify({
    deviceId: DEVICE,
    ...(epoch === undefined ? {} : { epoch }),
    events,
  });
}

function ev(seq: number, pkg = "com.example.app") {
  return { seq, pkg, label: "Example", channel: "text", ts: Date.now() };
}

async function post(base: string, body: string) {
  const res = await fetch(`${base}/api/v1/reports/batch`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body,
  });
  return { status: res.status, json: (await res.json()) as any };
}

async function total(base: string): Promise<number> {
  const s = (await (await fetch(`${base}/api/v1/stats/summary`)).json()) as any;
  return s.total;
}

test("duplicate seq counts once; response reports duplicates", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "qingqi-idem-"));
  const server = startServer({ port: 0, host: "127.0.0.1", dataDir: dir });
  const base = `http://127.0.0.1:${server.port}`;
  try {
    const first = await post(base, reportBody([ev(1), ev(2), ev(3)]));
    expect(first.status).toBe(200);
    expect(first.json).toMatchObject({ ok: true, accepted: 3, duplicates: 0, gaps: 0 });

    // 整批重发（响应丢失重试的典型形态）
    const retry = await post(base, reportBody([ev(1), ev(2), ev(3)]));
    expect(retry.json).toMatchObject({ ok: true, accepted: 0, duplicates: 3 });
    expect(await total(base)).toBe(3);

    // 部分重叠：seq 3 重复 + seq 4 新增
    const mixed = await post(base, reportBody([ev(3), ev(4)]));
    expect(mixed.json).toMatchObject({ accepted: 1, duplicates: 1 });
    expect(await total(base)).toBe(4);
  } finally {
    await server.closeGracefully();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("seq gaps are accepted but do not advance the watermark", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "qingqi-idem-"));
  const server = startServer({ port: 0, host: "127.0.0.1", dataDir: dir });
  const base = `http://127.0.0.1:${server.port}`;
  try {
    // 队列丢最旧：客户端 seq 1..5 只剩 1,2,4,5（3 被丢）
    const r1 = await post(base, reportBody([ev(1), ev(2), ev(4), ev(5)]));
    expect(r1.json).toMatchObject({ accepted: 4, gaps: 2 });
    expect(await total(base)).toBe(4);

    // 空洞被后续重传补上：seq 3 仍然可用（高水位停在 2，没被 4/5 推进）
    const r2 = await post(base, reportBody([ev(3)]));
    expect(r2.json).toMatchObject({ accepted: 1, duplicates: 0, gaps: 0 });
    expect(await total(base)).toBe(5);

    // 补洞后高水位续走：seq 6 连续推进
    // 注意：4/5 作为空洞事件未推进水位（停在 3），seq 6 相对水位仍是空洞——
    // gaps 是观测计数，不影响接受；水位要等 4 号补传才继续推进。
    const r3 = await post(base, reportBody([ev(6)]));
    expect(r3.json).toMatchObject({ accepted: 1, gaps: 1 });
    expect(await total(base)).toBe(6);
  } finally {
    await server.closeGracefully();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("epoch change resets the watermark (reinstall scenario)", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "qingqi-idem-"));
  const server = startServer({ port: 0, host: "127.0.0.1", dataDir: dir });
  const base = `http://127.0.0.1:${server.port}`;
  try {
    await post(base, reportBody([ev(1), ev(2)], "epoch-old-0001"));
    // 重装：同 deviceId、新 epoch、seq 从 1 重来——不得被判重复
    const r = await post(base, reportBody([ev(1), ev(2)], "epoch-new-0002"));
    expect(r.json).toMatchObject({ accepted: 2, duplicates: 0 });
    expect(await total(base)).toBe(4);
    // 旧 epoch 的重试仍被正确判重（互不干扰）
    const old = await post(base, reportBody([ev(1)], "epoch-old-0001"));
    expect(old.json).toMatchObject({ accepted: 0, duplicates: 1 });
  } finally {
    await server.closeGracefully();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("legacy payload without epoch/seq keeps old behavior", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "qingqi-idem-"));
  const server = startServer({ port: 0, host: "127.0.0.1", dataDir: dir });
  const base = `http://127.0.0.1:${server.port}`;
  try {
    // 无 epoch：走现状路径，无幂等
    const r1 = await post(base, reportBody([{ pkg: "com.example.app", label: "E", channel: "text", ts: Date.now() }], undefined));
    expect(r1.status).toBe(200);
    expect(r1.json.ok).toBe(true);
    // 同一载荷重发：旧路径不判重（eventId 缺失时本就无去重语义）
    const r2 = await post(base, reportBody([{ pkg: "com.example.app", label: "E", channel: "text", ts: Date.now() }], undefined));
    expect(r2.json.ok).toBe(true);
    expect(await total(base)).toBe(2);

    // epoch 存在但事件缺 seq：整批退回旧路径（不做高水位判定）
    const r3 = await post(base, JSON.stringify({
      deviceId: DEVICE,
      epoch: EPOCH,
      events: [{ pkg: "com.example.app", label: "E", channel: "text", ts: Date.now() }],
    }));
    expect(r3.json.ok).toBe(true);
    expect(await total(base)).toBe(3);
  } finally {
    await server.closeGracefully();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
