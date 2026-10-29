# 用户行为分析与体验洞察平台设计（DESIGN-TELEMETRY-ANALYTICS）

> **状态**：草案（待需求方确认 Q1–Q15）　**最后更新**：2026-10-19
> **关联**：[PLAN-TELEMETRY-ANALYTICS.md](PLAN-TELEMETRY-ANALYTICS.md)（开发计划：排期/测试/部署/分工）
> **定位**：本文是埋点、指标与洞察口径的**设计事实源**；实施排期不在本文维护。

---

## 1. 背景与问题

当前遥测只采集「跳过成功」一种结果，四类决定留存与口碑的行为**完全无数据**：

| 盲区 | 代码证据 | 后果 |
|---|---|---|
| 漏跳（广告出现但未匹配） | `SkipAdService.trySkip()` 中 `findTarget() ?: return` 直接返回 | 可靠性无法量化，无法知道哪些 App 缺规则 |
| 护栏拦截 | 同上，`LogRing.w()` 仅写本地环形日志，不计数不上报 | 无法区分「正确拦下支付按钮」与「误拦了可跳过的广告」 |
| 激活过程 | 无障碍 / 云端规则 / 电池白名单 / 保活引导 / 免打扰均无埋点 | 无法定位流失环节 |
| 服务存活 | `onServiceConnected/onDestroy/onUnbind` 仅 `LogRing.d()` | 无法知道服务每天死多少次、保活是否有效 |

另有三项**现有统计 API 的结构性缺陷**，修复成本极低但价值高：

| 缺陷 | 证据 | 修复 |
|---|---|---|
| 渠道维度在汇总时丢失 | `types/rules.ts:52-56` 的 `AppStat` 有 `byChannel`，但 `StatsSummary`(66-72) 只返回 `count` | summary 带出 `byChannel` |
| `recent` 截断到 50 条 | `store.ts:339-345` 上限 50，而 `config.RECENT_CAP = 500` | 提到 200 |
| 趋势只能看 14 天 | `store.ts:350` `slice(-14)`，而 `STATS_RETENTION_DAYS = 90` | 支持 14/30/90 可配 |

---

## 2. 目标与非目标

### 2.1 北极星（唯一）

```text
WAC = 本周至少成功跳过 1 次的设备数 / 本周活跃设备数
```

**口径限定**：当前只能对**开启遥测的设备子集**计算。看板须标注「遥测子集 WAC」，**禁止外推为全量**。

### 2.2 目标

| 编号 | 目标 | 可验收表述 | 达成阶段 |
|---|---|---|---|
| O1 | 激活 | 能定位漏斗最大流失步（按 ROM 分群），给出中位耗时与重试次数 | M3 |
| O2 | 可靠性 | 漏跳率可测；规则上线后该 App 漏跳率变化可归因 | M1 |
| O3 | 安全 | 两段式：M1 达成「拦截可测量」；M1.5 基线后定阈值 | M1 / M1.5 |
| O4 | 存活 | 服务在线率可按 ROM 分群读出 | M4 |
| O5 | 留存 | D7 设备留存可计算；基线后按「基线 +X pp」设目标 | M3 |
| O6 | 运营 | 输出按「漏跳量 × 受影响设备数」排序的 Top-N 规则待办 | M5 |
| O7 | 隐私 | 白名单外字段丢弃率 100%；抽样审计 0 违规；可一键清空 | M1 起持续 |

> **关于目标值**：本项目无任何真实基线，因此**不承诺具体提升数字**（如「提升 15pp」「覆盖率 80%」）。
> 统一改为「先达成可测量」，待 M1.5 积累 14 天真实数据后再设阈值。

### 2.3 非目标（设计红线）

以下不是「暂缓」，任何 PR 引入即驳回：

