# 项目开发总览（DEV-PROJECT-GUIDE）

> 状态：**现行（团队协作入口）** — 本文是新人上手与跨模块协作的第一入口，各专项细节以文中标注的事实源为准。
> 最后更新：2026-10-29。
> 性质：**入口级总览（Onboarding Guide）**。它回答「这个项目是什么、怎么跑起来、改哪里、按什么顺序做」，**不复制**任何专项文档的细节。
> 阅读顺序建议：§1 → §3 → §6（先跑起来）→ 回到 §4/§5 理解代码 → §7/§8 了解节奏与门禁。
> 事实源边界：本文只承载「跨模块的整合视图」；任何单一主题的权威描述见 [docs/README.md](../README.md) 的「规划事实源」表。
> **与 [DESIGN-TECH-SOLUTION.md](../planning/DESIGN-TECH-SOLUTION.md) 的分工**：两份均为团队入口，视角**互补而非重复**——该文给**模块 DoD、技术难点、验收标准**（回答「怎么做算做完」），本文给**部署运维、环境搭建、跨模块数据流**（回答「怎么跑起来、怎么上线」）。**两者冲突时以该文为准**（其为已评审基线）；需求编号以该文 `FR`/`NFR` 为主，本文 `F`/`N` 是同一批需求的另一种切分。

---

## 1. 项目概述与目标

### 1.1 产品是什么

**轻启**（`com.qingqi.adskip`）是一个自动跳过 Android 应用开屏广告的全栈工具，由两部分组成：

| 部分 | 形态 | 职责 |
| --- | --- | --- |
| **客户端** | 原生 Android 应用（Kotlin + Jetpack Compose） | 通过无障碍服务识别并点击界面已有的「跳过」控件；本地记录与统计 |
| **服务端** | Bun + TypeScript 单进程服务 | 规则中心（下发规则包）、统计 API、管理后台、APK 分发落地页 |

技术路线与开源项目 GKD、李跳跳同类：**只点击界面已经存在的按钮，不拦截、不破解、不修改任何广告内容**。

### 1.2 当前状态（2026-10-29 实测）

| 项 | 值 |
| --- | --- |
| 已发布版本 | `v1.1.0`（客户端 `versionName = 1.1`、`versionCode = 19`；服务端 `1.1.0`） |
| 客户端代码量 | 66 个 Kotlin 文件 / 10,955 行（`src/main`） |
| 服务端代码量 | 15 个 TS 文件 / 1,879 行 |
| 测试规模 | 客户端 288 项（35 文件，含 15 个契约守护）、服务端 97 项（4 文件） |
| 规则协议版本 | schema 2（选择器通道随规则下发） |
| 构建产物 | Debug APK 可构建；Release 由 CI 用 Secrets 签名 |

### 1.3 目标（分层，避免混读）

| 层级 | 目标 | 判定标准 | 事实源 |
| --- | --- | --- | --- |
| **产品目标** | 让用户装上就能少看开屏广告，且不必信任任何云端 | 全新安装、除无障碍外零配置，可跳过 ≥1 条真实规则 | [PLAN-CLIENT-REFORM.md](../planning/PLAN-CLIENT-REFORM.md) §3 |
| **工程目标** | 可机器验证的约定全部机检，不靠人守 | 文档漂移 ≤3 项/次审查；门禁不可静默绕过 | [PLAN-REMEDIATION.md](../planning/PLAN-REMEDIATION.md) §8 |
| **能力目标** | 从「只跳开屏」扩到分层治理 | L1 覆盖 Top 30；L2/L3 过合规评审后才启动 | [ROADMAP-ADS.md](../planning/ROADMAP-ADS.md) |
| **合规目标** | 可合法公开分发 | App 备案 + 隐私政策 + 用途说明齐备 | [PLAN-CLIENT-REFORM.md](../planning/PLAN-CLIENT-REFORM.md) §4 |

### 1.4 明确的非目标

| 不做 | 理由 |
| --- | --- |
| 拦截/破解广告内容（DNS、Hook、去广告 SDK） | 性质从「帮用户点按钮」变为「干扰他人投放」，法律风险等级跃升。L2/L3 需先过 Phase 0 合规评审 |
| 用户账号体系 | 工具类 App 的零账号体验是产品前提 |
| 引入 Redis / Postgres / 消息队列 | 违背零运行时依赖；单实例定位下内存态足够 |
| 上架应用商店 | 历史路径选择（参考李跳跳案），当前走 GitHub Release + 自建落地页分发 |
| 多平台（iOS） | 平台政策不允许无障碍 API 用于非无障碍目的 |

---

## 2. 需求分析

### 2.1 用户痛点从哪来

六个方向，按「用户受到的伤害类型」分类（非按广告形态）：**干扰体验 / 控制权被剥夺 / 强制付费套路 / 隐私被窥探 / 广告伪装成内容 / 性能与资费代价**。

支撑证据（含国内与国际、法规与调查、共 88 条）见 [DESIGN-AD-PAIN-RESEARCH.md](../planning/DESIGN-AD-PAIN-RESEARCH.md)。本文只取对需求有直接约束的三条：

