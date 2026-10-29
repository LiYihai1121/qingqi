# 行为分析平台开发计划（PLAN-TELEMETRY-ANALYTICS）

> **状态**：草案（待需求方确认前置假设）　**最后更新**：2026-10-19
> **关联**：[DESIGN-TELEMETRY-ANALYTICS.md](DESIGN-TELEMETRY-ANALYTICS.md)（设计事实源：目标/埋点/指标/数据模型/ADR/风险）
> **读者**：开发团队。本文只维护**怎么做、何时做、谁来做**；口径与设计不在本文重复。

---

## 0. 前置假设

以下信息未提供，按合理假设补全。**假设不成立时需重新评估排期与范围**。

| # | 前提 | 本方案假设 | 若不成立的影响 |
|---|---|---|---|
| A1 | 项目 | 轻启 AdSkip 行为分析平台（AdSkip Insight） | 文档整体重写 |
| A2 | 团队 | 1 名全栈（兼客户端/服务端/看板）+ 0.5 名规则运营（兼职） | 排期与并行度全部重算 |
| A3 | 周期 | 18 周日历（74 人日 ÷ 5 ≈ 14.8 周纯工时 + 2 周基线观察） | 需按 §3.5 裁剪 |
| A4 | 技术栈 | 沿用 Kotlin+Compose / Bun+TypeScript；**服务端零运行时依赖为硬约束** | 选型全盘重来 |
| A5 | 遥测默认值 | `off` + 首次启动征询 | 样本量级与项目价值（见 DESIGN Q1） |
| A6 | 设备量级 / 开启率 | ~500 设备 / 30% | 容量估算与是否需采样 |
| A7 | 真机 | ≥2 台不同 ROM（MIUI + AOSP） | D1/D2 验证降级为 JVM 单测 |
| A8 | 部署 | 自建单实例，systemd + 反向代理终止 TLS | §4.3 改写 |
| A9 | 导出/删除 | 需要 | M5 范围 −2 人日 |
| A10 | 看板鉴权 | 与现有 admin token 对齐 | 安全设计 |

---

## 1. 范围摘要

功能范围、非目标红线、埋点清单、指标口径、数据模型、接口与 ADR **全部以
[DESIGN-TELEMETRY-ANALYTICS.md](DESIGN-TELEMETRY-ANALYTICS.md) 为准**，本文不复述。

本期交付边界（摘要）：

- **做**：遥测 SDK、服务端接入与聚合、指标 API、看板、洞察引擎、治理面板、修复现有 `byChannel`/`recent`/`byDay` 三项缺陷
- **不做**：用户画像、屏幕内容采集、第三方 SDK、实时流、账号体系、A/B 平台、改造 `/api/v1/stats` 契约

---

## 2. 模块拆分与目录结构

### 2.1 客户端

```text
client/app/src/main/java/com/qingqi/adskip/
├── telemetry/                       # 新增：与 core/ 同级，禁止进入 engine/
│   ├── Telemetry.kt                 # 对外 API：track / flush / setScope
│   ├── TelemetryConfig.kt           # scope、采样率、容量、熔断开关
│   ├── TelemetryBuffer.kt           # 本地 JSONL 追加 + FIFO 淘汰
│   ├── TelemetryPrivacy.kt          # 隐私分级过滤 + 客户端字段白名单
│   ├── TelemetryUploader.kt         # 批量上报 + 指数退避（复用 SyncClient）
│   ├── Events.kt                    # 【单写者】事件名与 props 常量（埋点字典代码化）
│   └── hooks/
│       ├── ScreenHooks.kt           # screen_view / screen_exit
│       └── ServiceHooks.kt          # svc_lifecycle / sync_result
├── service/SkipAdService.kt         # 改动：trySkip 内埋 skip_outcome（3 处）
├── ui/...                           # 改动：onboarding_step、screen hooks
└── data/Prefs.kt                    # 改动：遥测 scope 相关键
```

```text
client/app/src/test/java/com/qingqi/adskip/telemetry/
├── TelemetryPrivacyTest.kt          # 红线：断言不引用节点 text/desc
├── TelemetryBufferTest.kt           # FIFO、容量上限、持久化
├── TelemetryContractTest.kt         # 与服务端契约向量对齐
└── TelemetryPerfTest.kt             # D1：track() 调用开销基准
```

### 2.2 服务端

