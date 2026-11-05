# L2/L3 隔离原型设计（DESIGN-L2L3-ISOLATED-PROTOTYPE）

> 状态：**隔离原型，未获准实现、未获准发布**；最后更新：2026-11-05。对应 Issue #84。
>
> 本文件只描述「为了让 Phase 0 合规评审有可核对的代码事实，先做一份不可发布的原型」，
> **不改变** [DESIGN-ADS-FILTERING.md](DESIGN-ADS-FILTERING.md) 的 `Proposed` / `Deferred` 状态，
> 也不代表 [ROADMAP-ADS.md](ROADMAP-ADS.md) 中 M2 / M3 的出口条件已达成。
> Phase 0 合规评审通过并留下书面批准记录之前，本原型**不得进入发布分支**、不得进入主 APK、不得对外分发。

## 1. 决策口径与适用范围

[DESIGN-ADS-FILTERING.md](DESIGN-ADS-FILTERING.md) 第 9 节当前决定为：L1 维持现有实现；L2 只可做隔离原型；L3 暂缓；L4 先做权限与数据保护设计。Issue #84 仍为 `OPEN`，Phase 0 合规评审未通过。

因此本次交付的目标被刻意收窄为**可编译、可测试、可撤销、可回滚的隔离原型**：用代码把「能力层的边界」变成可核对的事实，供后续合规评审与真机验证使用，而不是交付一项用户可开启的功能。

| 维度 | 本次交付 | 明确不做 |
| --- | --- | --- |
| L2（M2 / DNS 过滤） | 独立 Gradle library 模块，本地 `VpnService`、纯 JVM 数据面、只读规则源 | 接入 `:app`、上架开关、默认启用、第三方规则集、真机验收结论 |
| L3（M3 / 防摇一摇） | 独立 library 模块内的纯 JVM 判定与状态机骨架 | Xposed / LSPosed Hook、独立可选 APK、插桩、注册 Android 组件 |
| 服务端 | `GET /api/v1/filter-rules` 只读规则源 | 规则写入口、设备标识、查询明细上报 |
| 发布 | 独立 feature 分支，仅供评审 | 合并 `main`、打 tag、产出可安装制品 |

## 2. 硬边界（机器可执行的约束）

以下边界由 `client/app/src/test/java/com/qingqi/adskip/arch/CapabilityIsolationContractTest.kt` 以纯 JVM 文件系统扫描强制，不依赖 Android SDK、也不需要真机：

1. `:capability:l2vpn` 与 `:capability:l3shake` 必须在 `client/settings.gradle.kts` 注册且可独立编译，但 `client/app/build.gradle.kts` **不得**出现对项目两者的依赖；`include(":app")` 必须排在最前（`ProjectStructureTest.appModule()` 取第一个带清单的模块）。
2. `VpnService` 组件、`BIND_VPN_SERVICE` 权限只出现在 L2 模块自己的 `AndroidManifest.xml`；主 APK 清单不得出现两者。
3. L2 源码不得出现 `SSLSocket`、`TrustManager`、`KeyStore`、`CertificateFactory`、`HTTP_PROXY`、`installCA`、`MITM`；必须显式声明 `DNS_ONLY`。
4. L2 必须 `DEFAULT_ENABLED = false`、启动前校验 `consentGranted`、停止后要求 `networkRestoreRequired`，并使用 `setBlocking(false)` 与 `protect(upstream)`。
5. L3 源码不得出现 `de.robv.android.xposed`、`XposedBridge`、`XposedHelpers`、`handleLoadPackage`、`findAndHookMethod`、`LSPosed`；模块内不得存在 `AndroidManifest.xml`。
6. L3 必须 `DEFAULT_ENABLED = false`、`REQUIRES_SEPARATE_APPROVAL = true`、存在 `NOT_INSTALLED` 状态与 `isProtected` 保护判定。
7. 两个能力模块互不依赖，也都不依赖 `:app`。
8. 服务端 `filter-rules` 路由只读、不读取请求体、不暴露查询日志字段；种子规则源必须 `enabledByDefault: false`。
9. 本文件必须存在、写明 Phase 0 门禁与「不得进入发布分支」，并登记到 [docs/README.md](../README.md)。

与既有 `ArchitectureBoundaryTest` 的关系：后者扫描 `:app` 内的五层包依赖，而能力层**不在 `:app` 的编译类路径里**，跨模块边界只能用源码与构建脚本文本守护，故另立上述契约。

## 3. 模块与依赖拓扑