| 证据 | 数值 | 对本项目的含义 |
| --- | --- | --- |
| S1（人民网，2021-07） | **92.7%** 受访者对「跳不过、关不掉的 App 广告」感到困扰 | 需求真实且普遍 |
| S6（smzdm 实测，2026-09） | 100 款主流 App 中 **76 款**有开屏广告 | 覆盖面足够大，值得做 |
| GS6（Gartner，2026） | **81%** 美国消费者主动忽略广告，**52%** 采取拦截措施 | 不是中国独有现象 |

### 2.2 功能需求

| 编号 | 需求 | 优先级 | 状态 |
| --- | --- | --- | --- |
| **F1** | 自动识别并点击开屏广告的「跳过」控件，三通道：选择器 / 文本关键词 / 控件 ViewID | P0 | ✅ 已实现 |
| **F2** | 安全护栏：硬编码黑名单，防止误点支付/授权/登录等敏感按钮 | P0 | ✅ 已实现 |
| **F3** | 逐应用开关 + 跳过次数统计 | P1 | ✅ 已实现 |
| **F4** | 跳过日志（最近 200 条，可导出分享） | P1 | ✅ 已实现 |
| **F5** | 云端规则同步（ETag/304、HMAC 验签、证书 pin） | P1 | ✅ 已实现 |
| **F6** | 规则快照导出（当前界面节点树 JSON，用于编写选择器） | P1 | ✅ 已实现 |
| **F7** | 下拉快捷磁贴 + 厂商 ROM 保活引导 | P2 | ✅ 已实现 |
| **F8** | 免打扰时段 | P2 | ✅ 已实现 |
| **F9** | **内置默认规则集，零配置可用** | **P0** | ❌ **未实现**（当前规则库为空，见 §10.3 R1） |
| **F10** | 规则订阅源（免密钥，TOFU + 验签） | P1 | ❌ 未实现 |
| **F11** | 服务端版本闸门（软提示 → 426） | P2 | ❌ 未实现 |
| **F12** | 远端开关（kill switch，同签名） | P2 | ❌ 未实现 |
| **F13** | 上报可靠：待发队列 + 幂等 + 事件时间归日 | P0 | ❌ 未实现（服务端契约见 DESIGN-SERVER-MOBILE-READINESS） |

### 2.3 非功能需求

| 编号 | 类别 | 要求 | 当前状态 |
| --- | --- | --- | --- |
| N1 | 隐私 | 界面数据不出设备；跳过记录本地存储；上报可选且可关闭 | 部分（上报默认开启，无告知流程） |
| N2 | 安全 | 规则内容 fail-closed（验签失败拒载）；升级闸门 fail-open（缺头放行） | ✅ 已实现 |
| N3 | 性能 | 事件热路径不阻塞主线程；单节点树遍历上限 500 节点 | ✅ 已实现 |
| N4 | 可访问性 | 触控目标 ≥48dp；系统字体 200% 不裁切 | ⚠️ 部分（两处触控目标 20dp/36dp 未达 48dp） |
| N5 | 可维护性 | 能机检的约定不用人守 | ⚠️ 部分（代码层已做，文档层未做） |
| N6 | 兼容性 | `minSdk 26`（Android 8.0+）；旧客户端不受新协议影响 | ✅ 已实现 |
| N7 | 弱网可用 | 规则包压缩、429 带 `Retry-After` | ❌ 未实现 |

### 2.4 需求来源与追溯

需求台账（REQ-1…REQ-13，含每条的验收标准）见 [PLAN-REMEDIATION.md](../planning/PLAN-REMEDIATION.md) §1。里程碑与出口条件见 [ROADMAP-ADS.md](../planning/ROADMAP-ADS.md) 第 6 节。

---

## 3. 技术架构与选型说明

### 3.1 整体架构

```text
┌──────────────────────────────────────────────┐
│ 客户端（Android，单 Activity + Compose）        │
│  presentation ── platform ── data ── domain    │
│        │            │         │        │       │
│     designsystem   device   repository  engine │
│        │            │         │        │       │
│        └──── core（公共层，横切）──────┘        │
│                     di（组合根）                │
└──────────────────────────────────────────────┼──┘
                              HTTPS + HMAC + 证书 pin
                                               │
┌──────────────────────────────────────────────┴──┐
│ 服务端（Bun 单进程）                              │
│  api ── middleware ── storage ── utils          │
│  public/（管理后台 + 落地页，静态注入版本）         │
└─────────────────────────────────────────────────┘
```

分层原则：**每层只解决一类问题，跨层只经一个出口**。客户端不直接碰 `SharedPreferences`（经 `data/local`，再由 `data/repository` 供界面层），服务端 handler 不直接碰文件（经 `storage`）。五层依赖方向 `presentation → platform → data → domain` 由 `ArchitectureBoundaryTest` 机器强制，完整目录树见 [ARCHITECTURE-DIRECTORY-STRUCTURE.md](../architecture/ARCHITECTURE-DIRECTORY-STRUCTURE.md)。

### 3.2 客户端选型