| 非目标 | 原因 |
|---|---|
| 用户画像 / 身份关联 | 服务端无账号体系；违反「默认不出本机」承诺 |
| 采集屏幕文本 / contentDescription / 节点正文 / 坐标 / 账号 / 精确位置 | 屏幕内容属用户隐私，`SkipAdService` 现有注释已明确禁止 |
| 引入第三方分析 SDK 或 CDN 资源 | 外链会泄露访问行为，离线自建场景不可用 |
| 实时流处理（秒级） | 批量 + 延迟刷盘已够用，引入队列只增故障面 |
| 多租户 / 账号体系 | 自建单实例，单一运营者 |
| 广告内容分析 | 越界且无必要 |
| 改造既有 `/api/v1/stats` 契约 | 新链路独立（ADR-8），旧契约只读兼容 |
| A/B 实验平台 | 本期不做 |
| 自动修复 | 只产出洞察，不自动改规则/改配置 |

---

## 3. 目标用户与场景

| 角色 | 说明 | 分析视角 | 权限 |
|---|---|---|---|
| **U1 主力用户（被分析）** | Android 中重度用户，愿开无障碍，多为国内 ROM | 激活漏斗、服务存活、漏跳 | 不接触看板 |
| **U2 自建服务端者** | 部署 `server/`、配 TLS + 证书 pin + HMAC | 趋势、排行、诊断 | 看板使用者 |
| **U3 规则维护者** | 为 Top-N App 编写选择器规则 | 漏跳帕累托、规则效能 | 洞察主要消费者 |

> **关键区分**：U1 是**数据来源**，U2/U3 是**权限主体**。二者不可混为一谈。

核心场景：S1 首次激活 / S2 日常自动跳过 / S3 漏跳与误触排查 / S4 服务失效与恢复 / S5 规则同步 / S6 运营者看板分析。

---

## 4. 埋点设计

### 4.1 事件信封

```json
{
  "schema_ver": 1,
  "event_id": "uuid-v4",
  "event": "skip_outcome",
  "occurred_at": "2026-10-19T20:51:45+08:00",
  "sent_at": "2026-10-19T21:02:10+08:00",
  "session_id": "uuid-v4",
  "device": "hash(deviceId + rolling_salt)",
  "app_version": "3.1.0",
  "rom": "miui",
  "android_api": 33,
  "props": { "pkg": "com.tencent.mm", "result": "matched", "channel": "text", "latency_ms": 120 }
}
```

- `occurred_at` 用于**归日**（容忍离线补报的乱序），`sent_at` 用于估算链路延迟。
- `device` 为加盐哈希且滚动轮换，不可反推；**服务端不落 IP**（IP 仅用于限流）。

### 4.2 事件清单

| 事件 | props | 隐私级 | 优先级 |
|---|---|---|---|
| `app_open` | `launch_source` | L0 | P1 |
| `screen_view` | `screen` | L0 | P1 |
| `screen_exit` | `screen`, `dwell_ms` | L0 | P1 |
| `ui_click` | `screen`, `element_id`（语义 ID，**非控件文本**） | L0 | P2 |
| `onboarding_step` | `step`, `result`, `elapsed_ms`, `attempt` | L0 | P1 |
| **`skip_outcome`** | `pkg`, `result`(matched/unmatched/blocked), `channel?`, `action?`, `latency_ms` | **L1** | **P0** |
| `svc_lifecycle` | `state`, `uptime_ms` | L0 | P2 |
| `sync_result` | `kind`, `result`, `batch_size` | L0 | P2 |
| `rule_fire` | `pkg`, `channel`, `rule_ref` | L1 | P3 |
| `telemetry_opt_in` | `enabled`, `scope` | L0 | P0 |

> **合并说明**：原 `skip_performed` 已并入 `skip_outcome`（`action` 降为 prop），避免每次成功跳过上报两条、口径打架。

### 4.3 页面浏览、点击、停留与转化路径