```text
client/
├── app/                      主 APK（本次未改动其依赖）
├── capability/
│   ├── l2vpn/                :capability:l2vpn   本地 DNS-only VpnService 原型
│   └── l3shake/              :capability:l3shake 纯 JVM 防摇一摇 PoC 骨架
└── build-logic/convention/    adskip.android.library 约定插件（本次新增）

:app ──✗──> :capability:l2vpn      （刻意不连线）
:app ──✗──> :capability:l3shake     （刻意不连线）
:capability:l2vpn ──✗──> :capability:l3shake
```

两个模块使用新增的 `adskip.android.library` 约定插件：统一 AGP 版本（`compileSdk 37` / `minSdk 26`）、Java 17、ktlint 与 `unitTests.isReturnDefaultValues = true`，避免原型各自维护一套构建配置。该插件只服务于能力层，不改变 `adskip.android.application` 的行为。

因为 `:app` 不依赖能力模块，Android 的 manifest 合并与资源打包都不会把 `VpnService` 带进主 APK；即便模块参与 `assembleDebug`，产出的也是独立 library 制品，而非用户可安装功能。这是「物理隔离」而非「开关隔离」——开关可以被误打开，编译期依赖不存在则不能。

## 4. L2：本地 DNS 过滤原型

### 4.1 规则源与可追溯性

`HostsRuleParser` 采用保守解析：只接受以 `0.0.0.0` / `127.0.0.1` / `::` 为 sink、后跟合法主机名的 hosts 行；拒绝通配、路径、Adblock 语法、裸 IP、`localhost` 与无法归一化的输入。任何无法解析的内容不会「尽力猜测」，而是整份规则集判为无效并回退为全放行。

`FilterRuleSourceMeta` 强制携带 `source`、`license`、`version`、`updatedAt`、`sha256`。**本次种子规则源 `server/seed/filter-rules.json` 的 `domains` 与 `allowlist` 均为空数组**，刻意不捆绑任何第三方规则集，以免在合规评审前引入许可证与再分发风险。

`FilterRuleSet.decide()` 的顺序是：白名单优先 → 精确/子域匹配 → 默认放行。`notads.example.com` 不会被 `ads.example.com` 命中，避免「看起来像」的误伤。

### 4.2 判定与 fail-open

`DnsFilterEngine.decide()` 在以下任一条件成立时返回 `BYPASS`（不拦截）：未启用、未获显式授权、规则集无效或缺失、DNS 报文无法解析、QTYPE 不是 A/AAAA。只有「已启用 + 已授权 + 规则有效 + 标准 A/AAAA 查询 + 命中规则」才返回 `BLOCK`。

这条规则的意义是：**原型在任何异常路径上都退化成「没有这个能力」，而不是「把用户的网络断掉」**。返回值只含决策与原因字符串，不含域名。

### 4.3 TUN 数据面

`DnsTunnelLoop` 只识别 IPv4 / UDP / 目的端口 53 且未分片的报文，从中取出 DNS 载荷；其余返回 `Unhandled`（不解析、不改写、不回注）。命中规则时 `nxdomainResponse()` 只保留问题段、清空回答/授权/附加计数、置 QR 与 RCODE=3（NXDOMAIN），由 `wrapReply()` 重新封装 IPv4/UDP 并写回 TUN，**不发起任何外部连接**；未命中或 fail-open 时把原始查询交给上游解析器。

`L2DnsVpnService` 的 Builder 只做四件事：`addAddress(10.233.0.2, 32)`、`addDnsServer(1.1.1.1)`、`addRoute(1.1.1.1, 32)`、`setBlocking(false)`。路由被收窄到单个解析器地址，普通应用流量不经过 TUN。

上游 `DatagramSocket` 必须先经 `VpnService.protect()` 排除出 VPN：因为 `1.1.1.1/32` 已被路由进 TUN，未保护的查询会被自己的隧道重新捕获，形成无限回环。`CapabilityIsolationContractTest` 把 `protect(upstream)` 与 `setBlocking(false)` 都列为字面量断言，防止后续改动悄悄移除。

已知限制：`addRoute` 只能按 IP 网段、不能按端口过滤，因此发往 `1.1.1.1` 的非 DNS 报文会进入 TUN 且被 `Unhandled` 丢弃而不是转发。TUN 是三层接口，把出站报文原样写回会被协议栈误判为入站包，原型没有实现受保护 socket 的通用转发。该行为在真机验证前不得视为可接受，列入第 9 节未完成项。

