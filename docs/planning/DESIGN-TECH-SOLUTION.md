# 技术方案开发文档（DESIGN-TECH-SOLUTION）

> 状态：**已评审基线** — 本文是研发交付入口，架构与接口细节以文末「事实源边界」表指向的专项文档为准。
> 版本：v1.0；最后更新：2026-10-29。
> 读者：客户端研发、服务端研发、测试、发布执行者。
> 使用方式：新成员按 §1→§9 顺序通读；日常开发按 §5 定位模块、按 §6 查接口、按 §9 判断「做完没有」。

---

## 0. 文档约定

### 0.1 事实源边界（避免第二份真相）

| 主题 | 唯一事实源 | 本文的角色 |
| --- | --- | --- |
| 分层边界与目录契约 | [ARCHITECTURE.md](../architecture/ARCHITECTURE.md) 第 2 章 | 给出摘要与开发约束，冲突以 ARCHITECTURE 为准 |
| 接口字段与状态码 | [API.md](../api/API.md) | 给出调用清单与示例，字段以 API 为准 |
| 依赖版本 | `client/gradle/libs.versions.toml`、`server/package.json` | 只写「有哪些技术」，不抄版本号 |
| 整改阶段与优先级 | [PLAN-REMEDIATION.md](PLAN-REMEDIATION.md) | 引用阶段编号，不重复排期 |
| 服务端移动端契约细节 | [DESIGN-SERVER-MOBILE-READINESS.md](DESIGN-SERVER-MOBILE-READINESS.md) | 只引用，不重述 |
| 版本序列 | [ROADMAP.md](ROADMAP.md) | 本文不承诺版本号 |

> **与 [DEV-PROJECT-GUIDE.md](../development/DEV-PROJECT-GUIDE.md) 的分工**：两份均为团队入口，视角**互补而非重复**——本文给**模块 DoD、技术难点、验收标准**（回答「怎么做算做完」），该文给**部署运维、环境搭建、跨模块数据流**（回答「怎么跑起来、怎么上线」）。需求以本文 `FR`/`NFR` 为主编号，该文的 `F`/`N` 编号是同一批需求的另一种切分。
> **冲突裁决**：若两者对同一事实给出不同结论，**以本文为准**（本文状态为「已评审基线」）。已知需对齐的两处：界面数量统一表述为「6 个导航目的地 / 7 个 Screen 目录」，契约测试数量以 `arch/*.kt` 实测为准（当前 15 个）。

### 0.2 统一术语表

全文档与代码注释一律使用下列术语，**不得使用同义词**（如「规则集」与「规则包」混用）。

| 术语 | 英文 / 标识 | 定义 |
| --- | --- | --- |
| **规则包** | `RulesPackage` | 服务端下发的完整规则载荷，含 `schemaVersion` / `version` / `hash` / `rules` |
| **规则集** | `RuleSet` | 客户端合并后交给引擎的不可变结构，由规则包派生 |
| **关键词通道** | keyword channel | 按可见文本匹配跳过的规则类型 |
| **ViewID 通道** | viewId channel | 按 `resource-id` 后缀匹配的规则类型 |
| **选择器通道** | selector channel | 按类 CSS 子集表达式匹配的规则类型 |
| **选择器快检** | server pre-check | 服务端对选择器的**必要不充分**校验（长度/字符/配平） |
| **权威解析器** | `SelectorParser` | 客户端的选择器文法实现，语法判定以它为准 |
| **契约夹具** | contract fixture | 双端共享的 JSON 用例集，两端测试共同消费 |
| **安全护栏** | `SafetyGuard` | 点击前的硬拦截（黑名单包、系统包、面积/可见性/输入框） |
| **跳过事件** | skip event | 一次成功跳过的记录，含 `pkg` / `label` / `channel` / `ts` |
| **设备身份** | `deviceId` | 客户端首次运行生成并持久化的匿名标识（≥8 字符） |
| **规则签名** | `X-Rules-Signature` | 服务端对规则响应体的 HMAC-SHA256，客户端验签失败即拒载 |
| **证书固定** | certificate pinning | 客户端只信任预置指纹的证书链 |
| **失败关闭** | fail-closed | 安全前置条件不满足时**拒绝服务**而非降级放行 |
| **失败开放** | fail-open | 运营类前置条件缺失时**放行**而非阻断 |
| **毒丸事件** | poison event | 载荷非法、重试必然失败的上报事件，必须出队 |
| **归日** | day attribution | 把事件计入**事件发生日**而非到达日 |
| **幂等键** | `(deviceId, epoch, seq)` | 判定上报事件是否已处理的身份三元组 |

### 0.3 阅读地图

```
想改引擎/选择器  → §5.1.1 + DESIGN-PHASE1-SELECTOR.md
想改服务端接口    → §6.3 + API.md
想知道这版怎么做  → §8 + PLAN-REMEDIATION.md
想知道做完没有    → §9.2 完成定义 + §9.4 验收清单
```

---

## 1. 项目背景与目标

### 1.1 背景

**产品定位**：轻启（qingqi）是一款 Android 工具类应用，通过无障碍服务（`AccessibilityService`）识别并自动点击应用开屏广告的「跳过」按钮，同时提供规则中心（云端下发规则）、跳过统计与管理后台。

**关键背景事实**（决定了后续所有技术约束）：

