# 优酷跳过失败诊断方案（TOP30 #18）

> 状态：**已完成首轮实测（2026-10-29），根因已定位**。本文定义真机诊断流程与判定标准；结论以带日期的日志/快照证据为准。

## 0. 实测记录（2026-10-29，Mi 9 / Android 11 / 优酷 11.2.19）

按第 2~4 节流程冷启动优酷并以 `uiautomator dump` 高频抓帧，开屏广告树（xadsdk）关键事实：

- 跳过控件为 `text="跳过广告"`、`resource-id="com.youku.phone:id/splash_ad_txt_skip"` 的 TextView，**`clickable="false"`**；
- 自根节点（`xadsdk_splash_ad_root_view`）到跳过按钮，**整棵树无一 `clickable="true"`**——触摸由 SDK 自行处理，不向无障碍暴露可点击路径；
- 父链 text 全为空，`resource-id` 含 `skip`（本可被 ViewID 通道命中）。

**根因（B 线变异形态）**：引擎匹配成功（「跳过广告」命中关键词「跳过」），但 `SafetyGuard` 可点击路径校验（自身或 4 层祖先须可点击）必然拒绝，坐标手势兜底不执行，广告必然播完。2026-10-11 引入该护栏时「广告跳过按钮均为可点击节点或其容器可点击」的假设被优酷证伪。

**处置（已落地）**：新增包级云规则字段 `gestureFallback`（默认 `false`）——对该包放行「无可点击路径」的引擎已确认目标，以目标自身 bounds 为手势落点；黑名单/合法性/敏感系统界面护栏语义不变。协议见 `docs/api/API.md`，基线单测见 `SafetyGuardTest`「包级手势放权」一节。
>
> 背景：收到真机反馈「优酷开屏广告未能跳过」。代码走查（2026-10-29，main@d08bcc4）未发现优酷专属规则，本方案用于在真机上把失败原因定位到具体环节，再决定改规则还是改引擎参数。

## 1. 现状事实（代码走查结论）

当前对优酷（`com.youku.phone`）生效的只有全局规则，没有任何专属规则：

- 关键词：`跳过` / `跳過` / `skip` / `跳过广告` / `关闭广告`（客户端默认与服务端种子一致）；
- ViewID 包含：`skip` / `jump`；
- 云规则 `packages` 为空，没有优酷选择器。

匹配链路上的硬约束（决定哪些形态必然失败）：

| 约束 | 位置 | 影响 |
|---|---|---|
| 关键词只匹配 ≤12 字符的 text/desc | `SkipRuleEngine`（`maxTextLen=12`） | 聚合了广告主长文案的跳过控件不命中 |
| 遍历预算 500 节点 | `SkipRuleEngine`（`maxNodes=500`） | WebView 等深层级可能截断 |
| 点击前父链黑名单（4 层） | `SafetyGuard`（`DENY_WORDS` 含「下载/安装/开通/继续」等） | 跳过按钮父容器聚合了落地文案即被拦 |
| 可点击路径校验（自身或 4 层祖先可点击） | `SafetyGuard` | 纯文本行不再被坐标手势兜底；包级 `gestureFallback` 开启后放行引擎已确认目标 |
| 同应用点击去抖 1200ms / 全局扫描节流 150ms | `SkipAdService` | 影响有限，但窗口切换校验会放弃旧节点 |

上述形态各自对应一条 JVM 基线单测（`SkipRuleEngineTest` / `SafetyGuardTest` 中标注「优酷开屏场景基线」的用例），改动引擎或护栏前先跑这组测试确认语义变化。

## 2. 前置检查（先排除环境因素）

按序确认，任何一条不满足先修环境再进入第 3 节：

1. 无障碍服务已启用：应用设置页服务状态为开（MIUI 需确认未把服务回置）；
2. 优酷未被禁用：规则页里优酷的整包开关未关闭（`isPackageDisabled` 命中即整包放行）；
3. 不在勿扰时段：`prefs.isDoNotDisturbEnabled` 开启时，时段内服务静默；
4. 云端规则已同步：设置页显示最近同步时间；未配服务器时客户端用本地默认规则，效果等同第 1 节清单。

## 3. 步骤一：判读应用日志（区分「被拦截」与「没匹配到」）

应用日志（`LogRing`）不进 logcat，只能从应用内导出：

1. 清空日志（日志页）；
2. 真机复现：打开优酷 → 等开屏广告出现 → 让广告自然走完或手动跳过；
3. 回到应用 → 日志页导出，搜索 `youku`。

判定：

