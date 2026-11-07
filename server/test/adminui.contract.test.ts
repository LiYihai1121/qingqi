import { describe, expect, it } from "bun:test";
import fs from "node:fs";
import path from "node:path";

/**
 * 管理后台编辑器（server/public/admin.html）的字段往返契约。
 *
 * admin.html 是纯静态页，仓库里没有 DOM 测试环境；历史上正是缺少契约看护，
 * collectRules 不收集 gestureFallback，导致管理后台每次发布都把所有包的
 * 手势放权静默重置为 false（服务端 cleanRules 对缺失字段一律按 false 清洗，
 * 见 unit.test.ts 的 cleanRules 向量）。这里以源码断言守住「发布载荷必须
 * 覆盖 AppRule 全部字段」与「模拟器只发送 /rules/test 声明的字段」两条不变式。
 */

const html = fs.readFileSync(path.join(import.meta.dir, "..", "public", "admin.html"), "utf8");

/** 截取 admin.html 内联脚本的函数源码：从声明起，到该函数体列首的 `}` 为止 */
function fn(name: string): string {
  const start = html.indexOf(`function ${name}(`);
  if (start < 0) return "";
  const end = html.indexOf("\n}", start);
  return end < 0 ? "" : html.slice(start, end + 2);
}

describe("admin 编辑器字段往返契约（admin.html 源码）", () => {
  it("renderPackages 渲染包级手势放权当前值", () => {
    const render = fn("renderPackages");
    expect(render).toContain("pkg-gesture");
    expect(render).toContain("r.gestureFallback");
  });

  it("addPackage 新增包时初始化全部 AppRule 字段", () => {
    expect(fn("addPackage")).toContain("gestureFallback: false");
  });

  it("collectRules 收集全部 AppRule 字段（含手势放权）", () => {
    const collect = fn("collectRules");
    for (const field of ["keywords:", "viewIds:", "selectors:", "disabled:", "gestureFallback:"]) {
      expect(collect).toContain(field);
    }
    expect(collect).toContain(".pkg-gesture");
  });

  it("规则模拟器只发送 /rules/test 声明的 pkg/text/viewId 字段", () => {
    const sim = fn("runSimulator");
    // keywords/viewIds 曾被误发给 /api/v1/rules/test，服务端只读 pkg/text/viewId，
    // 字段被静默忽略且注释误导「用当前表单的规则」
    expect(sim).not.toContain("keywords:");
    expect(sim).not.toContain("viewIds:");
    expect(sim).toContain("text:");
    expect(sim).toContain("viewId:");
  });
});
