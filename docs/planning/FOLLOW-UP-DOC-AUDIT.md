# 文档与技能合规审查（FOLLOW-UP-DOC-AUDIT）

> 状态：**一次性审查报告（2026-10-29 实测）**；处置完成后归档或删除，不长期驻留。
> 最后更新：2026-10-29。
> 审查范围：根级 6 份 Markdown + `docs/` 28 份（6273 行）+ `skills/` 15 个技能（2618 行）。
> 复核记录：初稿覆盖 25 份 / 4918 行；2026-10-29 同日复核时增至 28 份 / 6273 行，新增 D18–D19（见 §2.3 末「复核新增」）。
> 审查方法：机器门禁复跑（`ProjectStructureTest` 文档登记逻辑、`SkillContractTest` 判定逻辑）+ 文档声明与源码逐条对账。
> 与 [FOLLOW-UP.md](FOLLOW-UP.md) 的分工：本文是**文档/技能层**的审查快照，`FOLLOW-UP.md` 是代码技术债台账。

---

## 1. 结论

**机器门禁全绿，语义一致性不合格。**

| 维度 | 结论 |
| --- | --- |
| 文档登记与命名 | ✅ 25 份全部登记，命名全部合规（脚本复刻判定：`UNREGISTERED=[]`、`BAD_NAMES=[]`） |
| 技能格式契约 | ✅ 15 个技能全部通过（name/description/登记/LICENSE-NOTICE） |
| **文档内容与代码一致** | ❌ 17 项文档失真（D1–D17），其中 6 项会直接误导使用方 |
| **必要说明覆盖** | ❌ 部署运维、密钥生命周期、测试分层三块缺失（C1–C3） |
| **技能职责与 I/O** | ❌ 15 个技能全部缺「输入/输出」定义；3 个技能违反自身「不复制规则正文」规范（S1–S5） |

**根因判断**：门禁只守「有没有登记 / 格式对不对」，不守「说的和做的是不是一回事」。于是文档在**结构层**被强制对齐，在**语义层**自由漂移。修复建议的落点因此不是「再写一遍文档」，而是把可机检的事实改成机检（见 §5）。

---

## 2. 文档事实漂移（D 组）

### P0：会直接误导使用方

**D1 — `SECURITY.md` 的支持版本表把当前版本标成「不支持」**

- 位置：`SECURITY.md:5-9`
- 现状：`| 3.1.x | ✅ |`、`| < 3.1 | ❌ |`
- 证据：当前 `versionName = "1.1"`（`client/app/build.gradle.kts:88`）、`server/package.json` = `1.1.0`；`RELEASE-HISTORY.md` 明确「SemVer 自 `1.0.0` 起重新计数」。按该表，**当前版本 1.1.0 落在 `< 3.1`，被标为不受支持**。
- 影响：安全研究者/用户据表判断「该版本不再接受漏洞报告」，与事实相反。
- 建议：改为 `| 1.1.x | ✅ |`、`| < 1.1 | ❌ |`，并加一行注：「2026-10-13 仓库重置后版本序列自 1.0.0 起重新计数，`3.x` 属重置前历史」。

**D2 — `API.md` 声明了不存在的 `APK_FILE` 环境变量**

- 位置：`docs/api/API.md:125`
- 现状：「可通过 `APK_FILE` 环境变量指定绝对路径或相对服务端工作目录的文件路径。」
- 证据：`server/src/config.ts:75` 为 `APK_FILE: path.join(ROOT, "..", "AdSkip-latest.apk")`，**未读取 `process.env`**；仅 `server.ts:93` 的 `startServer({ apkFile })` 可注入（测试用）。且 `README.md:241` 写「`config.ts` 硬编码，**无 env 覆盖**」——两处文档互相矛盾。
- 建议：删除该句，改为「路径由 `config.APK_FILE` 固定为 `server/` 上一级；测试可经 `startServer({ apkFile })` 注入」。

**D3 — `API.md` 声明 `MAX_BODY` 可被 env 覆盖，实际不可**

