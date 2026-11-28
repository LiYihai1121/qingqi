import { test, expect, describe } from "bun:test";
import { config, validateConfig, type Config } from "../src/config";
import { startServer } from "../server";

describe("configuration validation", () => {
  test("accepts valid default configuration", () => {
    expect(() => validateConfig(config)).not.toThrow();
  });

  test("rejects invalid PORT configuration", () => {
    const invalidPortCfg: Config = { ...config, PORT: -1 };
    expect(() => validateConfig(invalidPortCfg)).toThrow(/Invalid PORT/);

    const outOfRangePortCfg: Config = { ...config, PORT: 70000 };
    expect(() => validateConfig(outOfRangePortCfg)).toThrow(/Invalid PORT/);
  });

  test("rejects invalid MAX_BODY configuration", () => {
    const invalidBodyCfg: Config = { ...config, MAX_BODY: 0 };
    expect(() => validateConfig(invalidBodyCfg)).toThrow(/Invalid MAX_BODY/);
  });

  test("rejects unmatched TLS configuration", () => {
    const unmatchedTlsCfg: Config = { ...config, TLS_CERT: "cert.pem", TLS_KEY: "" };
    expect(() => validateConfig(unmatchedTlsCfg)).toThrow(/TLS_CERT and TLS_KEY/);
  });

  test("startServer enforces validation on startup options", () => {
    expect(() => startServer({ port: -5 })).toThrow();
  });
});