| 决策 | 选择 | 理由 | 放弃的方案 |
| --- | --- | --- | --- |
| 平台 | 原生 Android（Kotlin） | 核心能力依赖 `AccessibilityService`，跨平台框架无法稳定调用 | Flutter / React Native（无障碍 API 支持不完整） |
| UI | Jetpack Compose + Material 3 | 单 Activity、状态驱动，Compose 的声明式模型与 ViewModel 契合；M3 提供完整令牌体系 | XML + View（历史架构，已迁移） |
| 导航 | Navigation Compose，单 Activity | 4 个一级目的地 + 2 个二级页，`NavHost` 足够；避免多 Activity 的返回栈管理 | 多 Activity（历史架构） |
| 状态管理 | ViewModel + `StateFlow`，手动 DI（`AppContainer`） | 依赖数量少，DI 框架（Hilt）会引入编译期开销与学习成本 | Hilt / Koin |
| 存储 | `EncryptedSharedPreferences`（`SecureStore` 单一入口） | 规则与设置数据量小（KB 级），无需数据库 | Room（数据量不匹配）；明文 SP（安全不达标） |
| 网络 | OkHttp + 手写 `SyncClient` | 需要证书 pin、HMAC 验签、ETag，Retrofit 的抽象层反而碍事 | Retrofit / Ktor |
| 事件抽象 | `AdNode` 接口隔离 `AccessibilityNodeInfo` | 使规则引擎可跑纯 JVM 单测（注入 `FakeAdNode`），无需 Robolectric | 直接依赖 framework 类（引擎不可单测） |

### 3.3 服务端选型

| 决策 | 选择 | 理由 | 放弃的方案 |
| --- | --- | --- | --- |
| 运行时 | Bun | 内置 `Bun.serve` / `Bun.gzipSync` / ICU（时区）/ `bun:sqlite`，**零运行时依赖** | Node + Express（依赖树膨胀） |
| 语言 | TypeScript（`tsc --noEmit` 严格检查） | 与客户端共享协议形状，编译期可查 | 纯 JS（协议字段易漂移） |
| 存储 | 按天分片的 JSON 文件 + 内存缓存 | 单实例、数据量小（万级事件/天）；零依赖、可直接 `cat` 排查 | SQLite（迁移计划见 DESIGN-ARCH-EVOLUTION R2）；Postgres（过重） |
| 鉴权 | `ADMIN_TOKEN` 单令牌 | 只有一个管理员，无多租户需求 | JWT / OAuth（过度设计） |
| 规则签名 | HMAC-SHA256（`RULES_SIGNING_KEY`） | 客户端 fail-closed 验签，防中间人篡改规则 | TLS 单独（不足以防「合法但被篡改」的规则） |
| 管理后台 | 单页 HTML（`public/admin.html`） | 无构建步骤，直接由服务端静态托管 | React SPA（引入构建链，违背零依赖） |

### 3.4 关键取舍的代价

| 取舍 | 换来 | 付出 |
| --- | --- | --- |
| 单实例 + 内存态限流 | 零依赖、部署简单 | 重启清零；无法水平扩展 |
| JSON 分片存储 | 可直接读、无迁移成本 | 无事务；多实例会互相覆盖 |
| 手动 DI（`AppContainer`） | 无注解处理器、构建快 | 新增依赖需手改容器 |
| 规则验签 fail-closed | 规则完整性有保证 | 换密钥 = 全体客户端停更（需双签过渡） |

---

## 4. 模块划分与功能设计

### 4.1 客户端模块

路径根：`client/app/src/main/java/com/qingqi/adskip/`

| 层 | 目录 | 职责 | 关键文件 | 对外出口 |
| --- | --- | --- | --- | --- |
| 界面层 | `presentation/` | 单 Activity、路由、7 个特性页（Screen + ViewModel）、设计令牌与共享组件 | `MainActivity.kt`、`navigation/Routes.kt`、`designsystem/`、`feature/*` | Composable 函数 |
| 系统集成层 | `platform/accessibility/` | 无障碍服务（薄编排层）、节点适配、快照导出 | `SkipAdService.kt`、`FrameworkAdNode.kt`、`NodeSnapshot.kt` | 系统回调 |
| 系统集成层 | `platform/device/` | 权限四态（含通知权限）、保活引导、语言落地 | `AccessibilityStatus.kt`、`KeepAliveNavigator.kt`、`NotificationPermission.kt` | 各系统集成点 |
| 数据层 | `data/local/` | 存储原语与唯一写入路径 | `Prefs.kt`、`RulesStore.kt`、`StatsStore.kt` | `RulesStore.replaceAllRules()` |
| 数据层 | `data/remote/` | 规则同步、上报、证书 pin、HMAC 验签 | `SyncClient.kt` | `SyncClient.fetchRules()` |
| 数据层 | `data/repository/` | 规则合并/缓存、统计合批、设置门面 | `RulesRepository.kt`、`StatsRepository.kt`、`SettingsRepository.kt` | `RulesRepository.ruleSetFor(pkg)` |
| 数据层 | `data/sync/` | JobScheduler 周期同步 | `SyncJobService.kt` | 系统调度回调 |
| 业务逻辑层 | `domain/engine/` | 三通道匹配 + 安全护栏（纯逻辑，可 JVM 单测） | `SkipRuleEngine.kt`、`SafetyGuard.kt`、`selector/` | `SkipRuleEngine.findTarget(root, rules)` |
| 公共层 | `core/` | 时钟、线程、日志环、事件总线、加密存储、语言偏好 | `common/`、`events/`、`locale/`、`storage/` | `SecureStore.prefs()` |
| 组合根 | `di/` | 手动依赖装配 | `AppContainer.kt` | `AppContainer` |