- 位置：`docs/api/API.md:10`
- 证据：`server/src/config.ts:76` `MAX_BODY: 1024 * 1024`，无 `process.env` 读取。`config.ts` 中真正读 env 的只有 9 个：`PORT` / `HOST` / `ADMIN_TOKEN` / `STATS_READ_AUTH` / `RULES_SIGNING_KEY` / `TRUSTED_PROXIES` / `CORS_ORIGINS` / `TLS_CERT` / `TLS_KEY`。
- 建议：删除「env 可覆盖」；或补齐 `process.env.MAX_BODY` 实现（二选一，不要留悬空承诺）。

**D4 — `API.md` 的 CORS 默认行为写反了**

- 位置：`docs/api/API.md:14`
- 现状：「未配置时默认 `*`」
- 证据：`server/src/utils/httpUtil.ts:23-32` 在 `CORS_ORIGINS` 为空时**不发送** `Allow-Origin`；单测已固化该行为（`cors > 未配置白名单不发送 Allow-Origin（跨域默认拒绝）`）。
- 影响：第三方按文档接入会以为默认可跨域，实际浏览器侧被拦。
- 建议：改为「未配置时不发送 `Allow-Origin`（跨域默认拒绝）；需要开放时显式配置 `CORS_ORIGINS=*`」。

**D5 — `API.md` 的上报限流描述漏掉 IP 桶**

- 位置：`docs/api/API.md:13`
- 现状：「上报 30 次/分/deviceId（deviceId 缺失时按 IP）」
- 证据：`server/src/middleware/rateLimit.ts:91-102` 为 **IP 桶 + deviceId 桶双重扣减**，任一耗尽即 429。
- 建议：改为「上报 30 次/分/IP **且** 30 次/分/deviceId（双桶，任一耗尽即 429）」。

**D6 — 落地页版本占位符已废弃，代码与文档仍按旧行为描述**

- 位置：`server/server.ts:26-39,188-193`、`docs/api/API.md:121`
- 证据：`server/public/index.html` 已写死 `v1.1.0`（第 229/237/344/362 行），**不再含 `{{APP_VERSION}}`**；`CHANGELOG.md` [Unreleased] 已记录「落地页版本号写死为 `1.1.0`，不再输出占位符」。但 `server.ts` 仍执行 `html.replaceAll("{{APP_VERSION}}", APP_VERSION)`（no-op），`readAppVersion()` 成为只为死代码服务的函数。
- 建议：二选一——① 删除 `readAppVersion` + `replaceAll` + API.md 的占位符描述；② 恢复占位符（保留「版本号单一事实源」的原设计意图）。**推荐 ②**，因为写死版本号会让落地页与 `package.json` 再次漂移，而 CHANGELOG 记录的那次改动正是为了解决漂移。

### P1：结构性失真

**D7 — 测试计数在 3 份文档里写死且全部过时**

- 位置：`README.md:233`（87 项 / 4 文件）、`docs/planning/DESIGN-VERSION-MANAGEMENT.md:83`（`bun test` 87）、`docs/planning/DESIGN-ARCH-EVOLUTION.md:58`（87 项）
- 证据：实测 `bun test` = **97 pass / 0 fail / 4 files**；`CHANGELOG.md:81` 已写 97（最新一份反而是对的）。
- 建议：**从文档中删除计数**，改为「全绿」；计数只出现在 CI 输出与 `CHANGELOG` 的版本段落（历史快照，允许过时）。

**D8 — 客户端用例计数过时**

- 位置：`docs/planning/DESIGN-VERSION-MANAGEMENT.md:83`（「testDebugUnitTest（当前 248）」）
- 证据：`client/app/build/test-results/**/*.xml` 汇总 = **287 例 / 34 个 class**（构建产物口径，需以实际运行为准，但与 248 的差距已超出正常波动）。
- 建议：同 D7，删除数字。

**D9 — `README.md` 工程结构漏列关键文件与两个子包**

- 位置：`README.md:96-200`
- 漏列（实测 `client/app/src/main/java/com/qingqi/adskip/` 下 66 个 `.kt`）：
  - `data/RulesStore.kt`、`data/StatsStore.kt` —— **两者是 `PLAN-OFFLINE-SYNC.md` 明列的「唯一写入路径」不变量载体**，漏列会让读者找不到关键约束；
  - `core/SecureStore.kt`、`device/AccessibilityWatchdogJob.kt`、`ui/LanguageLabels.kt`；
  - `ui/onboarding/`、`ui/rules/` 两个整个子包（README 只列 6 个 ui 子包，实际 8 个）；
  - `server/bun.lock`、`server/test/fixtures/`（双端契约夹具目录）。
