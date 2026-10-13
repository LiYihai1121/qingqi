# 变更日志

本文件记录面向用户和运维的版本变更。版本号遵循 Semantic Versioning，正式版本标签以 Git 中的 `vX.Y.Z` 为准。

版本与提交的完整对应关系见 [发布历史与提交链路](docs/planning/RELEASE-HISTORY.md)。

## 目录

- [Unreleased](#unreleased)
- [1.0.0](#100---2026-10-13)

## [Unreleased]

### Changed

- **品牌统一「轻启」**：项目与应用的品牌名称由「净启动 AdSkip」统一为「轻启」——Android 客户端桌面名 / 无障碍服务名 / 快捷磁贴名、README 标题、服务端落地页与管理后台、架构文档与路线图标题等用户可见位置全部同步。包名（`applicationId`）、`Theme.AdSkip`、APK 产物名（`AdSkip-vX.Y.Z.apk` / `AdSkip-latest.apk`）等内部标识刻意不变，覆盖升级兼容性与分发链路不受影响。

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