**依赖方向约束**：`presentation → platform → data → domain`，`core` 被各层使用且不反向依赖任何业务包，`di` 是唯一装配点；
`domain/engine` 不依赖 `android.*`（由 `AdNode` 抽象保证）。边界由 `ArchitectureBoundaryTest` 强制，细则见 [ARCHITECTURE.md](../architecture/ARCHITECTURE.md) 第 2.1 节。

### 4.2 服务端模块

路径根：`server/src/`

| 模块 | 职责 | 关键文件 |
| --- | --- | --- |
| `api/` | 路由分发（v1 优先，回退 v0）与各端点 handler | `index.ts`、`rulesApi.ts`、`statsApi.ts`、`healthApi.ts` |
| `middleware/` | 鉴权、令牌桶限流、访问日志 | `auth.ts`、`rateLimit.ts`、`accessLog.ts` |
| `storage/` | 规则读写、统计分片、汇总缓存 | `store.ts` |
| `utils/` | 响应构造、HMAC 签名、日志、载荷校验 | `httpUtil.ts`、`sign.ts`、`validate.ts` |
| `types/` | 协议形状（v0/v1 共享） | `rules.ts` |
| `public/` | 管理后台 + 落地页（静态，注入版本号） | `admin.html`、`index.html` |

**安全约定**：限流**先于**鉴权（未认证尝试也计入限额，避免 token 暴力破解零成本）。当前该顺序由每个 handler 手工复制，改造方向见 [DESIGN-SERVER-MOBILE-READINESS.md](../planning/DESIGN-SERVER-MOBILE-READINESS.md) §5.4 D1（路由表化）。

### 4.3 三条关键链路

**① 跳过链路（客户端内，最高频）**

```text
AccessibilityEvent
  → SkipAdService：节流去抖（同包 500ms 内只处理一次）
  → RulesRepository.ruleSetFor(pkg)：合并全局 + 应用专属规则（LRU 64 缓存）
  → SkipRuleEngine.findTarget()：DFS 遍历 ≤500 节点，三通道按序命中
  → SafetyGuard.check()：黑名单 / 可见性 / 可编辑性复核
  → 执行 ACTION_CLICK（失败则手势点击中心点）
  → StatsRepository.record() + LogRing.push()
```

**② 规则同步链路**

```text
JobScheduler（每 12h / 跨重启持久化）
  → SyncClient.fetchRules(If-None-Match: hash)
  → 304 → 直接返回（缓存有效）
  → 200 → 解压 → 验 HMAC-SHA256（失败即拒载，保留旧规则）
        → 校验 schemaVersion（低于 MIN_SCHEMA_VERSION 拒载）
        → RulesStore.replaceAllRules()（单次原子提交）
```

**③ 上报链路**

```text
每次跳过 → 内存队列（当前无持久化）→ 批量 POST /api/v1/reports/batch（≤50 条）
  → 服务端按到达时间归日 → 按天分片落盘 → 汇总缓存置空
```

> 链路 ③ 的缺陷（失败即丢、无幂等、`ts` 被丢弃）已在 [DESIGN-SERVER-MOBILE-READINESS.md](../planning/DESIGN-SERVER-MOBILE-READINESS.md) §5.1 给出完整设计，尚未实施。

---

## 5. 数据模型与接口定义

> 协议字段的**最终形态**以 [docs/api/API.md](../api/API.md) 为准；本节只给形状与关键约束。

### 5.1 客户端本地模型

**`RuleSet`**（某应用当前生效的规则，全局 + 专属合并结果）

```kotlin
data class RuleSet(
    val keywords: List<String>,      // 文本通道
    val viewIds: List<String>,       // ViewID 通道（长度 ≥3 才生效）
    val selectors: List<SelectorAst>,// 选择器通道（加载时编译一次）
    val disabled: Boolean = false,   // 该应用被禁用
    val schemaVersion: Int = 2,
)
// isEmpty = 三通道全空；isEmpty 时引擎直接返回 null
```

**协议版本**：`SCHEMA_VERSION = 2`（选择器通道）、`MIN_SCHEMA_VERSION = 1`（低于此拒载）。发布 schema 2 对旧客户端无害——它会照常加载并忽略未知字段。

**存储键**（`SecureStore` / `Prefs`）：全局关键词、全局 ViewID、全局选择器、应用专属规则（按包名）、禁用列表、服务端地址、HMAC 密钥、证书 pin、语言偏好、免打扰时段。

### 5.2 服务端存储模型

| 数据 | 位置 | 形态 |
| --- | --- | --- |
| 规则包 | `server/data/rules.json`（+ 备份轮转） | 单个 JSON，含 `schemaVersion`/`version`/`hash`/`updatedAt` |
| 统计事件 | `server/data/stats/<YYYY-MM-DD>.json` | 按天分片，每日一个文件 |
| 汇总缓存 | 内存（`summaryCache`） | 上报与过期清理时置 `null` |
| 访问日志 | 内存环形（200 条） | `/api/v1/admin/logs` 可读 |

### 5.3 规则包 schema

```jsonc
{
  "schemaVersion": 2,
  "version": 3,                     // 单调递增，客户端据此判断是否有更新
  "updatedAt": "2026-08-21T15:04:32.982Z",
  "hash": "<sha256>",               // 同时作为 ETag
  "rules": {
    "globalKeywords": ["跳过", "跳過", "skip"],
    "globalViewIds": ["skip", "jump"],
    "globalSelectors": ["[text*='跳过'][clickable=true]"],
    "apps": {                        // 按包名的专属规则
      "com.example.app": {
        "keywords": [], "viewIds": [], "selectors": [], "disabled": false
      }
    },
    "disabled": ["com.example.other"]  // 全局禁用列表
  },
  // ---- v0 兼容字段（与 rules.* 同引用，勿单独修改）----
  "keywords": [], "viewIds": [], "selectors": [], "packages": {}
}
```

