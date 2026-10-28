# 开发流程合规审计与流程加固（FOLLOW-UP-PROCESS-AUDIT）

> 状态：审计完成，加固项随本提交落地；最后更新：**2026-10-29（第二次复跑，见第 6 节）**。
> 审计范围：v2.0（2026-08-24 初始提交）至 v3.4.0 后（2026-10-11）全部 102 个 main 提交、13 个版本标签、开放 PR 与发布记录。审计口径为仓库自订规则（[CONTRIBUTING.md](../../CONTRIBUTING.md)、[AGENTS.md](../../AGENTS.md)、[AGENT-WORKFLOW.md](../development/AGENT-WORKFLOW.md)），全部可机械验证。
> 第 1–5 节为**首次审计（2026-10-11，重置前）**，保留原文不追溯修改；第 6 节为**第二次复跑（2026-10-29，重置后）**，按 SDLC 六阶段评估。

## 1. 审计发现

| # | 发现 | 证据 | 严重度 | 处置 |
| --- | --- | --- | --- | --- |
| 1 | **58% 的 main 提交无 PR 关联**（59/102 主题不含 `(#N)`），违反「所有改动经 PR 合并」 | `git log` 主题扫描 | 高 | 流程加固 A2 + 钩子（`ci/agent-guardrails` / #96）+ 本审计后执行 |
| 2 | **主分支历史曾被重写**：2026-08-30 一次「版本历史整理」产生 10 个 merge commit 与「8 个逻辑提交，零 merge noise」的重排 | `git log --merges` 2026-08-30 批次 | 高 | 列入历史瑕疵（见第 2 节豁免线）；加固 A3 禁止再现 |
| 3 | **v3.0.0 与 v3.0.1 指向同一提交**（`b7ebabb`），且 v2.0.0–v3.0.1 五个标签全部为 2026-09-04 同日追溯补打 | `git tag --sort=creatordate` | 高 | 列入历史瑕疵（标签不可移动，不追溯）；加固 A4 固化「一 commit 一标签」 |
| 4 | **提交标题屡次超 72 字符**（≥5 例，含 #82/#83 等近期提交） | 主题长度扫描 | 中 | 加固 A1：commit-msg 钩子机器拦截 |
| 5 | **非 Conventional Commits 格式 ≥13 例**（`v3.2.0：…`、`build+ui: …`、11 个 `merge: …`） | 主题正则扫描 | 中 | 加固 A1 |
| 6 | **main 上存在 15 个 merge commit**（含 3 个发布 back-merge 未走 PR） | `git rev-list --merges` | 中 | 加固 A3：main 仅接受 squash 产生的单线性历史 |
| 7 | versionCode 序列合规 ✅（10→12→13→14 单调递增）；BREAKING 包名迁移落在 minor（3.4.0）符合 Android `versionName` X.Y 约定（已文档化） | 构建配置历史 | — | 无需处置 |
| 8 | CHANGELOG 与 RELEASE-HISTORY 对 13 个标签的覆盖完整 ✅；各发布制品可追溯到 tag + commit ✅（v3.0.x 同提交双标签除外，见 #3） | 逐标签核对 | — | 无需处置 |

## 2. 历史豁免线（不追溯原则）

- 上述 #2、#3 发生在流程文档定稿与机器门禁建立之前，且**标签与制品一经推送不可移动**是既定规则——对历史瑕疵做追溯性修复（改标签、重写历史）的代价与风险高于其收益。
- **豁免线划在 `v3.1.0`（2026-09-29，tag `ad188b0`）**：自该版本起，发布记录完整、制品签名链路生效、本审计的加固项生效。v3.1.0 之前的格式类瑕疵（#1/#4/#5/#6 的历史实例）不再逐条修复。
- 本豁免线是**一次性**的：此后任何新提交不适用。

## 3. 流程加固（本提交落地，机器优先）

| # | 加固项 | 机制 | 状态 |
| --- | --- | --- | --- |
| A1 | 提交标题机器校验：Conventional Commits 格式 + ≤72 字符 | `githooks/commit-msg`（merge 提交豁免；逃生口与 pre-commit 一致） | 本提交 |
| A2 | 主检出提交拦截 | `githooks/pre-commit` | 已落地（#96） |
| A3 | main 历史线性：合并一律 squash（远端 PR 流程天然保证）；本地禁止产生 merge commit 入 main | CONTRIBUTING「版本控制最佳实践」增补 | 本提交 |
| A4 | 标签唯一性：一个 commit 不得承载多个版本标签；发布 bump 提交必须经 PR（禁止在主检出直推 release 提交） | CONTRIBUTING「供应链与可追溯」增补 | 本提交 |