- 建议：补齐上述条目；或把「逐文件罗列」降级为「目录 + 职责一句话」，避免每次加文件都要改 README（**推荐后者**，见 §5 的机检替代方案）。

**D10 — `ARCHITECTURE.md` 的 UI 页面数量过时**

- 位置：`docs/architecture/ARCHITECTURE.md:60`（「ui/ | 4 个 Composable Screen」）、同节「四个页面同一套模式」
- 证据：实际 7 个 Screen（`home` / `apps` / `logs` / `profile` / `settings` / `onboarding` / `rules`）；`CHANGELOG.md` 1.1.0 已记「底部导航扩展为六页」+「新增引导页与规则页」。
- 建议：改为「7 个 Screen（六页导航 + 引导页 + 规则页）」，或去掉数字只写职责。

**D11 — 「四大守护契约测试」已过时**

- 位置：`docs/architecture/ARCHITECTURE-DIRECTORY-STRUCTURE.md:67`
- 证据：`client/app/src/test/java/com/qingqi/adskip/arch/` 下有 **15 个** `*Test.kt`（含 `SkillContractTest`、`UiContractTest`、`ColorSchemeContractTest`、`VendorDetectionContractTest` 等）。
- 建议：改为「契约测试集（清单见 ARCHITECTURE.md 第 10 节）」，不写数量、不枚举。

**D12 — `.pre-commit-config.yaml` 的钩子描述含已删除项**

- 位置：`docs/architecture/ARCHITECTURE-DIRECTORY-STRUCTURE.md:47`（「本地钩子框架（detect-secrets/ktlint/prettier）」）
- 证据：`.pre-commit-config.yaml` 头部注释明确「2026-10-11 审计：mirrors-prettier 已归档…两个失效钩子移除」，实际钩子为 pre-commit-hooks / ktlint / detect-secrets / gradle-wrapper-validate。
- 建议：删除 `prettier`。

**D13 — 多 Agent 认领板把运行时状态固化进规范文档，且已完全过时**

- 位置：`docs/development/AGENT-WORKFLOW.md:78-115`
- 证据：认领板记录 `gzdx` / `.worktrees/gzdx-gitleaks` / `.worktrees/gzdx-ui-polish`，状态「进行中」；`git worktree list` 实测当前 6 个 worktree 全为 `codex-*` / `remove-cloud-sync` / `wb-docs-telemetry`，**无一条与认领板对应**。另引用 `core/ThemePreferences.kt`，该文件在仓库中不存在。
- 影响：规范文档出现「第二份真相」——读者据认领板判断路径归属，得到的是 2026-09 的快照。
- 建议：认领板移出规范文档（落 `.worktrees/CLAIMS.md`，不入库），`AGENT-WORKFLOW.md` 只保留「认领板格式 + 仲裁规则」；同时删除失效路径示例。

**D14 — 品牌「轻启」改名未覆盖文档标题与工程名**

- 位置与证据：
  - `README.md:99` 结构树根仍写 `AdSkip/`（H1 已是「轻启」）；
  - `docs/api/API.md:1` H1 = 「AdSkip 服务端 API 参考（API）」；
  - `client/settings.gradle.kts:33` `rootProject.name = "AdSkip-main"`。
- 对照：`CHANGELOG.md` 1.1.0 声称「README 标题、服务端落地页与管理后台、架构文档与路线图标题等用户可见位置全部同步」——上述三处未同步；`RepoHygieneTest` 的 `LEGACY_BRAND = "AdSkip"` 只扫 locale 文案，扫不到文档。
- 建议：文档标题与 `rootProject.name` 改为「轻启 / qingqi」；**保留** `applicationId`、`Theme.AdSkip`、`AdSkip-vX.Y.Z.apk` 等内部标识（CHANGELOG 已说明刻意不变）。可考虑把 `RepoHygieneTest` 的扫描面扩到 `docs/**/*.md` 的 H1 行。

**D15 — CODEOWNERS 存在两份且归属冲突**