### 5.4 API 端点（11 个）

| 方法 | 路径 | 鉴权 | 限流 | 说明 |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/rules/latest` | 公开 | 读桶 | 下发规则包（ETag/304、HMAC 签名头） |
| `PUT` | `/api/v1/rules` | admin | 写桶 | 发布规则 |
| `POST` | `/api/v1/rules/test` | admin | 写桶 | 规则模拟器 |
| `POST` | `/api/v1/reports/batch` | 公开 | 上报桶 | 批量上报跳过事件（≤50 条） |
| `GET` | `/api/v1/stats/summary` | 公开 | 读桶 | 统计汇总（累计/今日/14 天/按应用） |
| `GET` | `/api/v1/health` | 公开 | 读桶 | 健康检查 |
| `GET` | `/api/v1/admin/logs` | admin | 读桶 | 最近 200 条访问日志 |
| `GET` | `/api/rules/latest` | 公开 | 读桶 | **v0 兼容** |
| `PUT` | `/api/rules` | admin | 写桶 | **v0 兼容** |
| `POST` | `/api/skip` | 公开 | 上报桶 | **v0 兼容**（单条上报） |
| `GET` | `/api/stats/summary` | 公开 | 读桶 | **v0 兼容** |

**非 API 路由**：`/`（落地页）、`/admin`（管理后台）、`/download`（APK 分发）、`/privacy`（隐私政策，待建）。

### 5.5 错误码与限流

| 状态码 | 含义 | 客户端应有行为 |
| --- | --- | --- |
| `200` / `304` | 成功 / 未修改 | 304 时沿用缓存 |
| `400` | 载荷非法 | **出队丢弃**（重试必然再失败） |
| `401` / `403` | 鉴权失败 | 出队 + 提示用户检查配置 |
| `413` | 载荷超限（1 MiB 协议层上限） | 出队 |
| `429` | 限流 | 退避重试（当前无 `Retry-After`，见 §10.1 T3） |
| `5xx` | 服务端故障 | 退避重试 |

令牌桶三档：读 0.5s/令牌、写 6s/令牌、上报 2s/令牌（按 IP 与 `deviceId` 双桶）。

---

## 6. 开发环境与依赖配置

### 6.1 工具链

| 工具 | 版本 | 用途 |
| --- | --- | --- |
| JDK | 17+（CI 固定 17；本机 21 实测可用） | Gradle 与 Android 构建 |
| Gradle | 9.6+（wrapper 管理） | 构建 |
| Android SDK | `compileSdk 37` / `targetSdk 35` / `minSdk 26` | 编译与运行 |
| Build Tools | 36.0.0 | 打包 |
| Bun | ≥1.1 | 服务端运行与测试 |
| Node | 22+ | 脚本工具（`scripts/`） |

### 6.2 依赖版本（唯一事实源：`client/gradle/libs.versions.toml`）

| 类别 | 坐标 | 版本 |
| --- | --- | --- |
| 构建 | AGP | 9.4.1 |
| 构建 | Kotlin（Compose 编译器插件） | 2.4.21 |
| UI | Compose BOM | 2026.09.00 |
| UI | activity-compose / lifecycle / navigation-compose | 1.13.0 / 2.11.0 / 2.10.2 |
| 安全 | androidx.security.crypto | 1.1.0 |
| 网络 | OkHttp | 5.5.1 |
| 测试 | JUnit / MockWebServer / org.json | 4.13.2 / 5.5.1 / 20260814 |
| 静态检查 | ktlint（插件 / CLI） | 14.2.0 / 1.8.0 |

> **禁止在模块 `build.gradle.kts` 里硬编码版本号**，一律走 version catalog。升级前先核对镜像 `maven-metadata`（见 [DEV-ENVIRONMENT.md](DEV-ENVIRONMENT.md)）。

### 6.3 首次搭建

```bash
# 1. 客户端
cd client
./gradlew assembleDebug        # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # 288 项单测
./gradlew ktlintCheck          # 静态检查