| 事实 | 影响 |
| --- | --- |
| 核心能力依赖 `AccessibilityService` | 技术路线必须原生 Android，跨端框架无法替代（见 §4.1） |
| 此类工具不符合 Google Play 对无障碍服务的审核口径 | 分发走自建渠道（GitHub Release + 服务端 `/download`），不上架商店 |
| 单维护者阶段 | 架构与流程必须「低人力可维持」：零运行时依赖、单实例、机检优先于人工评审 |
| 广告 SDK 的跳过控件形态各异（文本/ViewID/层级结构） | 需要三通道匹配 + 云端规则持续演进，不能靠硬编码 |
| 规则包可被中间人改写后利用（诱导点击） | 规则完整性必须做成硬约束（HMAC + 证书固定） |

### 1.2 目标

**产品目标**

| 编号 | 目标 | 度量 |
| --- | --- | --- |
| G-1 | 主流应用的开屏广告可被自动跳过 | Top 30 应用覆盖率 ≥80%（口径见 `TOP30-REAL-DEVICE-ACCEPTANCE.md`） |
| G-2 | 不产生误点击（支付/登录/下载类控件） | 误点击事件 = 0（黑名单 + 面积 + 可见性三重拦截） |
| G-3 | 规则可持续更新而不发版 | 云端下发 → 客户端 12h 内生效 |

**工程目标**

| 编号 | 目标 | 度量 |
| --- | --- | --- |
| E-1 | 规则下发完整可信 | 未通过验签的载荷 100% 被拒载（失败关闭） |
| E-2 | 统计数据可解释 | 补报成功率 ≥99%（7 天内离线）；重复上报只计一次 |
| E-3 | 门禁不可绕过 | 本地钩子 + CI 双门禁，主检出直接提交被机器拦截 |
| E-4 | 新成员可在一日内跑通全链路 | 按 `DEV-ENVIRONMENT.md` 操作，服务端 + 客户端均可本地启动 |

### 1.3 非目标（明确不做）

| 不做 | 理由 |
| --- | --- |
| iOS 客户端 | 产品形态（无障碍自动化）在 iOS 上无对应能力 |
| 上架应用商店 | 审核口径不支持该能力类别 |
| 用户账号体系 | 工具类产品的零账号体验是前提 |
| 多实例水平扩展 | 单维护者规模不需要；扩容前置是存储迁 SQLite（R2） |
| L2/L3 能力（DNS 过滤、Root Hook） | 需先通过 Phase 0 合规评审，见 `DESIGN-ADS-FILTERING.md` |

> **L4（通知过滤）属「条件性范围」，不是永久非目标。** `ROADMAP.md` 将其列为 `M4`（未排期），`ROADMAP-ADS.md` 标注为「可启动（无阻塞）」。它与 L2/L3 的区别是：L2/L3 必须**先过** Phase 0 评审，L4 只需**排期**即可启动。

---

## 2. 需求分析

### 2.1 角色与场景

| 角色 | 场景 | 触点 |
| --- | --- | --- |
| 终端用户 | 打开任意 App，开屏广告被自动跳过 | 无障碍服务（无感知） |
| 终端用户 | 查看跳过记录、开关应用级规则 | 客户端「应用 / 规则 / 日志」页 |
| 规则维护者 | 编辑并发布规则包、用模拟器验证规则 | `/admin` 管理后台 |
| 运维 | 部署服务端、查看统计、排查异常 | `/admin`、`/api/v1/health`、日志 |

### 2.2 功能需求

| 编号 | 需求 | 验收口径 | 优先级 |
| --- | --- | --- | --- |
| FR-1 | 三通道匹配跳过：关键词 / ViewID / 选择器 | 三通道各有独立单测；合并后由 `RuleSet` 交给引擎 | Must |
| FR-2 | 安全护栏：黑名单包、系统包、输入框、面积/可见性阈值 | 命中任一即拒绝点击，且不可被云端规则覆盖 | Must |
| FR-3 | 应用级规则：全局 + 应用专属 + 禁用开关 | 合并顺序与 `RulesRepository.ruleSetFor(pkg)` 一致 | Must |
| FR-4 | 规则云端同步：增量、验签、失败关闭 | `If-None-Match` 命中 304；验签失败不动本地规则 | Must |
| FR-5 | 跳过事件上报：单条与批量、离线补报 | 断网不丢（队列）、重复不重（幂等） | Must |
| FR-6 | 统计看板：总量/按日/按应用/最近记录 | 字段与 `API.md` 一致 | Should |
| FR-7 | 管理后台：登录、规则编辑、diff 预览、规则模拟器 | 无令牌时写接口 503；模拟器给出 `selectorNotes` | Should |
| FR-8 | 落地页与 APK 下载 | `/` 展示当前版本；`/download` 返回 APK 或 404 | Should |
| FR-9 | 厂商保活引导（自启动/后台白名单） | 逐级降级，全部失败时给出手动路径而非静默失败 | Should |
| FR-10 | 快捷磁贴开关服务 | 磁贴状态与无障碍真实状态一致 | Could |
| FR-11 | **内置默认规则集**：首次安装即可命中规则，无需任何配置 | 全新安装（仅开无障碍）可命中 ≥1 条真实规则 | **Must**（P0，**当前未实现**） |
| FR-12 | 规则来源分层：内置 / 订阅 / 手动，界面可见来源与更新时间 | 设置页显示「当前生效规则来自：内置（日期）」 | Should |
| FR-13 | 首启隐私告知与同意；拒绝后纯本地功能不降级 | 未同意时上报通道关闭，跳过功能不受影响 | **Must**（合规前置，见 C2） |

> FR-11–FR-13 是从合规与市场分析中补入的（详见 [PLAN-CLIENT-REFORM.md](PLAN-CLIENT-REFORM.md) §3.2 批次 0/1）。缺 FR-11 的直接后果是：**研发按本表排期会跳过全项目 ROI 最高的一项**——没有规则库，装完的 App 不点任何东西。

### 2.3 非功能需求

