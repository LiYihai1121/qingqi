# 项目目录结构设计（ARCHITECTURE-DIRECTORY-STRUCTURE）

> 状态：已生效；最后更新：2026-10-11。
> 适用范围：本 monorepo（Android 客户端 + Bun/TypeScript 服务端）的完整目录布局与 SDLC 职责映射。
> 本文是**完整目录树与设计理由**的单点事实源；机器强制的白名单见 [ARCHITECTURE.md](ARCHITECTURE.md) 第 2.2 节（由 `ProjectStructureTest` 守护），分层边界见其第 2.1 节。

## 1. 设计原则

1. **目录即契约**：包结构不是命名习惯，而是由 `ArchitectureBoundaryTest` / `ManifestContractTest` / `ProjectStructureTest` 强制的依赖方向声明——目录变化必须先改契约测试（契约先行）。
2. **单一事实源分层**：版本序列在 `docs/planning/ROADMAP.md`，协议在 `docs/api/API.md` 与架构文档第 7 节，目录规则在本文与架构文档第 2.2 节，依赖版本在 `client/gradle/libs.versions.toml` 与 `server/bun.lock`——其余位置只放指针，消除「双重真相」。
3. **本地 + 远端双层门禁**：`githooks/` 在提交瞬间拦截违规，`.github/workflows/` 以 required checks 在合并前收口；逃生口唯一（`ADSKIP_ALLOW_MAIN_COMMIT=1`）且必须在 PR 说明登记。
4. **零信任默认**：密钥、签名材料、运行时数据、Agent 产物目录在根 `.gitignore` 显式枚举；`server/data/`、`client/local.properties`、`signing/` 从不入库。
5. **SDLC 闭环**：每个阶段都有「目录载体 + 机器门禁 + 事实源文档」三件套，任一阶段的审计可被复现。

## 2. 完整目录树

`▲` = 建议新增（当前缺失，来源见第 5 节差距清单）；`[ig]` = 被 `.gitignore` 覆盖、禁入库。