- 证据：根 `CODEOWNERS` 全指向 `@gzdx1121`；`.github/CODEOWNERS` 指向 `@LiYihai1121` 且分路径更细。GitHub 以 `.github/CODEOWNERS` 为准，根文件是**死文件**；`ARCHITECTURE-DIRECTORY-STRUCTURE.md:36` 只登记了根文件。
- 建议：删除根 `CODEOWNERS`，只保留 `.github/CODEOWNERS`，并同步目录树。两份并存时，任何「改 CODEOWNERS」的操作都可能改到不生效的那份。

### P2：文档地图自身

**D16 — `docs/README.md` 的目录树把 `testing/` 缩进成 `planning/` 的子目录**

- 位置：`docs/README.md:22-24`
- 现状：`│   ├── testing/` 出现在 `planning/` 分支下，且其子项用 `│   │   ├──` 缩进。
- 事实：`docs/testing/` 与 `docs/planning/` 同级（`ALLOWED_DOC_DIRS` 也把两者并列）。
- 建议：把 `testing/` 提到与 `planning/` 同级。

**D17 — 命名规范里的 `guide/` 目录与守护测试白名单不一致**

- 位置：`docs/README.md:133`（`guide/`（按需创建））、`ProjectStructureTest.kt:760`（`ALLOWED_DOC_DIRS = setOf("api","architecture","development","planning","diagrams","testing")`）
- 影响：按文档新建 `docs/guide/` 会让 `ProjectStructureTest` 判红；文档说「按需创建」，门禁说「不允许」。且 `DOC_NAME_PATTERNS` 里已有 `guide` 的命名规则，三处口径不一致。
- 建议：二选一——① 把 `"guide"` 加入 `ALLOWED_DOC_DIRS`；② 从命名规范表删除 `guide/` 行。**推荐 ①**，因为 C1 的部署文档正需要它。

### 2026-10-29 复核新增（D18–D19）

> 本节是同日复核（新增 `PLAN-CLIENT-REFORM.md`、`DESIGN-AD-PAIN-RESEARCH.md` 之后）追加的条目。D18 属 **P0**（会直接导致实现者写错色值），D19 属 **P1**（历史关系倒置）。

**D18 — `DESIGN-UI-V4.md` 的品牌色声明与实现完全相反**

- 位置：`docs/planning/DESIGN-UI-V4.md:16`、`:55`
- 文档声明：
  - line 16：「品牌色由薄荷绿 `#1D7C43` 切为设计稿的科技蓝 `#3D7DF6`……**v3 的绿色取值一律不保留**——同时维护两套主色会让「品牌色」出现两个事实源。」
  - line 55：`Brand50` → `#3D7DF6`
- 实现事实：`client/app/src/main/java/com/qingqi/adskip/ui/theme/Color.kt:76` 为 `val Brand50 = Color(0xFF1D7C43)`（薄荷绿）；`:116` 为 `val LightPrimary = Brand50`，即主色就是薄荷绿。
- 原因：`3092d73 feat(ui): 品牌色切换为薄荷绿 (#198)` 把颜色切回，**未同步文档**。
- 影响：任何按 `DESIGN-UI-V4` 实现新页面的人会写出蓝色主色，与现有界面冲突。而该文档自称是「v4 界面（蓝色方案）的唯一落地依据」。
- 讽刺之处：文档在 line 16 亲手警告「同时维护两套主色会让品牌色出现两个事实源」，而它自己现在就是那个失真的第二事实源。
- 建议：把 line 16 与 line 55 的色值改为 `#1D7C43`，将「切为科技蓝」改写为「v4 曾切为科技蓝 `#3D7DF6`，`#198` 后回归薄荷绿 `#1D7C43`」，并在首部加一行变更记录。
- 门禁缺口：`ColorSchemeContractTest` 只校验「用到的 color role 都在 scheme 里」+ 动态取色文档一致 + 窗口主题跟随深色，**不校验文档声明的色值**。这是它能长期潜伏的原因（见 §5.2 新增守卫）。

**D19 — `DESIGN-UI-REVIEW.md` 的「取代」关系已倒置**

- 位置：`docs/planning/DESIGN-UI-REVIEW.md:3`
- 现状：「⚠️ 本文件描述的是 v3（薄荷绿）方案，已被 v4（蓝色）取代。」
- 事实：v4 的蓝色已被 `#198` 撤回，当前实现回到薄荷绿。因此「薄荷绿被蓝色取代」在**色值维度**上已经反了。
- 影响：读者会以为薄荷绿是历史方案，实际它是现行方案。与 D18 叠加后，两份 UI 文档对「哪个是现行色」给出相反答案。
- 建议：改为「本文件描述 v3 的评审结论；v4 曾改蓝色方案（#184），后于 `#198` 回归薄荷绿。**令牌与组件契约（三态语义、间距门禁、组件唯一实现）仍现行有效，色值以 `Color.kt` 为准**」——把「现行 / 历史」的判定边界从「色值」改为「契约」。