```text
server/src/
├── telemetry/
│   ├── db.ts                        # bun:sqlite 初始化 + 迁移
│   ├── schema.ts                    # 【单写者】事件 schema + 字段白名单 + schema_ver
│   ├── ingest.ts                    # 校验 / 限流 / 幂等 / 白名单
│   ├── rawWriter.ts                 # 按天 JSONL + 停机 flush
│   ├── rollup.ts                    # 幂等聚合 + meta_rollup_state
│   ├── backfill.ts                  # 重算
│   └── reconcile.ts                 # 日对账
├── api/
│   ├── metricsApi.ts                # 指标查询 + k-匿名 + 缓存
│   ├── insightsApi.ts               # 阈值告警 + RICE 排序
│   └── index.ts                     # 改动：注册 v2 路由
├── storage/store.ts                 # 改动：修复 byChannel / recent / byDay
├── types/rules.ts                   # 改动：StatsSummary 增加 byChannel
└── config.ts                        # 改动：遥测相关配置项
```

```text
server/test/
├── telemetry.contract.test.ts       # 白名单 + schema_ver
├── telemetry.ingest.test.ts         # 幂等 / 乱序归日 / 拒绝未知字段
├── telemetry.rollup.test.ts         # 幂等重跑 / backfill
└── telemetry.reconcile.test.ts      # 对账一致性

server/data/telemetry/               # 运行时产物，不入库
```

### 2.3 看板

```text
server/public/
├── admin.html                       # 改动：新增「洞察」入口
└── insight.html                     # 新增：行为分析看板
```

> 设计原型见 [docs/diagrams/telemetry-analytics-dashboard.html](../diagrams/telemetry-analytics-dashboard.html)（**非事实源**，仅示意面板结构）。

### 2.4 依赖边界

| 规则 | 守护方式 |
|---|---|
| `telemetry` 不得依赖 `engine`，反之亦然 | `ArchitectureBoundaryTest` 扩展 |
| `ui` 不得 import `service` | 现有契约测试 |
| 事件常量只出自 `Events.kt` / `schema.ts` | 单写者 + 契约测试 |
| 看板只读 metricsApi，不直连 SQLite | 代码评审 |

> **不在仓库根新增目录**：`ProjectStructureTest` 的 `ALLOWED_ROOT_DIRS` 为白名单制。
> 遥测代码全部落在既有 `client/` 与 `server/` 内，不触碰根结构。

---

## 3. 开发排期

### 3.1 里程碑

| 里程碑 | 周期 | 核心交付 | 出口条件（Gate） |
|---|---|---|---|
| **M0 准备** | 第 1 周 | 埋点字典冻结、隐私评审、开关设计 | 前提 A5/A6 确认；字典 v1 签署 |
| **M1 可观测地基** | 第 2–7 周 | 客户端 SDK + `skip_outcome` + 接入 + 原始存储 + 诊断面板 | 漏跳/拦截可读；对账 100%；P95 增量 ≤5ms；白名单丢弃 100% |
| **M1.5 基线校准** | 第 8–9 周 | 14 天真实数据、阈值校准（观察期，无工时） | 产出基线报告；O3 阈值定稿 |
| **M2 指标与看板** | 第 10–13 周 | rollup + metricsApi + 看板主体 | 趋势/渠道/排行/诊断可用 |
| **M3 激活与留存** | 第 14–15 周 | `onboarding_step` + 漏斗 + 留存 | 能定位最大流失步（按 ROM）；D7 可算 |
| **M4 存活与路径** | 第 16–17 周 | `svc_lifecycle` + 异常路径 + 洞察引擎 | 在线率按 ROM 可读；异常模式自动列出 |
| **M5 运营闭环** | 第 18 周 | `rule_fire` + 待办清单 + 治理面板 + 文档 | Top-N 待办可用；一键清空可用 |

### 3.2 任务拆解（WBS）

