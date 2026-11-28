import { describe, expect, it, beforeAll, afterAll, afterEach } from "bun:test";
import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import { startServer, type AppServer } from "../server";
import { config } from "../src/config";
import { createFilterRulesApi } from "../src/api/filterRulesApi";
import { createRateLimiter } from "../src/middleware/rateLimit";
import { computeRuleDigest, loadFilterRuleSource } from "../src/utils/filterRuleSource";

/**
 * L2 filter-rules 只读路由的服务端契约（Issue #84 隔离原型）。
 *
 * 门禁要点：Phase 0 未通过 → 规则源必须默认关闭、不得携带任何用户 DNS 查询数据、
 * 只提供 GET（规则发布走既有 admin 流程，本原型不开放写接口）、校验和不匹配时
 * 拒绝加载而不是静默下发一份可能被篡改的规则。
 *
 * 装配约束（实测）：Bun 在同一进程里起第二个 `Bun.serve` 时，新端口的请求仍会被
 * 第一个 server 的 fetch 闭包接管（`Connection: close` 也绕不开）。因此全文件只用
 * **一台**服务器，通过 `startServer({ filterRulesPath })` 注入一份可改写的临时规则源；
 * 「从未成功加载 → 503」这条需要全新实例的场景，改为直接调用 handler，不起第二台服务器。
 *
 * beforeAll / afterAll 放在文件顶层而非某个 describe 内：临时目录与测试服务器被多个
 * describe 共用，describe 级 afterAll 会在后续 describe 之前就删掉临时目录。
 */

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-l2-"));
const SEED_PATH = path.join(import.meta.dir, "..", "seed", "filter-rules.json");
const RULE_PATH = path.join(tmp, "filter-rules.json");
let server: AppServer;
let base: string;

const seedRaw = (): Record<string, unknown> =>
  JSON.parse(fs.readFileSync(SEED_PATH, "utf8")) as Record<string, unknown>;

const signInto = (file: string, raw: Record<string, unknown>): Record<string, unknown> => {
  raw.sha256 = computeRuleDigest(raw);
  fs.writeFileSync(file, JSON.stringify(raw));
  return raw;
};

beforeAll(() => {
  fs.writeFileSync(RULE_PATH, fs.readFileSync(SEED_PATH));
  const dataDir = path.join(tmp, "data");
  fs.mkdirSync(dataDir, { recursive: true });
  server = startServer({
    port: 0,
    host: "127.0.0.1",
    adminToken: "test123",
    dataDir,
    filterRulesPath: RULE_PATH,
  });
  base = `http://127.0.0.1:${server.port}`;
});

afterAll(async () => {
  await server.closeGracefully();
  fs.rmSync(tmp, { recursive: true, force: true });
});

describe("GET /api/v1/filter-rules (L2 isolated prototype)", () => {
  it("returns an auditable, disabled-by-default rule source", async () => {
    const res = await fetch(`${base}/api/v1/filter-rules`);
    expect(res.status).toBe(200);
    const body = (await res.json()) as Record<string, unknown>;
    expect(body.schemaVersion).toBe(1);
    expect(body.enabledByDefault).toBe(false);
    expect(body.phase0Approved).toBe(false);
    expect(typeof body.source).toBe("string");
    expect(typeof body.license).toBe("string");
    expect(body.license).not.toBe("");
    expect(typeof body.sha256).toBe("string");
    expect((body.sha256 as string).length).toBe(64);
    expect(Array.isArray(body.domains)).toBe(true);
    expect(Array.isArray(body.allowlist)).toBe(true);
  });

  it("marks the payload as prototype-only and non-cacheable", async () => {
    const res = await fetch(`${base}/api/v1/filter-rules`);
    const body = (await res.json()) as Record<string, unknown>;
    expect(body.prototypeOnly).toBe(true);
    expect(res.headers.get("cache-control")).toBe("no-store");
    expect(res.headers.get("etag")).toBe(body.sha256 as string);
  });

  it("exposes an ETag and answers 304 on If-None-Match", async () => {
    const first = await fetch(`${base}/api/v1/filter-rules`);
    const etag = first.headers.get("etag");
    expect(etag).toBeTruthy();
    const second = await fetch(`${base}/api/v1/filter-rules`, {
      headers: { "If-None-Match": etag as string },
    });
    expect(second.status).toBe(304);
  });

  it("is read-only: writes are not routed", async () => {
    for (const method of ["PUT", "POST", "DELETE"]) {
      const res = await fetch(`${base}/api/v1/filter-rules`, {
        method,
        headers: { "Content-Type": "application/json" },
        body: "{}",
      });
      expect(res.status).toBe(404);
    }
  });

  it("never carries user DNS query data in the payload", async () => {
    const body = (await (await fetch(`${base}/api/v1/filter-rules`)).json()) as Record<string, unknown>;
    for (const banned of ["queries", "queryLog", "dnsQueryHistory", "clients", "deviceIds"]) {
      expect(body).not.toHaveProperty(banned);
    }
  });
});