```text
qingqi/                                   # Monorepo 根：客户端 + 服务端 + 治理资产
├── README.md                            # 项目门面：定位/快速开始/目录摘要（非事实源）
├── CHANGELOG.md                         # 用户可见变更（单写者，Keep a Changelog）
├── CONTRIBUTING.md                      # 流程规范：分支/提交/PR 门禁/发版/回滚
├── AGENTS.md                             # Agent 执行摘要（协作一票否决项）
├── SECURITY.md                          # 安全政策：支持版本/上报渠道/安全措施声明
├── DCO                                   # 开发者原创证书（CI DCO Sign-off 强制）
├── CODEOWNERS                            # 代码所有权（路径 → 领域负责人）
├── LICENSE
├── .editorconfig                         # 编辑器无关格式唯一事实源（ktlint 根规则）
├── .gitattributes                        # 行尾/二进制处理（keystore 等按 binary）
├── .gitignore                            # 共享忽略规则（Agent 目录/密钥/产物/运行时数据）
├── .pre-commit-config.yaml               # 本地钩子框架（detect-secrets/ktlint/prettier）
├── kilo.json                             # Agent 配置（skills 挂载/权限）
│
├── client/                               # ===== Android 客户端（Kotlin + Compose）=====
│   ├── settings.gradle.kts               # 模块注册 + includeBuild(build-logic)
│   ├── build.gradle.kts                  # 根构建脚本（ktlint CLI 版本锁定）
│   ├── gradle.properties
│   ├── gradlew / gradlew.bat             # Wrapper（distributionSha256Sum 固定分发源）
│   ├── local.properties                  # ▲ 本机 SDK/签名路径 [ig]
│   ├── gradle/
│   │   ├── libs.versions.toml            # ★ 依赖版本唯一事实源（version catalog）
│   │   ├── wrapper/                      # wrapper jar + properties
│   │   └── verification-metadata.xml     # ▲ 依赖校验和/PGP（供应链完整性）
│   ├── build-logic/                      # 约定插件（复合构建）：统一 Android/Kotlin 配置
│   │   ├── settings.gradle.kts
│   │   └── convention/                   # AdSkipAndroidApplicationPlugin 等
│   └── app/                              # 应用模块（阶段 C 拆分后 engine 可独立）
│       ├── build.gradle.kts              # versionCode/versionName/签名三态策略
│       ├── proguard-rules.pro            # R8 规则
│       └── src/
│           ├── main/
│           │   ├── java/com/qingqi/adskip/
│           │   │   ├── AdSkipApp.kt      # 组合根（Application）
│           │   │   ├── AppContainer.kt   # 依赖注入容器（纯手工，无框架）
│           │   │   ├── ui/               # Compose 界面层（Screen/ViewModel/UiEffect）
│           │   │   ├── core/             # 零业务依赖基础件（LogRing/Clock/Executors）
│           │   │   ├── engine/           # 选择器匹配引擎（纯 JVM 可单测）
│           │   │   ├── service/          # 无障碍服务 SkipAdService
│           │   │   ├── device/           # 厂商 ROM 保活/权限判定（入口表 + 三态状态源）
│           │   │   ├── data/             # 规则/偏好/统计存储（EncryptedSharedPreferences）
│           │   │   ├── net/              # SyncClient（OkHttp + HMAC 验签 + 证书 pin）
│           │   │   └── sync/             # 同步编排（SyncJobService）
│           │   └── res/                  # 资源：values/ values-en/ values-*/ + xml/ + mipmap/
│           │       └── xml/              # network_security_config、无障碍配置
│           ├── test/java/com/qingqi/adskip/   # JVM 单测（Robolectric/MockWebServer）
│           │   ├── arch/                 # ★ 四大守护契约测试（边界/Manifest/结构/卫生）
│           │   └── <package>/            # 与 main 包路对应的业务单测
│           └── androidTest/              # ▲ E2E/仪器化测试（当前为 0）
│
├── server/                               # ===== 服务端（Bun + TypeScript，零运行时依赖）=====
│   ├── package.json                      # scripts(test/typecheck/start) + devDeps（含锁定 tsc）
│   ├── bun.lock                          # 依赖锁定（devDependency 固化）
│   ├── tsconfig.json                     # strict 模式
│   ├── server.ts                         # HTTP 入口：TLS 强制/安全头/优雅停机
│   ├── src/
│   │   ├── config.ts                     # 配置单一入口（env 解析 + 启动告警）
│   │   ├── api/                          # 路由：rules/reports/stats/health/admin（v0+v1）
│   │   ├── middleware/                   # auth（常数时间比较）/rateLimit/accessLog
│   │   ├── storage/                      # 规则存储（原子写 + 备份唯一名）
│   │   ├── types/                        # 协议类型（schemaVersion/规则模型）
│   │   └── utils/                        # validate（选择器白名单）/sign（HMAC）/logger（JSONL）
│   ├── test/
│   │   ├── unit.test.ts                  # 单元：validate/auth/rateLimit/cors/statsSummary
│   │   ├── smoke.test.ts                 # 进程内集成：全路由/ETag/验签/安全头/TLS 绑定
│   │   ├── selectors.contract.test.ts    # ★ 双端契约向量（与客户端夹具对齐）
│   │   ├── selectorSimulator.test.ts     # 管理后台模拟器用例
│   │   ├── fixtures/selectors.contract.json  # ★ 双端契约夹具
│   │   └── ▲ unit/ integration/ 分层目录  # 测试金字塔显式化
│   ├── public/                           # 静态资产：admin.html（管理后台）/index.html（落地页）
│   ├── seed/rules.json                   # 种子规则
│   └── data/                             # 运行时数据 [ig]
│
├── docs/                                 # ===== 文档（README.md 为唯一入口/文档地图）=====
│   ├── README.md                         # ★ 文档地图：登记/命名规范/事实源声明/规划全景
│   ├── api/API.md                        # 协议契约（路由/限频/响应头）
│   ├── architecture/
│   │   ├── ARCHITECTURE.md               # 分层职责/安全模型/目录契约（2.1/2.2 节）
│   │   └── ARCHITECTURE-DIRECTORY-STRUCTURE.md  # 本文：完整目录树 + SDLC 职责映射
│   ├── development/
│   │   ├── DEV-ENVIRONMENT.md            # 本机工具链/SDK/JDK/排障
│   │   └── AGENT-WORKFLOW.md             # 多 Agent 协作：隔离/所有权/契约/交接/认领板
│   ├── planning/
│   │   ├── ROADMAP.md / ROADMAP-ADS.md   # 版本序列 + 里程碑/出口条件（单写者）
│   │   ├── DESIGN-*.md                   # 技术设计（PHASE1-SELECTOR/BUILD-FRAMEWORK/…）
│   │   ├── FOLLOW-UP.md                  # 技术债登记（责任人/截止）
│   │   ├── FOLLOW-UP-PROCESS-AUDIT.md    # 流程审计报告（季度复跑）
│   │   └── RELEASE-HISTORY.md            # 发布链路：tag→commit→Release→校验和
│   ├── testing/                          # 真机验收计划与证据模板
│   ├── guide/                            # ▲ 面向贡献者/用户的操作指南（GUIDE-*.md）
│   └── diagrams/                         # 架构图资产（json 源 + html 渲染，lower-kebab）
│
├── skills/                               # ===== Agent 技能库（随仓库版本控制）=====
│   ├── README.md                         # 技能开发规范（SKILL.md 格式/校验/登记）
│   ├── scripts/new_skill.py              # 技能脚手架
│   └── <domain>-guard/SKILL.md           # 领域守卫技能（accessibility/compose/vpn/…）
│
├── githooks/                             # 本地强制门禁（core.hooksPath）
│   ├── pre-commit                        # 主检出提交拦截（逃生口唯一且须 PR 说明）
│   └── commit-msg                        # Conventional Commits + ≤72 字符校验
│
├── .github/                              # ===== 远端 CI/CD 与协作界面 =====
│   ├── workflows/
│   │   ├── ci.yml                        # PR/推送门禁：structure/android/server + DCO + secret-scan
│   │   └── release.yml                   # tag 发布：历史校验/证书指纹断言/SHA256SUMS/Release
│   ├── dependabot.yml                    # 依赖周更（github-actions/gradle/bun）
│   ├── ISSUE_TEMPLATE/                   # 需求/缺陷立项模板
│   └── PULL_REQUEST_TEMPLATE.md          # PR 自审清单（含 DCO/协作检查）
│
├── scripts/                              # 仓库级自动化脚本（changelog 生成等）
└── .vscode/                              # 仅入库 settings.json + extensions.json

[ig] 忽略层（.gitignore）
├── .kilo/ .kilocode/ .agents/ .mimosa/ .workbuddy/ .workbuddy-ai/ .cursor/   # Agent 产物目录
├── .worktrees/<agent>-<slug>/           # 每 Agent 一工作区（真实 git worktree）
├── node_modules/ client/build/ client/app/build/ *.apk       # 依赖与构建产物
├── *.jks *.keystore signing/ local.properties .env*          # 签名材料与本机配置
└── server/data/                          # 运行时数据
```