| 编号 | 类别 | 要求 | 现状 / 目标 |
| --- | --- | --- | --- |
| NFR-1 | 性能 | 单次节点判定 ≤2ms（主线程外） | 已由纯 JVM 引擎保证；需补基准 |
| NFR-2 | 性能 | 服务端单实例 ≤10 QPS（假设） | 未压测，**待补基线** |
| NFR-3 | 可靠性 | 服务端优雅停机不丢统计（flush 落盘） | 已实现（`store.flush()`） |
| NFR-4 | 可靠性 | 规则包损坏时回退种子/内置默认 | 已实现（三级兜底） |
| NFR-5 | 安全 | 非 loopback 监听必须 TLS | 已实现（启动即校验） |
| NFR-6 | 安全 | 规则载荷完整性可验证 | 已实现（HMAC，可配密钥） |
| NFR-7 | 兼容 | Android 8.0（API 26）及以上 | `minSdk = 26` |
| NFR-8 | 可维护 | 分层依赖可机器校验 | 15 个契约测试强制 |
| NFR-9 | 可维护 | 核心能力有自动化验证 | **缺口**：无仪器化测试 |
| NFR-10 | 可观测 | 线上异常可定位 | **缺口**：仅 JSONL 日志 + 200 条内存访问日志 |
| NFR-11 | 隐私 | 上报须首启告知并取得同意；仅采集必要字段（`deviceId` / `pkg` / `channel` / `ts`）；**不采集界面文本** | **缺口**：上报默认开启、无告知流程 → 与合规风险 C2 直接冲突 |

### 2.4 约束条件

| 约束 | 内容 | 来源 |
| --- | --- | --- |
| 技术约束 | 服务端零运行时依赖（仅标准库 + Bun 内建） | `server/package.json` 无 `dependencies` |
| 技术约束 | 客户端单模块（`:app`）+ `build-logic` 约定插件 | `settings.gradle.kts` |
| 合规约束 | L2/L3/L4 能力须先过 Phase 0 评审 | `ROADMAP-ADS.md` 第 3 节 |
| 分发约束 | 自建渠道，不上架商店 | `README.md` 合规提示 |
| 流程约束 | 禁止主检出直接提交；Conventional Commits；Squash Merge | `AGENTS.md` / `githooks/` |

---

## 3. 总体架构设计

### 3.1 系统全景

```text
┌──────────────── Android 客户端（Kotlin + Compose）────────────────┐
│  ui/        界面（7 个 Screen，MVVM + StateFlow + UiEffect）        │
│  data/      仓库（RulesRepository / StatsRepository / Prefs）      │
│  net/       SyncClient（OkHttp + 证书 pin + HMAC 验签）            │
│  sync/      SyncJobService（JobScheduler，12h）                    │
│  service/   SkipAdService（无障碍事件编排，薄层）                   │
│  engine/    SkipRuleEngine + SelectorParser/Matcher + SafetyGuard  │
│             （纯 JVM，零 Android 依赖）                            │
│  device/    厂商保活 / 磁贴 / 权限（系统入口收口）                  │
└───────────────┬───────────────────────────────┬──────────────────┘
                │ ① 规则下行（HTTPS + ETag）     │ ② 事件上行（HTTPS 批量）
                ▼                               ▼
┌──────────────── Bun 服务端（TypeScript，零运行时依赖）──────────────┐
│  server.ts        入口 / TLS 强制 / 安全头 / 优雅停机               │
│  src/api/         rules · stats · health · admin（v0 + v1）        │
│  src/middleware/  auth（常数时间比较）· rateLimit（令牌桶）· accessLog │
│  src/storage/     store.ts（规则缓存 + 统计分片 + 备份轮转）         │
│  src/utils/       validate · sign（HMAC）· logger（JSONL）· httpUtil │
└───────────────┬───────────────────────────────┬──────────────────┘
                │ ③ 静态分发                     │ ④ 数据落盘
                ▼                               ▼
        public/（落地页 / 管理后台）      data/（rules.json / stats/ / backups/）
```

### 3.2 客户端分层与依赖规则

依赖方向**严格单向**，由 `ArchitectureBoundaryTest` 机器强制：

| 包 | 允许依赖 | 禁止依赖 | 职责 |
| --- | --- | --- | --- |
| `engine/` | 仅 Kotlin/JDK | 任何 `android.*`、任何其他业务包 | 纯匹配逻辑与安全护栏 |
| `core/` | Kotlin/JDK/协程 | 其他业务包 | 时钟、线程、日志、事件总线 |
| `ui/` | Compose/AndroidX、`core/`、`engine/`、`data/` 仓库接口 | `service/`、`net/`、`sync/`、`data.Prefs` | 渲染与交互 |
| `data/` | `engine/`、`net/`（设置门面）、Android SDK | `ui/`、`service/` | 数据仓库与持久化 |
| `net/` | `data/`、Android SDK、org.json | `ui/`、`service/` | HTTP 传输与验签 |
| `sync/` | `data/`、`core/` | `ui/`、`service/` | 周期调度 |
| `service/` | `core/`、`data/`、`engine/`、`net/` | `ui/` | 无障碍事件编排 |

**开发约束**：新增跨包引用前先跑 `ArchitectureBoundaryTest`，让它红，再决定是改设计还是改契约。

### 3.3 服务端分层

| 模块 | 职责 | 边界 |
| --- | --- | --- |
| `server.ts` | HTTP 入口、TLS 校验、安全头、优雅停机 | 不含业务逻辑 |
| `src/api/` | 路由与 handler | 不直接读写文件，一律经 `storage` |
| `src/middleware/` | 鉴权、限流、访问日志 | 无状态（除内存桶） |
| `src/storage/` | 规则与统计的唯一读写口 | 不感知 HTTP |
| `src/utils/` | 校验、签名、日志、响应构建 | 纯函数优先 |