describe("filter rule source validation", () => {
  it("loads the bundled seed and reproduces its checksum", () => {
    const source = loadFilterRuleSource(SEED_PATH);
    expect(source.enabledByDefault).toBe(false);
    expect(source.sha256).toBe(computeRuleDigest(source));
  });

  it("rejects a tampered checksum", () => {
    const raw = seedRaw();
    raw.sha256 = "0".repeat(64);
    const bad = path.join(tmp, "tampered.json");
    fs.writeFileSync(bad, JSON.stringify(raw));
    expect(() => loadFilterRuleSource(bad)).toThrow();
  });

  it("rejects invalid domains and undisclosed licences", () => {
    const raw = seedRaw();
    const withBadDomain: Record<string, unknown> = { ...raw, domains: ["*.example.com"] };
    withBadDomain.sha256 = computeRuleDigest(withBadDomain);
    const badDomain = path.join(tmp, "bad-domain.json");
    fs.writeFileSync(badDomain, JSON.stringify(withBadDomain));
    expect(() => loadFilterRuleSource(badDomain)).toThrow();

    const noLicence: Record<string, unknown> = { ...raw, license: "" };
    noLicence.sha256 = computeRuleDigest(noLicence);
    const badLicence = path.join(tmp, "bad-license.json");
    fs.writeFileSync(badLicence, JSON.stringify(noLicence));
    expect(() => loadFilterRuleSource(badLicence)).toThrow();
  });

  it("rejects a source that is enabled by default or claims Phase 0 approval", () => {
    const patches: Record<string, unknown>[] = [
      { enabledByDefault: true },
      { phase0Approved: true },
      { schemaVersion: 2 },
      { domains: "not-an-array" },
    ];
    patches.forEach((patch, index) => {
      const file = path.join(tmp, `reject-${index}.json`);
      signInto(file, { ...seedRaw(), ...patch });
      expect(() => loadFilterRuleSource(file)).toThrow();
    });
  });

  it("rejects missing provenance metadata", () => {
    for (const field of ["source", "license", "version", "updatedAt", "note"]) {
      const file = path.join(tmp, `missing-${field}.json`);
      signInto(file, { ...seedRaw(), [field]: "" });
      expect(() => loadFilterRuleSource(file)).toThrow();
    }
  });

  it("rejects wildcards, paths, single labels, URLs and uppercase domains", () => {
    const domains = ["*.example.com", "example.com/path", "localhost", "Ads.Example.com", "http://example.com"];
    domains.forEach((domain, index) => {
      const file = path.join(tmp, `domain-${index}.json`);
      signInto(file, { ...seedRaw(), domains: [domain] });
      expect(() => loadFilterRuleSource(file)).toThrow();
    });
  });
});

describe("filter-rules fallback on the injected rule source", () => {
  afterEach(() => {
    fs.writeFileSync(RULE_PATH, fs.readFileSync(SEED_PATH));
  });

  it("forwards startServer.filterRulesPath instead of reading the bundled seed", async () => {
    const raw = signInto(RULE_PATH, { ...seedRaw(), source: "injected-fixture" });
    const res = await fetch(`${base}/api/v1/filter-rules`);
    expect(res.status).toBe(200);
    const body = (await res.json()) as Record<string, unknown>;
    expect(body.source).toBe("injected-fixture");
    expect(body.prototypeOnly).toBe(true);
    expect(body.enabledByDefault).toBe(false);
    expect(body.phase0Approved).toBe(false);
    expect(res.headers.get("etag")).toBe(raw.sha256 as string);
  });

  it("keeps the last good rule source when the file is later corrupted", async () => {
    const good = signInto(RULE_PATH, { ...seedRaw(), version: "9.9.9-injected" });
    const before = await fetch(`${base}/api/v1/filter-rules`);
    expect(before.status).toBe(200);
    expect(((await before.json()) as Record<string, unknown>).version).toBe("9.9.9-injected");

    fs.writeFileSync(RULE_PATH, "{ this is not json");
    const after = await fetch(`${base}/api/v1/filter-rules`);
    expect(after.status).toBe(200);
    const body = (await after.json()) as Record<string, unknown>;
    expect(body.sha256).toBe(good.sha256);
    expect(after.headers.get("etag")).toBe(good.sha256 as string);
  });

  it("answers 503 rather than serving an empty rule set when nothing was ever valid", () => {
    // 需要「从未成功加载」的全新实例；同进程再起一台 Bun.serve 会命中上面的闭包复用坑，
    // 故直接调用 handler，等价于 createApi 里注册的那个 latest。
    const limiter = createRateLimiter(config);
    try {
      const api = createFilterRulesApi(config, limiter, path.join(tmp, "never-valid.json"));
      const req = new Request("http://127.0.0.1/api/v1/filter-rules");
      const res = api.latest(req, new URL(req.url), { ip: "127.0.0.1" }) as Response;
      expect(res.status).toBe(503);
    } finally {
      limiter.dispose();
    }
  });
});
