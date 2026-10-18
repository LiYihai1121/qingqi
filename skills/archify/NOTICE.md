# NOTICE.md — archify（第三方上游技能）

本目录为第三方技能的随仓库副本，遵守上游许可分发。

## 来源

- 上游仓库：<https://github.com/kmmao/archify>
- 上游许可证：MIT（全文见同目录 [LICENSE](LICENSE)）
- 引入版本：v2.4.0（2026-10-19 从上游 `main` 分支快照引入）
- 技能署名：tt-a1i（Archify）；上游基于 Cocoon-AI/architecture-diagram-generator（MIT, v1.0）

## 许可

Archify 以 MIT License 分发：

```text
MIT License

Copyright (c) 2026 tt-a1i (Archify)
Copyright (c) 2025 Cocoon AI (original "architecture-diagram-generator")
```

上游 LICENSE 全文随包附带于本目录，与代码一同版本控制，不得分离或改写。

## 引入方式与更新方式

- 引入：从上游 `main` 分支打包后放入 `skills/archify/`，保留上游 SKILL.md、schemas、renderers、examples、assets 原样，
  仅追加本 NOTICE.md 与 LICENSE；不改上游产物内容，避免与上游产生分歧。
- 更新：重新拉取上游发布，整目录替换，并在 PR 中记录上游版本与许可证变化（若上游换许可证，本目录须重新评审）。
- 本地运行渲染器需先在本目录执行 `npm install`（上游依赖 ajv；`node_modules/` 已被根 `.gitignore` 忽略，不入库）。

## 本仓库内使用边界

- 触发与用法以上游 SKILL.md 为准；本仓库不复制其正文。
- 与自研 `architecture-boundary-guard`（边界契约测试地图）职责不重叠：本技能只负责把架构/流程/时序/数据流/
  状态机画成带主题切换与导出功能的独立 HTML，不定义、不校验仓库的边界规则。