## 4. 重规划后的完整开发流程（现行版）

1. **需求**：Issue 立项（模板）→ 认领板登记（AGENT-WORKFLOW 3.2）；
2. **开发**：独立 worktree（`.worktrees/<agent>-<slug>`）→ `pre-commit` 拦截主检出 → `commit-msg` 校验格式与长度 → 契约先行（跨边界改动先写失败测试）；
3. **验证**：`ktlintCheck` + `assembleDebug` + `testDebugUnitTest` + `bun test` + `bun run typecheck` 自跑全绿；
4. **合并**：PR（多 Agent 协作检查三项）→ CI 全门禁 → Squash Merge（main 历史保持单线性，每个提交天然携带 `(#N)`）→ 认领板状态更新；
5. **发布**：`release/vX.Y.Z` 分支 bump 版本（单独提交、经 PR）→ tag `vX.Y.Z` 唯一指向 → CI 签名 + `SHA256SUMS` → RELEASE-HISTORY / CHANGELOG 同步；
6. **治理**：本审计每季度复跑一次（同口径机械扫描），偏差进 FOLLOW-UP。

## 5. 与其他治理工件的关系

- 钩子基础设施（`core.hooksPath`、主检出拦截）由 [#96](https://github.com/LiYihai1121/qingqi/pull/96) 建立，本提交在其上追加 `commit-msg`；
- 认领板与协作事件记录见 [#95](https://github.com/LiYihai1121/qingqi/pull/95)；架构演进（R1–R3）见 [#101](https://github.com/LiYihai1121/qingqi/pull/101)；
- 本文档为**点时审计报告**，不承载常驻规则——常驻规则一律进 CONTRIBUTING（本提交已增补），避免两份真相。

---

## 6. 第二次复跑（2026-10-29）：SDLC 六阶段评估

> 复跑口径：第 4 节第 6 条要求的「每季度同口径机械扫描」+ 按需求/设计/开发/测试/发布/维护六阶段逐项对账。
> 基线：重置后 `main`（`e2e13f9`）、已发布 `v1.0.0` / `v1.0.1` / `v1.1.0`、9 个本地分支 + 6 个 worktree、34 个测试类。
> 机械扫描项：提交标题正则、PR 关联率、分支清理、worktree 落点、CI 作业清单、测试/代码行数比、覆盖率配置、`androidTest` 存在性。

### 6.1 成熟度总览

| 阶段 | 成熟度 | 一句话判断 |
| --- | --- | --- |
| 需求分析 | **2 / 5** | 有能力矩阵与出口条件，但没有需求规格、没有追溯链、没有用户反馈闭环 |
| 设计 | **4 / 5** | 文档密度与契约先行制度化程度高；缺独立 ADR 与非功能目标 |
| 开发 | **4 / 5** | 提交与分支规范实测优秀；但门禁依赖手动启用，可被静默绕过 |
| 测试 | **3 / 5** | 契约测试与 CI 强；无覆盖率、无仪器化测试、真机验收未完成 |
| 发布 | **5 / 5** | 版本契约、签名校验、台账追溯三者闭环，是六个阶段里最成熟的 |
| 维护 | **2.5 / 5** | 有技术债台账但无 owner/截止；线上零可观测，问题只能靠用户反馈 |

**总体结论**：这是一套**「可机器验证的部分做到了 L3–L4，不可机器验证的部分停在 L1–L2」**的不均衡流程。工程门禁（提交、契约、发布）远超同规模项目；需求与运营（要做什么、线上怎么样）基本靠个人判断。风险不是「流程没写」，而是「写了的部分被执行、没写机制的部分靠自觉」。

### 6.2 需求分析

**实际执行**：`ROADMAP.md`（版本序列）+ `ROADMAP-ADS.md`（广告类型覆盖矩阵、Phase 0–5 周次、里程碑出口条件）+ `.github/ISSUE_TEMPLATE/`（bug/feature 两套）+ `TOP30-REAL-DEVICE-ACCEPTANCE.md`（验收口径）。L2/L3 设了 Phase 0 合规评审硬门。

**偏差**：

| # | 偏差 | 证据 |
| --- | --- | --- |
| R1 | **无需求规格文档**。能力矩阵描述「治理哪类广告」，不描述「用户要什么、怎样算做到」 | `PLAN-OFFLINE-SYNC.md` §2.2 自述「本项目现无独立需求文档」；全仓无 `REQUIREMENTS-*.md` |
| R2 | **需求追溯链断裂**。Issue → 分支 → PR → CHANGELOG 无强制关联 | 近 100 提交 24 条无 `(#N)`（较首次审计 58% 大幅改善，但仍非零） |
| R3 | **优先级无成本依据**。ROADMAP-ADS 给周次，不给工作量/风险量化；「范围内/范围外」的显式声明仅 `PLAN-OFFLINE-SYNC` 一份 | 该文 §1.4 是全仓唯一成体系的非目标清单 |
| R4 | **无用户反馈闭环**。无 issue triage 记录、无版本回访、无「用户报告 → 修复 → 回访」的跟踪 | 仓库无 triage 文档；`FOLLOW-UP.md` 的缺陷全部来自内部实测，无一条来自用户 |

**改进建议**：① Issue 模板增加「验收标准」「非目标」两个必填段；② 一页式需求卡（问题 / 受影响用户 / 验收标准 / 非目标 / 归属里程碑），不追求重文档；③ CI 增加 PR 描述必含 `Closes #N` 的检查——这是让 R2 归零的最低成本手段。

### 6.3 设计

**实际执行**：11 份 `DESIGN-*.md` + `ARCHITECTURE.md`（11 节，含分层边界表、安全模型、协议演进）+ `ARCHITECTURE-DIRECTORY-STRUCTURE.md`（完整目录树）+ 15 个契约测试把设计约束变成可执行断言。跨边界改动「先写失败测试」已制度化并写进 `AGENTS.md` 与 `contract-first` 技能。

**偏差**：

| # | 偏差 | 证据 |
| --- | --- | --- |
| D1 | **无独立 ADR**。决策散落在各 DESIGN 文档的「设计决策」节，无法按「决策—备选—后果」检索；新文档不引用旧决策 | 全仓 `find -iname "*adr*"` 为空 |
| D2 | **设计文档职责边界靠人守**。11 份 DESIGN 之间存在交叉（离线同步 / 服务端移动端就绪 / 架构演进三者的范围重叠） | 本日新增的 `DESIGN-SERVER-MOBILE-READINESS.md` 只能在首部**手工声明**边界，无机制校验 |
| D3 | **无非功能目标**。有线程模型，无目标吞吐 / 延迟 / 可用性 / 容量 | `ARCHITECTURE.md` 第 6 节只有线程模型，无 SLO 段 |

**改进建议**：① 建 `docs/planning/adr/`（或直接在 DESIGN 内加固定 `## 决策记录` 段并编号），ADR 模板三行：决策 / 被否方案 / 后果；② `ARCHITECTURE.md` 增「非功能目标」一节——哪怕写「单实例，目标 ≤10 QPS，无可用性承诺」也是把隐含假设显式化，比留空强。

### 6.4 开发

**实际执行**：分支 `type/id-slug`；`githooks/pre-commit` 拦主检出直接提交；`githooks/commit-msg` 校验 Conventional Commits + ≤72 字符；worktree 隔离 + 认领板 + 唯一写入者。

**实测数据（正向）**：近 60 条提交标题 **59/60** 合规（唯一例外 `release(v3.4.1):` 属重置前历史）；近 100 条提交 **76%** 带 PR 号；重置后 `main` 无 merge 提交（全 squash）。

**偏差**：

| # | 偏差 | 证据 |
| --- | --- | --- |
| Dev1 | **门禁依赖手动启用**。`git config core.hooksPath githooks` 是每克隆一次的手工步骤，未配置即完全无保护 | `githooks/pre-commit` 头注释自述启用方式；无脚本或 CI 校验该配置是否生效 |
| Dev2 | **钩子拦不住「在 main 工作区改动」，只拦提交** | 本次审计自身即如此：`docs/README.md`、`ROADMAP-ADS.md` 的改动直接落在 `main` 工作区，未建分支 |
| Dev2b | **并发写同一工作区（本次审计期间实测）**。审计执行期间，另一会话于 01:43 在 `main` 工作区新建了未跟踪的 `PLAN-CLIENT-REFORM.md`（23 KB）并同步向 `docs/README.md` 追加登记行，与本次审计的编辑落在同一文件 | `git status` 显示 `?? docs/planning/PLAN-CLIENT-REFORM.md`；`git diff docs/README.md` 可分离出两组互不相关的改动。这正是 AGENT-WORKFLOW §3.1「同一文件同一时间只有一个写入者」要防的场景，且**没有任何机器机制发现它** |
| Dev3 | **worktree 落点存在违规实例** | `git worktree list` 含 `.agents/worktrees/feature/199-service-permissions-drop-tile`，规范要求 `.worktrees/<agent>-<slug>/` |
| Dev4 | **已合并分支未删除** | `fix/20261027-server-reliability`、`refactor/remove-cloud-sync` 已并入 `main` 仍在本地 |
| Dev5 | **认领板失效** | `AGENT-WORKFLOW.md` 认领板记录 `gzdx` 系列 worktree，与实际 6 个 `codex-*` worktree 完全不符；且引用已不存在的 `core/ThemePreferences.kt` |

**改进建议**：① 提供 `scripts/setup.sh`（`core.hooksPath` + 预检），并在 CI 里断言 `git config core.hooksPath` 输出为 `githooks`——这是当前**最高性价比**的加固，因为所有本地门禁都挂在这一个配置上；② 分支删除脚本化（`git branch --merged main` 自动清理）；③ 认领板移出规范文档（运行时状态不入库）。

### 6.5 测试

**实际执行**：34 个测试类；客户端 5573 行测试 / 10946 行主代码（**51%**），服务端 1199 / 1879（**64%**）；CI 7 个作业（`version-contract` / `structure` / `android` / `server` / `dco` / `secret-scan`）；15 个架构契约测试；双端选择器夹具；稳定性测试有真实执行记录（2026-10-11，Mi 9 / Android 11 / MIUI V125 / v3.4.0）。

**偏差**：

| # | 偏差 | 证据 |
| --- | --- | --- |
| T1 | **无覆盖率度量**。行数比 ≠ 覆盖率；哪些分支没被测到无从得知 | 无 jacoco / kover 配置；`ARCHITECTURE-DIRECTORY-STRUCTURE.md` 自己把「无覆盖率配置」列为差距第 2 项 |
| T2 | **无仪器化 / E2E 测试**（最严重）。核心能力是无障碍服务，其行为（服务绑定、窗口事件、真实点击）无法用 JVM 单测覆盖 | `client/app/src/androidTest/` 不存在 |
| T3 | **真机验收未完成** | `TOP30-REAL-DEVICE-ACCEPTANCE.md` 首行自述「**未完成、未验证**」 |
| T4 | **无性能 / 负载基线**。服务端无压测；客户端无启动耗时、内存、耗电基线 | 稳定性计划有 KPI 表，但只有单次记录，无回归对比 |
| T5 | **`bun run typecheck` 本地不可用**，而它是 `AGENTS.md` 五条门禁之一 | 实测在 `server/` 执行返回 `255`：`could not open bin metadata file ... corrupted node_modules`（需 `bun install --force`） |

**改进建议**：① 接入 kover（Gradle 原生，不引入新依赖），先观测后设阈值，与 `DESIGN-ARCH-EVOLUTION.md` 的 R1 同批；② `androidTest` 先做最小集三条：服务能绑定、注入一个含「跳过」的节点能被点击、黑名单包不被点击——这三条覆盖了历史上真实出过的事故（自检超时、系统界面误跳）；③ `typecheck` 失败前置：在 `bun install` 后加一步自检，或改用 `bun node_modules/typescript/bin/tsc --noEmit` 直接调用（本日实测该方式可用）。

### 6.6 发布

**实际执行**：`ci.yml` 的 `version-contract` 作业强制 server `X.Y.Z` / Android `X.Y` / `versionCode` 正整数 / 三者对齐 / CHANGELOG 有对应版本段；`release.yml` 校验 annotated tag、提交对象、版本一致，构建 R8 变体，强制 `apksigner verify`，产出 `SHA256SUMS`；`RELEASE-HISTORY.md` 逐版记录 tag → commit → 制品 → 校验和，并把 `v1.0.0` 如实标为「历史标签、不补建 Release」。

**偏差**：

| # | 偏差 | 证据 |
| --- | --- | --- |
| Rel1 | **无回滚演练记录**。规则写了（优先回滚已验证制品或 `git revert`），但无一次实际演练证据 | `RELEASE-HISTORY.md` 只有发布记录，无回滚记录 |
| Rel2 | **服务端无独立发版路径**。`server/package.json` 版本随 Android 版本走，服务端热修必须等下一个 App 版本号 | `ci.yml` 的 `version-contract` 强制 `versionName == server major.minor` |
| Rel3 | **无发布后验证清单**。台账记录「发布了什么」，不记录「发布后确认线上可用」 | 缺健康检查、`/download` 可达、管理后台登录三项的核对项 |

**改进建议**：① `RELEASE-HISTORY.md` 增加「发布后验证」列（三项：`/api/v1/health` 200、`/download` 可下载且 SHA-256 匹配、`/admin` 可登录）；② 服务端热修走 `hotfix/<id>-<slug>` + 独立 tag（如 `server-v1.1.1`），与 App 版本解耦——否则一个后端 bug 要等整个 App 发版周期。

### 6.7 维护

**实际执行**：`FOLLOW-UP.md` 技术债台账（已修复 / 待修复 / 风险与回滚）；`CHANGELOG.md` 三段式；dependabot 自动提依赖更新；季度流程审计（本文件）。

**偏差**：

| # | 偏差 | 证据 |
| --- | --- | --- |
| M1 | **技术债无 owner 无 deadline**。表头是「优先级 / 问题 / 方案 / 预计工作量」 | `docs/README.md` 事实源表声称「FOLLOW-UP.md 技术债登记（责任人/截止）」，与文档实际不符 |
| M2 | **线上零可观测**。只有 JSONL 日志 + 200 条内存访问日志；无指标导出、无错误聚合 | `ROADMAP.md` 把 `/metrics` 列为未完成；`FOLLOW-UP.md` 的「可观测版」里程碑整段未勾选 |
| M3 | **不知道用户在用哪个版本**。无版本上报，无法判断能否下线旧协议（`SCHEMA_VERSION_MIN` 无法安全提升） | 上报载荷只有 `deviceId`/`pkg`/`channel`/`ts`，无 `appVersion` |
| M4 | **审计本身不闭环**。首次审计承诺「每季度复跑」，2026-10-11 至 2026-10-29 无第二次记录（本次为补做）；且 A1/A2 加固依赖手动启用钩子，从未被验证生效 | 本文件此前最后更新为 2026-10-11 |

**改进建议**：① `FOLLOW-UP.md` 加「责任人 / 截止」两列（单人项目可写「本人 / 下个 minor」），让「挂着」变成「可见地挂着」；② 上报载荷加 `appVersion`（1 个字段），这是 M3 的最低成本解法，同时解锁「能否提升 `SCHEMA_VERSION_MIN`」的判断；③ `/metrics` 按 `DESIGN-ARCH-EVOLUTION.md` R3 排期，之前先用结构化日志的固定字段（`duplicates` / `gaps` / `clamped`）兜住。

### 6.8 与首次审计的对照

| 首次加固项 | 承诺机制 | 复跑结论 |
| --- | --- | --- |
| A1 提交标题机器校验 | `githooks/commit-msg` | ✅ **生效**：59/60 合规（首次审计为 ≥13 例违规） |
| A2 主检出提交拦截 | `githooks/pre-commit` | ⚠️ **部分生效**：机制有效，但依赖手动 `core.hooksPath`，且只拦提交不拦改动 |
| A3 main 历史线性 | Squash Merge | ✅ **生效**：重置后 `main` 无 merge 提交 |
| A4 标签唯一性 | 一 commit 一标签 | ✅ **生效**：`v1.0.1` / `v1.1.0` 各自指向独立 squash 提交 |
| 季度复跑 | 同口径机械扫描 | ❌ **未执行**：逾期 18 天，本次补做 |

**结论**：首次审计的 4 项加固有 3 项确证生效，说明「把规则变成机器门禁」这条路在本仓库是走得通的。**失效的那一项（季度复跑）恰恰是没有机器门禁的那一项**——这正好印证本轮的核心判断。

### 6.9 改进优先级

| 批次 | 项 | 理由 |
| --- | --- | --- |
| **P0** | Dev1 钩子自动启用 + CI 断言、T2 `androidTest` 最小三条、M1 技术债加 owner/截止 | 前两项是「门禁可被静默绕过」与「核心能力零自动化」，第三项让技术债可见 |
| **P1** | T1 覆盖率度量、R1/R2 需求卡与追溯、Rel3 发布后验证清单、M3 上报加 `appVersion` | 都是低成本高杠杆，且互不依赖 |
| **P2** | D1 ADR、D3 非功能目标、Rel2 服务端独立发版、Dev3/Dev4 分支与 worktree 清理、M2 `/metrics` | 需要决策或排期，不宜顺手做 |
| **持续** | 季度复跑（本次逾期 18 天）→ 建议改为**每个 minor 发布前**触发，挂在发布核对清单里 | 挂在发布流程上比挂在日历上可靠——发布是必然发生的事件，季度不是 |

> 复跑方式见本文第 6.1 节的机械扫描项清单；新增扫描项时应同时更新本清单，避免「审计口径」本身漂移。