| ID | 任务 | 模块 | 依赖 | 工时(人日) | 产出 |
|---|---|---|---|---|---|
| **M0** | | | | **4** | |
| T-001 | 埋点字典 v1 冻结 + 事件 schema 定义 | 设计 | — | 2 | `Events.kt` / `schema.ts` 初稿 |
| T-002 | 隐私分级评审 + 白名单字段表 | 设计 | T-001 | 1 | 白名单表 + 分级结论 |
| T-003 | 遥测开关与征询流程设计 | 设计 | T-001 | 1 | 交互稿 + scope 语义 |
| **M1 客户端** | | | | **14.5** | |
| T-010 | `TelemetryConfig` + Prefs 扩展 | client | T-001 | 1.5 | scope/采样/容量配置 |
| T-011 | `TelemetryBuffer` JSONL + FIFO | client | T-010 | 2 | 本地缓冲 |
| T-012 | `TelemetryPrivacy` 分级过滤 | client | T-010 | 1.5 | 客户端白名单 |
| T-013 | `TelemetryUploader` 批量 + 退避 | client | T-011 | 2.5 | 复用 SyncClient |
| T-014 | `skip_outcome` 埋点（改 trySkip） | client | T-013 | 2 | 三分结局 |
| T-015 | `svc_lifecycle` / `sync_result` 埋点 | client | T-013 | 1 | 存活与同步 |
| T-016 | 熔断与采样降级开关 | client | T-013 | 1 | 故障降级能力 |
| T-017 | 客户端单测（privacy/buffer/contract/perf） | test | T-016 | 3 | 4 个测试类 |
| **M1 服务端** | | | | **13.5** | |
| T-020 | `db.ts` + 迁移（bun:sqlite） | server | T-001 | 2 | 表结构 |
| T-021 | `schema.ts` 白名单 + schema_ver | server | T-001 | 1.5 | 强校验 |
| T-022 | `ingest.ts` 校验/限流/幂等 | server | T-020,021 | 2.5 | 接入端点 |
| T-023 | `rawWriter.ts` 按天 JSONL + 停机 flush | server | T-022 | 1.5 | 原始层 |
| T-024 | 服务端单测（contract/ingest） | test | T-023 | 2.5 | 2 个测试文件 |
| T-025 | 修复既有三项缺陷（byChannel/recent/byDay） | server | — | 1 | 立即可用 |
| T-026 | 服务端配置与路由注册 | server | T-022 | 2.5 | v2 路由 |
| **M2 聚合与看板** | | | | **18** | |
| T-030 | `rollup.ts` 幂等聚合 | server | T-023 | 3 | 日粒度事实表 |
| T-031 | `backfill.ts` + `reconcile.ts` | server | T-030 | 2 | 重算 + 对账 |
| T-032 | `metricsApi` 查询 + k-匿名 + 缓存 | server | T-030 | 2.5 | 指标接口 |
| T-033 | 服务端单测（rollup/reconcile） | test | T-031 | 2.5 | 2 个测试文件 |
| T-034 | 看板骨架 + 趋势 + 排行 | web | T-032 | 3 | `insight.html` |
| T-035 | 漏跳/误拦诊断面板 | web | T-032 | 2 | 诊断面板 |
| T-036 | CI 新增遥测 job | ci | T-024,033 | 1 | workflow 改动 |
| T-037 | 部署脚本 + 灰度开关 + 回滚演练 | ops | T-026 | 2 | 部署手册 |
| **M3 激活与留存** | | | | **11** | |
| T-040 | `onboarding_step` 埋点 | client | T-013 | 1 | 漏斗数据源 |
| T-041 | `screen_view/exit` + `ui_click` 埋点 | client | T-013 | 1.5 | 浏览/点击/停留 |
| T-042 | `fact_funnel_daily` + 漏斗 API | server | T-030 | 2 | 漏斗接口 |
| T-043 | `fact_device_daily` + 留存 API | server | T-030 | 2 | 留存接口 |
| T-044 | 漏斗 + 留存看板 | web | T-042,043 | 3 | 看板面板 |
| T-045 | 真机验证（≥2 ROM） | test | T-041 | 1.5 | 验证报告 |
| **M4 存活与路径** | | | | **7** | |
| T-050 | 存活指标 API（按 ROM 分群） | server | T-043 | 1.5 | 在线率 |
| T-051 | 异常路径检测 | server | T-043 | 2.5 | 模式识别 |
| T-052 | 异常路径 + 存活看板 | web | T-050,051 | 2 | 看板面板 |
| T-053 | 洞察引擎（阈值 + RICE） | server | T-050 | 1 | 洞察卡片 |
| **M5 运营闭环** | | | | **6** | |
| T-060 | `rule_fire` 埋点 | client | T-013 | 1 | 规则效能 |
| T-061 | 漏跳帕累托 + 待办清单 | server | T-053 | 2 | Top-N 排序 |
| T-062 | 治理面板（scope/采样/保留/清空/导出） | web+server | T-032 | 2 | 治理能力 |
| T-063 | 文档（埋点字典/运维手册/隐私说明） | docs | — | 1 | 3 份文档 |
| **合计** | | | | **74 人日** | |

### 3.3 依赖与关键路径

