# 移动端稳定性测试方案与首次执行记录（STABILITY-TEST-PLAN）

> 状态：已生效；最后更新：2026-10-11。
> 适用范围：客户端（无障碍服务、跳过引擎、同步）在真机与 Android Emulator 上的运行稳定性；与 [TOP30-REAL-DEVICE-ACCEPTANCE.md](TOP30-REAL-DEVICE-ACCEPTANCE.md) 的分工——后者管「Top 30 App 逐个跳过有效性验收」，本文管「时间/压力/网络/中断维度下的运行稳定性」。
> 首次执行记录见第 8 节（2026-10-11，Mi 9 / Android 11 / MIUI V125 / v3.4.0）；模拟器矩阵记录见第 9 节（2026-10-11，API 33 / 35）。

## 1. 目标

验证应用在**长时间运行、资源压力、网络波动、系统中断、厂商 ROM 干预**下的功能连续性与资源纪律。核心判据：崩溃/ANR 为零、服务可用性 ≥99.5%、内存无单调增长、无电池/热异常、跳过效用可证。

## 2. 场景矩阵

| 编号 | 场景 | 设计 | 时长 | 通过判据 |
|---|---|---|---|---|
| S1 | 耐久（浸泡） | 启用服务后模拟真实使用：≥6 款重广告目标应用 × 每轮每款 12–15 秒停留；每轮间插息屏/亮屏；每应用采样 meminfo 与服务状态 | 基线 2h + 8–24h | 0 崩溃/ANR；PSS 末段斜率 ≈0；服务持续绑定 |
| S2 | 压力 | `am send-trim-memory RUNNING_LOW`；`am kill`；15 次任务切换爆发；存储 <5%；低电量模式 | 30–60 min | 动作后服务可恢复或本就免受影响；无状态丢失 |
| S3 | 网络波动 | 飞行模式往返 ×5；Wi-Fi/蜂窝切换；弱网/限速；服务端不可达 | 30 min | 同步退避生效、无崩溃、无重试风暴 |
| S4 | 中断与生命周期 | 来电/闹钟、旋转、分屏、权限撤销（无障碍开关）、覆盖安装升级、Doze force-idle | 30 min | 每次中断后服务自愈或 UI 正确反映 |
| S5 | 厂商 ROM 专项 | MIUI：自启动/省电策略/后台弹出权限、最近任务锁定；其他 ROM（EMUI/ColorOS/OxygenOS）同矩阵 | 每 ROM 60 min | 保活入口可导航；ROM 清理后按设计恢复 |
| S6 | 效用回归 | 内置「测试：模拟开屏广告」；真实广告页人工沉浸（开屏/插屏/信息流）；关键词增删回归 | 每版本发版前 | 跳过计数与记录同步增长；误跳率 0 |

## 3. KPI 与阈值

| 指标 | 采集方式 | 阈值 |
|---|---|---|
| 崩溃率（crash-free sessions） | logcat `FATAL EXCEPTION` | **100%** |
| ANR 率 | `am_anr` | 0 |
| 服务可用性 | dumpsys 采样序列 | ≥99.5% |
| 内存（PSS） | `dumpsys meminfo` 序列 + GC 日志 | 末段 10 样品斜率 <1MB/h；Activities/Views 实例数不随轮次增长 |
| CPU | `dumpsys cpuinfo` | 空闲 <3%，事件突发 30s 内恢复 |
| 电池/温度 | `dumpsys battery` | 温升 ≤5°C；24h drain <8% |
| 同步成功率 | logcat + 服务端 accessLog | ≥99%；失败指数退避 |
| 跳过效用 | 首页计数 + 记录页 | 计数与记录一致增长；误跳率 0 |

## 4. 工具与命令