## 3. 目录职责（SDLC 映射）

| SDLC 阶段 | 目录/文件 | 功能与交付价值 |
|---|---|---|
| **需求定义** | `docs/planning/ROADMAP*.md`、`.github/ISSUE_TEMPLATE/`、PR 模板「关联 Issue」 | 版本序列单点事实源 + 需求立项入口，保证「提交 → PR → Issue」可追溯 |
| **架构设计** | `docs/architecture/`、`docs/api/`、`docs/diagrams/` | 分层边界与协议契约为**可执行事实**：包结构被守护测试强制，API.md 描述路由与协议版本 |
| **开发实现** | `client/app/src/main/`、`server/src/`、`server/server.ts`、`server/public/` | 客户端按依赖方向分层（ui → service/device/engine，engine/data/net 单向向下）；服务端 api/middleware/storage/utils 单一职责；public 承载管理后台与落地页 |
| **测试验证** | `client/app/src/test/arch/`（守护契约）、`server/test/`（单元+冒烟+双端契约夹具） | 契约先行的载体：边界/Manifest/结构/卫生四类守护为 required check；`fixtures/selectors.contract.json` 固化双端协议向量 |
| **构建工程** | `client/gradle/`（version catalog）、`client/build-logic/`、`client/app/build.gradle.kts`、`server/package.json` + `bun.lock` | 依赖版本单点事实源；约定插件消除配置漂移；catalog 被 build-logic 复用，杜绝第二份目录 |
| **配置管理** | `.editorconfig`（格式）、`client/local.properties` + `server/.env*`（本机，忽略）、`server/tsconfig.json` | 配置即契约：ktlint 以 `.editorconfig` 为唯一根规则；签名密钥只走 CI Secrets |
| **资源资产** | `client/app/src/main/res/`（多 locale + xml）、`server/public/`、`server/seed/rules.json` | 多语言资源由守护测试校验 locale 覆盖；静态资产与种子数据随发布链路分发 |
| **依赖管理** | `libs.versions.toml` / `package.json` + `bun.lock` / `.github/dependabot.yml` | 三层锁定：catalog 钉版本、lockfile 钉传递依赖、Dependabot 周更自动升级 |
| **质量门禁** | `.github/workflows/`、`githooks/`、`.pre-commit-config.yaml` | 本地+远端双层：钩子在提交瞬间拦截，CI 以 required checks 收口 |
| **安全合规** | `SECURITY.md`、`DCO`、`CODEOWNERS`、`.gitignore`（`*.jks/signing/`） | 披露政策 + 原创声明 + 所有权 + 密钥不入库 + CI 密钥扫描（advisory）；声明与实现必须一致 |
| **发布运维** | `.github/workflows/release.yml`、`docs/planning/RELEASE-HISTORY.md`、`scripts/generate-changelog.mjs` | tag 触发 → 历史校验 → R8 构建 → 证书指纹断言 → SHA256SUMS → Release；链路与 CHANGELOG 半自动生成 |
| **协作治理** | `.worktrees/`、`AGENTS.md`、`CONTRIBUTING.md`、`docs/development/AGENT-WORKFLOW.md`（认领板）、`skills/` | 一人一工作区、一任务一分支、单写者事实源；技能库把领域知识固化为可复用守卫 |
| **文档治理** | `docs/README.md`（地图+命名规范）、`docs/guide/` ▲ | 同一事实单点维护；新文档必须登记入地图且命名符合机器规则 |