| 原语 | 落地方式 |
|---|---|
| 页面浏览 | 单 Activity + Navigation，以 `Routes.kt` 导航目标为 `screen` 标识：`home/apps/logs/profile/settings` |
| 停留时长 | `screen_view` 进、`screen_exit` 出配对算 `dwell_ms`。用途是**识别困惑**（设置页停留过长 + 反复进出 = 配置看不懂） |
| 点击 | 只埋关键控件，用语义 `element_id`（如 `settings.sync_now`），**绝不上传控件文本** |
| 转化路径 | 唯一关键转化 = 首次成功跳过（`time_to_first_skip`）；路径看 `onboarding_step` 序列与异常绕行 |

> 无障碍服务内没有页面，dwell 无意义。该场景真正重要的是 `latency_ms`（广告出现 → 成功跳过），P50/P95 比页面停留更有价值。

### 4.4 隐私分级与上报策略

| 级别 | 内容 | 策略 |
|---|---|---|
| **L0** | 事件名、时间、版本、ROM、页面、结果码 | 随遥测开关开启 |
| **L1** | 第三方 `pkg`（使用痕迹） | 沿用现有 skip 上报的**同一 opt-in 开关**，不新增授权面 |
| **红线** | 屏幕文本 / desc / 节点正文 / 坐标 / 账号 / 精确位置 / 剪贴板 | **永不采集** |

上报策略：

- 本地缓冲：事件追加写本地 JSONL，容量上限 FIFO 淘汰
- 批量触发：≥20 条或每 15 分钟，单批 ≤ `MAX_BATCH_EVENTS`（50）
- 幂等：`event_id` 唯一索引去重
- 乱序容忍：按 `occurred_at` 归日
- 失败重试：指数退避，成功才清除；超保留期丢弃
- 限流：复用 `RATE_LIMIT_REPORT_PER_MIN`（30）
- 采样：高频低价值事件可配置；`skip_outcome` **不采样**
- 开关：三档 `core/full/off`，**默认 off**

> **必须接受的偏差**：上报默认关闭，可分析样本天然偏向「愿意配服务端的极客用户」。
> 因此看板绝对数字**不代表全体用户**，只能用于相对变化与分群对比。

### 4.5 埋点治理规范

| 环节 | 规则 |
|---|---|
| 命名与版本 | 事件名 `snake_case`；props 名全仓唯一；信封带 `schema_ver`；未登记事件服务端直接拒绝 |
| 评审准入 | 新增事件需过 ① 隐私分级 ② **是否有消费指标**——无消费方的埋点一律不批 |
| 验证 | 每个新事件必须有契约测试向量；上线前 debug 模式抓包核对载荷 |
| 废弃 | 「标记 deprecated → 保留一个迭代 → 删除」三步，同步更新字典与所有引用 |

---

## 5. 指标体系与分析方法

### 5.1 指标字典

| 指标 | 定义 | 来源 | 层级 |
|---|---|---|---|
| **WAC（北极星）** | 本周至少成功跳过 1 次的设备 / 本周活跃设备（仅遥测子集） | `device_daily` + `skip_outcome` | 北极星 |
| 激活各步转化率 / 中位耗时 / 重试次数 | 漏斗每步 `completed/entered` + `elapsed_ms` P50 | `onboarding_step` | 一级 |
| 跳过成功率 / 漏跳率 / 拦截率 | `skip_outcome` 三分结局占比 | `skip_outcome` | 一级 |
| 跳过耗时 P50/P95 | `latency_ms` 分位数 | `skip_outcome` | 一级 |
| 渠道构成 | text/viewId/selector 占比（需先修 `byChannel` 丢失） | `skip_outcome` | 二级 |
| Top-N 规则覆盖率 | 至少成功跳过 1 次的目标 App / N（需 Top-N 清单） | `skip_outcome` + 目标清单 | 二级 |
| 服务在线率 / 日均中断 | 在线时长占比；`destroyed` 次数/日/设备 | `svc_lifecycle` | 一级 |
| D1/D7/D30 设备留存 | 首现日后第 N 日仍活跃占比 | `device_daily` | 一级 |
| 同步成功率 | `(ok+304)/总尝试` | `sync_result` | 二级 |
| 日均跳过/活跃设备 | 诊断辅助指标（**非北极星**） | `skip_outcome` | 诊断 |