```bash
adb shell dumpsys meminfo <pkg>                    # 内存序列（TOTAL PSS + Objects 段）
adb shell dumpsys activity services <pkg>          # 服务存活 / ServiceRecord / restartTime
adb shell dumpsys accessibility                    # 绑定与启用真值
adb shell settings get secure enabled_accessibility_services
adb logcat -c ; adb logcat -d -v time > soak.log   # 崩溃/ANR/引擎信号
adb shell am send-trim-memory <pkg> RUNNING_LOW ; am kill <pkg>
adb shell dumpsys deviceidle force-idle / unforce  # Doze
adb shell dumpsys battery ; adb shell dumpsys cpuinfo
adb shell uiautomator dump /sdcard/ui.xml + screencap   # 效用证据（必须配截图，语义树有重复文案）
# 深度分析：Android Studio Profiler；现场指标：Play Vitals（crash/ANR）
```

## 5. 环境因素核查清单

- **设备矩阵**：≥2 厂商 ROM（须含 MIUI）× ≥3 Android 版本；覆盖 minSdk 26–targetSdk 35
- **ROM 状态**：省电策略/自启动/后台弹出权限、最近任务锁定态、系统广告服务（如 `com.miui.systemAdSolution`）激活
- **权限**：无障碍已授权、通知、存储
- **网络**：Wi-Fi/蜂窝/飞行/弱网/代理拦截、服务端可达性
- **电源**：电量区间、充电态、室温 vs 高温环境
- **存储**：剩余 >20% 与 <5% 两态
- **目标应用**：登录/未登录两态（开屏广告出现率差异大）；版本记录
- **系统干扰**：来电/短信/闹钟、并发无障碍服务、旋转/分屏/深色模式
- **本地化**：locale（中/英）、时区、12/24 小时制
- **构建差异**：debug 无 R8 vs release R8——**发布前必须复测 release 签名包**（本次首跑即 release 包）

## 6. 证据留存与验收

每次试验留存：设备/ROM/构建三元组、logcat 全文、meminfo/cpuinfo 序列、服务状态时间线、UI 截图、跳过计数前后值。**通过标准**：S1–S5 KPI 达标且 S6 计数增长、误跳为 0；任一 KPI 越界记缺陷进 [FOLLOW-UP.md](../planning/FOLLOW-UP.md)，按严重度决定是否阻塞发版。

## 7. 已知陷阱（执行纪律）

1. **语义树文本 ≠ 可见状态**：`StatusOrb` 两种状态文案会同时出现在无障碍语义树（读屏重复问题），自动化断言「服务未开启」必须配截图确认——2026-10-11 曾因此产生一次误报。
2. **PowerShell `$PID` 陷阱**：`$pid` 为只读自动变量，赋值失败后插值打印的是 shell 自身 PID，勿当作设备进程 ID（本次曾造成「进程重启」假象）。
3. **monkey 启动 ≠ 广告呈现**：自动化打开应用多数窗口不出现可跳过广告，S6 必须用内置自检或人工沉浸兜底。
4. **复测新构建用并行安装**：给 debug 变体临时加 `applicationIdSuffix .debug` 本地构建（**不提交**），与设备上的 release 正式版共存安装、分别授权无障碍服务——避免「卸载正式版丢失本机 100+ 条记录」；验证后卸载并行包，`enabled_accessibility_services` 恢复为单一组件。
5. **模拟器直写 `enabled_accessibility_services` 不触发绑定（API 33/35 实测）**：`settings put` 后 `dumpsys accessibility` 仍无 Bound 服务、应用侧 `AccessibilityManager.getEnabledAccessibilityServiceList` 查不到（首页显示「服务已关闭」）。必须走系统设置 UI：无障碍 → Downloaded apps → 服务详情 → 打开开关 → ALLOW；另外 `pm clear` 会重置该注册，清数据后需重新授权。双包并行时两套服务会互相把对方窗口当第三方处理（见第 9 节发现 4），**干净测试环境一次只装一个包**。
6. **双包并行安装互伤**：并行包与正式包同时启用无障碍服务时，彼此的窗口会被对方引擎当作第三方应用扫描点击（关键词 chip「点击即删除」会被对方引擎点掉）。只做「装一个、测一个」。