## 4. 与机器强制规则的关系

- **已生效的目录白名单**（根级条目、`client/` 布局、标准源集、包路径一致）以 [ARCHITECTURE.md](ARCHITECTURE.md) 第 2.2 节与 `ProjectStructureTest` 为准——本文只描述设计意图与完整树，不复制其白名单（避免两份真相）。
- **新增根级条目**：先改 `ProjectStructureTest` 白名单与架构文档 2.2 节，再建目录（契约先行）。
- **新增 docs/ 文档**：命名遵守 `docs/README.md` 文档命名规范（`architecture/` 下须为 `ARCHITECTURE[-<TOPIC>].md`），同 PR 登记到文档地图。

## 5. 差距清单（▲ 项与处置）

| # | 缺口 | 建议动作 | 依据 |
|---|---|---|---|
| 1 | 无 `client/app/src/androidTest/` | 落地保活/权限/跳过主链路 E2E（Maestro 或仪器化） | 测试层缺口：E2E 为 0 |
| 2 | 无覆盖率配置 | 客户端 JaCoCo、服务端 `bun test --coverage`，先观测后阈值 | 度量层缺口 |
| 3 | 无 `client/gradle/verification-metadata.xml` | `--write-verification-metadata sha256` | 供应链完整性 |
| 4 | 无 `.gitleaks.toml` allowlist | secret-scan 转强制前完成历史扫描 triage | K6 后续 |
| 5 | `docs/guide/` 未建 | 随 M1d 规则编写指南创建，按 `GUIDE-<TOPIC>.md` 命名并登记 | 文档地图规划 |
| 6 | `server/test/` 未分层 | 随用例增长拆 `unit/` `integration/` | 测试金字塔显式化 |

---

本文不替代流程规范，不制造第二份真相。
