import { describe, expect, it, beforeAll, afterAll } from "bun:test";
import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import { startServer, type AppServer } from "../server";
import { computeRuleDigest, loadFilterRuleSource } from "../src/utils/filterRuleSource";

/**
 * L2 filter-rules 只读路由的服务端契约（Issue #84 隔离原型）。
 *
 * 门禁要点：Phase 0 未通过 → 规则源必须默认关闭、不得携带任何用户 DNS 查询数据、
 * 只提供 GET（规则发布走既有 admin 流程，本原型不开放写接口）、校验和不匹配时
 * 拒绝加载而不是静默下发一份可能被篡改的规则。
 */

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-l2-"));
let server: AppServer;
let base: string;

beforeAll(() => {
  server = startServer({ port: 0, host: "127.0.0.1", adminToken: "test123", dataDir: tmp });
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
  const seedPath = path.join(import.meta.dir, "..", "seed", "filter-rules.json");

  it("loads the bundled seed and reproduces its checksum", () => {
    const source = loadFilterRuleSource(seedPath);
    expect(source.enabledByDefault).toBe(false);
    expect(source.sha256).toBe(computeRuleDigest(source));
  });

  it("rejects a tampered checksum", () => {
    const raw = JSON.parse(fs.readFileSync(seedPath, "utf8")) as Record<string, unknown>;
    raw.sha256 = "0".repeat(64);
    const bad = path.join(tmp, "tampered.json");
    fs.writeFileSync(bad, JSON.stringify(raw));
    expect(() => loadFilterRuleSource(bad)).toThrow();
  });

  it("rejects invalid domains and undisclosed licences", () => {
    const raw = JSON.parse(fs.readFileSync(seedPath, "utf8")) as Record<string, unknown>;
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
});