- 出现 `W/Safety: blocked click on pkg=com.youku.phone` → 引擎**找到了目标**，是护栏拦截（走 §4 A 线）；
- 只有 Service 生命周期日志、无 Safety 告警 → 引擎**没有匹配到目标**（走 §4 B 线）；
- 连 `TYPE_WINDOW_STATE_CHANGED` 相关的扫描都没发生 → 回到 §2 查环境。

## 4. 步骤二：抓取优酷开屏节点树并按线处置

抓取手段：优酷广告在前台时执行

```bash
adb shell uiautomator dump /sdcard/youku-splash.xml && adb pull /sdcard/youku-splash.xml
```

注意：应用内「导出界面快照（调试）」抓的是**导出瞬间的前台窗口**（按下按钮时前台是本应用），适合本应用自测浮层；抓优酷请用 `uiautomator dump`。XML 里关注跳过控件的 `text` / `content-desc` / `resource-id` / `clickable` 及其各级父容器的聚合 `text`。

### A 线：Safety 拦截（日志有 blocked click）

1. 在 XML 中找到跳过控件，沿父链检查 4 层内是否有「下载/开通/安装/继续」等聚合文案；
2. 若确认是落地文案聚合进父容器导致误拦：
   - 短期处置：在云端为 `com.youku.phone` 配置专属选择器，直接锚定控件本身（模板见 §5），绕开含落地文案的容器节点；
   - 长期处置（需评审，勿单方面改）：放宽 `SafetyGuard` 父链黑名单或引入按包豁免——这会整体削弱护栏，必须带真机证据走单独 PR；
3. 回归：重跑优酷基线单测 + 自测浮层，确认拦截语义变化被显式声明。

### B 线：无匹配记录

在 XML 中定位跳过控件，按形态处置：

| 控件形态 | 处置 |
|---|---|
| 有「跳过」文本但超 12 字符 | 为优酷配选择器 `[text*="跳过"]`（选择器通道不设 12 字符上限） |
| 无文本、`resource-id` 含 skip/jump | 现有 ViewID 规则本应命中——若日志仍无记录，怀疑遍历预算或窗口归属问题，回填证据开 Issue |
| 无文本、`resource-id` 不含 skip/jump | 配专属选择器（模板见 §5） |
| 跳过控件不在 dump 里 | 广告在独立窗口/SurfaceView 渲染，无障碍拿不到——记录为「能力边界」，按 ROADMAP-ADS-VERIFY 口径登记，不改规则 |

## 5. 选择器规则草案模板

> 仅在拿到快照证据、确认控件形态后填入真实 `resource-id`/文本，再经服务端 `POST /api/rules` 下发；禁止在无证据时预填猜测的 viewId。

```json
{
  "packages": {
    "com.youku.phone": {
      "keywords": [],
      "viewIds": [],
      "selectors": [
        "[text*=\"跳过\"][click=\"true\"]",
        "[vid$=\":id/jump_over\"][click=\"true\"]"
      ],
      "disabled": false
    }
  }
}
```

选择器模板（按快照证据二选一或组合，文法见 `docs/planning/DESIGN-PHASE1-SELECTOR.md` §3）：

- `[text*="跳过"][click="true"]`：可点击的跳过文本控件；
- `[vid$=":id/<真实控件名>"][click="true"]`：无文本自绘按钮；
- `[desc*="跳过"] [click="true"]`：跳过描述容器内任意可点击节点（兜底主力）；
- 长度/深度限制：单条 ≤256 字符、compound ≤4、value ≤64，超出会被解析器拒绝。

## 6. 验证与回填

> **当前状态（2026-10-29 23:46）**：含 `gestureFallback` 支持的构建已装入真机（数据保留，签名兼容），无障碍服务已用新构建重绑并清理了旧组件残留。**剩余步骤：云端为 `com.youku.phone` 下发 `"gestureFallback": true` 后复测**——下发需服务器 `ADMIN_TOKEN`（`PUT /api/v1/rules` 需携带当前规则全集合并该包字段，见 `docs/api/API.md`），复测需设备空闲。TOP30 第 18 行已按上述状态回填，未标记通过。

1. 规则下发后重复 §3 复现至少 3 次，按 TOP30 口径记录成功率与误触率（误触必须为 0）；
2. 结果回填 `TOP30-REAL-DEVICE-ACCEPTANCE.md` 第 18 行（包名/版本/状态），并注明本诊断文档编号；
3. 若结论指向引擎参数（`maxTextLen`/`maxNodes`/护栏语义），先在基线单测中把目标行为写成失败测试，再改实现（契约先行）。