### 3.4 三条关键数据流

**① 规则下行**

```text
客户端：取本地 hash → GET /api/v1/rules/latest (If-None-Match: <hash>)
服务端：命中 → 304（零正文）；未命中 → 200 + ETag + X-Rules-Signature
客户端：解压 → HMAC 验签 → 失败则拒载并保留本地规则
        → 成功则 applyCloudRules（单次原子提交）
```

**② 事件上行**

```text
跳过发生 → 写本地日志与计数 → 入待发队列(seq++)
         → 批量 ≤50 → POST /api/v1/reports/batch
服务端：IP 预检 → 读体 → 校验清洗 → 幂等判定 → 归日 → 分片写入 → 汇总缓存失效
```

**③ 静态分发**

```text
GET /        → 落地页（版本号来自 server/package.json）
GET /admin   → 管理后台（同源，Bearer 令牌登录）
GET /download→ APK（路径固定为 server/ 上一级，缺失返回 404）
```

---

## 4. 技术选型及理由

### 4.1 选型原则

1. **平台能力优先于开发效率**：核心能力是 `AccessibilityService`，跨端框架无法覆盖，故排除 Flutter / RN / KMP。
2. **零依赖优先**：服务端只用 Bun 内建与标准库；客户端只引入「解决当前问题必需」的库。
3. **可机器验证优先**：能用测试守住的约束不写成文档。
4. **失败方向按语义分**：完整性相关 fail-closed，可用性相关 fail-open（见 §7 难点 6）。

### 4.2 客户端选型

| 技术点 | 选择 | 理由 | 放弃的方案及原因 |
| --- | --- | --- | --- |
| 语言 | Kotlin | 官方主推；协程/空安全；与 Compose 同源 | Java（样板多、无协程） |
| UI | Jetpack Compose + Material 3 | 声明式、状态驱动，与 MVVM 单向流契合 | XML View（状态同步靠手写） |
| 架构 | MVVM + StateFlow + UiEffect | 单向数据流，一次性事件与状态分离 | MVP（生命周期管理复杂）、MVI 框架（过度） |
| DI | **手写 `AppContainer`** | 单模块、依赖图 <20 个，手写可读性更高且零编译期开销 | Hilt（KSP 增量构建变慢，小项目不划算）、Koin（运行时解析，错误发现晚） |
| 本地存储 | EncryptedSharedPreferences | 数据形态是 KV（规则/计数/配置），且需加密 | Room（关系型，过重）、DataStore（加密需自行封装） |
| 网络 | 裸 OkHttp | 仅 3 个端点；需要证书固定与拦截器 | Retrofit（3 个端点是过度封装）、Ktor（无 KMP 需求） |
| 序列化 | org.json | 零依赖、载荷结构简单 | kotlinx.serialization（**建议后续迁移**，见 §7 难点 7） |
| 后台任务 | JobScheduler | 固定 12h 周期同步；`setPersisted` 跨重启 | WorkManager（周期不保证准点，且引入依赖） |
| 测试 | JUnit 4 + MockWebServer + 手写 fake | JVM 快；网络层可回归 | MockK（无复杂 mock 需求）、Robolectric（暂不需要） |
| 静态检查 | ktlint（规则源 `.editorconfig`） | 与 IDE 无关，规则单一事实源 | detekt（与 ktlint 规则重叠） |
| 构建 | Version Catalog + 约定插件 | 版本单一事实源；跨模块一致 | 各模块硬编码版本（必然漂移） |

### 4.3 服务端选型

| 技术点 | 选择 | 理由 | 放弃的方案及原因 |
| --- | --- | --- | --- |
| 运行时 | Bun | 内置 HTTP/测试/压缩/文件 API，零依赖即可满足全部需求 | Node（需引入框架与测试库） |
| 语言 | TypeScript（strict） | 协议有版本演进，需要类型约束 | JavaScript（协议字段无保护） |
| HTTP | `Bun.serve` | 单进程、支持 TLS、`requestIP` 可直接取真实 IP | Express/Fastify（多一层依赖） |
| 存储 | JSON 分片 + `tmp`+`rename` 原子写 | 单实例、读多写少、数据量小 | SQLite（R2 再迁）、Redis（引入外部服务） |
| 限流 | 内存令牌桶 | 单实例够用；桶数有上限防内存放大 | 分布式限流（无多实例需求） |
| 加密 | `node:crypto`（HMAC/timingSafeEqual） | 标准库，无依赖 | 第三方 JWT 库（不需要 JWT，只需 HMAC） |
| 测试 | `bun:test` | 内置，in-process 冒烟 | Vitest/Jest（多一层依赖） |

### 4.4 明确不引入的技术

| 不引入 | 理由 |
| --- | --- |
| Hilt / Koin | 单模块手写容器更轻；引入后 KSP 拖慢增量构建 |
| Room | 数据是 KV 不是关系型；引入即需 schema 迁移管理 |
| Retrofit | 端点少；OkHttp 直接用更透明 |
| Redis / 消息队列 | 违背零依赖；单实例无此需求 |
| OpenTelemetry | 单实例阶段过度工程；先用结构化日志固定字段 |
| 跨端框架 | 核心能力跨不过去（见 §4.1） |

---

## 5. 核心模块与功能设计

> 每个模块给「职责 / 对外接口 / 不做什么 / 完成定义（DoD）」。DoD 是验收依据，不是建议。

### 5.1 客户端模块

#### 5.1.1 `engine/` — 匹配引擎与安全护栏