### 4.4 启停状态机与可撤销性

`L2VpnController` 的状态流为 `DISABLED → AWAITING_CONSENT → RUNNING → STOPPED`。`requestStart(false)` 直接拒绝；`onVpnEstablished()` 只有在 `AWAITING_CONSENT` 才进入 `RUNNING`；`stop()` 与 `onUnexpectedExit()` 走同一条恢复路径，把 `networkRestoreRequired` 置回 `true`。服务 `onDestroy()` 关闭 TUN 描述符、中断工作线程并复位状态，`onRevoke()` 立即 `stopSelf()`。

`VpnService` 只在 intent 携带 `EXTRA_CONSENT_GRANTED=true` 时启动，且返回 `START_NOT_STICKY`——进程被杀后不会自动重启，避免「用户以为关掉了但能力还在后台跑」。

### 4.5 隐私边界

只保留 `allowed` / `blocked` / `bypassed` 三个聚合计数（`L2Snapshot`），不保存、不上传、不返回任何 DNS 查询明细、完整 URL、设备标识或客户端列表。服务端种子与路由中不出现 `queryLog`、`dnsQueryHistory`、`clientQueries` 一类字段，该约束同样由契约测试扫描守护。

## 5. L3：防摇一摇 PoC 骨架

`ShakeDetector` 是纯 JVM 的加速度采样判定：滑动时间窗内统计超过阈值的峰值数，达到 `minPeaks` 后触发并在 `cooldownMs` 内抑制重复触发；`reset()` 可一键清空。算法本身不读取传感器、不接触系统服务。

`ShakeGuardContract` 定义 `NOT_INSTALLED / DISABLED / ARMED / ACTIVE` 四态。`enable()` 只有在 `ShakeApproval(granted=true, decisionRef, decidedAt)` 三要素齐备时才推进到 `ARMED`；`activate()` 才进入 `ACTIVE`；`disable()` 任何时刻都可调用并立即复位。`onCandidate()` 在非 `ACTIVE` 状态或命中保护包名时一律返回 `PASS_THROUGH`。

`isProtected()` 按包名语义片段匹配 `bank` / `pay` / `wallet` / `login` / `auth` / `oauth` / `reward` / `bonus` / `security` / `anticheat` / `verify`，覆盖支付、登录、授权、奖励与安全校验流程，避免依赖一份永远滞后于 App 更新的静态白名单。

**本模块没有任何 Hook 框架、没有 `AndroidManifest.xml`、没有 Android 组件、没有独立 APK，也不向第三方进程写入任何东西。** `SUPPRESS_SHAKE` 只是一个返回值，不是一次实际干预。这与「L3 暂缓；除独立可选概念验证外不实现」一致：交付的是可评审的判定骨架，不是能力。

## 6. 服务端只读规则源

`GET /api/v1/filter-rules` 由 `server/src/api/filterRulesApi.ts` 提供，注册在 `server/src/api/index.ts`。行为：沿用既有 `limitRead` 限流；`loadFilterRuleSource()` 校验 `schemaVersion`、来源/许可证/版本/更新时间、`enabledByDefault === false`、`phase0Approved === false` 以及 SHA-256 校验和，任一不满足即抛错；校验失败时回退最后一份有效规则，从未成功加载则返回 503 而不是下发空规则；命中 `If-None-Match` 返回 304，正常响应带 `ETag` 与 `Cache-Control: no-store`，并附 `prototypeOnly: true`。

路由只读：没有 PUT / POST / DELETE 分支，不调用 `readBody()`，不接受任何上报。`createApi()` 新增可选的 `filterRulesPath` 参数以便测试注入临时规则源，默认指向 `server/seed/filter-rules.json`。

`phase0Approved: false` 与 `enabledByDefault: false` 是**显式的数据字段**，客户端不得据该接口自动启用 VPN；在 Phase 0 通过前，它只是一个可核对的格式契约。

## 7. 契约与回归测试清单