**同批复核结论（不新增编号）**

- D1（`SECURITY.md` 版本表）、D2–D4（`API.md` 的 `APK_FILE` / `MAX_BODY` / CORS 默认值）**截至 2026-10-29 02:10 仍未修**，原文与本节引用一致。
- 文档规模从本报告初稿的 **25 份 / 4918 行** 增至 **28 份 / 6273 行**（+12% 份数、+27% 行数），单日增量主要来自 `PLAN-CLIENT-REFORM`（310 行）与 `DESIGN-AD-PAIN-RESEARCH`（431 行）；此后又新增 `DEV-PROJECT-GUIDE`（601 行），**当前 30 份**。**文档膨胀本身已成为需要治理的问题**，见 §5.3「不建议做的事」的反面：新增文档应同时说明它替代或合并了哪份旧文档。

---

## 3. 覆盖度缺口（C 组）

**C1 — 缺部署运维文档（最严重）**

- 证据：全仓 `systemd` / `进程守护` / `日志轮转` 命中 **0**；`部署` 在 `README.md` 与 `DEV-ENVIRONMENT.md` 各 1 次，且都是「部署目录」这个名词，不是部署流程。
- 缺失内容：生产拓扑（反代终止 TLS / 直接 TLS 二选一）、`ADMIN_TOKEN` 与 `RULES_SIGNING_KEY` 的生成与注入、证书申请与 pin 指纹下发、`server/data/` 的备份与**恢复演练**、日志采集与轮转、进程守护与开机自启、健康检查接入、升级与回滚步骤。
- 佐证：`ROADMAP.md:176` 把「服务端 Docker 镜像与一键部署脚本」列为未完成项——**这是已知缺口，但没有文档承载它**；`DESIGN-ARCH-EVOLUTION.md:65` 甚至要求「文档同步 DEV-ENVIRONMENT.md 部署节」，而该节从未存在。
- 建议：新建 `docs/guide/GUIDE-DEPLOY.md`（需先做 D17 的 ①）。内容以「一台干净 Linux + 一个域名」为起点，写可复制的命令序列，而不是架构描述。

**C2 — 缺密钥/证书生命周期说明**

- 证据：`RULES_SIGNING_KEY` 仅命中 2 个文件，且都是「配了就会签名」；没有任何文档说明**轮换流程**、**丢失后果**（客户端 fail-closed → 规则永久停更）、**双密钥过渡**。
- 建议：并入 C1 的部署文档，单列「密钥与证书生命周期」一节；同时把 `DESIGN-SERVER-MOBILE-READINESS.md` §5.4 D5 的双签方案作为待办指向。

**C3 — 缺测试分层与「何时必须加测试」的规范**

- 证据：`docs/testing/` 只有真机验收（`TOP30-REAL-DEVICE-ACCEPTANCE.md`）与稳定性（`STABILITY-TEST-PLAN.md`）；`ARCHITECTURE.md` 第 10 节是**测试清单**（有哪些），不是**策略**（何时该加、加在哪一层、什么算够）。
- 影响：新增功能时「要不要写测试」全靠个人判断，这是契约测试能覆盖边界、却覆盖不到业务逻辑的原因。
- 建议：在 `ARCHITECTURE.md` 第 10 节前加「测试分层」小节（JVM 单测 / 契约测试 / 进程内集成 / 真机验收 四层的职责与准入），不必新建文档。

**C4 — `PLAN-OFFLINE-SYNC.md` 计划新建 8 份文档，文档地图只登记了 2 份**

- 证据：该文 §2.2 列出 9 份（8 新建），包括 `REQUIREMENTS-OFFLINE-SYNC.md`、`DESIGN-OFFLINE-SYNC-*.md`、`ROADMAP-OFFLINE-SYNC.md` 等，正文中按「已存在」引用；`docs/README.md`「规划中尚未创建的文档」只登记 `GUIDE-RULE-AUTHORING.md` 与「覆盖度矩阵公示页」。
- 影响：读者按链接跳转会 404（实际是纯文本引用，不会 404，但会找不到）。
- 建议：把这 8 份登记进「规划中尚未创建的文档」，或在该文首部加一行「本文所列 8 份文档均未创建，为待产出清单」。