# 2. 服务端
cd server
bun install
ADMIN_TOKEN=dev-token bun start   # 打开 http://localhost:3210/admin，用该 token 登录
bun test                          # 97 项
bun run typecheck                 # bun x tsc --noEmit
```

**常见卡点**：

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 写接口全返回 `503` | 未配置 `ADMIN_TOKEN` | 这是设计行为，设置该 env 后重启 |
| 手机装不上 | 手机已装 debug 签名版或旧密钥版本 | 卸载 `com.qingqi.adskip` 后重装 |
| `assembleRelease` 直接失败 | 未配置签名且未声明 `adskip.unsignedRelease` | 本机调试用 `assembleDebug`；正式签名走 CI Secrets |
| `bun run typecheck` 报 `run bun install --force` | Bun 1.4.x 生成的 `.bin/tsc` 垫片无法定位包根（TS 7 原生二进制） | 脚本已改为 `bun x tsc --noEmit`；若仍失败执行 `bun install --force` |

### 6.4 门禁命令（提交前必跑）

```bash
cd client && ./gradlew ktlintCheck && ./gradlew assembleDebug && ./gradlew testDebugUnitTest
cd server && bun test && bun run typecheck
```

跨模块变更（协议、清单、目录结构）需跑完整门禁。分支与提交规范见 [CONTRIBUTING.md](../../CONTRIBUTING.md)。

---

## 7. 开发计划与里程碑

### 7.1 已发布版本

| 版本 | 主题 | 关键内容 |
| --- | --- | --- |
| `1.0.0` | 重置后首个正式版 | 承载全部既有能力 + 批量上报 `label` + 上报通道证书锁定 |
| `1.0.1` | 跳过链路修复 | 修复模拟自测无法点击、跳过次数重复累加 |
| `1.1.0` | 界面重构与品牌统一 | 六页导航与 v4 视觉、UI 评审 P0/P1/P2 整改、品牌统一「轻启」 |

版本链路（tag / 提交 / 制品 / SHA-256）见 [RELEASE-HISTORY.md](../planning/RELEASE-HISTORY.md)。

### 7.2 整改六阶段（当前主线）

依赖关系与每阶段出口条件见 [PLAN-REMEDIATION.md](../planning/PLAN-REMEDIATION.md) §6。

| 阶段 | 目标 | 优先级 | 关键产出 |
| --- | --- | --- | --- |
| **0 止血** | 消掉「说错了会让人做错事」 | P0 | 服务端 R1–R7 修复；文档 P0 六项；删易漂移数字；钩子自动启用 |
| **1 可信数据** | 统计不丢、不重、不错日 | P0 | `ts` 归日 + 钳制；`epoch+seq` 幂等 |
| **2 可验证** | 覆盖率与核心能力有自动化兜底 | P1 | kover 报告；3 条 `androidTest`；修 `typecheck` |
| **3 可运营** | 能部署、能轮换密钥 | P1 | 部署文档；双签密钥；压缩 + `Retry-After` |
| **4 可治理** | 把「靠人守」转成机制 | P2 | 三个文档守卫；版本闸门；`/metrics` |
| **5 客户端开箱可用** | 装了就能用 | **P0** | 内置规则集；订阅源；服务器降为可选；UI 规范修复 |
| **6 合规与分发** | 能合法公开分发 | **P0（关键路径）** | 隐私政策；首启同意；App 备案（20 工作日，须最先启动） |

> **阶段 5 的价值高于 3 和 4**——它决定「装了有没有用」。**阶段 6.4 备案必须最先启动**，因为它不依赖任何代码且周期不可压缩。

### 7.3 能力里程碑

| 里程碑 | 内容 | 状态 |
| --- | --- | --- |
| M1a–M1c | L1 无障碍点击层：选择器引擎、协议 v2、节点快照工具 | ✅ 已交付 |
| M1d | Top 30 应用规则入库 + 真机回归 + 规则审核通道 | 收尾中（即阶段 5.1） |
| M2 / M3 | L2 DNS 过滤 / L3 防摇一摇 | **阻塞**（Phase 0 合规评审未完成） |
| M4 | L4 通知过滤 | 可启动（未排期） |
| M5 / M6 | 激励视频代跳 / 内容型拦截 | 待复评 |

### 7.4 版本号规则

- 客户端 `versionName` 用 `X.Y`（与 CONTRIBUTING 一致），服务端用完整 `X.Y.Z`。
- `versionCode` **全局单调递增、禁止复用**（Play Store 要求）。当前 19。
- 发布从 `main` 创建 `release/vX.Y.Z`，冻结功能并同步三处版本号，通过 CI 后打 annotated tag。

---

## 8. 测试方案

### 8.1 四层测试

| 层 | 位置 | 职责 | 准入 |
| --- | --- | --- | --- |
| **JVM 单测** | `client/app/src/test/`、`server/test/` | 纯逻辑：规则引擎、选择器解析、载荷校验、统计归日 | 任何业务逻辑改动 |
| **契约测试** | `client/.../arch/`（15 个）、`server/test/fixtures/` | 双端必须一致的形状：清单、目录、色板、UI 间距、选择器夹具 | 跨端边界改动 |
| **进程内集成** | `server/test/smoke.test.ts` | 起服务 → 打真实 HTTP → 断言响应 | 协议或路由改动 |
| **真机验收** | 手工，记录于 `docs/testing/` | 无障碍服务的真实行为 | 涉及 `service/`、`device/` 或规则内容 |

### 8.2 现有规模

| 套件 | 数量 | 位置 |
| --- | --- | --- |
| 客户端单测 + 契约 | **288 项 / 35 文件** | `client/app/src/test/` |
| 服务端单测 + 冒烟 + 契约 | **97 项 / 4 文件** | `server/test/` |
| 真机验收 | 计划已写，**尚未实测** | [TOP30-REAL-DEVICE-ACCEPTANCE.md](../testing/TOP30-REAL-DEVICE-ACCEPTANCE.md) |
| 稳定性测试 | 方案已写（S1–S6） | [STABILITY-TEST-PLAN.md](../testing/STABILITY-TEST-PLAN.md) |

### 8.3 已知缺口

| 缺口 | 影响 | 处置 |
| --- | --- | --- |
| `androidTest/` 目录不存在 | 无障碍服务零自动化，历史事故只能靠人工回归 | 阶段 2.2：三条最小用例 |
| 无覆盖率统计 | 「测了什么」靠感觉 | 阶段 2.1：接 kover，先出报告不设阈值 |
| 无测试分层规范 | 新增功能「要不要写测试」靠个人判断 | 在 [ARCHITECTURE.md](../architecture/ARCHITECTURE.md) 第 10 节前补「测试分层」小节 |
| 服务端 P0 三项无测试保护 | 归日/幂等改动无回归网 | 阶段 1 同步新增 3 例（跨天/时区/配置校验） |

---

## 9. 部署与运维说明

### 9.1 当前状态（如实说明）

**项目目前没有完整的部署运维文档**——这是已确认的覆盖缺口（[FOLLOW-UP-DOC-AUDIT.md](../planning/FOLLOW-UP-DOC-AUDIT.md) C1）。全仓 `systemd` / 进程守护 / 日志轮转命中 0 次；`部署` 一词只作名词出现。

本节给出**最小可用路径**，完整文档待建（`docs/guide/GUIDE-DEPLOY.md`，需先放开 `ALLOWED_DOC_DIRS`，见 C1/D17）。

### 9.2 最小部署路径

前置：一台 Linux 主机、一个已解析域名、TLS 证书（或前置反向代理终止 TLS）。

```bash
# 1. 拉取并安装
git clone <repo> && cd <repo>/server && bun install