## 8. 首次执行记录（2026-10-11，Mi 9 / Android 11 / MIUI V125 / v3.4.0 正式版）

| KPI | 阈值 | 实测 | 判定 |
|---|---|---|---|
| 崩溃（FATAL） | 0 | 0（logcat 28,711 行；崩溃行均为系统组件且与飞行模式切换时刻吻合，与应用无关） | ✅ |
| ANR | 0 | 0 | ✅ |
| 进程稳定性 | 无重启 | pid 11652 全程未变（会话前已运行 2h16m） | ✅ |
| 服务可用性 | ≥99.5% | 18/18 采样存活；1 次服务实例重建（恰逢应用自身 activity 前台重启，进程未死、恢复 <1s，待归因） | ✅ 附观察项 |
| 内存 | 无单调增长 | 前台 158MB → 后台 18 样品收敛 ~98MB 零漂移 → 前台 174MB | ✅ 无泄漏特征 |
| 压力（S2 子集） | 存活 | `RUNNING_LOW` 存活；`am kill` 杀不死（无障碍进程受保护）；12 次快速切换无碍；深度 Doze 后服务仍活 | ✅ |
| 网络（S3 子集） | 无崩溃 | 飞行模式往返正常 | ✅ |
| 跳过效用（S6） | 计数增长 | 首测未证明（monkey 无广告呈现 + 自检缺陷）；**修复版复测已证明**：自检自动跳过（记录 `16:10:18 com.qingqi.adskip.debug`）+ 真实后台跳过 4 条（com.miui.home） | ✅（复测） |

**本次发现**：

1. **自检结构性失效（已修复 #145 并真机验证）**：`FakeAdOverlay` 的 `clearAndSetSemantics` 把「跳过」按钮自身清出语义树，引擎 5 秒窗口内无可点节点，自检必然超时。修复将清除动作移至首页内容层。**复测（2026-10-11 16:10，同机）**：本地临时给 debug 变体加 `applicationIdSuffix .debug` 并行安装（不动正式版），启用其无障碍服务后点击自检——浮层弹出后引擎在约 2 秒内自动点击「跳过」，记录页新增 `16:10:18 | com.qingqi.adskip.debug`，首页「最近应用」变为「轻启」（本应用自身），无超时提示；同会话另有 4 条 `com.miui.home` 真实后台跳过（16:08:17–16:08:57），修复版在真实活动中持续生效。
2. **服务实例一次重建**（观察项，埋点已补）：会话中 SkipAdService 的 ServiceRecord 更新、restartTime=-9s，恰逢应用 activity 前台启动；进程未死、无崩溃、恢复 <1s。归因埋点已由 PR #151 补齐（`onUnbind`/`onDestroy` LogRing 日志），下轮 S1 浸泡用三段时序还原路径。
3. monkey 驱动的自动浏览不产生可跳过广告（目标应用登录态、无开屏广告窗口）——S6 的自动化部分按第 7 节纪律改走内置自检 + 人工沉浸。

