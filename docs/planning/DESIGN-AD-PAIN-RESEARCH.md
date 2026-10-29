# 手机广告用户痛点研究（DESIGN-AD-PAIN-RESEARCH）

> 状态：**资料台账（持续增补）** — 本文只做事实收录与标注，不做产品决策；决策依据见 [PLAN-CLIENT-REFORM.md](PLAN-CLIENT-REFORM.md)。
> 最后更新：2026-10-29（第 2 版：新增国际证据台账、跨区域对比、覆盖范围说明）。
> 性质：**证据汇编（Evidence Ledger）**。每条事实标注来源、时间、可信度；未找到一手来源的内容一律标为「待核」或降级，不用推测填补。
> 收录范围：**中国大陆 + 欧盟 + 美国 + 其他英语市场**。覆盖法规、监管执法、量化调查、学术研究、平台政策五个维度。
> 使用方式：查事实 → 按 ID 定位；引用时同时给出 ID 与来源，便于回溯。

---

## 1. 标注体系

### 1.1 可信度分级

| 级别 | 定义 | 使用限制 |
| --- | --- | --- |
| **A** | 一手来源：法规原文、政府网站公告、法院判决、同行评议会议论文、国际组织报告 | 可直接引用为事实 |
| **B** | 主流媒体或专业机构，且明确标注机构、样本或时间；含律所专业分析 | 可引用，需带机构名 |
| **C** | 行业媒体、机构报告、百科条目、第三方实测（有方法但非官方） | 可引用，须注明「据…」 |
| **D** | 自媒体、问答平台、无原始出处的二手转述 | **不作为论据**，仅作线索 |

### 1.2 时效标记

| 标记 | 含义 |
| --- | --- |
| `现行` | 截至 2026-10 仍有效 |
| `已废止` | 已被新规或判决替代 |
| `待核` | 仅单一来源，或来源未给出原始出处 |
| `历史` | 仅作时间线参考，不代表当前状态 |
| `提案` | 尚未成为生效法律 |

### 1.3 ID 前缀说明

| 前缀 | 范围 | 类别 |
| --- | --- | --- |
| `L` | 国内 | 法规与标准 |
| `R` | 国内 | 监管行动时间线 |
| `S` | 国内 | 量化调查数据 |
| `J` | 国内 | 司法案例 |
| `V` | 国内 | 厂商系统能力 |
| `E` | 国内 | 工具生态 |
| `M` | 国内 | 行业规模 |
| `GL` | 国际 | 法规与标准（Global Law） |
| `GR` | 国际 | 监管与执法（Global Regulation） |
| `GS` | 国际 | 量化数据（Global Statistics） |
| `GA` | 国际 | 学术研究（Global Academic） |
| `GP` | 国际 | 平台政策（Global Platform） |

每条证据固定 7 个字段：`ID`、`事实`、`数值/条款`、`时间`、`主体`、`来源`、`级别`。

---

## 2. 国内证据台账

### 2.1 法规与标准（L 系列）