# 2. 生成密钥（写入 env，勿入库）
export ADMIN_TOKEN="$(openssl rand -hex 32)"
export RULES_SIGNING_KEY="$(openssl rand -hex 32)"

# 3. 启动（生产必须 TLS）
TLS_CERT=/path/fullchain.pem TLS_KEY=/path/privkey.pem \
ADMIN_TOKEN="$ADMIN_TOKEN" RULES_SIGNING_KEY="$RULES_SIGNING_KEY" \
HOST=0.0.0.0 PORT=3210 bun start

# 4. 验证
curl -fsS https://<域名>:3210/api/v1/health
```

**硬性约束**：非 loopback 地址**强制 TLS**（`server.ts` 中校验），明文端口不得暴露公网。若由反向代理终止 TLS，服务端只监听 loopback。

### 9.3 密钥与证书生命周期

| 密钥 | 用途 | 丢失后果 | 轮换 |
| --- | --- | --- | --- |
| `ADMIN_TOKEN` | 管理接口鉴权 | 管理后台不可用，重新生成即可 | 随时可换 |
| `RULES_SIGNING_KEY` | 规则包 HMAC | **客户端 fail-closed → 全体客户端停止更新规则**，且用户不可见 | **当前无过渡手段**，双签方案见 DESIGN-SERVER-MOBILE-READINESS §5.4 D5 |
| TLS 证书 | 传输加密 | 客户端拒连 | 常规续期；客户端 pin 需预置新旧两个 |

> 当前最严重的运营单点是 `RULES_SIGNING_KEY` 无法轮换：单密钥 + 客户端失败关闭 = 换密钥即全体停更。改造前**不要换**。

### 9.4 备份与恢复

| 数据 | 位置 | 备份方式 |
| --- | --- | --- |
| 规则包 | `server/data/rules.json` | 服务端自带备份轮转；另建议每日快照 |
| 统计分片 | `server/data/stats/*.json` | 每日增量备份（按天文件，可直接 `rsync`） |
| 密钥 | 环境变量 / Secrets | 存密码管理器，**不入库、不进镜像** |

**恢复演练未做过**——这是 C1 的一部分，建议在部署文档定稿时一并补上「从零恢复到可用」的步骤与验证。

### 9.5 监控与排障

| 手段 | 现状 |
| --- | --- |
| 健康检查 | `GET /api/v1/health` |
| 访问日志 | 内存环形 200 条，`/api/v1/admin/logs` |
| 结构化日志 | 已有 `utils/logger.ts`，字段未稳定 |
| 指标导出 | ❌ 无（`/metrics` 属阶段 4.3） |
| 崩溃采集（客户端） | ❌ 无（属阶段 6.7） |

**排障入口**：客户端「设置 → 导出日志」（内存 500 条环形）；服务端 `server/data/` 下的分片文件可直接读。

---

## 10. 风险与后续扩展方向

### 10.1 技术风险

| # | 风险 | 触发条件 | 影响 | 缓解 |
| --- | --- | --- | --- | --- |
| T1 | 上报事件丢失 | 断网 / Doze / 失败即丢 | 统计系统性偏低 | 阶段 1：待发队列 + 幂等 |
| T2 | 事件错日 | 服务端用到达时间分片 | `byDay` 曲线失真 | 阶段 1：`ts` 归日 + 钳制 |
| T3 | 限流重试风暴 | 429 无 `Retry-After` | 客户端盲猜退避 | 阶段 3：加头 + 抖动退避 |
| T4 | 规则包过大 | 无压缩 | 弱网下白付流量 | 阶段 3：gzip（签名口径不变） |
| T5 | 签名密钥不可轮换 | 换密钥 | **全体客户端规则停更** | 阶段 3：双签过渡 |
| T6 | 单实例内存态 | 重启 / 扩容 | 限流清零、幂等丢失、分片互相覆盖 | 迁移 SQLite（DESIGN-ARCH-EVOLUTION R2） |
| T7 | 无障碍服务被 ROM 杀 | 厂商省电策略 | 功能静默失效 | 已有守护巡检 + 保活引导；仍需真机矩阵验证 |

### 10.2 合规风险

| # | 风险 | 等级 | 说明 | 处置 |
| --- | --- | --- | --- | --- |
| C1 | **App 备案缺失** | **高（法定强制）** | 有联网能力即适用，不上架应用商店不免除；审核约 20 工作日 | 阶段 6.4，**必须最先启动** |
| C2 | 隐私政策缺失 | 高 | 已上报 `deviceId` 与跳过记录，无告知同意 | 阶段 6.1 / 6.2 |
| C3 | 无障碍用途未声明 | 中 | 「非无障碍目的使用无障碍服务」是灰色地带 | 阶段 6.3：明文写「只点已有按钮、不读内容、不传界面数据」 |
| C4 | 反不正当竞争 | 中（L1）/ 高（L2+） | 只点已有按钮风险可控；L2 拦截性质变化 | L2/L3 过 Phase 0 前不实现 |
| C5 | 出海路径 | 高 | Google Play 2026-01-28 起禁止 Accessibility API 自动化操作 | 阶段 6.6：核实政策，若禁止则明确只走国内侧载 |

> 合规证据（法规原文、监管通报、判例、国际对比）见 [DESIGN-AD-PAIN-RESEARCH.md](../planning/DESIGN-AD-PAIN-RESEARCH.md)；完整评估见 [PLAN-CLIENT-REFORM.md](../planning/PLAN-CLIENT-REFORM.md) §4。

### 10.3 产品风险

| # | 风险 | 现状 | 影响 |
| --- | --- | --- | --- |
| R1 | **开箱不可用** | `RulesRepository` 无内置规则，`DEFAULT_SERVER = ""`，首次启动规则为空 → 引擎返回 `null` → **什么都不点**，且无任何提示 | 新用户首日流失；引导页承诺「智能识别广告内容」与实际不符 |
| R2 | 规则覆盖不足 | `server/seed/rules.json` 的 `packages` 为空，仅 5 个通用关键词 + 2 个 viewId | 图片按钮、自定义控件基本失效 |
| R3 | 竞品挤压 | GKD 社区订阅已覆盖数百款应用、上千规则组 | 「规则数量」这一局追不平 |
| R4 | 系统能力替代 | 华为鸿蒙 6.0、iOS 27.2 已提供摇一摇拦截 | 需求被系统层吸收一部分 |

**定位结论**：不与 GKD 正面竞争规则数量；差异化走「隐私姿态（纯本地优先、上报可选、可审计）+ 工程可靠性（契约测试、幂等上报、fail-closed 验签）」。

### 10.4 后续扩展方向

| 方向 | 内容 | 前置条件 |
| --- | --- | --- |
| 规则生态 | 社区订阅源、快照 → PR 共建、规则审核通道 | 阶段 5.2 / 5.3 |
| 能力分层 | L4 通知过滤 → L2 DNS → L3 Hook | 依次通过 Phase 0 合规评审 |
| 存储演进 | JSON 分片 → `bun:sqlite` | 多实例或数据量增长时 |
| 架构演进 | 客户端多模块化（`engine`/`data` 独立 module） | 见 DESIGN-MODULE-REARCH |
| 可观测 | `/metrics`、结构化日志字段稳定、客户端崩溃采集 | 阶段 4.3 / 6.7 |
| 部署形态 | Docker 镜像 + 一键脚本 | 阶段 3 |

---

## 11. 附：术语表

术语在本文与全部文档中保持以下含义，避免同物异名。

| 术语 | 含义 | 不要写成 |
| --- | --- | --- |
| **轻启** | 产品名（`com.qingqi.adskip`） | AdSkip（历史名，仅用于历史文档引用） |
| **客户端** | Android 应用 | App（口语可，正式文档用「客户端」） |
| **服务端** | Bun 单进程服务 | 后端（可互换，但同一文档内统一） |
| **规则包** | 服务端下发的完整 JSON（含 `schemaVersion`/`hash`/`rules`） | 规则文件、配置文件 |
| **规则集（`RuleSet`）** | 客户端对某应用合并后的生效规则 | 规则包（两者不同层） |
| **三通道** | 选择器 / 文本关键词 / ViewID | 三种匹配（不规范） |
| **跳过事件** | 一次成功的跳过记录（含 `pkg`/`label`/`channel`/`ts`） | 日志（日志是本地展示层） |
| **归日** | 把事件计入其**发生日**而非到达日 | 分片（分片是存储手段） |
| **fail-closed** | 校验失败时拒绝（用于规则完整性） | 失败关闭（口语可） |
| **fail-open** | 校验失败时放行（用于升级闸门） | 失败开放（口语可） |
| **闸门** | 版本淘汰机制（软提示 → 426） | 门槛、限制 |

---

## 12. 维护约定

- 本文是**入口**，不是事实源。任何单一主题的改动，先改对应专项文档，再检查本文的指针是否需要同步。
- 新增模块、新增阶段、新增风险时，同步更新 §4 / §7 / §10 的表格，并在 [docs/README.md](../README.md) 的阅读顺序中确认本文仍指向正确位置。
- 本文中的**数字**（代码行数、测试数、版本号）属易漂移内容，每次发版后核对一次；若维护成本过高，改为「见 `CHANGELOG.md`」而不写具体值。