### 5.2 四类分析方法

| 方法 | 对象 | 关键点 |
|---|---|---|
| **漏斗** | 安装 → 开无障碍 → 配云端规则 → 电池白名单 → 保活引导 → 首次跳过 | 不只看转化率，还要看**每步中位耗时与重试次数**；必须按 `rom` 分群 |
| **留存** | 以「设备」为单位的 D1/D7/D30 | 无账号体系；正确定义是「服务是否仍生效」而非「App 是否被打开」，需结合 `svc_lifecycle` 做**有效留存** |
| **路径** | 激活步骤序列、设置页流转、失败补救 | 重点找异常绕行：反复开关无障碍（≥3 次/日）、同步失败重试风暴、点「立即同步」后无后续 |
| **分群对比** | `rom` / `android_api` / 是否开云端 / 遥测档位 / 版本 | ROM 是第一分群维度；注意**辛普森悖论**，整体下降可能只是分群占比变了 |

### 5.3 统计纪律

- 小样本（十到千级设备）：所有比率带样本量与置信区间
- **k-匿名门槛**：单一设备 / 单一 App 计数 < 5 不展示明细
- 标注幸存者偏差与多重比较风险

---

## 6. 洞察输出与闭环

### 6.1 统一影响面口径

```text
影响面 = 受影响设备占比 × 人均日发生次数
造成的跳过损失 = 影响面 × 单次平均丢失跳过数
样本可信度 = 样本量 ≥ 30 且 置信区间宽度 < 阈值  → 否则标注「样本不足」
```

### 6.2 三类洞察

| 类型 | 判定依据 | 输出 |
|---|---|---|
| A 流失环节 | 漏斗相邻两步跌幅超阈值；或该步耗时/重试显著偏高 | 步骤 + 跌幅 + 耗时 + 受影响设备数 + 失败原因分布 |
| B 异常路径 | 序列模式：同动作短时高频重复；进入某状态后无有效后续 | 具体序列 + 频次 + 涉及设备 + 推测意图 |
| C 可用性问题 | 某失败事件发生率与覆盖设备数同时超阈值 | 问题 + 影响面 + 跳过损失估算 |

### 6.3 优先级：RICE

```text
Score = Reach(受影响设备数) × Impact(0.5/1/2/3) × Confidence(0.5/0.8/1.0) ÷ Effort(人日)
```

Impact 定级：3 = 阻断核心功能；2 = 显著降级；1 = 可感知瑕疵；0.5 = 打磨项。

### 6.4 闭环

产生（Insight Engine）→ 分发（看板 + 可选告警）→ 受理（一键转 issue，附证据/影响面/建议/RICE）→ 验证（回看同源指标）→ 归档。

---

## 7. 数据模型与接口

### 7.1 服务端表结构（`bun:sqlite`）

```sql
CREATE TABLE IF NOT EXISTS events_raw (
  event_id TEXT PRIMARY KEY, day TEXT NOT NULL, occurred_at TEXT NOT NULL,
  device_hash TEXT NOT NULL, app_version TEXT, rom TEXT, android_api INTEGER,
  event TEXT NOT NULL, schema_ver INTEGER NOT NULL DEFAULT 1, props_json TEXT
);
CREATE INDEX IF NOT EXISTS idx_raw_day_event ON events_raw(day, event);

CREATE TABLE IF NOT EXISTS fact_skip_daily (
  day TEXT, pkg TEXT, channel TEXT, result TEXT, cnt INTEGER,
  p50_latency_ms INTEGER, p95_latency_ms INTEGER,
  PRIMARY KEY (day, pkg, channel, result)
);

CREATE TABLE IF NOT EXISTS fact_funnel_daily (
  day TEXT, rom TEXT, step TEXT, entered INTEGER, completed INTEGER, p50_elapsed_ms INTEGER,
  PRIMARY KEY (day, rom, step)
);

CREATE TABLE IF NOT EXISTS fact_device_daily (
  day TEXT, device_hash TEXT, first_seen_day TEXT, skips INTEGER, svc_uptime_ms INTEGER,
  PRIMARY KEY (day, device_hash)
);

CREATE TABLE IF NOT EXISTS meta_rollup_state (
  day TEXT PRIMARY KEY, rolled_at TEXT, source_max_ts TEXT
);

CREATE TABLE IF NOT EXISTS reconcile_log (
  day TEXT, raw_cnt INTEGER, rollup_cnt INTEGER, diff INTEGER, checked_at TEXT
);
```