```text
T-001/002/003（字典与隐私）
        ↓
T-010~013（客户端 SDK 核心）──┐
        ↓                     │
T-014/015/016（具体埋点）     │
                              │
T-020/021（服务端 schema/db） │
        ↓                     │
T-022（ingest） → T-023（raw） │
        ↓                     │
T-030（rollup） → T-031（对账）│
        ↓                     │
T-032（metricsApi） → T-034/035（看板）
        ↓
T-042/043（漏斗/留存）→ T-044
        ↓
T-050~053（存活/路径/洞察）
        ↓
T-060~062（运营闭环）
```

**关键路径**：`T-001 → T-010~013 → T-022 → T-023 → T-030 → T-032 → T-034`

**可并行**：客户端埋点（T-014~016）与服务端接入（T-020~023）无依赖；文档（T-063）可全程穿插。

### 3.4 工时汇总

| 类别 | 人日 | 占比 |
|---|---|---|
| 设计 | 4 | 5.4% |
| 客户端 | 15 | 20.3% |
| 服务端 | 29.5 | 39.9% |
| 测试 | 9.5 | 12.8% |
| 看板 | 12 | 16.2% |
| CI/部署/文档 | 4 | 5.4% |
| **合计** | **74** | 100% |

> 74 人日 ÷ 5 = 约 14.8 周纯工时；叠加 2 周基线观察期与并行开销 → **18 周日历**（与 A3 一致）。
> 量级估算，置信度 ±40%，不是承诺。

### 3.5 范围裁剪预案

> 换算基准：1 人全职时 **5 人日 ≈ 1 周**，且不含 2 周基线观察期。

| 裁剪方案 | 释放人日 | 剩余人日 | 预计周期 | 影响 |
|---|---|---|---|---|
| 全量交付 | — | 74 | 18 周 | — |
| 砍 M5 运营闭环（T-060~063） | −6 | 68 | 约 17 周 | 无规则待办、无治理面板 |
| 再砍 M4 异常路径（T-051/052） | −4.5 | 63.5 | 约 16 周 | 只剩服务存活计数 |
| 再砍 M3 留存曲线（T-043/044 留存部分） | −4 | 59.5 | 约 15 周 | 无 D7/D30 留存 |
| 只保留 M1 + 最小看板（T-034） | — | 约 35 | 约 9 周 | 只有漏跳/误拦诊断 |

> **M1 绝不可裁剪** —— 它是所有洞察的地基。

---

## 4. 测试策略、CI/CD 与部署

### 4.1 测试策略

| 层级 | 范围 | 工具 | 关键用例 |
|---|---|---|---|
| 单元 | 客户端 | JVM 单测 | ① `TelemetryPrivacyTest`：**断言遥测模块不引用节点 text/desc**（红线守护）<br>② `TelemetryBufferTest`：FIFO、容量上限、落盘/恢复<br>③ 事件构造不抛异常、OFF 档零上报 |
| 单元 | 服务端 | `bun test` | ① 白名单：未知字段（如 `screen_text`）必须被丢弃并记录<br>② 幂等：同批重复投递 3 次计数不增<br>③ 乱序归日：`occurred_at` 与 `sent_at` 跨天时归入正确日期<br>④ rollup 幂等：重跑两次结果一致<br>⑤ 对账：raw 与 rollup 差值 = 0 |
| 契约 | 双端 | 契约向量 | 客户端样本 → 服务端必须接受；服务端样本 → 客户端解析一致。复用 `selectors.contract.test.ts` 模式 |
| 性能 | 客户端 | benchmark | `track()` 开销；trySkip 埋点前后 P95 对比 → **增量 ≤5ms** |
| 真机 | 端到端 | ≥2 台不同 ROM | 连续 30 分钟 profile：无新增 ANR、跳过成功率不下降；MIUI 回置场景数据完整 |
| 回归 | 全量 | 现有门禁 | 见 §4.2 |

### 4.2 CI

沿用现有 6 项门禁（DCO / Android Build & Test / Structure Contract / Server Tests / Secret Scan / Version & Release Contract），新增：

| 门禁 | 命令 | 说明 |
|---|---|---|
| 客户端遥测单测 | `cd client && ./gradlew testDebugUnitTest --tests "*Telemetry*"` | 纳入既有 Android job |
| 隐私红线守护 | 同上（`TelemetryPrivacyTest`） | **失败即阻断合并** |
| 服务端遥测单测 | `cd server && bun test` | 新测试文件自动纳入 |
| 类型检查 | `cd server && bun run typecheck` | 若 `.bin` shim 损坏，改用 `node node_modules/typescript/bin/tsc --noEmit` 或 `bun install --force` |
| 静态检查 | `cd client && ./gradlew ktlintCheck` | 既有 |