| 项 | 内容 |
| --- | --- |
| 职责 | 三通道匹配、选择器解析与求值、点击前安全裁决 |
| 对外接口 | `SkipRuleEngine.decide(node, ruleSet): Decision`；`SafetyGuard.canClick(node, pkg, allowSelf)`；`SelectorParser.parse(expr): Ast?` |
| 不做什么 | 不执行点击、不读存储、不依赖任何 Android 类型 |
| 关键约束 | 非法选择器返回 `null`（fail-safe），调用方静默丢弃该条 |
| DoD | ① 全部逻辑可在 JVM 单测；② `ArchitectureBoundaryTest` 绿；③ 选择器契约夹具双端一致 |

#### 5.1.2 `service/` — 无障碍事件编排

| 项 | 内容 |
| --- | --- |
| 职责 | 接收 `AccessibilityEvent`、节流去抖、组装 `AdNode`、调用引擎、执行点击、上报事件 |
| 对外接口 | 系统回调 `onAccessibilityEvent` / `onInterrupt`；对外仅暴露运行状态 |
| 不做什么 | 不含匹配规则、不做安全裁决（委托 `engine`） |
| 关键约束 | 单次事件处理在主线程外完成；点击前必须过 `SafetyGuard` |
| DoD | ① 事件节流有单测；② 自测态放行仅经显式参数，不削弱默认拦截；③ **需补仪器化用例**（见 §9.2） |

#### 5.1.3 `data/` — 仓库与持久化

| 项 | 内容 |
| --- | --- |
| 职责 | 规则合并、统计计数、偏好读写、加密存储 |
| 对外接口 | `RulesRepository.ruleSetFor(pkg): RuleSet`、`applyCloudRules(...)`；`StatsRepository.recordSkip(...)`、`flush()` |
| 不做什么 | 不感知 UI、不感知网络格式 |
| 关键约束 | `RulesStore.replaceAllRules` 是**唯一**多键规则写入路径（单次原子提交）；`StatsStore.addSkips` 是**唯一**计数写入路径 |
| DoD | ① 两条「唯一写入路径」有测试固化；② 明文→密文迁移幂等；③ 计数不因多轮 flush 膨胀 |

#### 5.1.4 `net/` — 传输与完整性

| 项 | 内容 |
| --- | --- |
| 职责 | 规则拉取（ETag）、上报、证书固定、HMAC 验签 |
| 对外接口 | `syncRules` / `syncRulesSilently` / `reportSkip` |
| 不做什么 | 不直接改存储键值（交给仓库） |
| 关键约束 | 未配签名密钥或证书指纹 → **拒连/拒载**（失败关闭）；响应体上限 2MB |
| DoD | ① 验签失败不落地；② 304 不重载；③ MockWebServer 覆盖 200/304/签名错/超大响应 |

#### 5.1.5 `ui/` — 界面

| 项 | 内容 |
| --- | --- |
| 职责 | 7 个 Screen 的渲染与交互（首页/应用/规则/日志/设置/我的/引导） |
| 对外接口 | 无（仅通过 ViewModel 与数据层交互） |
| 不做什么 | 不直读 `Prefs`、不碰网络、不依赖 `service` |
| 关键约束 | 一次性事件走 `UiEffect`，不得用 Toast 混在状态里 |
| DoD | ① `UiContractTest` / `ColorSchemeContractTest` 绿；② 无硬编码色值（走主题令牌） |

#### 5.1.6 `device/` — 系统入口

| 项 | 内容 |
| --- | --- |
| 职责 | 厂商 ROM 识别、保活引导、权限中心、磁贴 |
| 对外接口 | `VendorKeepAlive.detect()`、`KeepAliveNavigator.open*()`、`SkipTileService` |
| 不做什么 | 不读业务数据、不发起网络 |
| 关键约束 | Intent 逐级降级；全部不可解析时给出手动路径（不静默失败） |
| DoD | ① `ManifestContractTest` 绿（`<queries>` 与入口表对齐）；② 纯逻辑部分可在 JVM 单测 |

### 5.2 服务端模块

| 模块 | 职责 | DoD |
| --- | --- | --- |
| `api/rulesApi` | 规则下发（v0/v1）、发布、规则模拟器 | 验签按**原始字节**签发；模拟器对判不了的选择器回报 `unevaluable` 而非假 `miss` |
| `api/statsApi` | 统计汇总 | 有界（`byDay` 14 天 / `recent` 50 条）；`STATS_READ_AUTH` 可开启鉴权 |
| `api/healthApi` | 健康检查 | 恒 200 + 时间戳；不依赖存储可用性 |
| `middleware/auth` | Bearer 鉴权 | 常数时间比较；未配令牌返回 503 |
| `middleware/rateLimit` | 令牌桶 | 桶数上限 5 万；XFF 仅在受信代理时采信 |
| `storage/store` | 规则与统计读写 | 原子写（tmp+rename）；备份轮转命名不撞名；三级兜底（运行时→种子→内置） |

### 5.3 关键流程时序：一次跳过

```text
AccessibilityEvent(窗口变化)
  → SkipAdService: 节流/去抖 → 组装 AdNode 快照
  → engine: 关键词通道 → ViewID 通道 → 选择器通道
  → SafetyGuard: 包黑名单 → 系统包 → 输入框 → 可见性/面积
  → 点击 → 校验点击结果（避免误触）
  → data: appendLog + addSkipCounts（唯一计数路径）
  → net: 入待发队列（离线则等待）
```

---

## 6. 数据模型与接口定义

### 6.1 客户端本地模型

