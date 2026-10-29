import { test, expect } from "bun:test";
import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import * as http from "node:http";
import { startServer } from "../server";
import { config } from "../src/config";
import { createStore } from "../src/storage/store";

/**
 * `startServer` 的返回类型把 `port` 标成可选（真实实现总会绑定），
 * 这里在测试侧显式收敛成 `number`，避免每处调用都写非空断言。
 */
function portOf(server: { port?: number }): number {
  if (server.port === undefined) throw new Error("startServer 未返回绑定端口");
  return server.port;
}

/**
 * 实例隔离：token / 规则 / 统计 / 访问日志必须各自属于自己那台服务。
 *
 * 为什么是「先后两台」而不是「同时两台」：本机 Bun 1.4.3（Windows）同进程
 * 起两个 `Bun.serve` 时，访问第二个端口的请求会被**第一个实例**的 fetch 处理
 * （最小复现：两个实例各返回不同字符串，第二个端口返回第一个的响应）。
 * 并发双实例在这版运行时上无法测，且失败信息会指向错误的原因（看起来像
 * 鉴权串台，实际是请求根本没到第二台）。
 *
 * 顺序启动同样能抓到真回归：第二台拿到的是不同的 `adminToken` 与数据目录，
 * 若 config / store / accessLog 走模块级单例，上一台的令牌会继续生效、
 * 统计与日志也会串进来——这正是本用例要守的契约。
 */
test("server instances isolate tokens, rules, stats and access logs", async () => {
  const dirs = [0, 1].map(() => fs.mkdtempSync(path.join(os.tmpdir(), "qingqi-instance-")));
  const publish = (port: number, token: string, keyword: string) => fetch(`http://127.0.0.1:${port}/api/v1/rules`, {
    method: "PUT", headers: { "content-type": "application/json", authorization: `Bearer ${token}` },
    body: JSON.stringify({ keywords: [keyword], packages: {} }),
  });
  const latestKeywords = async (port: number): Promise<string[]> => {
    const body = await (await fetch(`http://127.0.0.1:${port}/api/v1/rules/latest`)).json() as any;
    return body.rules.globalKeywords;
  };
  const summaryTotal = async (port: number): Promise<number> =>
    ((await (await fetch(`http://127.0.0.1:${port}/api/v1/stats/summary`)).json()) as any).total;
  const skip = (port: number) => fetch(`http://127.0.0.1:${port}/api/skip`, {
    method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ pkg: "com.example" }),
  });

  try {
    // 第一台：令牌 token-0，发布 first，记一次点击
    const a = startServer({ port: 0, host: "127.0.0.1", dataDir: dirs[0], adminToken: "token-0" });
    try {
      expect((await publish(portOf(a), "token-1", "wrong")).status).toBe(401);
      expect((await publish(portOf(a), "token-0", "first")).status).toBe(200);
      expect((await latestKeywords(portOf(a)))).toEqual(["first"]);
      expect((await skip(portOf(a))).status).toBe(200);
      expect(await summaryTotal(portOf(a))).toBe(1);
      const logs = await (await fetch(`http://127.0.0.1:${portOf(a)}/api/v1/admin/logs`, {
        headers: { authorization: "Bearer token-0" },
      })).json() as any;
      expect(logs.entries.some((entry: any) => entry.path === "/api/skip")).toBe(true);
    } finally {
      await a.closeGracefully();
    }

    // 第二台：令牌 token-1、全新数据目录；上一台的令牌/规则/统计/日志都不得可见
    const b = startServer({ port: 0, host: "127.0.0.1", dataDir: dirs[1], adminToken: "token-1" });
    try {
      expect((await publish(portOf(b), "token-0", "leak")).status).toBe(401);
      expect((await publish(portOf(b), "token-1", "second")).status).toBe(200);
      expect((await latestKeywords(portOf(b)))).toEqual(["second"]);
      expect(await summaryTotal(portOf(b))).toBe(0);
      const logs = await (await fetch(`http://127.0.0.1:${portOf(b)}/api/v1/admin/logs`, {
        headers: { authorization: "Bearer token-1" },
      })).json() as any;
      expect(logs.entries.some((entry: any) => entry.path === "/api/skip")).toBe(false);
    } finally {
      await b.closeGracefully();
    }
    // 默认配置不得被任何一次 startServer 污染
    expect(config.ADMIN_TOKEN).not.toBe("token-0");
    expect(config.ADMIN_TOKEN).not.toBe("token-1");
  } finally {
    dirs.forEach(d => fs.rmSync(d, { recursive: true, force: true }));
  }
});