**C5 — 缺服务端「开发→运行」的最短路径说明**

- 证据：`README.md:228-234` 给了 `bun install` / `bun test` / `bun run typecheck`，但没有「怎么把服务端跑起来并看到管理后台」的三行命令（`ADMIN_TOKEN=xxx bun start` → 打开 `/admin` → 用 token 登录）。
- 影响：新人第一次跑服务端会卡在「没有 ADMIN_TOKEN，写接口全 503」。
- 建议：在 README「服务端」小节补 3 行可复制命令 + 一句「未配 `ADMIN_TOKEN` 时写接口返回 503 是设计行为」。

---

## 4. 技能问题（S 组）

> 前置结论：15 个技能**全部通过** `SkillContractTest`（name/description/登记/LICENSE-NOTICE）。以下是不被门禁覆盖的**规范层**问题。

**S1 — 全部 15 个技能缺「输入 / 输出」定义（最普遍）**

- 证据：`skills/README.md`「SKILL.md 格式」只要求 `name` + `description`，正文只要求「指针 + 自检动作」。15 个技能的实际结构是：`规范唯一事实源` → `开工前自检` → `提交前自检` → `自检：确认未出现以下情况`。**没有任何一个技能声明「读什么」「产出什么形态」**。
- 影响：Agent 加载技能后无法确定交付物是「检查清单」「PR 描述」「报告」还是「代码改动」。唯一例外是 `code-simplifier`（明确「report only concrete, evidenced findings with their location…」）和 `security-audit`（「只报告有证据的发现」）——恰好是第三方/审计类技能，说明这个信息**是必要的**，只是没被规范要求。
- 建议：在 `skills/README.md`「SKILL.md 格式」增加两个必填段：
  ```markdown
  ## 输入
  - 必读：<规范文件 / 源码路径>
  - 可选：<上下文>
  ## 输出
  - 形态：<自检清单回执 / PR 描述段落 / 审查报告>
  - 不产出：<明确排除物，如「不改代码」>
  ```
  并给 4 个流程类技能（`branch-guard` / `contract-first` / `release` / `planning-docs-guard`）优先补齐。

**S2 — description 语言混排（中文 6 / 英文 9）**

- 证据：中文——`contract-first`、`network-security-guard`、`planning-docs-guard`、`release`、`security-audit`、`server-bun-typescript`；英文——其余 9 个（含 3 个第三方：`archify`、`code-simplifier`、`software-development-full`）。
- 影响：技能自动发现依赖语义匹配，混排降低跨技能的一致性；且同类技能（如 `branch-guard` 英文 vs `release` 中文）风格割裂。
- 建议：自研技能统一中文（与仓库其余文档一致），第三方技能保留原文并在 `skills/README.md` 说明「第三方技能不改写 description，避免升级冲突」。

**S3 — `branch-guard` 违反自身「不复制规则正文」规范**

- 位置：`skills/branch-guard/SKILL.md`「会被构建拦下的硬规则」一节
- 现状：列出 5 个测试名 + 各自的检查摘要（ktlint / ArchitectureBoundaryTest / ManifestContractTest / ProjectStructureTest / RepoHygieneTest）。
- 问题：① 这是 `ARCHITECTURE.md` 第 10 节的复制；② 已不完整——漏了 `SkillContractTest`、`UiContractTest`、`ColorSchemeContractTest` 等 10 个 arch 测试；③ 与 `skills/README.md:80`「**规则不复制**…不复制分支命名表、门禁命令等规则正文」直接冲突。
- 建议：整节替换为一句指针——「契约测试全集见 [ARCHITECTURE.md](../../docs/architecture/ARCHITECTURE.md) 第 10 节；违反即在 CI 判红，不依赖人工评审。」

**S4 — `contract-first` 复制包名清单**