| 模型 | 字段 | 存储位置 |
| --- | --- | --- |
| `RuleSet` | `keywords` / `viewIds` / `selectors` / `disabled` | 内存（由仓库组装） |
| `CloudPkgRule` | `keywords` / `viewIds` / `selectors` / `disabled` | 加密偏好（按包名分键） |
| `SkipEvent` | `ts` / `pkg` / `label` / `channel` | 加密偏好（日志数组，上限 200 条） |
| 设备身份 | `deviceId`（≥8 字符）、`epoch`（安装期 UUID）、`seq`（单调） | 加密偏好 |
| 同步状态 | `rules_hash` / `lastSyncAt` / `certPins` / `rulesSigningKey` | 加密偏好 |

**键空间约束**：包名键必须匹配 `^[a-zA-Z][A-Za-z0-9_]*(\.[a-zA-Z][A-Za-z0-9_]*)+$`，否则不得进入偏好键空间（防键空间污染）。

### 6.2 服务端模型

| 模型 | 字段 | 说明 |
| --- | --- | --- |
| `RulesPackage` | `schemaVersion` / `version` / `updatedAt` / `hash` / `rules` + v0 兼容平铺字段 | `hash` = sha256(schemaVersion + rules) 前 16 位 |
| `RuleSetV1` | `globalKeywords` / `globalViewIds` / `globalSelectors` / `apps` / `disabled` | 下发核心结构 |
| `StatsDay` | `day` / `byApp` / `events` | 按天分片，保留 90 天 |
| `StatsSummary` | `total` / `today` / `byDay` / `byApp` / `recent` | 响应结构 |

### 6.3 接口清单

**v1（当前）**

| 方法 | 路径 | 鉴权 | 限流 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/rules/latest` | 无 | 读 120/min | 规则下发；`If-None-Match` 命中 304 |
| PUT | `/api/v1/rules` | admin | 写 10/min | 发布规则包 |
| POST | `/api/v1/rules/test` | admin | 读 120/min | 规则模拟器 |
| POST | `/api/v1/reports/batch` | 无 | 上报 30/min | 批量上报（≤50 条） |
| GET | `/api/v1/stats/summary` | 可配 | 读 120/min | 统计汇总 |
| GET | `/api/v1/health` | 无 | 读 120/min | 健康检查 |
| GET | `/api/v1/admin/logs` | admin | 读 120/min | 最近 200 条访问日志 |

**v0（兼容旧客户端，行为保持不变）**：`GET /api/rules/latest`、`PUT /api/rules`、`POST /api/skip`、`GET /api/stats/summary`。

**静态**：`GET /`（落地页）、`GET /admin`（管理后台）、`GET /download`（APK）。

### 6.4 通用约定

| 项 | 约定 |
| --- | --- |
| 请求体类型 | 必须 `application/json`，否则 415 |
| 请求体上限 | 1 MiB；JSON 键数 ≤100、嵌套 ≤5 |
| 错误格式 | `{"error": "<信息>"}` |
| 状态码 | 400 载荷非法 / 401 未鉴权 / 404 不存在 / 413 超限 / 415 类型错 / 429 限流 / 503 未配置令牌 |
| 500 语义 | 一律返回 `internal error`，细节只进服务端日志 |

### 6.5 协议演进规则

1. 新增字段必须是**纯增量**：旧客户端忽略即行为不变。
2. `schemaVersion` 单调递增，`SCHEMA_VERSION_MIN` 决定最低可服务版本。
3. 破坏性变更必须同时给出：客户端降级路径 + 灰度开关 + 回滚方案。
4. 双端共享的判定向量固化进 `server/test/fixtures/selectors.contract.json`，两端测试共同消费。

---

## 7. 关键技术难点与解决方案

### 难点 1：无障碍事件的时序与误触

**问题**：`AccessibilityEvent` 高频触发且节点树可能尚未稳定；误点支付/登录/下载控件会造成真实损失。

**方案**：
- 节流 + 去抖（同窗口短时多次事件合并）；
- `SafetyGuard` 硬拦截四层：包黑名单（云规则不可覆盖）→ 系统包 → 输入框/可编辑 → 可见性与面积阈值；
- 点击后校验结果（避免「点了但没跳」被反复点击）。

**取舍**：宁可漏跳（用户体验损失小）不可误点（可能造成财产损失）。因此护栏是**硬编码优先级高于云端规则**。

### 难点 2：选择器文法两端一致

**问题**：服务端要做规则模拟器，但完整文法需要节点树（父/兄弟关系），服务端没有。

**方案**：**服务端快检 + 客户端权威**。服务端只校验长度、字符白名单、括号引号配平（必要不充分）；语法权威在客户端 `SelectorParser`。判不了的表达式服务端回报 `unevaluable` 并给出原因，**不伪装成 miss**。两端一致的向量固化进契约夹具。

**取舍**：接受「服务端可能放过非法选择器」，换取「不维护两套文法实现」。

### 难点 3：规则载荷的完整性

**问题**：规则下发无鉴权，中间人可改写规则诱导点击。

**方案**：三层叠加——HTTPS + 证书固定（防 MITM）+ HMAC-SHA256 签名（防篡改）。签名对**即将发出的原始字节**计算，客户端验签失败即拒载（失败关闭）。

**取舍**：未配密钥的客户端会完全无法使用云规则。这是有意的：**不可用优于被利用**。

### 难点 4：离线与弱网下的上报可靠性

**问题**：手机断网/Doze 是常态，事件即发即丢；重试又会重复计数。

**方案**（见 `DESIGN-SERVER-MOBILE-READINESS.md`）：
- 客户端待发队列（2000 条 / 7 天，丢最旧），单消费者按序发送；
- 幂等三元组 `(deviceId, epoch, seq)`，服务端维护高水位；
- 错误分类：400/413/415/422 视为毒丸**出队**，429/5xx/网络保留退避。

**取舍**：队列上限 7 天 = 归日钳制窗口 7 天，两者刻意一致，避免「能补报但归错日」。

### 难点 5：厂商 ROM 保活

**问题**：国产 ROM 的后台清理策略各异，无障碍服务容易被杀。

**方案**：`device/` 层做「识别 → 逐级降级 → 引导」而非对抗：厂商入口表 → 通用兜底（应用详情/无障碍/电池优化）→ 全部不可解析时给出手动路径。**不申请不必要的权限、不试图绕过系统限制。**

**取舍**：接受「部分设备无法稳定保活」，换取合规与不被商店/系统判为恶意。

### 难点 6：无鉴权上报的数据可信度

**问题**：上报端点无鉴权，包名与时间戳均可伪造，可灌爆统计或磁盘。

**方案**：条数封顶（`byApp` 上限 2000/天，超限聚合 `_other`）+ 时间钳制（7 天窗口）+ 日键窗口限制（单请求最多碰 8 个日键）+ IP/设备双桶限流。

**取舍**：接受「统计可被污染到有限程度」，换取零账号体验；用钳制把污染面收窄到「7 天内的数值失真」而非「磁盘 DoS」。

### 难点 7：构建版本矩阵

**问题**：AGP / Kotlin / Compose 编译器插件 / KSP 四者版本强耦合，升级顺序错一步就炸。

**现状**：Compose 插件版本（2.4.21）高于 AGP 内置 Kotlin（2.2.10），靠 `composeMappingProducerClasspath` 的 `resolutionStrategy` 打补丁。

**方案**：Version Catalog 单一事实源 + 升级前查兼容矩阵 + CI 固定工具链；补丁处必须写明原因（现有代码已做到）。

**待办**：消除补丁，或明确记录「该补丁在什么条件下可移除」。

---

## 8. 开发计划与里程碑

### 8.1 阶段划分

阶段编号与内容引用 [PLAN-REMEDIATION.md](PLAN-REMEDIATION.md) §6，此处只给里程碑出口条件。

| 里程碑 | 阶段 | 出口条件（全部满足才算完成） |
| --- | --- | --- |
| **M-A 止血** | 阶段 0 | ① 文档与实现逐条对得上（无悬空 env / 无失效路径）；② `bun test` 全绿；③ 钩子自动启用且 CI 断言生效 |
| **M-B 数据可信** | 阶段 1 | ① 跨天归日正确；② 重复 `seq` 只计一次；③ 上报带 `appVersion` |
| **M-C 质量可见** | 阶段 2 | ① 覆盖率报告可下载；② 三条仪器化用例稳定通过；③ 五条门禁本地全可跑 |
| **M-D 可运营** | 阶段 3 | ① 压缩开启且验签不受影响；② 部署文档可让新机器 30 分钟跑起；③ 双密钥可轮换 |
| **M-E 可治理** | 阶段 4 | ① 三个文档守卫生效；② 版本闸门软段可用；③ `/metrics` 可抓取 |
| **M-F 开箱可用** | 阶段 5 | ① 全新安装零配置可命中 ≥1 条真实规则；② 规则来源可见；③ 服务器配置降为可选且不配时功能完整 |
| **M-G 合规可分发** | 阶段 6 | ① App 备案编号取得；② 隐私政策可达且首启告知必现；③ 无障碍用途说明落地；④ Google Play 政策结论已落文档 |

### 8.2 关键路径与依赖

```text
M-A ──┬──> M-B ──> M-D
      └──> M-C ──┘
      └──> M-E（不阻塞任何里程碑）