| 层 | 文件 | 覆盖内容 |
| --- | --- | --- |
| 跨模块边界 | `client/app/.../arch/CapabilityIsolationContractTest.kt` | 依赖隔离、清单、TLS/Hook 禁用词、默认关闭、单独批准、文档与文档地图、只读路由 |
| L2 单测 | `client/capability/l2vpn/src/test/.../L2PrototypeTest.kt` | hosts 保守解析、白名单优先、QNAME 解析、fail-open 六分支、状态机、聚合计数、TUN NXDOMAIN 与 `Unhandled` |
| L3 单测 | `client/capability/l3shake/src/test/.../L3PrototypeTest.kt` | 默认禁用、单独批准证据、保护流程、一键撤销、摇动窗口判定 |
| 服务端契约 | `server/test/filterRulesApi.contract.test.ts` | 响应形状、默认关闭与未批准、ETag/304、只读方法、校验和拒绝篡改 |
| 既有守护 | `ProjectStructureTest` / `RepoHygieneTest` / `ManifestContractTest` / `ArchitectureBoundaryTest` | 目录白名单、模块双向注册、包声明、文档登记、分支命名、主 APK 权限 |

采用契约先行：先写上述失败测试并确认红灯，再补实现。结构类测试必须用 `./gradlew testDebugUnitTest --tests 'com.qingqi.adskip.arch.*' --rerun-tasks --no-build-cache` 跑，否则 Gradle 构建缓存会返回 `FROM-CACHE` 造成假绿。

## 8. 验证口径

本原型的「完成」只指以下几项全绿，不代表功能可用、更不代表可以发布：

- `./gradlew ktlintCheck assembleDebug testDebugUnitTest --rerun-tasks --no-build-cache`
- `bun test` 与 `bun run typecheck`（在 `server/` 下，需先 `bun install --frozen-lockfile`）
- `npx --yes markdownlint-cli2@0.24.0`
- `git status` 确认改动全部落在 `feature/84-l2-l3-isolated-prototype`

真机矩阵、拦截率、正常域名零误伤、Top 10 App 跳转触发率等 M2 / M3 出口条件**一律未验证**，不得由单元测试结果推定通过；口径见 [ROADMAP-ADS.md](ROADMAP-ADS.md) 与 [TOP30-REAL-DEVICE-ACCEPTANCE.md](../testing/TOP30-REAL-DEVICE-ACCEPTANCE.md)。

## 9. 未完成项与已知风险

1. **真机未验证**：IPv6 DNS、TCP DNS、DoT/DoH、Android Private DNS、多 VPN 冲突、网络切换、断网恢复、进程被杀后的状态一致性、上游超时表现，均未在设备上跑过。
2. **非 DNS 报文处理**：如 4.3 所述，发往 `1.1.1.1` 的非 DNS 报文会被 `Unhandled` 丢弃而非转发；这是原型限制，不是设计目标。
3. **规则集为空**：种子无任何第三方规则，因此拦截率必然为 0；不得据此宣称 M2 出口条件达成。
4. **规则注入链路未接通**：`L2DnsVpnService.setRules()` 需要调用方在校验来源、许可证与校验和后注入，原型未实现客户端拉取与校验流程。
5. **L3 无实际干预**：`SUPPRESS_SHAKE` 只是返回值，没有 Hook、没有独立可选 APK、没有真机 Top 10 验证。
6. **上游解析器固定**：`1.1.1.1` 为原型占位，上线前必须评估地域可用性、隐私政策与可配置性。
7. **文档地图与认领板**：新增文档须登记 `docs/README.md`；并行开发期须在 [AGENT-WORKFLOW.md](../development/AGENT-WORKFLOW.md) 第 3.2 节登记工作区与认领路径。

## 10. Phase 0 放行条件与回滚

进入正式实现与发布分支前，至少必须全部满足：

1. Phase 0 合规评审通过，并把 [DESIGN-ADS-FILTERING.md](DESIGN-ADS-FILTERING.md) 第 9 节 ADR 状态从 `Proposed` / `Deferred` 更新为已批准，留下决策记录与日期；
2. Issue #84 关闭或明确拆分出已批准的后续工作项；
3. [ROADMAP-ADS.md](ROADMAP-ADS.md) 中 M2「拦截率 ≥ 70%、正常域名零误伤」与 M3「Top 10 App 跳转触发率 0」有真机证据；
4. 规则来源、许可证与再分发条款完成审查；隐私影响评估确认不收集查询明细；
5. 独立可选安装、用户显式授权、一键撤销、崩溃后网络恢复均有真机验证记录；
6. 由集成者决定能力层是否、何时接入 `:app`，并同步更新 `ALLOWED_CLIENT_ENTRIES` 与相关架构契约。

**回滚**：本原型没有改动主 APK 的依赖与清单，回滚就是删除 `client/capability/`、`client/settings.gradle.kts` 中的两条 `include`、约定插件别名与服务端路由/种子；无需数据迁移、无需用户侧操作、不影响已发布版本。若需要保留分支供评审，直接不合入即可。