> **已知环境坑**：Gradle 曾因 `GRADLE_USER_HOME` 缓存锁导致初始化失败，需确保缓存目录可写或改目录。

### 4.3 上线部署

| 环节 | 方案 |
|---|---|
| 部署形态 | Bun 进程由 systemd 托管；TLS 由受信反向代理终止，应用绑 `127.0.0.1`，**明文端口不暴露公网** |
| 三级开关（灰度） | ① 全局 `scope`（off/core/full）② 采样率 ③ **单类事件熔断**（可远程关闭） |
| 发布顺序 | 先发服务端（新端点对旧客户端无影响）→ 再发客户端（默认 off，无感） |
| 回滚 | ① 服务端关闭 `/api/v2/telemetry` 路由即回到现状<br>② 客户端开关置 off<br>③ 删除 `data/telemetry/` 完全回退，**不影响现有 stats** |
| 监控 | ingest 成功率、rollup 延迟、对账差异、磁盘占用、`_other` 占比 |
| 备份 | SQLite 每日 copy 轮转（复用 `rotateBackup` 模式）；raw 随既有备份策略 |

### 4.4 兼容与迁移

- 无存量数据迁移：遥测为全新链路（ADR-8），旧 `data/stats/*.json` 保持不动。
- 客户端分批升级：信封带 `schema_ver`，服务端同时支持相邻两个版本，一个迭代后下线旧版。
- 离线补报：按 `occurred_at` 归日，不影响历史聚合正确性。

---

## 5. 人力与资源

| 角色 | 投入 | 职责 |
|---|---|---|
| 全栈开发（兼 TL） | 1.0 | 客户端 SDK + 服务端链路 + 看板；技术决策；PR 自审 |
| 规则运营 / 数据分析 | 0.5（M3 起） | 消费洞察、编写 Top-N 规则、验证效果 |
| 隐私/安全评审 | 随 PR | 审查新增事件的分级与字段白名单 |

资源：≥2 台不同 ROM 真机（D1/D2 必须真机，模拟器无法复现 MIUI 回置）；JDK 17+、compileSdk 37、Bun 1.1+；服务端磁盘初期 < 100 MB。

---

## 6. 分工与协作规范

沿用仓库既有约定（[CONTRIBUTING.md](../../CONTRIBUTING.md) / [AGENTS.md](../../AGENTS.md) / [AGENT-WORKFLOW.md](../development/AGENT-WORKFLOW.md)）：

| 项 | 规范 |
|---|---|
| 分支 | `<type>/<id>-<slug>`，type ∈ `feature`/`fix`/`docs`/`ci`/`test`/`refactor`；从最新 `main` 创建 |
| 提交 | Conventional Commits，标题 ≤72 字符；**必须 `git commit -s`**（DCO） |
| 工作区 | `git worktree` 落点 `.worktrees/<agent>-<slug>/`；**主检出禁止直接提交** |
| 合并 | Squash Merge；合并后删除源分支 |
| 单写者 | `Events.kt`、`schema.ts`、埋点字典、`CHANGELOG.md` / `ROADMAP*.md` / `ARCHITECTURE.md` / `docs/README.md` |
| 交接 | 改了什么 / 边界变化 / 验证数字 / 遗留风险 / 下一步命令 |

**Definition of Done**：

- [ ] 事件已登记进埋点字典，且有对应消费指标
- [ ] 通过隐私分级评审，字段在白名单内
- [ ] 单测覆盖（含契约测试），CI 全绿
- [ ] `ktlintCheck` + `assembleDebug` + `testDebugUnitTest` 通过
- [ ] `bun test` + `bun run typecheck` 通过
- [ ] 主链路性能未劣化（P95 增量 ≤5ms）
- [ ] 提交带 DCO 签名，PR 含五项交接

---

## 7. 附录：关键命令

```bash
# 客户端
cd client
./gradlew ktlintCheck
./gradlew testDebugUnitTest
./gradlew testDebugUnitTest --tests "*Telemetry*"
./gradlew assembleDebug

# 服务端
cd server
bun test
bun run typecheck
# typecheck 若因 .bin shim 损坏失败：
node node_modules/typescript/bin/tsc --noEmit     # 或 bun install --force

# worktree（禁在主检出直接提交）
git config core.hooksPath githooks
git worktree add .worktrees/wb-telemetry -b feature/201-telemetry-sdk
```