### 7.2 客户端 SDK

```kotlin
package com.qingqi.adskip.telemetry

enum class Scope { OFF, CORE, FULL }

object Telemetry {
    fun track(event: String, props: Map<String, Any?> = emptyMap())
    fun flush()
    fun setScope(scope: Scope)
    fun setEventEnabled(event: String, enabled: Boolean)
}
```

约束：`track()` 必须**非阻塞**（内存构造 + 异步写缓冲），禁止在 `trySkip` 热路径同步 IO。

埋点位置：`findTarget()` 返回 null → `unmatched`；`SafetyGuard.canClick()` 失败 → `blocked`；点击成功 → `matched`。

### 7.3 HTTP 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/api/v2/telemetry` | 无（限流 + 封顶） | 批量上报，`events[]` ≤ 50 |
| GET | `/api/v2/metrics/overview?days=14` | admin | 总览：total/today/趋势/渠道 |
| GET | `/api/v2/metrics/funnel?from&to&rom` | admin | 激活漏斗 |
| GET | `/api/v2/metrics/retention` | admin | D1/D7/D30 设备留存 |
| GET | `/api/v2/metrics/apps?limit=20` | admin | 按应用排行 + 漏跳 |
| GET | `/api/v2/insights` | admin | 洞察卡片（含 RICE 排序） |
| GET/POST | `/api/v2/admin/telemetry/config` | admin | scope / 采样率 / 保留期 |
| DELETE | `/api/v2/admin/telemetry/data?device=` | admin | 清空指定设备数据 |

上报响应：

```json
{ "accepted": 48, "rejected": 2,
  "errors": [{ "index": 7, "reason": "unknown_field:screen_text" }] }
```

> 未知字段直接丢弃并计数告警 —— 这是隐私红线的工程落点。

---

## 8. 架构与技术选型

### 8.1 硬约束（不可协商）

| 约束 | 来源 |
|---|---|
| 服务端 `package.json` **无 dependencies 字段** | 仓库约定 → 只能用 Bun/Node 内置模块 |
| `engine/` 层**纯 JVM、零依赖** | `ArchitectureBoundaryTest` → 遥测**不得进入 engine** |
| `ui/` 不 import `service/` | 现有分层约定 |
| 隐私默认不出本机、上报可选 | README 承诺 |
| 仓库根不新增目录 | `ProjectStructureTest` 根白名单 |

### 8.2 ADR