- 位置：`skills/contract-first/SKILL.md` 自检动作第 1 条（「跨包（ui / service / data / net / device / core / engine）」）
- 问题：这是 `ARCHITECTURE.md` 2.1 边界表的复制；实际 `ui/` 下已有 `rules`、`onboarding` 子包，顶层包清单一旦变化（如将来拆 `core:*` 模块）就要改两处。
- 建议：改为「识别边界类型：跨包 / 跨端 / 协议；包清单与允许依赖见 ARCHITECTURE.md 2.1 表」。

**S5 — `release` 复制版本号同步规则，且规则本身口径不清**

- 位置：`skills/release/SKILL.md` 自检动作第 2 条（「三处版本号同步：versionName、versionCode、`server/package.json`」）
- 问题：① 属 `CONTRIBUTING.md`「版本发布」正文的复制；② **规则未定义「同步」的格式口径**——实测 `versionName = "1.1"`、`server = "1.1.0"`，两者算不算同步？当前只能靠人判断。
- 建议：技能里删掉枚举，改指针；同时在 `CONTRIBUTING.md` 明确「同步 = 版本主体一致；Android 用 `X.Y`，server 用 `X.Y.Z`，`versionCode` 独立单调递增」。

**S6 — `software-development-full` 职责边界过宽，与其余 14 个技能全部重叠**

- 证据：1274 行，覆盖「开发/修改/Bug 修复/重构/代码审查/测试/API/数据库/前后端/脚本/嵌入式」，`skills/README.md` 的同类技能最长 54 行。
- 问题：违反 `skills/README.md:90`「新技能须有明确触发边界，**不与既有技能重叠职责**」。它自述「与项目规则冲突时以项目规则为准」（正文 0.1 节），但这条优先级声明埋在 1274 行里，Agent 未必读到；而 description 又写「开发类任务均适用」，触发面几乎覆盖全部开发任务。
- 建议：二选一——① 移出 `skills/`（作为可选外部技能，不入库，避免与项目规则争抢触发）；② 保留但把 description 首句改为「**仅在无项目专用技能覆盖时兜底**；项目规则优先」，并在 `skills/README.md` 事实源表登记其从属地位。**推荐 ②**（保留价值在于跨项目可移植，但必须让触发面收窄）。

**S7 — `code-simplifier` 的插件清单指向不存在的目录**

- 证据：`.codex-plugin/plugin.json` 有 `"skills": "./skills/"`，但该技能目录下**没有 `skills/` 子目录**（`SKILL.md` 在根）；`.claude-plugin/plugin.json` 无 `skills` 字段。
- 影响：按清单安装插件时会找不到技能；仓库内直接读 `SKILL.md` 则不受影响——属「装了不生效」的隐患。
- 建议：二选一——① 删除两份 plugin.json（对仓库内使用无作用，仅打包分发时需要）；② 把 `SKILL.md` 移入 `skills/` 子目录并同步调整 `SkillContractTest` 的扫描规则。**推荐 ①**。

**S8 — `archify` 的前置依赖未进规范**

- 证据：`skills/archify/package.json` 依赖 `ajv ^8.17.1`；`skills/README.md` 目录树里提了一句「本目录 npm install 后使用」，但「开发一个新技能」与「校验」两节都没写依赖安装步骤，也无 node 版本约束。
- 影响：新机器上直接用 archify 渲染会因缺 `ajv` 失败，而失败信息来自 `.mjs` 内部，不指向「你该先 install」。
- 建议：在 `skills/README.md` 增加「带依赖技能的启用步骤」小节（`cd skills/archify && npm ci`，`package-lock.json` 已入库）；并在 `archify/SKILL.md` 顶部加前置检查（缺 `node_modules` 时明确报错）。

**S9 — `skills/README.md` 目录树与磁盘实际不符**

- 漏登记：`code-simplifier/.claude-plugin/`、`.codex-plugin/`、`archify/package.json`、`archify/package-lock.json`、`archify/renderers/shared/`。
- 为什么没被判红：`SkillContractTest` 只校验「技能目录名出现在 README 文本中」，不校验子文件——所以树可以任意失真。
- 建议：树只画到「技能目录 + 一句话职责」，内部结构交给各 `SKILL.md` 自述；这样新增子文件不再需要改 README（与 D9 的建议同源）。

---

## 5. 修复优先级与机检替代方案

### 5.1 优先级