test("failed acknowledged writes do not consume IDs or counters", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "qingqi-rollback-"));
  const cfg = { ...config, STATS_DIR: path.join(dir, "stats") };
  const store = createStore(cfg);
  const event = { eventId: "id-1", pkg: "com.example", label: "Example", channel: "selector" };
  try {
    fs.writeFileSync(cfg.STATS_DIR, "blocked");
    expect(() => store.recordBatch("device-1", [event])).toThrow();
    expect(store.statsSummary().total).toBe(0);
    fs.unlinkSync(cfg.STATS_DIR);
    expect(store.recordBatch("device-1", [event, event])).toBe(1);
    expect(store.recordBatch("device-1", [event])).toBe(0);
    expect(store.recordBatch("device-2", [event])).toBe(1);
    expect(store.statsSummary().total).toBe(2);
  } finally {
    store.dispose();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("rule publish accepts 2000 applications and rejects stale If-Match", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "qingqi-contract-"));
  const server = startServer({ port: 0, host: "127.0.0.1", dataDir: dir, adminToken: "test-token" });
  const base = `http://127.0.0.1:${server.port}`;
  try {
    const latest = await (await fetch(`${base}/api/v1/rules/latest`)).json() as any;
    const packages = Object.fromEntries(Array.from({ length: 2000 }, (_, i) => [`com.app${i}`, { keywords: ["skip"] }]));
    const publish = () => fetch(`${base}/api/v1/rules`, {
      method: "PUT", headers: { "content-type": "application/json", authorization: "Bearer test-token", "if-match": latest.hash },
      body: JSON.stringify({ keywords: ["new"], packages }),
    });
    expect((await publish()).status).toBe(200);
    expect((await publish()).status).toBe(412);
  } finally {
    await server.closeGracefully();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("event IDs deduplicate durably across restart", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "qingqi-dedup-"));
  const options = { port: 0, host: "127.0.0.1", dataDir: dir };
  let server = startServer(options);
  const report = () => fetch(`http://127.0.0.1:${server.port}/api/v1/reports/batch`, {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ deviceId: "device-123456", events: [{ eventId: "event-1", pkg: "com.example", channel: "selector" }] }),
  });
  try {
    expect((await report()).status).toBe(200);
    await server.closeGracefully();
    server = startServer(options);
    expect((await report()).status).toBe(200);
    const summary = await (await fetch(`http://127.0.0.1:${server.port}/api/v1/stats/summary`)).json() as any;
    expect(summary.total).toBe(1);
  } finally {
    await server.closeGracefully();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("expired cache is evicted and failed writes remain retryable", () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "qingqi-store-"));
  const cfg = { ...config, STATS_DIR: path.join(dir, "stats"), STATS_RETENTION_DAYS: 0 };
  const store = createStore(cfg);
  try {
    store.recordSkip("com.example", "Example", "text");
    fs.writeFileSync(cfg.STATS_DIR, "blocked");
    expect(() => store.flush()).toThrow();
    expect(store.storageHealthy()).toBe(false);
    fs.unlinkSync(cfg.STATS_DIR);
    store.flush();
    expect(store.storageHealthy()).toBe(true);
    expect(store.statsSummary().total).toBe(1);
    store.cleanupOldStats(new Date(Date.now() + 86400000));
    expect(store.statsSummary().total).toBe(0);
  } finally {
    store.dispose();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("background flush retries disk failures and clears degraded health", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "qingqi-retry-"));
  const cfg = { ...config, STATS_DIR: path.join(dir, "stats") };
  const store = createStore(cfg, { flushDelayMs: 10 });
  const originalError = console.error;
  console.error = () => {};
  const until = async (predicate: () => boolean) => {
    const deadline = Date.now() + 2000;
    while (!predicate() && Date.now() < deadline) await Bun.sleep(10);
    expect(predicate()).toBe(true);
  };
  try {
    fs.writeFileSync(cfg.STATS_DIR, "blocked");
    store.recordSkip("com.example", "Example", "text");
    await until(() => !store.storageHealthy());
    fs.unlinkSync(cfg.STATS_DIR);
    await until(() => store.storageHealthy());
    const day = new Date().toISOString().slice(0, 10);
    expect(JSON.parse(fs.readFileSync(path.join(cfg.STATS_DIR, `${day}.json`), "utf8")).byApp["com.example"].count).toBe(1);
  } finally {
    console.error = originalError;
    store.dispose();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("graceful shutdown drains an in-flight upload before disposing storage", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "qingqi-drain-"));
  const server = startServer({ port: 0, dataDir: dir });
  const body = JSON.stringify({ pkg: "com.example" });
  try {
    let upload!: http.ClientRequest;
    const response = new Promise<number>((resolve, reject) => {
      upload = http.request({ hostname: "127.0.0.1", port: server.port, path: "/api/skip", method: "POST", headers: {
        "content-type": "application/json", "content-length": Buffer.byteLength(body),
      } }, res => { res.resume(); res.on("end", () => resolve(res.statusCode!)); });
      upload.on("error", reject);
      upload.write(body.slice(0, 5));
    });
    const deadline = Date.now() + 1000;
    while (server.pendingRequests === 0 && Date.now() < deadline) await Bun.sleep(5);
    expect(server.pendingRequests).toBeGreaterThan(0);
    const closing = server.closeGracefully();
    upload.end(body.slice(5));
    expect(await response).toBe(200);
    await closing;
    const day = new Date().toISOString().slice(0, 10);
    expect(JSON.parse(fs.readFileSync(path.join(dir, "stats", `${day}.json`), "utf8")).byApp["com.example"].count).toBe(1);
  } finally {
    await server.closeGracefully();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