| ID | 决策 | 已否决备选 | 依据 | 反悔条件 |
|---|---|---|---|---|
| ADR-1 | 聚合存储 `bun:sqlite` | 纯 JSON / Postgres / ClickHouse | Bun 内置，零 npm 依赖，满足硬约束 | 设备 > 5000 或日事件 > 50 万 |
| ADR-2 | 原始层按天 JSONL | 直写 SQLite / Parquet | 追加写、易清理、可 grep、唯一可重算真相源 | 需列式分析或保留 > 1 年 |
| ADR-3 | 传输复用现有 `SyncClient` | 独立通道 / MQ | 复用 ETag/限流/批量补报/证书 pin，零新增组件 | 需秒级实时 |
| ADR-4 | 客户端缓冲本地 JSONL + JobScheduler | Room / DataStore / WorkManager | 不引入依赖；Room 对「只追加不查询」是过度设计 | 需事务保证或结构化查询 |
| ADR-5 | 看板原生 HTML + 手写 SVG | Chart.js(CDN) / React | 与 `public/admin.html` 一致；CDN 外链泄露访问行为 | 图表交互复杂度显著上升 |
| ADR-6 | 遥测默认 `off` | 默认 `core` | 守住现有隐私承诺 | 开启率过低致样本失效（见 Q1） |
| ADR-7 | 隐私用服务端字段白名单强制 | 仅靠评审 | 评审会漏；工程强制优于约定 | 不反悔 |
| ADR-8 | 遥测链路独立于现有 stats | 改造现有 stats | 可增量上线、随时回滚，不破坏既有契约 | 无 |

### 8.3 分层

```text
客户端：采集层（Telemetry SDK）→ 缓冲层（本地 JSONL）→ 传输层（批量 HTTPS）
服务端：接入层（校验/限流/白名单/幂等）→ 原始层（按天 JSONL）→ 聚合层（bun:sqlite）
        → 服务层（Query API）→ 分析层（Metric Registry + Insight Engine）
展示层：原生 HTML + 手写 SVG 看板 · 治理面板
横切：隐私分级 · 采样 · 保留与清理 · 审计 · 开关与熔断
```

---

## 9. 风险

| # | 风险 | 等级 | 触发信号 | 预案 |
|---|---|---|---|---|
| R1 | 样本偏差 | 高 | 开启率 < 20% 或活跃上报设备 < 30 | 首次启动强引导；仅本地聚合兜底；结论标注子集口径 |
| R2 | 隐私信任受损 | 高 | 审计发现越界字段或社区质疑 | 默认 off；白名单强校验；一键清空；远程熔断 |
| R3 | 埋点拖慢主链路 | 高 | 真机 P95 增量 > 10ms 或新增 ANR | 全异步；采样降级；单类熔断；最坏整体关闭 |
| R4 | 端点伪造污染 | 中 | `_other` 占比 > 30% 或单日 app 数触顶 | 封顶 + 限流 + device 额度；异常告警 |
| R5 | 存储增长失控 | 中 | 磁盘超预算 80% | 缩短保留期；rollup 后删 raw；降采样 |
| R6 | 范围膨胀 | 中 | 未过隐私分级的新事件被提交 | 埋点字典作准入闸门 |

---

## 10. 待确认问题

| # | 问题 | 影响 |
|---|---|---|
| Q1 | 遥测默认值：`off` / `core` / `off`+强引导？ | **项目成败单点**，不确认无法定稿 |
| Q2 | 活跃设备量级与预计开启率？ | 决定是否需聚合层、是否采样 |
| Q3 | 是否接受持久化 `device_hash`？ | 不接受则留存与路径分析无法做 |
| Q4 | 埋点允许给 `trySkip` 增加多少延迟？（建议 P95 ≤5ms） | 验收阈值 |
| Q5 | 是否需要「仅本地聚合」模式？ | 约 +5 人日 |
| Q6 | 团队人力与每周工时？ | 排期前提 |
| Q7 | 是否有 ≥2 台不同 ROM 真机？ | D1/D2 真机验证依赖 |
| Q8 | Top-N 目标 App 清单？ | 覆盖率与待办排序 |
| Q9 | 服务端存储预算与保留期？ | 是否 rollup 后删 raw |
| Q10 | 遥测查询是否需鉴权？ | 安全设计 |
| Q11 | 是否需要导出/删除能力？ | 约 +2 人日 |
| Q12 | 洞察如何进入 backlog？ | 闭环机制 |
| Q13 | P3（规则效能）本期做吗？ | 约 12 人日 |
| Q14 | 期望首个可用版本时间？ | 是否砍 P2/P3 |
| Q15 | 是否接受「目标值待基线后校准」？ | 验收标准固化方式 |