| ID | 事实 | 数值 / 条款 | 时间 | 主体 | 来源 | 级别 |
| --- | --- | --- | --- | --- | --- | --- |
| L1 | 《互联网广告管理办法》施行，替代 2016 年《暂行办法》 | 全文 | 2023-05-01 施行（2023-03 发布） | 市场监管总局 | [gov.cn](https://www.gov.cn/zhengce/202305/content_6858084.htm) | A |
| L2 | 弹窗广告必须显著标明关闭标志、确保一键关闭 | 禁止四种情形：①无关闭标志或计时结束才能关；②关闭标志虚假、不可清晰辨识、难以定位；③关闭须经两次以上点击；④关闭后在同一页面继续弹出 | 同上 | 市场监管总局 | [央广网报道条文](https://www.cnr.cn/tech/techyw/kan/20230325/t20230325_526195124.shtml) | B |
| L3 | 弹窗违规罚则 | 最高可罚 3 万元 | 2023-05-01 | 市场监管总局 | 同上（央广网） | B |
| L4 | 禁止以高灵敏度「摇一摇」「全屏热力图」诱导用户操作 | 要求开屏和弹窗提供清晰有效的关闭按钮 | 2023-02（2023-03-01 公开） | 工信部《关于进一步提升移动互联网应用服务能力的通知》 | [miit.gov.cn](https://gsca.miit.gov.cn/zwgk/zcwj/wjfb/art/2023/art_7aa9c3f277a14a6ead1bd397871e782f.html) | A |
| L5 | 摇一摇广告触发行为安全要求 | 《网络安全标准实践指南——摇一摇广告触发行为安全要求》：App 应设便捷关闭渠道与合理触发阈值 | 2025-07 | 全国网络安全标准化技术委员会 | 二手转述（[百家号](https://baijiahao.baidu.com/s?id=1838404418610329440)） | C `待核` |
| L6 | 个性化推荐必须提供「不针对个人特征的选项」或「便捷的拒绝方式」 | 《个人信息保护法》第 24 条第 2 款原文：「通过自动化决策方式向个人进行信息推送、商业营销，应当同时提供不针对其个人特征的选项，或者向个人提供便捷的拒绝方式。」 | 2021-11-01 施行 | 全国人大常委会 | [cac.gov.cn 原文](https://www.cac.gov.cn/2021-08/20/c_1631050028355286.htm) | A |
| L7 | 信息流广告必须全程清晰标注「广告」字样，与原生内容显著区分，严禁隐形植入 | 地方监管提示 | 2026-05-29 | 太原市市场监督管理局 | [太原市监局](https://scjgj.taiyuan.gov.cn/gggs/20260529/30301137.html) | A（地方规范性提示，非全国规章） |
| L8 | 弹窗信息推送服务另有专门规定 | 《互联网弹窗信息推送服务管理规定》 | 2022-09-09 | 国家网信办等 | [gov.cn](https://www.gov.cn/zhengce/zhengceku/2022-09/09/content_5709179.htm) | A |
| L9 | 智能电视开机广告 | 《智能电视开机广告技术规范》：总时长不超过 30 秒，且必须具备一键关闭 | 2023 前后 | 行业协会标准 | [央视报道](https://content-static.cctvnews.cctv.com/snow-book/index.html?item_id=17956931956463254835) | C（非本主题核心，仅作参照） |
| L10 | 2026 年个人信息保护系列专项行动 | 中央网信办、工信部、公安部联合发布公告，依据《网络安全法》《个人信息保护法》《网络数据安全管理条例》《App 违法违规收集使用个人信息行为认定方法》 | 2026 | 三部门联合 | [中国技术监督情报协会转述](https://catsi.cn/view-41-bc12bcf264f042c78f3447d39b484f9f.html) | C `待核`（需查三部门原始公告） |

### 2.2 监管行动时间线（R 系列）

| ID | 时间 | 主体 | 行动 | 来源 | 级别 |
| --- | --- | --- | --- | --- | --- |
| R1 | 2021-07 | 工信部 | 专项整治「关不掉」「乱跳转」，禁止高灵敏度「摇一摇」诱导 | [百度百科·开屏广告](https://baike.baidu.com/item/开屏广告/63379291) | C |
| R2 | 2023-02 | 工信部 | 发布《关于进一步提升移动互联网应用服务能力的通知》（见 L4） | miit.gov.cn | A |
| R3 | 2025-11 | 市场监管部门 | 就「双11」期间部分 App 开屏广告轻微晃动即强制跳转展开调查 | 百度百科·摇一摇 | C `待核` |
| R4 | 2025-11 | 广州地铁 App | 因乘车码下方「摇一摇」广告影响过闸效率，经用户反馈后撤下该位置广告 | 百度百科·摇一摇 | C `待核` |
| R5 | 2026-03-13 | 工信部信息通信管理局 | 《关于侵害用户权益行为的 APP(SDK) 通报（2026 年第 2 批，总第 55 批）》：**24 款** APP 及 SDK，涉游戏、教育、招聘、出行、工具等领域 | [百度百科·通报条目](https://baike.baidu.com/item/侵害用户权益行为的APP(SDK)通报/67514837) | C |
| R6 | 2026-06 | 工信部信息通信管理局 | 召开专题会议，针对部分 App 信息窗口违规跳转要求企业自查整改 | 百度百科·摇一摇 | C `待核` |
| R7 | 2026-06 底 → 08 初 | 多家 App | 多款应用开屏广告一度取消（6 月底），至 8 月初部分 App 开屏广告再度出现 | 百度百科·摇一摇 | C `待核` |
| R8 | 2026-09-22 | 工信部 | 通报 **26 款** App 及 SDK，涉信息窗口无法关闭或诱导跳转 | 百度百科·摇一摇 | C `待核`（批次号未核，见 §6 C8） |
| R9 | 2026-10-15 | 工信部信息通信管理局 | 《关于侵害用户权益行为的 APP(SDK) 通报（2026 年第 6 批，总第 59 批）》：**17 款** APP 及 SDK | [miit.gov.cn 原文](https://www.miit.gov.cn/jgsj/xgj/gzdt/art/2026/art_a2e3677caf8a4d70a892b75c4d7e0f49.html) | **A** |
| R10 | 2026 二季度 | 工信部 | 「来电来信免打扰」为 **13.8 亿**用户提供营销电话/短信防护 **91.2 亿次**；关停违规语音专线 1034 条、短信端口 762 个、处罚企业 315 家次 | 微信公众号转述 | C `待核`（电话/短信，非 App 广告，仅作治理力度参照） |

> **R9 是本台账中唯一取到官网原文的通报**，可作为「工信部确实在按批次持续通报」的 A 级锚点。R5/R8 的批次与数量来自二手来源，与 R9 的编号体系（2026 年第 N 批，总第 M 批）对照后可信度提升，但**具体批次归属仍需逐条核**。

### 2.3 量化调查数据（S 系列）

| ID | 指标 | 数值 | 时间 | 机构 / 样本 | 来源 | 级别 |
| --- | --- | --- | --- | --- | --- | --- |
| S1 | 受访者对「跳不过、关不掉的 App 广告」感到困扰 | **92.7%** | 2021-07 | 人民网调查（样本量未在报道中列明） | [人民网](http://yn.people.com.cn/GB/n2/2021/0722/c372455-34831930.html) | B |
| S2 | 受访者反映「误触跳转到第三方 App 或网页」 | **64.0%** | 2021-07 | 同上 | 同上 | B |
| S3 | 受访者表示「跳过」或「关闭」按钮太小 | **63.5%** | 2021-07 | 同上 | 同上 | B |
| S4 | 受访者认为「误触直接下载广告 App」 | **52.8%** | 2021-07 | 同上 | 同上 | B |
| S5 | 存在问题弹窗「影响正常网页浏览或软件使用」 | **59.21%** | 2022（2023-03 发布） | 艾媒咨询 | [艾媒网](https://www.iimedia.cn/c1061/92091.html) | C |
| S6 | 100 款主流 App 中有开屏广告 | **76 款**；仅 1 款可彻底免费关闭 | 2026-09 | 什么值得买作者实测（逐款人工核查，非抽样统计） | [smzdm](https://post.m.smzdm.com/p/ad796lpd/) | C |
| S7 | 受测 App 中「拿起手机」场景即触发摇一摇跳转 | **12 款中过半** | 2023-05 | 媒体实测（媒体名未明确） | [四平信用网转述](http://credit.siping.gov.cn/tslm/fxts/202305/t20230506_2351428.html) | C `待核` |
| S8 | 存在摇一摇跳转广告的 App 比例 | **34%** | 2023 监测 | 监测方未明确 | 百度百科·开屏广告 | C `待核` |
| S9 | 摇一摇触发灵敏度区间 | **0.1°–15°** | 2023 监测 | 同上 | 同上 | C `待核` |
| S10 | 超七成受访老人希望减少广告 | **>70%** | 2023-03 | 新京报贝壳财经（样本量未列明） | [新京报](https://www.bjnews.com.cn/detail/167893650514524.html) | B |
| S11 | 电视机开屏广告持负面态度 | 9.04% 完全不能接受 + 15.69% 不太接受 = **24.73%** | 2024 | 艾媒咨询 | [艾媒网](https://www.iimedia.cn/c1061/102052.html) | C（电视场景，非手机，仅作对照） |
| S12 | 某电商平台内部测试：关闭按钮缩小 1 像素，误点广告概率提升 0.7% | 0.7%/px | 未标时间 | **无原始出处** | 百度知道回答 | **D（不可引用）** |
| S13 | 某视频平台等待时长 15s→30s，次日留存下跌 4 个百分点 | −4pp | 未标时间 | **无原始出处** | 百度知道回答 | **D（不可引用）** |

> S12/S13 保留在台账中是为了**标记为不可用**——这两个数字在中文互联网被反复转引，但都找不到原始测试报告。后续若有人引用，应直接指回本条。

### 2.4 司法案例（J 系列）

| ID | 案件 | 时间 | 法院 | 结果 | 来源 | 级别 |
| --- | --- | --- | --- | --- | --- | --- |
| J1 | 武汉大学法学院学生张馨月 诉 厦门鸿天创视科技（美图秀秀）「摇一摇」开屏广告侵权 | 2023-04-06 一审 | 福建省厦门市思明区人民法院 | ① 停止侵权；② 对触发跳转的交互动作参数作合规设置：**转动角度不小于 35 度、操作时间不少于 3 秒**；③ 赔偿数据流量损失费 **1 元** | [上观新闻](https://export.shobserver.com/toutiao/html/606650.html)、[嘉兴举报中心转述](http://jiaxing.zjjubao.com/a/html/80082384) | B |
| J2 | 该案认定的权益类型 | 同上 | 同上 | 侵犯**自主选择权**；原告同时主张个人信息安全（该项是否获支持未见明确报道） | 同上 | B `待核` |
| J3 | 李跳跳（MissLee）收到腾讯律师函，被指涉嫌违反《反不正当竞争法》第二条，宣布无限期停更 | 2023-08-24 | **未进入诉讼 / 未见判决** | 停更 | [百度百科](https://baike.baidu.com/item/MissLee%20(%E6%9D%8E%E8%B7%B3%E8%B7%B3)/68183230)、[光明网](https://m.gmw.cn/2023-08/30/content_1303498686.htm) | B |

> **J3 的常见误读**：多个二手文章称李跳跳「败诉」或「被判不正当竞争」。公开信息显示这是**律师函 → 主动停更**，没有生效判决。本项目文档此前也写作「诉讼先例」，已在 [PLAN-CLIENT-REFORM.md](PLAN-CLIENT-REFORM.md) §4.2 更正。

### 2.5 厂商系统能力（V 系列）

| ID | 厂商 | 系统 / 版本 | 能力 | 时间 | 来源 | 级别 |
| --- | --- | --- | --- | --- | --- | --- |
| V1 | 华为 | 鸿蒙 6.0 | 新增拦截应用内「摇一摇」广告跳转 | 截至 2025-12 | 百度百科·摇一摇 | C `待核` |
| V2 | 苹果 | iOS 27.2 beta 2 | 新增「Restrict Motion Data」（限制运动数据），可切断依赖运动传感器的摇一摇广告；**仅中国区可用** | 2026-09 | 百度百科·摇一摇、[百家号](https://baijiahao.baidu.com/s?id=1877316587710022322) | C `待核` |
| V3 | 多厂商 | 通用 | 权限管理中的「应用获取设备方向」开关可逐 App 关闭运动传感器权限 | 2026-10 | [百家号教程](https://baijiahao.baidu.com/s?id=1879621018001696188) | D（操作教程，非官方说明） |
| V4 | 小米/华为/OPPO/vivo/苹果 | 各系统 | 系统级「个性化广告推荐」「用户体验改进计划」开关，以及锁屏杂志、负一屏资讯的清理 | 2026-09 | [smzdm 实测](https://post.m.smzdm.com/p/ad796lpd/) | C |

### 2.6 工具生态（E 系列）

| ID | 产品 | 技术方式 | 现状 | 来源 | 级别 |
| --- | --- | --- | --- | --- | --- |
| E1 | GKD（开源，活跃） | 无障碍 + 声明式规则 + **社区订阅**（覆盖数百款应用、上千规则组） | 活跃 | 检索结果多处提及订阅教程 | C |
| E2 | 李跳跳 | 无障碍，内置规则，免费、不联网 | 2023-08 停更（见 J3） | 百度百科 | B |
| E3 | 轻启动 | 智能识别广告模块并阻止加载，内存约 2MB | 有报道提及 | [腾讯云社区](https://cloud.tencent.com/developer/article/2665278) | D |
| E4 | AdGuard | 本地 VPN 过滤广告请求，需手动配置规则 | 有报道提及 | 同上 | D |
| E5 | 厂商内置「纯净模式」/ 付费去广告 | 部分 App 提供付费免广告（如门禁类 App 1 元/月纯净版） | 存在 | [微博转南都报道](https://weibo.com/2/detail/5346419923224431) | C |

### 2.7 行业规模（M 系列，背景数据）

| ID | 指标 | 数值 | 时间 | 来源 | 级别 |
| --- | --- | --- | --- | --- | --- |
| M1 | 中国互联网广告市场规模（QuestMobile 口径） | 2025 上半年近 **3600 亿元**；2026 上半年 **3853.4 亿元**（同比 +7.1%），全年预计 8362.6 亿元 | 2025–2026 | [澎湃转 QuestMobile](https://m.thepaper.cn/newsDetail_forward_31429458)、[三个皮匠转述](https://www.sgpjbg.com/searchtag/27318090.html) | C |
| M2 | 全网用户规模 | **12.69 亿**（截至 2025-09，同比 +2%）；月人均使用时长 178.2 小时（同比 +8.2%） | 2025-09 | [百家号转 QuestMobile 秋季报告](https://baijiahao.baidu.com/s?id=1847947300513607884) | C |
| M3 | 互联网广告发布收入占各类媒介广告发布收入 | **89.1%** | 2025 | 三个皮匠转述 | C `待核` |
| M4 | 口径冲突：广告「业务收入」vs「市场规模」 | 2025 年中国互联网广告业务收入 **13574.3 亿元**（同比 +34.6%），与 M1 的 8362.6 亿元并存 | 2025–2026 | 同上 | C |

---

## 3. 国际证据台账

### 3.1 法规与标准（GL 系列）

| ID | 事实 | 条款 / 数值 | 时间 | 主体 | 来源 | 级别 |
| --- | --- | --- | --- | --- | --- | --- |
| GL1 | DSA 第 25 条禁止 dark patterns | Art. 25(1)：在线平台提供者不得以「欺骗或操纵服务接受者，或以其他方式实质性扭曲或损害接受者做出自由知情决定能力」的方式设计、组织或运营其界面 | 2022-10 通过，2024-02 全面适用 | 欧盟 | [Irish Examiner 引述原文](https://www.irishexaminer.com/business/companies/arid-41839641.html) | B |
| GL2 | DSA 罚则 | 最高可达**全球营业额 6%**；屡犯可被禁止在欧盟开展业务 | 同上 | 欧盟 | 媒体转述（百家号） | B `待核`（须核 DSA Art. 74） |
| GL3 | DSA 对定向广告的限制 | 禁止针对儿童，以及基于性别、种族、政治观点等敏感数据的定向广告 | 同上 | 欧盟 | 媒体转述 | B `待核` |
| GL4 | GDPR 对个性化广告的同意要求 | 需明确、知情、具体的同意；拒绝应与同意同样容易 | 2018-05-25 施行 | 欧盟 | 待核原文条款（Art. 4(11)、Art. 7） | B `待核` |
| GL5 | 《数字公平法》（DFA）提案 | 覆盖 dark patterns、成瘾性设计、网红营销、不公平个性化，特别关注未成年人。2025-07～10 公众咨询；2025-12 结果摘要显示约**七成**受访者支持有约束力措施；已列入欧盟委员会 2026 年工作计划 | **提案阶段（未立法）** | 欧盟委员会 | [digitalfairnessact.com](https://digitalfairnessact.com/)、[Freshfields 分析](https://www.freshfields.com/en/our-thinking/blogs/technology-quotient/the-eus-proposed-digital-fairness-act-an-online-platforms-guide-to-potential-i-102m1e4)、[中文分析](https://mp.weixin.qq.com/s?__biz=MzI3MTYzODg0Mg==&mid=2247605712&idx=2&sn=91e62a37e16b5f47a63bf19a2409ce0b) | C `提案` |
| GL6 | 加州隐私权法（CPRA）首次在立法中界定「黑暗模式」 | 2020-11 通过 | 2020 | 美国加州 | 百度百科·黑暗模式 | C `待核` |
| GL7 | FTC 法第 5 节将 dark patterns 纳入规制 | — | 现行 | 美国 FTC | 百度百科·黑暗模式 | C |
| GL8 | 英国《数字市场、竞争与消费者法案》（DMCCA）限制黑暗模式 | 2024 | 2024 | 英国 | 中文分析转述 | C `待核` |
| GL9 | 澳大利亚消费者政策研究中心（CPRC）《Duped by Design》 | **29%** 澳大利亚人创建过不想要的账号；**29%** 误注册过服务；**25%** 分享过超出意愿的个人信息 | 2022-06 | CPRC | [原始 PDF](http://cprc.org.au/wp-content/uploads/2022/06/CPRC-Duped-by-Design-Final-Report-June-2022.pdf) | B |
| GL10 | 纽约市「一键取消」订阅规则生效 | 加入已有类似法律的州之列 | 2026-10 | 纽约市 | CNBC 转述 | C `待核` |

### 3.2 监管与执法（GR 系列）

| ID | 事实 | 时间 | 主体 | 来源 | 级别 |
| --- | --- | --- | --- | --- | --- |
| GR1 | 爱尔兰媒体委员会（Coimisiún na Meán）就 Facebook / Instagram 的 dark patterns 启动 DSA 调查 | 2026-05-06 | 爱尔兰监管机构 | [Irish Examiner](https://www.irishexaminer.com/business/companies/arid-41839641.html) | B |
| GR2 | Meta 被认定 DSA 违规：举报机制设「不必要的操作步骤与额外要求」+ 使用暗模式或欺骗性界面设计 | 2025-11 | 欧盟 | 百家号分析 | C `待核` |
| GR3 | 美国 FTC「click-to-cancel」规则 2025-01 生效，**2025-07 被第八巡回上诉法院撤销（vacated）** | 2025-07 | 美国第八巡回上诉法院 | [Sidley Austin](https://www.sidley.com/zh-hans/insights/newsupdates/2025/07/us-ftc-click-to-cancel-rule-struck-down)、[Guardian 转述](https://baijiahao.baidu.com/s?id=1837723187068129058) | B |
| GR4 | FTC 于 2025-03-13 就更新 Negative Options Rule 征求公众意见（含是否采纳被撤销规则的条款），意见截止 2025-04-13 | 2025-03 | 美国 FTC | [Holland & Knight](https://www.hklaw.com/en/insights/publications/2025/09/ftc-steps-up-subscription-enforcement-after-click-to-cancel-rule)、CNBC 转述 | B |
| GR5 | 美国国会 2026-01 拟立法恢复 click-to-cancel（众议院、参议院各一项法案） | 2026-01 | 美国国会 | CNBC 转述 | B `提案` |
| GR6 | FTC 报告：复杂 dark patterns 呈上升趋势，用于诱导购买与交出个人信息 | 2022-09-16 | 美国 FTC | [ftc.gov](https://www.ftc.gov/news-events/news/press-releases/2022/09/ftc-report-shows-rise-sophisticated-dark-patterns-designed-trick-trap-consumers) | **A** |
| GR7 | 亚马逊因在线结账与订阅取消流程中的 dark patterns 被处理 | 2025 | 美国 | 百度百科·黑暗模式 | C `待核` |

### 3.3 量化数据（GS 系列）

| ID | 指标 | 数值 | 时间 | 机构 / 样本 | 来源 | 级别 |
| --- | --- | --- | --- | --- | --- | --- |
| GS1 | 全球广告拦截用户总数 | **11 亿**（桌面 4.48 亿 + 移动 6.29 亿 = 10.77 亿）；较 2023 年 +13% | 2025 Q4 | eyeo《2026 Ad-Blocking Report》 | [SignalBridge 汇总](https://www.signalbridgedata.com/blog/ad-blocker-statistics-2026) | C（厂商自报） |
| GS2 | 全球广告拦截率 | **30.0%**（2025），2021 年为 37.0% | 2025 | eyeo | 同上 | C |
| GS3 | 16 岁以上互联网用户「至少有时」使用拦截器 | **29.5%**（Q2 2025），2023 Q2 为 32.8% | 2025 Q2 | GWI via DataReportal | 同上 | C |
| GS4 | 移动端占拦截用户比重 | **58.4%**（629/1077M）；较 2023 年 **+27%**；桌面 −4% | 2025 Q4 | eyeo | 同上 | C |
| GS5 | 各国拦截率 | 印尼 **40.1%**（最高）、加纳 9.2%（最低）、美国 32.5%、德国 31.5%、英国 28.5% | 2025 Q2 | GWI via DataReportal | 同上 | C |
| GS6 | 美国消费者主动尝试忽略或屏蔽广告 | **81%**；其中 **52%** 采取具体措施阻止广告曝光（拦截器、VPN、付费去广告） | 2026 | Gartner 调查 | [The Measure 引述](https://www.themeasure.net/ad-blocking-tops-1-billion-users-as-browsers-give-consumers-more-control) | B |
| GS7 | 曾安装或使用广告拦截器的消费者比例 | **52%**（48 个全球市场）；美国 45% | 2026 | YouGov via eMarketer | [Web Tonic 汇总](https://www.webtonic.io/blog/ad-blocking-statistics) | C |
| GS8 | 全球消费者使用拦截器的频率 | 21% 经常使用，另 11% 偶尔使用 | 2026 | GWI | 同上 | C |
| GS9 | 移动端拦截率实测（分区域中位数） | 北美 28%、英国 32%、西欧 36%、德国 41%、东南亚 51% | 2026 | DeBlocker | 同上 | C |
| GS10 | 出版商对移动拦截率的自估偏差 | 自估 8–15%，实测 **30–60%** | 2026 | DeBlocker | 同上 | C |
| GS11 | 用户对平台打击拦截器的反应 | YouTube 打击后，拦截器求助/绕过页面流量 **+336%**；仅 **11%** 称因此减少使用 | 2026 | All About Cookies（1,000 名美国 YouTube 用户） | 同上 | C |
| GS12 | Brave 浏览器月活 | **超 1 亿** | 2025 | Brave | The Measure | C |
| GS13 | Acceptable Ads 生态参与人数 | **超 4 亿** | 2026 | eyeo | The Measure | C |

> **GS1–GS5 的口径差异**：eyeo（Adblock Plus 母公司）统计的是「显式安装了带拦截功能的产品」的用户数，GWI 统计的是「自我报告至少有时使用」的人群比例。两者不可互相换算，引用时必须写明口径——这也是 GS2 与 GS3 数值不同的原因。

### 3.4 学术研究（GA 系列）

| ID | 研究 | 关键结论 | 时间 | 机构 / 会议 | 来源 | 级别 |
| --- | --- | --- | --- | --- | --- | --- |
| GA1 | 移动 App 中 dark patterns 的普遍率 | 分析 **240 款**流行移动 App，**95%** 至少包含一个 dark pattern 实例 | 引用于 2023 论文 | 原始研究未在检索结果中列明 | [arXiv 2310.17846](https://arxiv.org/pdf/2310.17846v2.pdf) 引用 | B `待核`（需回溯原始文献 [26]） |
| GA2 | 「ATTention Please!」— iOS 应用跟踪透明度权限弹窗研究 | 分析 **4,000 个** iOS App，**59%** 的 ATT 弹窗使用四种 dark patterns（误导用户、激励追踪、含混淆术语、省略目的）；另做 114 人用户研究 | 2024 | USENIX Security 2024 | [论文 PDF](https://www.usenix.org/system/files/usenixsecurity24-mohamed.pdf) | **A**（同行评议会议） |
| GA3 | 印度数字应用中的 dark patterns 实证研究 | **200 款**印度移动 App、8 个类别、2019–2024 年，混合方法 | 2025 | IEEE 国际会议 | 百度学术条目 | B |
| GA4 | 欧盟同意规制与用户实证研究之间的差距 | 指出 DSA Art. 25(1) 的「dark patterns」定义与用户实际体验研究之间存在断层 | 期刊论文 | Harvard JOLT | [PDF](https://jolt.law.harvard.edu/assets/articlePDFs/v37/Symposium-12-Bielova-Santos-Gray-Two-Worlds-Apart-Closing-the-Gap-Between-Regulating-EU-Consent-and-User-Studies.pdf) | B |
| GA5 | 零售投资领域的 dark patterns | 国际证监组织对 dark patterns 的跨司法辖区梳理 | 2024-02 | IOSCO | [IOSCO PDF](https://www.iosco.org/library/pubdocs/pdf/IOSCOPD794.pdf) | **A**（国际组织） |
| GA6 | Mark Leiser《Dark Patterns, Deceptive Design, and the Law》专著 | 欺骗性设计与数字操纵的规制 | 2026-02 有 HKU 书谈 | 学术专著 | [HKU 活动页](https://www.law.hku.hk/events/book-talk-dark-patterns-deceptive-design-and-the-law/) | B |

> **GA1 是本文最有力的单一数据点**（95% 的流行 App 含 dark pattern），但它来自一篇 arXiv 论文对另一项研究的引用，原始出处未在检索结果中显现。引用时应写「据一项覆盖 240 款 App 的研究」，并注明转引自 arXiv，不能写成「某权威机构统计」。

### 3.5 平台政策（GP 系列）

| ID | 平台 | 政策 | 生效时间 | 来源 | 级别 |
| --- | --- | --- | --- | --- | --- |
| GP1 | Google Play | **Accessibility API 政策更新：禁止任何利用该 API 让应用「自主发起、规划和执行操作」的行为**。官方理由是此类行为可能在未经许可下更改用户设置、规避 Android 隐私控制、或在用户不知情/未同意的情况下执行操作 | 2025-10-30 发布，**2026-01-28 生效** | [CSDN 专栏转述政策原文](https://download.csdn.net/blog/column/12512885/154987895)、[百家号转述](https://baijiahao.baidu.com/s?id=1847910765881246676) | C `待核`（**必须查 Google 官方政策页**） |
| GP2 | Apple | iOS 27.2「Restrict Motion Data」仅中国区可用 | 2026-09 | 百家号 | C `待核` |
| GP3 | Brave / Shift | 浏览器内置拦截成为主流分发路径：Brave 内置拦截（2025 MAU 超 1 亿）；Shift Browser 上线内置 Blocker（adblock-rust 引擎），付费版含视频贴片拦截 | 2025–2026 | The Measure | C |

> **GP1 是本台账中对「跳广告类工具」最关键的一条**。若该政策表述准确，意味着**自动化点击类应用无法在 Google Play 上架**——出海路径被封死，只剩国内侧载（sideload）与开源分发。这条必须优先核实，已列入 §7 缺口 G8。

---

## 4. 六大痛点方向与证据映射

归纳方式：按「用户受到的伤害类型」分类，而非按广告形态分类——同一形态可能同时命中多个方向。

| # | 方向 | 典型表现 | 反感根源（心理机制） | 国内证据 | 国际证据 |
| --- | --- | --- | --- | --- | --- |
| 1 | **干扰体验**（注意力被强制征用） | 开屏强制停留、弹窗盖住操作区、视频贴片与暂停广告 | 任务流被打断，且不可预测 | S1（92.7%）、S5（59.21%）、S6（76/100） | GS6（81% 主动忽略）、GS7（52% 曾用拦截器） |
| 2 | **控制权被剥夺**（手机不听我的） | 摇一摇高灵敏触发、虚假关闭键、关闭需多次点击/延时出现 | 自主性被侵犯；操作结果与意图脱钩 | L2、L4、J1（≥35°/≥3s）、S2/S3/S4、S9 | GL1（DSA Art.25）、GR1（Meta 被查）、GA2（59% ATT 弹窗含暗模式） |
| 3 | **强制付费套路**（被当成提款机） | 会员仍看开屏与贴片、纯净版另收费、会员层级套娃 | 公平感被破坏：制造问题再卖解决方案 | E5、澎湃实测（见 §6 C2） | GR3（FTC click-to-cancel 被撤销）、GR4/GR5（国会拟恢复）、GL9（29% 误注册） |
| 4 | **隐私被窥探**（被监视却无从拒绝） | 刚聊完就推同款、个性化关闭入口极深、关闭后不减量只减精度 | 信息与权力不对称 | L6（PIPL 第 24 条）、§6 C3 | GL4（GDPR 同意）、GL5（DFA 拟管「不公平个性化」）、GA2 |
| 5 | **广告伪装成内容**（分不清真假） | 信息流不标广告或标识极小、测评种草软植入 | 知情权被剥夺；信任不可逆损耗 | L7（须全程标注「广告」） | GL1（DSA「欺骗性界面」）、GA1（95% App 含暗模式） |
| 6 | **性能与资费代价**（成本转嫁） | 广告 SDK 后台耗电耗流量、低端机卡顿 | 收益归平台，成本归用户，且不可归因 | 证据薄弱（§7 G4） | GS9/GS10（移动拦截率被低估）、GS11（打击后绕过流量 +336%） |

### 4.1 六个方向的共同结构

六者都指向同一件事：**广告主获得了用户没有的东西**。

| 广告主有 | 用户没有 | 对应方向 |
| --- | --- | --- |
| 强制力（延时关闭、高灵敏触发） | 拒绝的入口 | 1、2 |
| 信息（知道用户在看什么） | 知情（不知是广告、不知数据如何被用） | 4、5 |
| 时间（占 5 秒） | 选择（不能跳过） | 1 |
| 设备资源（后台跑 SDK） | 知情与补偿 | 6 |
| 定价权（免广告另行收费） | 议价能力 | 3 |

**推论**：用户厌恶的不是「广告存在」，而是「在这段关系里自己完全被动」。这解释了为什么体验粗糙的跳广告工具仍有真实需求——用户购买的是**控制权**，不是「无广告」。

### 4.2 跨区域对比

| 维度 | 中国大陆 | 欧盟 | 美国 |
| --- | --- | --- | --- |
| **规制路径** | 部门规章 + 专项整治 + 通报（工信部、市场监管总局） | 统一立法（DSA/GDPR）+ 成员国执法 | 分散：FTC 执法 + 州法 + 国会立法尝试 |
| **规制对象** | 具体交互（弹窗关闭、摇一摇灵敏度、信息流标注） | 界面设计的一般性禁止（dark patterns、欺骗性界面） | 订阅取消（negative option）、欺骗性行为 |
| **最强手段** | 通报 + 责令整改（罚则较轻，弹窗违规最高 3 万元） | **全球营业额 6%** 罚款 + 可禁止运营 | 个案执法 + 州法（联邦规则刚被撤销） |
| **当前状态（2026-10）** | 持续高压但反复（开屏广告 6 月取消、8 月复现） | 执法加速（Meta 被查、DFA 立法推进中） | **联邦层面倒退**（click-to-cancel 2025-07 被撤销），州层面补位 |
| **用户侧工具** | 侧载 + 无障碍类工具活跃（GKD 等） | 浏览器内置拦截为主（Brave 等） | 浏览器扩展为主 |
| **对「跳广告工具」的容忍度** | 未明令禁止，但受反不正当竞争风险约束 | 平台政策与 DSA 双重约束 | Google Play 政策（GP1）实质封堵 |

**关键观察**：中国的规制最具体但罚则最轻，欧盟最严但抽象，美国最不稳定。三方都在从「管广告内容」转向「管界面设计」——这正是 dark patterns 成为共同落点的原因。

---

## 5. 去重记录

同一事实被多源报道时，台账只保留一条，此处记录合并关系，避免后续重复收录。

| 合并项 | 原始多条来源 | 保留 ID | 说明 |
| --- | --- | --- | --- |
| 武汉大学学生诉美图秀秀 | 上观新闻、嘉兴举报中心、四平信用网、多家百家号 | **J1** | 判决内容（35°/3s、赔 1 元）多源一致，取信息最全的上观新闻为主源 |
| 李跳跳停更 | 百度百科、光明网、腾讯云社区、IT之家转述 | **J3** | 均为 2023-08-24 同一事件，取光明网（主流媒体）+ 百科交叉 |
| 摇一摇触发灵敏度与覆盖率 | 百度百科「摇一摇」「开屏广告」两条目 | **S8 + S9** | 同一次 2023 监测的两个指标，合并为一组共用来源 |
| 开屏广告治理时间线 | 百度百科「摇一摇」条目内 2025-11 至 2026-09 各节点 | **R3–R8** | 同一条目连续叙述，保留为时间线但整体标注单一来源 |
| 100 款 App 实测 | smzdm 文章与其转载版本 | **S6** | 仅保留原始实测文章 |
| 弹窗一键关闭条款 | 央广网、中国消费网、太原市监局提示 | **L2** | 太原市监局为地方执行提示（L7），条款本身归 L2 |
| 全球拦截用户数 | SignalBridge、The Measure、Web Tonic、TechRT 等多篇 2026 汇总 | **GS1–GS5** | 全部溯源到同一份 eyeo《2026 Ad-Blocking Report》，取 SignalBridge 的结构化汇总为主源 |
| 美国 click-to-cancel 撤销 | Sidley Austin、Holland & Knight、Guardian、CNBC | **GR3** | 均为 2025-07 同一判决，取律所分析（Sidley）+ 主流媒体（Guardian）交叉 |
| dark patterns 95% 普及率 | arXiv 论文、多篇二手文章 | **GA1** | 均引用同一原始研究，标注为「转引自 arXiv」 |
| 欧盟 DSA 罚则 | 多家中文媒体转述 | **GL2** | 均指向 DSA Art. 74，须核原文 |

---

## 6. 冲突与存疑记录

| # | 冲突 / 存疑 | 处置 |
| --- | --- | --- |
| C1 | 李跳跳被广泛描述为「败诉」「被判不正当竞争」，实际为律师函后主动停更，**无生效判决** | 以 J3 为准；引用时必须区分律师函与判决 |
| C2 | 「会员仍看广告」的实测（澎湃 2025-08：五款视频 App VIP 后仍有开屏/贴片；爱奇艺「基础会员」标注「不享受广告特权」）**未进入 S 系列**——检索只拿到摘要，未取到完整实测方法 | 列为待补证据（§7 G2），方向 3 暂用 E5 + 定性描述，不引用具体百分比 |
| C3 | 「监管专门查个性化广告关不掉」仅有标题级信息，未取到通报文号与日期 | 标 `待核`，方向 4 的主支撑改用 L6（法规原文）+ GL4（GDPR） |
| C4 | M1 与 M4 的市场规模数值差异（8362.6 亿 vs 13574.3 亿） | 属统计口径差异，非数据冲突；引用时必须写明口径 |
| C5 | S12/S13 两个流传甚广的数字（0.7%/px、留存 −4pp）无原始出处 | 标 **D 级不可引用**，保留为「已知污染项」 |
| C6 | S8（34%）、S9（0.1°–15°）的监测方不明 | 标 `待核`；引用写「据 2023 年监测」，不写具体机构 |
| C7 | V2（iOS 27.2「Restrict Motion Data」仅中国区）为二手报道，未核 Apple 官方说明 | 标 `待核`；产品文档引用前须查 Apple Developer Release Notes |
| C8 | R8（2026-09-22 通报 26 款）与官网可查的 R9（2026-10-15，第 6 批，17 款）**不是同一批次**；R8 的批次号未核实 | R9 为 A 级锚点；R8 标 `待核`，需到工信部官网按日期反查 |
| C9 | GS1（11 亿用户）与 GS3（29.5% 人口）看似矛盾 | 非矛盾：前者是绝对用户数（eyeo 口径），后者是人口比例（GWI 口径），分母与定义均不同。引用时必须成对说明 |
| C10 | GA1（95% App 含 dark pattern）为转引，原始研究未定位 | 标 `待核`；引用须写「据一项覆盖 240 款 App 的研究，转引自 arXiv」 |
| C11 | GP1（Google Play 2026-01-28 禁止 Accessibility API 自动化操作）仅见中文二手转述 | **最高优先级待核项**（§7 G8）；该条若成立，直接决定出海可行性 |

---

## 7. 数据缺口清单

按对结论的影响程度排序。

| # | 缺口 | 影响 | 建议获取方式 | 状态 |
| --- | --- | --- | --- | --- |
| **G8** | Google Play Accessibility API 政策原文（GP1） | **决定跳广告类工具能否出海**；目前仅二手转述 | 检索 support.google.com/googleplay/android-developer 的 Accessibility API 政策页与政策更新日志 | **新增·最高优先** |
| **G1** | 工信部各批次通报的原文与文号 | 监管时间线此前依赖单一二手来源 | 工信部官网「信息通信管理局 → 工作动态」按批次检索 | **部分解决**（R9 已取到原文；R5/R8 待核） |
| **G2** | 澎湃 2025-08 视频 App 会员实测的完整方法与逐项结果 | 方向 3（强制付费）缺一手量化支撑 | 抓取原文全文 | 未解决 |
| **G3** | 各厂商系统级拦截能力的官方说明（V 系列） | 判断「系统已在解决」还是「仍需第三方工具」 | 各厂商开发者文档 / 系统更新日志 | 未解决 |
| **G4** | 广告 SDK 对耗电、流量、低端机帧率的影响量化 | 方向 6 目前只有定性描述与论坛调研 | 自测或引用第三方性能测试报告 | 未解决 |
| **G5** | 跳广告类工具的总装机量 / 活跃度 | 判断赛道真实体量 | 应用商店榜单、开源项目 star/下载量、GKD 发布数据 | 未解决（GS1 只覆盖浏览器类拦截器） |
| **G6** | 中消协 2024–2026 针对 App 广告的专项报告原文 | 国内数据目前停在 2021/2022 年 | 中消协官网报告库 | 未解决 |
| **G7** | 老年群体误触扣费的可核案例数量 | 方向 2 的弱势群体加重情节缺量化 | 黑猫投诉按关键词统计、法院公开案例 | 未解决 |
| **G9** | DSA 第 25 条与第 74 条的官方原文（EUR-Lex） | GL1/GL2 目前依赖媒体引述 | 检索 EUR-Lex Regulation (EU) 2022/2065 | **新增** |
| **G10** | 欧盟 DFA 的正式提案文本与时间表 | GL5 目前只有咨询结果与工作计划 | 欧盟委员会官网 Have Your Say / 2026 工作计划原文 | **新增** |
| **G11** | 日本、韩国、印度、东南亚的广告监管现状 | 国际部分目前只覆盖欧盟+美国+澳新 | 各国消费者保护机构官网 | **新增**（扩展覆盖范围用） |

---

## 8. 覆盖范围与后续补充方向

### 8.1 已覆盖范围

| 维度 | 中国大陆 | 欧盟 | 美国 | 其他 |
| --- | --- | --- | --- | --- |
| 法规原文 | ✅ L1/L4/L6/L8（A 级 4 条） | ⚠️ GL1/GL2 为媒体引述 | ⚠️ GL6/GL7 为二手 | ⚠️ GL8 英国、GL9 澳大利亚（有原始 PDF） |
| 监管执法 | ✅ R9（官网原文）+ R1–R8/R10（二手） | ✅ GR1/GR2 | ✅ GR3/GR4/GR5/GR6 | — |
| 量化数据 | ✅ S1–S11（B/C 级） | — | ✅ GS6（Gartner） | ✅ GS1–GS5、GS7–GS13（全球口径） |
| 学术研究 | ❌ 无 | ✅ GA2/GA4 | — | ✅ GA1/GA3/GA5/GA6 |
| 平台政策 | — | — | ✅ GP1（Google Play，待核） | ✅ GP3（浏览器厂商） |
| 司法判例 | ✅ J1（B 级） | ❌ 无 | ❌ 无 | — |

**结论**：国内侧法规与数据最扎实；国际侧量化数据与学术研究较强，但**法规原文多为二手引述**，这是当前最大的结构性弱点。

### 8.2 后续补充方向（按优先级）

1. **先补 A 级原文缺口**（G8、G9、G10）：这三项决定了国际部分能否从「据媒体报道」升级为「据法规原文」。其中 G8 优先级最高，因为它直接影响产品可行性判断。
2. **再补国内量化缺口**（G2、G6、G7）：国内数据目前最新的是 2026-09 的实测（S6）和 2023 年的调查（S1–S4、S10），中间两年是空的。
3. **然后扩展地理覆盖**（G11）：目前「国际」实际等于「欧美 + 澳」，日本、韩国、印度、东南亚均未覆盖，而这些市场的广告形态与监管差异很大（GS5 显示印尼拦截率 40.1%，远高于欧美）。
4. **最后补赛道体量**（G5）：现有数据（GS1 的 11 亿）是浏览器类拦截器口径，不能直接推断「跳广告 App」的规模，两者是完全不同的分发路径。

### 8.3 不建议继续投入的方向

| 方向 | 理由 |
| --- | --- |
| 继续找「关闭按钮缩小 1 像素」类数字的原始出处 | 已判定为无源转引（S12/S13），继续追查的时间成本高于收益，标 D 级即可 |
| 收集更多「用户很生气」的定性报道 | 边际信息量已接近零；S1–S4 与 GS6 已经充分刻画了情绪强度 |
| 逐一核实每个厂商 ROM 的广告开关路径 | 变化太快（V3 已降级为 D），且对结论无影响；真正需要的是「系统级能力是否足以替代第三方工具」这个判断（G3） |

---

## 9. 完整来源清单

按首次出现顺序排列，便于逐条核对。

**国内·法规与政府（A 级）**

1. 《互联网广告管理办法》— https://www.gov.cn/zhengce/202305/content_6858084.htm
2. 工信部《关于进一步提升移动互联网应用服务能力的通知》— https://gsca.miit.gov.cn/zwgk/zcwj/wjfb/art/2023/art_7aa9c3f277a14a6ead1bd397871e782f.html
3. 《中华人民共和国个人信息保护法》— https://www.cac.gov.cn/2021-08/20/c_1631050028355286.htm
4. 《互联网弹窗信息推送服务管理规定》— https://www.gov.cn/zhengce/zhengceku/2022-09/09/content_5709179.htm
5. 太原市市场监督管理局规范商业广告宣传提示（2026-05-29）— https://scjgj.taiyuan.gov.cn/gggs/20260529/30301137.html
6. 工信部《关于侵害用户权益行为的 APP(SDK) 通报（2026 年第 6 批，总第 59 批）》— https://www.miit.gov.cn/jgsj/xgj/gzdt/art/2026/art_a2e3677caf8a4d70a892b75c4d7e0f49.html
7. 工信部《关于开展移动互联网应用程序备案工作的通知》（背景参考）— https://www.miit.gov.cn/zwgk/zcwj/wjfb/tz/art/2023/art_920db564162e4312916a01bed6540ad8.html

**国内·主流媒体（B 级）**

8. 人民网：92.7% 受访者感到困扰 — http://yn.people.com.cn/GB/n2/2021/0722/c372455-34831930.html
9. 上观新闻：武大学生起诉美图秀秀获赔 1 元 — https://export.shobserver.com/toutiao/html/606650.html
10. 光明网：「李跳跳」违法了吗 — https://m.gmw.cn/2023-08/30/content_1303498686.htm
11. 新京报：超七成受访老人希望减少广告 — https://www.bjnews.com.cn/detail/167893650514524.html
12. 央广网：互联网广告管理新规（弹窗条款与罚则）— https://www.cnr.cn/tech/techyw/kan/20230325/t20230325_526195124.shtml
13. 央视网：中消协「广告一键关闭」表态 — https://news.cctv.com/2024/11/01/ARTID009M4r25mZJGIRnE7wb241101.shtml
14. 澎湃新闻：QuestMobile 2025 互联网广告半年报告 — https://m.thepaper.cn/newsDetail_forward_31429458

**国内·行业与第三方（C 级）**

15. 艾媒网：弹窗广告影响使用（59.21%）— https://www.iimedia.cn/c1061/92091.html
16. 艾媒网：电视机开屏广告接受度 — https://www.iimedia.cn/c1061/102052.html
17. 什么值得买：实测 100 款 App，76 款有开屏广告 — https://post.m.smzdm.com/p/ad796lpd/
18. 百度百科「摇一摇」— https://baike.baidu.com/item/摇一摇/64541382
19. 百度百科「开屏广告」— https://baike.baidu.com/item/开屏广告/63379291
20. 百度百科「黑暗模式」— https://baike.baidu.com/item/黑暗模式/15641845
21. 百度百科「侵害用户权益行为的 APP(SDK) 通报」— https://baike.baidu.com/item/侵害用户权益行为的APP(SDK)通报/67514837
22. 中国消费网：APP 弹窗广告不能成为电子牛皮癣 — https://www.ccn.com.cn/Content/2025/09-11/1425494142.html
23. 中国消费网：中消协 50 款 APP 账号注销与自动化推荐退订测评 — https://www.ccn.com.cn/Content/2021/12-15/1606200914.html
24. 三个皮匠：互联网广告统计口径 — https://www.sgpjbg.com/searchtag/27318090.html
25. 中国技术监督情报协会：2026 年个人信息保护系列专项行动 — https://catsi.cn/view-41-bc12bcf264f042c78f3447d39b484f9f.html

**国际·法规与监管（A/B 级）**

26. Irish Examiner：Coimisiún na Meán 就 Meta dark patterns 启动调查（含 DSA Art.25(1) 原文）— https://www.irishexaminer.com/business/companies/arid-41839641.html
27. FTC 报告：dark patterns 上升（2022-09-16）— https://www.ftc.gov/news-events/news/press-releases/2022/09/ftc-report-shows-rise-sophisticated-dark-patterns-designed-trick-trap-consumers
28. Sidley Austin：FTC click-to-cancel 规则被撤销 — https://www.sidley.com/zh-hans/insights/newsupdates/2025/07/us-ftc-click-to-cancel-rule-struck-down
29. Holland & Knight：FTC 加强订阅执法 — https://www.hklaw.com/en/insights/publications/2025/09/ftc-steps-up-subscription-enforcement-after-click-to-cancel-rule
30. CPRC《Duped by Design》（2022-06）— http://cprc.org.au/wp-content/uploads/2022/06/CPRC-Duped-by-Design-Final-Report-June-2022.pdf
31. Digital Fairness Act 追踪站 — https://digitalfairnessact.com/
32. Freshfields：DFA 对平台的影响分析 — https://www.freshfields.com/en/our-thinking/blogs/technology-quotient/the-eus-proposed-digital-fairness-act-an-online-platforms-guide-to-potential-i-102m1e4
33. 欧盟 DSA 官方页面 — https://digital-strategy.ec.europa.eu/en/policies/digital-services-act-package

**国际·量化数据（C 级）**

34. SignalBridge：Ad Blocker Usage Statistics 2026（eyeo/GWI 数据汇总）— https://www.signalbridgedata.com/blog/ad-blocker-statistics-2026
35. Web Tonic：Ad Blocking Trends（DeBlocker/eMarketer 数据）— https://www.webtonic.io/blog/ad-blocking-statistics
36. The Measure：Ad Blocking Tops 1 Billion Users（含 Gartner 81%/52%）— https://www.themeasure.net/ad-blocking-tops-1-billion-users-as-browsers-give-consumers-more-control
37. TechRT：Ad Blocker Usage Statistics 2026 — https://techrt.com/ad-blocker-usage-statistics/
38. Pmarketresearch：Worldwide Ad Blocker Market 2026 — https://pmarketresearch.com/worldwide-ad-blocker-market-research

**国际·学术（A/B 级）**

39. USENIX Security 2024「ATTention Please!」— https://www.usenix.org/system/files/usenixsecurity24-mohamed.pdf
40. arXiv 2310.17846（引用 95% 普及率研究）— https://arxiv.org/pdf/2310.17846v2.pdf
41. Harvard JOLT：EU 同意规制与用户研究的差距 — https://jolt.law.harvard.edu/assets/articlePDFs/v37/Symposium-12-Bielova-Santos-Gray-Two-Worlds-Apart-Closing-the-Gap-Between-Regulating-EU-Consent-and-User-Studies.pdf
42. IOSCO：零售投资中的 dark patterns（2024-02）— https://www.iosco.org/library/pubdocs/pdf/IOSCOPD794.pdf
43. HKU：Mark Leiser 专著书谈 — https://www.law.hku.hk/events/book-talk-dark-patterns-deceptive-design-and-the-law/

**待核线索（D 级，不作论据）**

44. 百度知道：广告为何招人嫌（含 S12/S13 两个无源数字）— https://zhidao.baidu.com/question/566979177509589092.html
45. 腾讯云社区：关闭手机广告攻略（E3/E4 工具线索）— https://cloud.tencent.com/developer/article/2665278
46. CSDN：Google Play Accessibility API 政策更新（GP1 线索）— https://download.csdn.net/blog/column/12512885/154987895

---

## 10. 维护约定

- 新增证据按现有分类追加，编号不复用、不重排；作废条目保留并标 `已废止`，不删除。
- 每条证据必须能追到 §9 的来源清单；无法追溯的不得进入台账，只能进「待核线索」。
- 引用本文件时同时给出 ID 与来源，例如「S1（人民网，2021-07）」「GS6（Gartner 2026）」「GA2（USENIX Security 2024）」。
- 国内与国际分区维护，**同一事实若两地都有报道，优先保留一手来源所在区域**，另一区域只做交叉引用，不重复收录。
- 本文件与 [PLAN-CLIENT-REFORM.md](PLAN-CLIENT-REFORM.md) 的分工：本文只收录事实，改造决策与优先级排序归该文档，两者不互相复制。
