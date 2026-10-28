# 发布历史与提交链路（RELEASE-HISTORY）

本文记录版本阶段、Git tag、合并提交和 GitHub Release 的对应关系，避免仅凭短哈希或提交标题判断历史是否断链。每次项目更迭完成后，必须同步更新本页的 Release list，并核对对应的 annotated tag。

标签发布由 [.github/workflows/release.yml](../../.github/workflows/release.yml) 自动执行：先校验 annotated tag、提交对象和 Android/服务端版本一致性，再构建 R8 Release 变体、运行服务端检查、生成 APK SHA-256 校验和并创建 GitHub Release。Android SDK 安装与 `.github/workflows/ci.yml` 保持一致（Build Tools 36.0.0，并从 beta 通道安装 Android API 37.0 平台）。打包步骤强制 `apksigner verify`：只有配置 `ADSKIP_KEYSTORE_BASE64` 等仓库 Secrets 才会使用正式密钥签名；未配置正式签名时发布任务会因 `apksigner verify` 拦截未签名制品而失败（本地调试请使用 `assembleDebug`）。

## 目录

- [版本起点声明](#版本起点声明)
- [Release list](#release-list)
- [后续规则](#后续规则)

## 版本起点声明

- **新仓版本起点是 `v1.0.0`**：2026-10-13 仓库历史重置（`推倒重来`），原 `.git` 备份至**仓库同级目录** `F:\LocaRepository\ui-app.git-backup-20261013-224952`（未推远端；注意不是仓库内的 `.git-backup-*`，2026-10-16 核实），新仓以单次初始提交重建。SemVer 自 `1.0.0` 起重新计数，新仓不继承旧版本号、不移动旧 tag。
- **重置前的产品历史**（`v2.0.0`–`v3.4.2`，含 15 个 tag、GitHub Release 与已发布 APK 制品）完整保留在备份 `ui-app.git-backup-20261013-224952` 与远端 `LiYihai1121/ui-app` 旧仓；旧仓远端未受本次重置影响（force-push 是单独的不可逆动作，未执行）。此外自 2026-10-16 起，旧历史同时成为 `main` 的祖先链（见下条），旧 tag 与旧 Release 因此仍可从 `main` 直接追溯。
- **`versionCode` 连续性**：Android `versionCode` 不随 git 历史重置而回退——Play Store 要求单调递增，已装用户需能从旧版（末版 `v3.4.2` = 16）升级。故新仓 `v1.0.0` 的 `versionCode = 17`，承接重置前末版继续递增；`versionName` 则随 SemVer 重置为 `1.0`。
- **本版本承载内容**：AdSkip 全部既有功能 + 重置前已合入但未随版发布的两项加固（v1 批量上报事件 `label` 字段、上报通道证书锁定 fail-closed），详见 [CHANGELOG.md](../../CHANGELOG.md) `[1.0.0]`。
- **重置内容如何回到 `main`（2026-10-16，PR #167）**：为满足 `main` 分支保护（`strict` 必需检查）且**不做任何 force-push**，重置内容先落在 `release/v1.0.1` 分支（其历史含重置根提交 `64e2d88` 与切版提交 `c167021`），在该分支上以 `git merge -s ours origin/main --allow-unrelated-histories` 声明旧 `main` 为祖先，再经 PR #167 **squash 合入**旧 `main`。结果：`main` 的提交历史 = 旧历史（161 提交，含末版 `v3.4.2`）+ 1 个 squash 提交 `c68133a`，其**树内容**为重置后的新仓内容。故重置期的 `64e2d88` / `c167021` **不在 `main` 祖先链上**，`v1.0.0` tag 因此按下方「后续规则」属**历史标签**；承载它的 `release/v1.0.1` 分支不得删除。旧 tag 与旧 GitHub Release 全部保留、未移动。

## Release list

| 版本 | Git tag | 对应提交 | GitHub Release | 状态 |
| --- | --- | --- | --- | --- |
| `1.0.0` | `v1.0.0` | `c167021`（重置期切版提交，**不在 `main` 祖先链上**） | 未创建 | **历史标签**（本地 annotated tag，`versionCode = 17`、`versionName = 1.0`、`server = 1.0.0`；因不指向 `main`，按本页「后续规则」属历史标签——不得补建 Release、不得复用版本号、承载分支 `release/v1.0.1` 不得删除。其内容已由 `1.0.1` 在 `main` 上承接） |
| `1.0.1` | `v1.0.1` | `c68133a`（PR #167 squash 合入 `main`） | [v1.0.1](https://github.com/LiYihai1121/qingqi/releases/tag/v1.0.1) | **已发布**（annotated tag `v1.0.1` 指向合并后 `main` `c68133a`；`versionCode = 18`、`versionName = 1.0`、`server = 1.0.1`；制品 `AdSkip-v1.0.1.apk` 2,226,747 bytes，SHA-256 `ea6cd1222cee432795831781e3c3fd36af632025d10299f33b9a0b26f822976e`，另附 `SHA256SUMS`；承载 v1.0.0 后实测的两处跳过链路缺陷修复，详见 [CHANGELOG.md](../../CHANGELOG.md) `[1.0.1]`） |
| `1.1.0` | `v1.1.0` | `6d8e622`（PR #196 squash 合入 `main`） | [v1.1.0](https://github.com/LiYihai1121/qingqi/releases/tag/v1.1.0) | **已发布**（annotated tag `v1.1.0` 指向合并后 `main` `6d8e622`；`versionCode = 19`、`versionName = 1.1`、`server = 1.1.0`；制品 `AdSkip-v1.1.0.apk` 2,288,495 bytes，SHA-256 `86bb8c374acb174d022349431562a5c10537785841f9f630e72b0f9145f40634`，另附 `SHA256SUMS`；Release 运行 `39164406751` completed success，承载 v4 蓝色 UI 重构、UI 评审 P0/P1/P2 整改与 archify 技能，详见 [CHANGELOG.md](../../CHANGELOG.md) `[1.1.0]`） |

Release list 的维护要求：版本变更、tag、合并提交和 GitHub Release 必须一一对应；tag 创建后补填实际提交，GitHub Release 创建后补填链接、制品和校验和。未完成任一项时，状态必须明确标记为“待发布”“未创建”或“制品待补传”，不得写成“已发布”。

## 后续规则

- 不删除或移动已推送的标签和共享分支历史。
- 新的历史整理使用合并提交或 `git revert`，不使用 `reset --hard`、强制推送或替换已有提交。
- 发布版本以 annotated tag 为准，提交、标签和构建制品必须可以相互追溯。
- 每次项目更迭完成后更新 `CHANGELOG.md` 和本页 `Release list`；正式版本必须创建新的 annotated tag，不得复用旧版本号。
- 发布核对至少包括：版本号一致、Android `versionCode` 递增、tag 指向合并后的 `main`、GitHub Release 状态和 `SHA256SUMS` 已记录。
- 发布 tag 必须指向合并后的 `main` 提交；指向非 `main` 提交的 tag 视为历史标签——不得补建 Release、不得复用版本号，其承载分支不得删除。