| 批次 | 项 | 理由 |
| --- | --- | --- |
| **P0（当天可完成）** | D1、D2、D3、D4、D5、D6 | 全是「文档说错了会让人做错事」，且都是改几行字。D1 涉及安全政策，最优先。 |
| **P1（一个 PR）** | D7、D8、D9、D10、D11、D12、D14、D15、D16、D17 | 结构与命名失真。建议与 D9 的「降级为目录+职责」一起做，一次性减少未来维护面。 |
| **P2（独立批次）** | C1、C2、C5 | 部署文档是新增内容，需要一次完整梳理；C1 依赖 D17 先放开 `guide/` 目录。 |
| **P3（技能规范）** | S1、S2、S3、S4、S5、S6、S7、S8、S9 | 先改 `skills/README.md` 的格式要求（S1/S2），再批量改 15 个技能；S3–S5 是删复制，改动小收益大，可提前。 |
| **P4（结构性）** | C3、C4、D13 | 需要决策（认领板放哪、测试策略写到哪一节），不宜顺手改。 |

### 5.2 把可机检的事实改成机检（治本）

本轮 15 项文档漂移里，有 8 项是**可以用测试守住的**。建议按「先写失败的测试」补三个守卫，替代人工对账：

| 守卫 | 断言 | 能防住 |
| --- | --- | --- |
| `ApiDocContractTest` | `API.md` 中出现的每个 `SCREAMING_CASE` 环境变量名，必须在 `server/src/config.ts` 的 `process.env.*` 集合中 | D2、D3，以及未来任何「文档声明了不存在的 env」 |
| `DocsPathContractTest` | `docs/**/*.md` 中被反引号包裹且形如仓库路径的字符串，去掉相对前缀后必须存在（豁免 `▲` 标记行） | D9 类的失效路径、C4 的悬空引用 |
| `BrandContractTest`（扩展现有 `RepoHygieneTest`） | `docs/**/*.md` 的 H1 行与 `client/settings.gradle.kts` 的 `rootProject.name` 不得出现 `LEGACY_BRAND` | D14，以及未来的改名遗漏 |

> 计数类漂移（D7、D8）**不建议**加守卫——正确做法是从文档里删掉数字，让 CI 输出与 `CHANGELOG` 承载。

### 5.3 不建议做的事

| 不建议 | 理由 |
| --- | --- |
| 为每份文档补「最后更新时间」并靠人工保鲜 | 时间戳不产生正确性，只会制造「看起来很新」的错觉。要么机检，要么删掉易漂移的细节。 |
| 把 `README.md` 的逐文件清单补全 | 本轮 D9 的漏列正是「逐文件罗列」的必然结果。应降级为目录 + 职责，把细节交给代码本身。 |
| 新增独立的「测试策略」「部署」两份大文档 | 部署值得单独一份（C1）；测试分层并入 `ARCHITECTURE.md` 第 10 节即可（C3），避免文档数继续膨胀。 |

---

## 6. 复跑方式

```bash
# 文档登记与命名（等价于 ProjectStructureTest 的文档两项）
python - <<'PY'
import os,re
docs='docs'; m=open(os.path.join(docs,'README.md'),encoding='utf-8').read()
links={l.split('#')[0] for l in re.findall(r'\]\(([^)\s]+)\)',m)}
bad=[os.path.relpath(os.path.join(r,f),docs).replace('\\','/')
     for r,_,fs in os.walk(docs) for f in fs
     if f.endswith('.md') and f!='README.md'
     and os.path.relpath(os.path.join(r,f),docs).replace('\\','/') not in links]
print('未登记:',bad)
PY

# 技能格式契约（等价于 SkillContractTest）
cd client && ./gradlew testDebugUnitTest --tests "*SkillContractTest*"

# 文档/技能引用一致性
cd .. && python - <<'PY'
import re,glob,os
bad={}
for p in glob.glob('docs/**/*.md',recursive=True)+glob.glob('*.md')+glob.glob('skills/**/*.md',recursive=True):
    for s in re.findall(r'`([^`\n]+)`',open(p,encoding='utf-8').read()):
        s=s.strip()
        if '/' in s and re.search(r'\.(md|ts|kt|json|mjs|py|yml|kts)$',s) and ' ' not in s and '*' not in s:
            c=s.lstrip('./')
            if not os.path.exists(c) and '▲' not in s: bad.setdefault(s,p)
print('可疑引用:',len(bad))
PY
```