**证据**：`F:\All_Data\Temp\kilo\stab\`（首杀：logcat-soak.txt、mem-baseline.txt、5 张截图；复测：ui-dbg-overlay3.xml、screen-dbg-records.png 等 8 个文件）。

## 9. 第二次执行记录（2026-10-11，Android Emulator / API 33 & 35 / v3.4 本地并行构建）

> 补齐第 5 节设备矩阵的「Android 版本 ≥3」维度：真机 Mi 9 / Android 11 之外，用桌面模拟器覆盖 Android 13（API 33）与 Android 15（API 35），均为 x86_64 default 镜像、WHPX 加速、medium_phone 规格。构建为本地临时 `applicationIdSuffix .debug` 并行 debug 包（v3.4 / versionCode 15，基于 main `81b953f`，按 §7.4 不提交）。模拟器环境（SDK emulator + AVD `adskip_api33` / `adskip_api35`）由 android-emulator 插件流程装妥；**本轮结束后已按用户要求卸载**（含引擎与系统镜像），复测时按 §7.5 纪律重装即可。

| KPI | 阈值 | API 33（Android 13）实测 | API 35（Android 15）实测 | 判定 |
|---|---|---|---|---|
| 崩溃（FATAL） | 0 | 0 | 0 | ✅ |
| ANR | 0 | 0 | 0 | ✅ |
| 进程稳定性 | 无重启 | pid 4172 全程不变 | pid 2759 全程不变 | ✅ |
| 压力（S2 子集） | 存活 | `RUNNING_LOW` + `am kill` 后 pid 不变、服务持续绑定 | 同左 | ✅（与真机一致：无障碍进程受保护） |
| 网络（S3 子集） | 无崩溃 | 飞行模式往返 ×2，服务存活 | 同左 | ✅ |
| 跳过效用（S6） | 计数增长、误跳 0 | 自检通过：引擎自动点击浮层「跳过」，记录 `09:28:28 \| com.qingqi.adskip.debug` 落库 | 自检通过：同样自动跳过 | ⚠️ 附缺陷（计数失真 + 误跳，见下） |

**本次发现（缺陷已登记 [FOLLOW-UP.md](../planning/FOLLOW-UP.md)）**：

1. **🔴 发布版包名下自检必然超时（P1，新缺陷）**：`SafetyGuard.canClick` 对 `pkg == "com.qingqi.adskip"`（SELF_PKG，防自触发死循环）一律拒绝点击。无后缀构建（与发布版同包名）在 API 33 上自检 3/3 超时；换 `.debug` 后缀并行包（包名不同）后自检立即通过。推论：**发布版的「模拟开屏广告」按钮在真机上也会永远超时**——首跑 §8 的 #145 复测用的恰是 `.debug` 并行包，绕过了护栏，形成「已修复」假阳性。修复方向：`testActive` 时放行自身包 + 补单测。
2. **🔴 跳过计数双重累加（P1，新缺陷）**：单次自检跳过后，API 33 首页 Total skips 先显示 2、5 秒 flush 后变 3，而记录页恒为 1 条。根因在 `Prefs.recordSkip`（立即写 SP total+1）与 `StatsRepository.recordSkip`（又 `pendingTotal++`，`total()=SP+pending` 双算）；且 `flush()` 落盘后不重置 pending，计数持续膨胀。该结构自 v3.4.0（#78）引入。
3. **🟡 引擎误跳系统界面文本（P2，新缺陷）**：引擎不要求节点 `clickable`，任何含关键词的可见文本都会被点。API 33 设置页中本应用服务行「轻启 · Skip Splash Ads」（含 "skip"）被引擎点击并落库 `com.android.settings` 跳过 1 条（首页 Recent app=Settings 实证）；API 35 设置导航期间同样出现 2 条系统包跳过计数。
4. **🟡 双包并行互伤（测试方法学，已进 §7 纪律）**：两包同时启用服务时互相当对方是第三方应用：正式包引擎把 `.debug` 应用的关键词 chip（文本含关键词、点击即删除）逐个点掉（6→1）。单包环境不复现。
5. **观察项**：API 35 全新安装首启即缺 "no ads" 关键词（5/6），疑引擎在 `testActive` 窗口误点自身 chip，归因待查（与发现 3/4 同根因方向）。

**证据**：`F:\All_Data\Temp\kilo\emu\evidence\`（API 33：home、selfcheck 超时帧 ×2、settings 行、debug-home、debug-selfcheck-result、clean-logs、api33-logcat-full3.txt 等 11 项；API 35：settings、allow-dialog、home、selfcheck、selfcheck-result 5 张）。
