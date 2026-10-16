# 变更日志

本文件记录面向用户和运维的版本变更。版本号遵循 Semantic Versioning，正式版本标签以 Git 中的 `vX.Y.Z` 为准。

版本与提交的完整对应关系见 [发布历史与提交链路](docs/planning/RELEASE-HISTORY.md)。

## 目录

- [1.0.1](#101---2026-10-16)
- [1.0.0](#100---2026-10-13)

## [1.0.1] - 2026-10-16

针对 v1.0.0 上线后实测到的两处跳过链路缺陷的修复版。无新增能力，无破坏性变更，`versionCode` 自 `17` 递增至 `18`。

发布：annotated tag [`v1.0.1`](https://github.com/LiYihai1121/ui-app/releases/tag/v1.0.1) 指向合并后的 `main`（`c68133a`，PR #167 squash 合入）。制品 `AdSkip-v1.0.1.apk`（2,226,747 bytes），SHA-256 `ea6cd1222cee432795831781e3c3fd36af632025d10299f33b9a0b26f822976e`，Release 同时附 `SHA256SUMS`。

### Fixed

- **模拟开屏广告自测在发布版永远点不动**：安全护栏对「本应用自身包名」一律拒绝点击（防自触发死循环），而自测浮层恰好就在本应用内，其「跳过」按钮因此永远点不了——自检只会走到超时。现改为**仅自测态**由调用方显式放行自身包，默认拦截行为不变，且放行不削弱支付类应用、敏感系统界面等其它底线。
- **跳过次数被重复累加**：单次跳过首页先显示 2、约 5 秒后变 3，而跳过记录恒为 1；且每轮落盘都会把同一批增量再算一次，计数随使用时间无界膨胀。现计数收敛为单一写入路径（日志写入不再顺带计数），落盘后立即清空待落盘增量。

### 验证 (1.0.1)

- `cd client && ./gradlew ktlintCheck assembleDebug testDebugUnitTest` 全绿；新增 `StatsRepositoryTest`（7 例）与扩充后的 `SafetyGuardTest`（21 例）通过。
- Structure Contract（`com.qingqi.adskip.arch.*`）全过；`release.yml` 运行 `38866018994` completed success，产出并校验 `AdSkip-v1.0.1.apk` 与 `SHA256SUMS`。

## [1.0.0] - 2026-10-13

仓库重置（`推倒重来`）后的首个正式版本，SemVer 自 `1.0.0` 起重新计数。重置前的产品历史（`v2.0.0`–`v3.4.2`，含 tag、GitHub Release 与已发布制品）完整保留在备份 `.git-backup-20261013-224952` 与远端 `LiYihai1121/ui-app` 旧仓，新仓不继承旧版本号、不移动旧 tag。

本版本承载 AdSkip 全部既有功能（Android 无障碍跳过客户端 + Bun/TypeScript 服务端：规则中心、统计 API、管理后台、落地页），并落地重置前已合入但未随版发布的两项加固（旧仓「未发布」批次）。

### Added (1.0.0)

- **v1 批量上报事件 `label` 字段**：服务端 `cleanReportEvent` 接受可选 `label`（向后兼容，旧客户端不发送不影响）；客户端 v1 事件补 `event.put("label", label)`。API 文档同步更新。
- **上报通道证书锁定**：`reportSkip` 的 `httpPost` 改用 `clientForPins`（`CertificatePinner`），与规则同步通道一致。指纹经设置页下发，未配置 pins 时 fail-closed 同步拒发（不降级明文通道）。

### Changed (1.0.0)

- 版本号：`versionCode` 自 `16` 递增至 `17`（Play Store 单调递增约束，git 历史重置不回退），`versionName` 为 `1.0`；服务端 `server/package.json` 为 `1.0.0`，二者 major.minor 一致。
- 安全模型新增「传输层锁定」行（`CertificatePinner` 覆盖 v0/v1 全路径 + fail-closed）。

### 验证

- `cd server && bun run typecheck` + `bun test`（97 用例）全绿。
- `cd client && ./gradlew ktlintCheck assembleDebug testDebugUnitTest` 全绿；`assembleDebug` 产出 `app-debug.apk`（约 14 MB）。
- Structure Contract（`com.qingqi.adskip.arch.*`：目录白名单 / 包边界 / Manifest 契约 / 仓库卫生）全过，确认重置后守护仍成立。