```

- M-B 必须先于 M-D：压缩涉及签名口径，需在归日/幂等改动稳定后做；
- M-C 可与 M-B 并行（一个动客户端测试基建，一个动服务端数据路径）；
- 版本号归属：**待 ROADMAP 冻结时确定**，本文不承诺。

### 8.3 开发流程约定

| 环节 | 约定 | 事实源 |
| --- | --- | --- |
| 分支 | `type/id-slug`；主检出禁止直接提交 | `AGENTS.md`、`githooks/pre-commit` |
| 提交 | Conventional Commits，标题 ≤72 字符 | `githooks/commit-msg` |
| 跨边界改动 | 先写失败的契约测试，再写实现 | `contract-first` 技能 |
| 合并 | Squash Merge，一提交一件事 | `CONTRIBUTING.md` |
| 评审 | 安全/协议/数据/发布配置变更需领域审查 | `CODEOWNERS` |

---

## 9. 测试与验收标准

### 9.1 测试分层

| 层 | 范围 | 工具 | 运行时机 | 目标占比 |
| --- | --- | --- | --- | --- |
| L1 单元 | 纯逻辑（引擎、校验、限流、存储） | JUnit 4 / `bun:test` | 每次提交 | 70% |
| L2 契约 | 分层边界、Manifest、目录结构、双端夹具 | 15 个 `arch/*Test` + 契约测试 | 每次提交 | 15% |
| L3 集成 | 服务端全路由 in-process | `smoke.test.ts` | 每次提交 | 10% |
| L4 仪器化 | 服务绑定、真实点击、权限 | `androidTest` | 每日/发布前 | 5% |
| L5 真机验收 | Top 30 应用覆盖率 | 手工 + 记录 | 发布前 | — |

### 9.2 完成定义（DoD）

一个功能「做完」需同时满足：

1. 有对应的 L1 或 L2 测试，且**先红后绿**；
2. 若改动跨包/跨端/动协议 → 契约测试已更新；
3. 若改动涉及平台层（Service/权限/系统 Intent）→ 有 L4 用例或明确的豁免理由；
4. 文档中受影响的章节已同步（`API.md` / `ARCHITECTURE.md` / 本文）；
5. 五条门禁本地自跑全绿（不推给 CI）。

### 9.3 验收标准

| 类别 | 标准 | 判定方式 |
| --- | --- | --- |
| 功能 | FR-1~FR-10 逐条通过 | 对应测试 + 手工走查 |
| 安全 | 未验签载荷 100% 拒载；非 loopback 无 TLS 拒绝启动 | 单测 + 启动测试 |
| 可靠性 | 断网 7 天内事件可补报；重复上报只计一次 | 集成测试 |
| 性能 | 单次判定 ≤2ms；服务端 ≤10 QPS 无错误 | 基准 + 压测（待补） |
| 兼容 | API 26~最新可安装运行 | 模拟器矩阵 |
| 可维护 | 覆盖率有基线；门禁不可绕过 | CI 报告 |

### 9.4 发布验收清单

- [ ] `versionName` / `versionCode` / `server/package.json` 三者一致（CI `version-contract` 自动校验）
- [ ] `CHANGELOG.md` 有对应版本段
- [ ] `assembleRelease` 通过且 `apksigner verify` 成功
- [ ] `SHA256SUMS` 已生成并上传
- [ ] 服务端 `/api/v1/health` 返回 200
- [ ] `/download` 可下载且 SHA-256 与 Release 一致
- [ ] `/admin` 可用令牌登录
- [ ] `RELEASE-HISTORY.md` 台账已回填

### 9.5 缺陷分级

| 级别 | 定义 | 处理时限 |
| --- | --- | --- |
| P0 | 误点击/数据丢失/安全漏洞/无法启动 | 立即修复，阻断发布 |
| P1 | 核心功能不可用（跳过失效、规则不同步） | 当版修复 |
| P2 | 非核心功能异常、统计偏差 | 下一版修复 |
| P3 | 体验问题、文案、日志 | 排入 backlog |

---

## 10. 风险与应对措施

### 10.1 技术风险

| # | 风险 | 概率 | 影响 | 应对 | 触发信号 |
| --- | --- | --- | --- | --- | --- |
| T1 | 构建版本矩阵失配（AGP/Kotlin/Compose 插件） | 中 | 高 | Catalog 单一事实源；升级前查兼容矩阵；补丁处写明移除条件 | CI 编译失败且报 `compose-group-mapping` |
| T2 | 依赖传递导致版本失控（如协程） | 高 | 中 | 凡直接 import 的库必须在 catalog 显式声明 | 升级 Compose 后协程行为变化 |
| T3 | 压缩开启后签名失配 → 全量拒载 | 中 | 极高 | 签名覆盖未压缩字节，代码/文档/契约测试三方固化；env 可关 | 客户端日志 `signature mismatch` 集中上升 |
| T4 | 无障碍服务被 ROM 杀死 | 高 | 高 | 保活引导 + 状态可降级 + 首页显式提示 | 用户反馈「不生效」，`AccessibilityStatus` 为 false |
| T5 | 误点击造成财产损失 | 低 | 极高 | `SafetyGuard` 四层硬拦截，优先级高于云端规则 | 日志出现支付类包名点击 |
| T6 | 协议演进破坏旧客户端 | 中 | 高 | 纯增量字段 + `SCHEMA_VERSION_MIN` + 灰度开关 | 旧版本同步失败率上升 |
| T7 | 统计分片损坏或磁盘写满 | 低 | 中 | 原子写 + 备份轮转 + 保留 90 天；写失败不阻塞请求 | 日志 `backup rotation failed` / 磁盘告警 |

### 10.2 合规与分发风险

| # | 风险 | 应对 |
| --- | --- | --- |
| C1 | 无障碍权限被判定为滥用 | 明确单一用途（跳过开屏广告）、不采集输入内容、不上架商店、`SECURITY.md` 声明能力边界 |
| C2 | 规则库被用于商业竞品分析 | 规则包不含用户数据；统计只到包名粒度 |
| C3 | L2/L3 能力越界 | 未过 Phase 0 评审前不实现（`ROADMAP-ADS.md` 硬门） |

### 10.3 进度与人力风险

| # | 风险 | 应对 |
| --- | --- | --- |
| P1 | 单维护者，关键路径无人备份 | 所有决策落文档；契约测试承担「知识固化」职责 |
| P2 | 整改批次与功能开发抢道 | 整改阶段按 M-A→M-B 顺序，功能需求插入前先确认不破坏门禁 |
| P3 | 审计逾期（季度复跑已逾期 18 天） | 改为「每个 minor 发布前」触发，挂进 §9.4 清单 |

### 10.4 风险登记要求

新增风险必须同时给出：**概率、影响、应对动作、触发信号、责任人**。缺任一项视为未登记。当前技术债台账见 [FOLLOW-UP.md](FOLLOW-UP.md)（**待补「责任人 / 截止」两列**）。

---

## 附录 A：术语速查

见 §0.2。新增术语必须先加表再用，不得在正文自创同义词。

## 附录 B：常见开发任务索引

| 我要… | 看这里 |
| --- | --- |
| 加一个关键词规则通道的行为 | §5.1.1 + `engine/SkipRuleEngine.kt` |
| 加一个服务端接口 | §5.2 + §6.3 + `API.md`，并补 `smoke.test.ts` |
| 改协议字段 | §6.5 + `API.md` + 双端夹具 |
| 加一个客户端页面 | §5.1.5 + `UiContractTest` |
| 排查「规则没生效」 | 顺序：验签 → 证书 pin → 版本 hash → 合并逻辑 → 引擎判定 |
| 排查「统计不准」 | 顺序：队列 → 幂等 → 归日 → 汇总缓存 |
