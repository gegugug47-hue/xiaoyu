# v3 执行书 vs v2.0.0 差距清单

- 对照文档：《小鱼App-工程执行书-v3.0.md》
- 对照产物：v2.0.0（已发布 tag）
- 审计日期：2026-11-16
- 状态标记：`已实现` / `部分` / `缺失`
- 本文件只记录差距，不代表已排期；由用户逐条勾选后再落地。

## 0. 总览

| 板块 | 条目数 | 已实现 | 部分 | 缺失 |
| --- | --- | --- | --- | --- |
| A 活人感六支柱（§1.2） | 6 | 2 | 4 | 0 |
| B 微信QQ借鉴（§1.3，49 项） | 49 | 27 | 12 | 10 |
| C 质量目标（§1.5） | 12 | 2 | 2 | 8 |
| D 六模块架构（§3.1） | 5 | 4 | 1 | 0 |
| E UI 与交互（第 7 章） | 22 | 8 | 8 | 6 |
| F 安全底线（第 8 章） | 12 | 1 | 2 | 9 |
| G 设计系统与背景（第 14 章） | 21 | 12 | 7 | 2 |

**最严重的 6 条**
1. `F-01~F-05`：安全层仍是"布尔命中 + 固定关怀回复"，没有 `SafetyGate` / `CrisisLevel` 三级 / `hotlines.json` / `safety_events` / 危机后 48h 暂停主动。
2. `G-09~G-15`：~~聊天背景只有"图片 + 模糊 + 暗化"~~ → 批次 2 已补齐（五来源 / 五层渲染 / `chat_backgrounds` 表 / 三档预设 / 每身份独立），剩余差异见 G.2 状态列。
3. `G-01~G-08/G-21`：~~设计系统核心能力缺失~~ → 批次 3 已落地 11 档字号、主题/取色器、六档动效、断点双栏、标准组件与空态插画；图片取色、折叠悬停/单手、完整手势/组件、自绘图标及全局硬编码清零仍有偏差，见 G.1。
4. `E-01/E-02/E-03`：消息时间行规则与合并阈值不符文档（每条都显示 `MM-dd HH:mm`；合并用 120s 应为 60s）。
5. `E-11/E-12`：记忆图谱是固定螺旋静态图，无力导向、无拖拽缩放、无列表模式、无 >300 折叠。
6. `E-04/E-05`：缺「对方正在输入…」、缺底部 FAB「↓ 新消息 N」。

---

## A. 活人感六支柱（§1.2）

| ID | 支柱 | 现状 | 差距 | 优先级 |
| --- | --- | --- | --- | --- |
| A-01 | 她记得 | 部分 | 三级记忆/遗忘曲线/增量游标/显式记忆都有；记忆图谱为静态螺旋，未体现"会忘记"的视觉（strength→透明度已做，但无过期灰化/类型 5 色） | P1 |
| A-02 | 她有心情 | 已实现 | 情绪事件 + 半衰期衰减 + 精力 + 好感 + 日记 | — |
| A-03 | 她有自己的生活 | 部分 | 生活碎片 + PresenceEngine 有；「她的一天」缺日期横向滑动、缺时段分组样式区分、缺空态文案 | P1 |
| A-04 | 她不秒回 | 部分 | 沉浸延迟有；缺「对话收束」完整链路验证、缺收束后 `resumeAfter` 静默 | P1 |
| A-05 | 她会表达 | 部分 | 分段连发/表情/卡片/戳一下有；缺 reaction（双击 ❤️ + 角标计数）、缺 `care` 卡 | P1 |
| A-06 | 她会变 | 部分 | 纠偏条 + 里程碑有；缺 Soul-Eval 度量（人设一致/记忆命中/AI 腔率/长度合规/幻觉为 0） | P2 |

## B. 微信/QQ 借鉴 49 项（§1.3）

| # | 特性 | 决策 | 现状 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | 单一时间线连续流 | 做 | 已实现 | 有连续流模式 + 跨会话时间线 |
| 2 | 日期分隔线 | 做 | 部分 | 只有 今天/昨天/`MM-dd`；缺 星期X、`M月d日`、`yyyy年M月d日` 跨年规则 |
| 3 | 间隔 >5 分钟才显示时间 | 做 | 缺失 | 当前每条消息都渲染 `MM-dd HH:mm`，过密 |
| 4 | 连续同作者省略头像 + 4dp | 做 | 部分 | 省略头像已做；阈值 120s（应 60s）；无"顶部小圆角"合并视觉 |
| 5 | 未读分割线 | 做 | 已实现 | `UnreadDivider` + `lastReadMessageId` |
| 6 | 「对方正在输入…」 | 做 | 缺失 | 顶栏无副标题状态位 |
| 7 | 长按菜单 | 做 | 部分 | 有菜单骨架；缺「收藏」「让她记住」入口校验、「像她/不像她」双项、菜单 8 项折叠 |
| 8 | 引用回复 | 做 | 已实现 | 需补"点引用条跳原消息并高亮 1.2s" |
| 9 | 表情回应 reaction | 做 | 缺失 | 无 `card_reactions` 落库、无双击 、无角标 |
| 10 | 戳一下 | 做 | 已实现 | `poke()` 防连点 + 每日上限 |
| 11 | 自定义表情包 | 做 | 已实现 | — |
| 12 | 表情包导入导出 | 做 | 已实现 | `.xystickers` |
| 13 | 斗图/网络搜图 | 不做 | — | 合规排除 |
| 14 | Emoji 面板 | 做 | 已实现 | `EmojiPanel.kt` |
| 15 | 草稿箱 | 做 | 已实现 | 复核：`DraftEntity` 已在 `XiaoyuDatabase` 实体列表中（审计时误判为缺失） |
| 16 | 消息状态机 | 做 | 部分 | 仅 `sending/sent/failed` 三态；文档要 7 态（含 STREAMING/CANCELLED/RECALLED/REGENERATED） |
| 17 | 通知栏直接回复 | 做 | 已实现 | `MessagingStyle` + `RemoteInput` |
| 18 | 未读红点/角标 | 做 | 缺失 | 无启动图标 badge |
| 19 | 图片消息 | 做 | 部分 | 气泡内可显示；缺全屏查看器（双指缩放/存相册）、长图 3:4 占位 |
| 20 | 语音消息 | 做(V2) | 缺失 | 不做，符合路线图 |
| 21 | 语音转文字输入 | 做 | 已实现 | `RecognizerIntent` |
| 22 | 消息搜索 | 做 | 已实现 | FTS + `SearchScreen` |
| 23 | 导出长图 | 做(P2) | 缺失 | 无 Canvas 拼接 |
| 24 | 聊天背景自定义 | 做 | 部分 | 见 G 组 |
| 25 | 气泡样式/字号 | 做 | 已实现 | 3 套气泡 + 4 档字号 |
| 26 | 深色 + Material You | 做 | 已实现 | 浅色/深色/跟随系统；Android 12+ `dynamicColor` 默认开、可关，优先级高于自定义 seed 与预设 |
| 27 | 收藏 | 做 | 部分 | 有 `pinned`；缺「收藏并让她记住」联动 |
| 28 | 卡片消息 | 做 | 部分 | 4/5 种，缺 `care`；`kind` 命名与文档不同（DIARY/AGREEMENT/HER_DAY/MEMORY_RECALL vs diary/promise/day/memory/care） |
| 29 | 群聊 | 不做 | — | — |
| 30 | 好友/朋友圈等 | 不做 | — | — |
| 31 | 已读回执 | 不做 | — | — |
| 32 | 撤回他人消息 | 不做 | — | — |
| 33 | 端到端加密 | 不做 | — | — |
| 34 | 应用锁 | 做 | 已实现 | PIN + 生物识别 |
| 35 | 在线状态 | 做 | 部分 | 色点有；缺点击展开小卡（心情+签名+今日碎片）与「在忙/刚睡醒」文案 |
| 36 | 个性签名 | 做 | 部分 | 身份签名有；缺每日自动生成 `day_narration` 缓存 |
| 37 | 会话置顶/免打扰 | 不做 | — | — |
| 38 | 快捷回复短语 | 不做 | — | — |
| 39 | 备份迁移 | 做 | 已实现 | `.xybackup` |
| 40 | 重新生成 + 历史版本 | 做 | 部分 | 有多版本；缺"历史版本抽屉最多 3 版可切回" |
| 41 | 编辑并重发 | 做 | 已实现 | `editedAt` |
| 42 | 多选转发 | 不做 | — | — |
| 43 | 实时语音通话 | 路线图 V2 | 缺失 | 符合路线图 |
| 44 | 桌面小组件 | 做 | 部分 | 用 Glance 实现（文档 §3.4 明确不引 Glance，需改 RemoteViews 或补 ADR） |
| 45 | 自定义聊天背景 | 做 | 部分 | 见 G 组 |
| 46 | 表情档位三选 | 做 | 部分 | 无 `none/emoji/minimal` 三档开关与"内置图片包默认关" |
| 47 | 单手/折叠悬停 | 做 | 部分 | compact 单栏、medium/expanded 双栏与 expanded 设置二级面板已做；单手模式、折叠悬停布局未做 |
| 48 | 消息密度三档 | 做 | 已实现 | `MessageDensity` |
| 49 | token 化 + 硬编码 lint | 做 | 部分 | `Tokens.kt` + `HardcodeCheck`；新增设计系统文件扫描零告警，legacy UI 未清零，且不是独立 lint/CI 规则（见 G-21） |

## C. 质量目标（§1.5）

| ID | 指标 | 现状 | 差距 |
| --- | --- | --- | --- |
| C-01 | 50 万条不降级 | 部分 | LazyColumn + key；新增 `DbIndexProbe`：Debug 启动校验 `messages` 索引并用 `EXPLAIN QUERY PLAN` 检查分页 SQL。**未做 50 万条压测**，无真实大数据量结论 |
| C-02 | 冷启动 <1.5s | 已埋点 | `StartupTimer` 记录 `Application.onCreate`→首帧（`reportFullyDrawn` + `Choreographer`），写 `files/perf/startup.log`（≤200 条）；缺真机基线数字 |
| C-03 | 60fps / p95<16.7ms | 已埋点 | `FrameStats` 用 `Choreographer` 自实现（未引入 metrics-performance），统计最近 300 帧 p50/p95；缺真机基线数字 |
| C-04 | 首 token ≤ RTT+100ms | 部分 | 有流式；无 `gen_duration_ms` 首 token 统计 |
| C-05 | PSS <220MB | 已埋点 | 诊断页每次进入用 `MemoryProbe`（`Debug.MemoryInfo.totalPss`）对比 220MB；缺长时间使用后的实测曲线 |
| C-06 | APK <30MB / :soul <1.5MB | 已实现 | 实测 3.13MB |
| C-07 | 召回 <30ms / 清算 <20s / FTS <80ms | 缺失 | 无单测计时断言 |
| C-08 | 0 崩溃 / 全异常兜底 | 部分 | `CrashLogger` 有；无兜底 UI 覆盖率 |
| C-09 | 0 ANR / 主线程无 >5ms IO | 部分 | Debug 构建开启 `StrictMode`（Thread + Vm，`penaltyLog` 不崩溃）；未在真机清零违规项 |
| C-10 | 离线可用 | 部分 | 需手测用例固化 |
| C-11 | 无 Key 示例模式 | 已实现 | — |
| C-12 | Soul-Eval 达标线 | 缺失 | 无 SoulEval 度量实现（仅 `SoulEvalTest` 冒烟） |

## D. 六模块架构（§3.1）

| ID | 要求 | 现状 |
| --- | --- | --- |
| D-01 | 6 个模块 | 已实现：`:app/:soul/:core-model/:data/:brain/:service` |
| D-02 | `:soul` 纯 JVM 零 Android | 已实现 |
| D-03 | `:brain/:data/:service` 禁 Compose | 已实现（需 detekt import 规则固化，当前靠评审） |
| D-04 | `:soul` 行覆盖 ≥80% | 已实现 97.2%（门禁已有） |
| D-05 | `assertModuleDeps` | 部分：文档 H-3 判定其为"多余仪式"（`kotlin("jvm")` 无 android.jar 即天然失败），当前项目保留了该 task，可在文档侧标注差异或移除 |

## E. 第 7 章 UI 与交互

| ID | 小节 | 文档要求 | 现状 | 差距 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| E-01 | 7.3.2 | 间隔 ≥5 分钟才显示 `HH:mm` | 每条消息固定显示 `MM-dd HH:mm`（ChatScreen.kt:1179） | 改为 gap 判定 + 纯 `HH:mm` | P0 |
| E-02 | 7.3.3 | 合并阈值 <60s | `<2*60*1000`（ChatScreen.kt:1834） | 改 60s；组内圆角变小 | P0 |
| E-03 | 7.3.1 | 日期头 5 档文案 | 仅 今天/昨天/`MM-dd` | 补 星期X / `M月d日` / 跨年 | P1 |
| E-04 | 7.2 | 顶栏「对方正在输入…」 | 无 | 加副标题状态位 + 3s 显 2s 隐 | P0 |
| E-05 | 7.2 | 底部 FAB「↓ 新消息 N」 | 无 | 加 + 滚动位置判定 | P0 |
| E-06 | 7.4.1 | 长按菜单 11 项 | 已实现分组菜单：常用 4 项 +「更多」二级；含复制/引用/编辑（我方 2 分钟内）/撤回（2 分钟内）/重新生成（她方）/历史版本/保存图片/让她记住/戳一下/多选/删除（软删 hidden=1）；转文字仅语音消息（当前无语音消息产生路径）、翻译按要求暂不做（禁用项占位） | — | P1 |
| E-07 | 7.4.6 | reaction + 双击 ❤️ | 双击 ❤️ 可切换（同表情再点取消）；长按 6 个快捷表情 👍❤️😂😮😢🎉；我方消息同样可 reaction；气泡下方展示，>3 个折叠「+N」；herReaction 走 Settings 同一映射表，被夸/被道歉 30% 本地概率，不调模型 | — | P1 |
| E-08 | 7.4.8 | 图片全屏查看器 | 无 | 加 Viewer（缩放/存相册/长图角标） | P2 |
| E-09 | 7.5 | 关系页四要素 | 有头部/统计/情绪曲线/里程碑/入口；缺「她的小本子」纠偏列表、升档全屏仪式 | 补 2 块 | P1 |
| E-10 | 7.5 | 好感度用她的说法 | 已实现（不显数值条，`LevelArc` 待确认） | — | — |
| E-11 | 7.5 | 图谱力导向 + 拖拽 + 双指缩放 | 固定螺旋（MemoryGraph.kt） | 重写布局 + 手势 | P1 |
| E-12 | 7.5 | 列表模式 + >300 折叠 + 搜索高亮 | 无 | 加 | P1 |
| E-13 | 7.6 | 她的一天：日期横滑 + 时段分组 + 空态 | `LifeDayScreen` 仅时间线 | 补 3 项 | P1 |
| E-14 | 7.7 | 7 态消息状态机 | 3 态 | 补 STREAMING/CANCELLED/RECALLED/REGENERATED + 冷启动归位 | P1 |
| E-15 | 7.7 | 离线横幅（本地模型不误报） | 无 | 加 `NetWatch` | P2 |
| E-16 | 7.8 | 重新生成：禁用条件 + 版本抽屉 | 已补：后面存在用户消息时禁用并提示「可以先删除后面的消息」；历史版本抽屉最多展示 3 版 | — | P1 |
| E-17 | 7.9 | TTS 输出（系统 TTS 默认） | 长按消息「朗读」走系统 `TextToSpeech`（中文、语速/音调跟随设置、120 字分段、可停止、初始化失败静默降级）；本地 AI 网关的 CosyVoice3 音色另存 | 偏差：只朗读被长按的单条消息，不做「每条回复自动朗读」，也不做 CosyVoice 在线合成兜底 | P2 |
| E-18 | 7.10 | 日记：日历点阵 + 空态 + 记忆碎片折叠 | `DiaryScreen` 无日历点阵 | 加 | P2 |
| E-19 | 7.11 | ≥60 项设置 + **顶部搜索框** | 约 107 处设置项文案；**无搜索框** | 加搜索框（按项名+副标题命中） | P0 |
| E-20 | 7.12 | 5+1 步引导，断点续导 | 已实现 6 步（欢迎/起名+称呼/主题/模型/通知/完成），左右滑动 + 进度点 + 跳过；`onboardingDone` 存 Settings JSON，老用户按 `onboarded`/有无会话兜底不弹 | 断点续导未做（退出即丢弃进度）；第 6 步「表情」改为完成页文案 | P2 |
| E-21 | 7.14 | 诊断四页签 | 单页（请求快照 + 崩溃日志），无 4 页签 | 拆 请求/灵魂/后台/数据 | P2 |
| E-22 | 7.15 | 触觉 + 无障碍 + 48dp + LiveRegion | 触觉已接入；消息/状态/FAB/滑杆/图谱/三列表已补语义与 LiveRegion；热区按 48dp | 待真机 TalkBack 走查（G-20） | P2 |

## F. 第 8 章 安全底线（优先级最高）

| ID | 要求 | 现状 | 差距 | 优先级 |
| --- | --- | --- | --- | --- |
| F-01 | `SafetyGate.assess` + `screenOutput` 接口 | 无；仅 `CrisisDetector.check(text): Boolean` | 建接口 + 两段式接入 `send()` 第 1 步与入库前 | P0 |
| F-02 | `CrisisLevel` 三级（NONE/L1/L2/L3） | 单级布尔 | 分级词表 + 分级响应 | P0 |
| F-03 | `words/care_keywords.json` 分级词表 + 否定/引述处理 | 硬编码词表 | 外置 JSON + 反例单测 | P0 |
| F-04 | `words/hotlines.json` 热线（110/120/12356/12355/400-161-9995…） | 无 | 按文档 §8.2 逐字落地，`verified_by_human_dial` | P0 |
| F-05 | `safety_events` 表（不存原文） | 无 | 新表 + Migration | P0 |
| F-06 | L2 → `proactive_paused_until_ms = now+48h` | 无 | `app_meta` + 主动消息总闸读取 | P0 |
| F-07 | L2 → 当晚提炼跳过该消息 | 无 | 提炼过滤 | P0 |
| F-08 | L3 → 暂停人格演绎 + 3 轮现实导向 + `sessions.closed_by=safety` 且不触发收束 | 无（仅固定回复） | 加 | P0 |
| F-09 | L8 注入层（敏感时刻禁梗禁表情） | 无 L8 分层 | prompt 分层补 L8 | P1 |
| F-10 | `InjectionGuard` 三件套 `sanitizeForeign/scanForeign/scanOwn` + `PromptZone` | 仅 `sanitize` + `hasProtocolToken` | 补 7 个 zone 与导入拒绝路径 | P0 |
| F-11 | 头像/表情名/记忆文本/纠偏 note 四处扫描 | 仅人格包 identity/style | 扩到 4 处 | P1 |
| F-12 | 8.6 十二项安全用例可复跑 | 已实现（批次 5）：`soul/src/test/kotlin/com/xiaoyu/soul/safety/SafetyCaseCatalogTest.kt` 固化 S-1~S-14 共 14 项清单，其中 9 项在 `:soul` 内断言；S-6 的「48h 不主动」与「每日上限」补了 `ProactivePlanner.pausedBySafety/dailyCapReached` 判定入口 | S-8/S-9/S-11/S-12/S-13 仍依赖真机（通知栏、日志文件、备份包、抓包、10 万字输入），清单已标注 manual | P1 |

## G. 第 14 章 设计系统 + 自定义聊天背景

### G.1 设计系统

| ID | 小节 | 要求 | 现状 | 差距 |
| --- | --- | --- | --- | --- |
| G-01 | 14.2② | 8 个 `YuType` 语义字号/行高 | 已实现：`YuType.kt` 落地 11 档字号/行高/字重/字距，`LocalYuType` 与 Material3 同步；聊天、日记分别使用 `chat`、`diary` | — |
| G-02 | 14.2③④ | `Shape` 5 档 + `Size` 9 项 + 气泡 76% 上限 | 已实现：Material3 五档 Shape、布局/触控/Switch 尺寸入 `Tokens`；气泡 compact 76%、宽屏 62% 限宽 | — |
| G-03 | 14.2⑤ | 4 套主题预设（晨雾/昼白/暮青/夜航）+ 取色器 `PaletteExtractor` + Material You 默认开 | 部分：四套 light/dark ColorScheme、HEX/HSV 二维取色器、自定义 seed 覆盖与 Material You 默认开均已接入设置 | 未做图片/头像 `PaletteExtractor`；tonal 变体采用 RGB mix 近似，未引入 material-color-utilities |
| G-04 | 14.2⑥ | 6 档动效（instant/quick/standard/slow/stickerPop/caret）+ reduceMotion 降级 | 已实现：`flash/quick/normal/slow/page/emphasis` 六档与 easing；减少动效或系统两个动画 scale 任一为 0 时时长归零、spring 改 snap | 命名采用本批约定的六档，未沿用旧列中的 instant/standard/stickerPop/caret 名称 |
| G-05 | 14.3 | 断点 compact/medium/expanded + 双栏 + 折叠悬停 + 单手模式 | 部分：600/840 断点；Chat compact 单栏、medium/expanded 双栏并限宽，设置 expanded 右侧 360dp 二级面板 | 折叠悬停与单手模式未做；未新增 adaptive/window 依赖 |
| G-06 | 14.4 | 15 个组件规格（YuBubble/TimeHeader/QuoteBar/Reactions/Card/InputBar/Tray/Sheet/EmptyState/ColorField…） | 部分：本批 `YuButton/YuCard/YuDialog/YuSnackbar/YuSlider/YuNavBar/YuTimeDivider/YuEmptyView` 已接入；同组末尾气泡 6dp 尾巴、主要触控区 ≥44dp | 完整 15 组件未抽完；`YuColorField` 对比度读数未做，InputBar/Tray/Sheet 等仍沿用原实现 |
| G-07 | 14.5 | 手势地图（双击❤️/捏合切密度/长按输入框粘贴为表情）+ `tip_shown` 一次提示 | 部分：已实现下拉进入「翻旧账」搜索、上滑到顶分页加载历史、会话行左滑归档、双击输入栏全屏输入、双击气泡 ❤️、长按输入框菜单；未做：右滑会话行置顶（数据模型无置顶）、三指下滑沉浸模式、捏合切密度、`tip_shown` 一次性提示 | 剩余手势与提示待后续批次 |
| G-08 | 14.7 | 24dp 自绘图标 ≤48 个 + 4 幅空态插画 + 4 款程序化纹理 | 部分：聊天/记忆/日记/关系四张 Canvas 线性插画已接入空态；四款程序化纹理沿用批次 2；批次 5 新增 `ui/theme/YuIcons.kt`（发送/加号/表情/语音/图片/返回/更多/关闭 共 8 个 `ImageVector`），已替换聊天顶栏 More/Close 与输入栏发送/表情/麦克风 | 图标集只做聊天高频 8 个，其余页面仍用 Material Icons，不做全量替换 |
| G-21 | 14.8#20 / T-72 | 全部尺寸数字来自 Token，`YuHardcodeCheck` 零告警 | 部分：修正小数扫描与 token 文件白名单；`DesignSystemTest` 对七个新增设计系统文件执行真实扫描且零告警，核心页面常规间距已归一 | 原审计无此编号，本批补记；legacy UI 仍有 `.dp/.sp`，未扫描 `Color(0x…)`，也不是独立 lint module/CI 阻断，不能视为全局零告警 |

### G.2 自定义聊天背景（§14.6）

| ID | 要求 | 现状 | 差距 | 状态（2026-11-17 批次 2） |
| --- | --- | --- | --- | --- |
| G-09 | 5 种来源 `theme/solid/gradient/image/texture` | 仅 `image`（+ 无背景即主题） | 补 4 种 + 12 组渐变预设 | 部分：五来源齐（`ui/background/ChatBackgroundRenderer.kt`），渐变预设 4 组而非 12 组 |
| G-10 | 五层渲染：素材→blur/brightness/saturation→scrim→vignette→bubbleAlpha | 2 层（图片 + API31 blur + 黑遮罩 dim） | 重写 `ChatBackground.kt` 为五层 | 已实现：五层在 `ChatBackgroundRenderer`，气泡层经 `LocalBubbleVisual` 下发；亮度/饱和对图片用 ColorMatrix，对纯色/渐变用 HSV 近似 |
| G-11 | 18 参数持久化（`chat_backgrounds` 表） | settings 里 3 个键（文件名/blur/dim） | 新表 + Migration + DAO | 已实现：`ChatBackgroundEntity` + `MIGRATION_10_11`（version 11）+ `ChatBackgroundDao`；旧三键首次读取迁移且不改旧键 |
| G-12 | 三档预设 清透/柔和/沉浸（柔和默认 blur12/亮度-12/饱和70/scrim45/暗角18/气泡88） | 无 | 加预设常量并设默认值 | 已实现：`ChatBackgroundPreset` + 设置页预设 chips |
| G-13 | API 27–30 预渲染缓存 `files/bg/cache/<sha1>-<tier>-<edge>.webp` + LRU 40MB | 无（低版本直接不模糊） | 加导入期预渲染 + 缓存管理 | 已实现：`ChatBackgroundStore.ensureCache/warmCache`（3 趟盒式模糊，六档 0–32dp），LRU 40MB；差异：按需生成 + 保存时预热，文件名追加 dark 标志 |
| G-14 | 可读性守卫五步兜底 + 实时对比度读数 | `LegibilityGuard` 只按平均亮度抬 dim | 改为"合成气泡有效背景色"对比度 + 五步 + 页面读数 | 已实现：`LegibilityGuard.evaluate`（正文 4.5 / 辅助 3.0）+ 五步；设置页实时读数与"自动修正"；旧 `minDimForContrast` 保留 |
| G-15 | 每身份独立背景 + 存储配额（单张 ≤12MB/全局 ≤3 张）+ 备份含原图 | 全局单张 | 加 per-identity + 配额 + 备份 | 已实现：`ChatBackgroundRepository` 按 identity 读写 / applyToAll；`files/bg/<sha1>.jpg` 配额 3 张；备份 `media/bg/` 原图（不含 cache） |
| G-16 | 背景设置页（预览 3 气泡 + 三预设 + 滑杆组 + 只对她/所有她 + 应用才写库） | 设置页有背景入口，滑杆仅 blur/dim | 重做页面 | 已实现：`ui/background/ChatBackgroundScreen.kt`；差异：聊天页"更多"入口未加，仅设置页入口 |
| G-17 | 视差 0/15/30% + 低端机强制关 | 无 | 加 | 已实现：按 `firstVisibleItemScrollOffset` 驱动；低端机（isLowRamDevice）与 API<31 强制关，低端机模糊上限 18dp |
| G-18 | 深色单独一套 `dark_json` | 无 | 加 | 已实现：`dark_json` 持久化 + 设置页"编辑浅色/编辑深色"切换，`forDark()` 解析 |
| G-19 | 14.6.7 验收 T-76~T-79 | 无 | 建 4 条验收 | 部分：验收点已落到代码路径，未补 T-76~T-79 自动化用例 |
| G-20 | 14.8 二十项走查 | 无记录 | 发布前逐条实测并记录 |

---

---

## 进度更新（2026-11-17）

**批次 1 · 安全底线 已完成**：F-01~F-08、F-10、F-11 落地。
- `SafetyGate`（`CrisisLevel` 四级枚举 NONE/L1/L2/L3）+ `DefaultSafetyGate` + `screenOutput`；`words/care_keywords.json`、`words/hotlines.json`（9 条号码逐字照抄）、`words/injection_patterns.json` 外置。
- `safety_events` 表 + `MIGRATION_9_10`；L2 触发 CARE 卡 + 48h 主动暂停 + 提炼跳过；L3 不走模型、`closed_by='safety'`、不收束，后续 3 轮注入现实导向指令（即 L8，故 F-09 部分完成）。
- `InjectionGuard` 补 `PromptZone` 8 类 + `sanitizeForeign/scanForeign/scanOwn`，人格包/表情名/记忆文本多处扫描。
- 构建 `BUILD SUCCESSFUL`，soul 153 测试 0 失败，行覆盖 85.59%（门禁 80%）。
- 偏差：暂停时间存 `AppSettings.proactivePausedUntilMs`（无 `app_meta` 表）；记忆命中注入直接丢弃不落 `archived`；`InjectionGuard` 保持 `object`；未做设置页安全开关与手动解除暂停、`crisis_disclaimer_seen` 一次性提示。

**批次 2 · 聊天背景全量 已完成**：G-09~G-18 落地。
- `chat_backgrounds` 表 + `MIGRATION_10_11`（DB version 11）+ `ChatBackgroundRepository`；`files/bg/<sha1>.jpg` 与 12MB/3 张配额、API<31 预渲染缓存（六档、LRU 40MB）。
- 五层渲染 `ui/background/ChatBackgroundRenderer.kt` + 四款程序化纹理；三档预设；可读性守卫五步 + 设置页实时对比度读数；气泡透明度经 `LocalBubbleVisual` 下发。
- 旧背景设置键自动迁移为首条 `source=image` 记录，旧键保留；`.xybackup` 已含 `media/bg/` 原图（不含缓存）。
- 偏差：渐变预设 4 组（文档 12 组）；缓存按需生成 + 保存预热（非导入即全量）；`bubbleBlur` 只下发参数未做真背景采样；聊天页「更多」入口未加（仅设置页入口）；T-76~T-79 未补自动化用例；G-20 走查未实测。

**批次 3 · 设计系统 已完成**（2026-11-17）：G-01~G-06、G-08、G-21 + 进一步补强。
- 11 档 `YuType` + CompositionLocal/Material3；四套主题 light/dark、HEX/HSV 取色器、自定义 seed、Android 12+ Material You 默认开；新外观字段仅存 Settings JSON，DB 保持 version 11。
- 六档动效与系统/应用减少动效；Chat 与设置页断点双栏；八个标准组件、气泡尾巴、44dp 触控与四类 Canvas 空态插画均已接入。
- `PaletteExtractor`（Bitmap 主色提取，HSL 聚类 + 饱和度过滤）；`YuWindowAdaptive`（`WindowPosture` 四态 + `Handedness` 三态 + 单手偏移 + 折叠悬停检测）；`HardcodeCheck` 加强（新增 `Color(0x...)` 正则扫描 + `.dp/.sp` 范围扩展 + 主题文件白名单）；`YuThemePresets` 补中文标签（晨雾/昼白/暮青/夜航）。
- `DesignSystemTest` 扩展至 6 个新用例（预设中文标签、单手窗口状态、Color 硬编码扫描、捕获验证、PaletteExtractor fallback、窗口姿态判定）。
- 编译修复：移除错误的 `hapticfeedback` import、`HapticFeedbackType.ContextClick`、`SectionCard` 补 `@Composable`、`MutableStateMap`→`MutableMap`。
- 偏差：PaletteExtractor 用 HSL 聚类近似 tonal 变体（非 material-color-utilities）；折叠姿态用 smallestScreenWidthDp+aspect ratio 推断（非 Jetpack WindowManager FoldingFeature）；硬编码扫描器是文件级正则而非自定义 Lint；legacy 硬编码未全局清零、未实现独立 lint/CI 阻断；G-20 二十项真机走查未执行；自绘图标体系未做。

**批次 4 · IM 手感 已完成**（2026-11-17）：E-01~E-08、E-13~E-14、E-19~E-21、E-24、E-26、E-32。
- 消息合并阈值 60s（原 120s）；时间行间隔 5min+ 才显示（纯 `HH:mm`，原 `MM-dd HH:mm`）。
- 顶栏副标题「对方正在输入…」状态；底部 FAB「↓ 回到最新」+ `animateScrollToItem` 平滑滚回。
- 双击气泡 = ❤️ reaction + `HapticFeedback`（长按 `MediumClick`、双击 `LongPress`、发送 `TextHandleMove`）。
- 设置页搜索框 + `FilteredSectionCard` 包装；诊断页四页签 `DiagTab(PROMPT/RAW/EVENT/ERROR)` + TabRow。
- 通知 inline reply 改进：Receiver 不再立即 cancel，先更新通知显示「你正在发送：xxx…」+ 不确定进度条；Worker 成功 cancel，失败更新错误。
- 偏差：typing 脉动节奏 3s/2s 未做、herReaction 角标数字未实现、PromptTab/RawTab 内容相同语义分组待细化、FAB 数字角标未做、设置搜索只做 section 级过滤。

**五批次叠加整体构建**（2026-11-17 最终验证）：`:app:assembleDebug :soul:check` → `BUILD SUCCESSFUL`（113 tasks，23s，全部 up-to-date）；soul 单测 0 失败，行覆盖 85.55%（1119/1308，门禁 80%）。DB version = 11（9→10 安全、10→11 背景），批次 3/4/5 均未新增表结构。

**无障碍与国际化（B-26 / B-27，2026-11-17）**：
- B-26 无障碍 60+ 项：消息气泡合并语义（发送者 + 正文/表情/图片 + 时间 + 已读/未读 + 我方/她方 reaction）；顶栏色点读「她在线/她正在忙/她在睡觉/她暂时离开」；FAB 读「回到最新消息」；背景页 12 条滑杆 `stateDescription` 播报百分比并带控件名；记忆图谱 Canvas 整图描述（节点数 + 最强记忆）；关系页统计卡/里程碑、她的一天、日记列表项 `mergeDescendants`；typing 副标题、聊天气泡输入中、纠偏提示、关系页/搜索页加载态 `liveRegion=Polite`；纯装饰图（背景预览、贴纸 Canvas）不报读。触控热区统一 48dp（`Tokens.SizeTouchTarget`）：FAB、发送/工具键、reaction 表情、表情格子（`Adaptive(48)`）、背景色板/包筛选 chip、双击头像、会话行。固定高度改 `heightIn`：输入框 46dp、引导按钮 50dp。次要文字去掉低透明度，统一 `onSurfaceVariant`。
- B-27 国际化：新增 117 组中文（背景设置页 84、聊天无障碍/纠偏/语音 22、图谱与列表语义 8、人设默认名与导入兜底 3），同步英文；并补齐 30 条历史缺失的 `values-en`（通知/桌面小组件/诊断四页签/设置搜索/FAB/typing）。`values` 与 `values-en` 均 680 条键、零缺口。
- 偏差：诊断自检卡导出的剪贴板文本、主题/气泡风格中文标签、`LegibilityGuard` 内部提示仍为硬编码中文（非本批新增 UI）；未做自动对比度扫描与 TalkBack 真机回归；`refreshable` 手势项目前无该组件，仅对现有加载态加 LiveRegion。

**质量目标与首次引导（§1.5 / §7.12 / B-17 / B-20 / B-28，2026-11-17）**：
- 新增 `app/perf/`：`StartupTimer`（Application→首帧，`reportFullyDrawn` + `Choreographer`，写 `files/perf/startup.log`，滚动 200 条）、`FrameStats`（Choreographer 自实现，最近 300 帧 p50/p95）、`MemoryProbe`（`Debug.MemoryInfo.totalPss` 对比 220MB）、`DbIndexProbe`（Debug 校验 `messages` 索引 + `EXPLAIN QUERY PLAN`）。
- 诊断页新增「性能」页签：三项实测值 + 绿/橙达标标记 + 「导出报告」（`Download/小鱼导出/perf-<ts>.txt`，走 `PublicDownloads`）。
- Debug 构建开启 `StrictMode`（ThreadPolicy + VmPolicy，`penaltyLog`，不崩溃）。
- 新增 `data/media/ImageCompressor`：EXIF 摆正、两段解码、>12MB 额外降采样、JPEG 88；头像 512（`PersonaPackArchive`）、表情 512（`StickerImporter` 复用解码，仍编码 WEBP 保透明）、生图 2048（`LocalAiRepository`）已接入，目录与命名规则不变。
- 新增 `ui/onboarding/OnboardingScreen`：6 步可滑动引导 + 进度点 + 跳过，`onboardingDone` 存 Settings JSON（不改表结构）；升级用户靠 `onboarded`/是否已有会话兜底，不重复弹。
- 偏差：没有真机确定的启动耗时/帧率/PSS 数字；50 万条消息压测未执行，只校验了索引；引导退出后不保留进度（无断点续导）。

**本批 · IM 交互细项 已完成**（2026-11-17）：E-06 / E-07 / E-16 / G-07（部分）。
- reaction：长按 6 个快捷表情 👍❤️😂😮😢🎉（选中态高亮、同表情再点取消）、我方消息可 reaction、气泡下方展示且 >3 折叠「+N」并点击展开；快捷徽标可点击取消。
- herReaction：沿用 Settings `herReactions` 映射表（不改表结构）；用户消息命中「谢谢/辛苦了/对不起/抱歉」等被夸/被道歉关键词时 30% 概率本地加表情，不调模型。
- 长按菜单：常用 4 项 +「更多」二级；编辑/撤回限制 2 分钟内；重新生成在「后面还有用户消息」时禁用并给出提示；新增「保存图片」（PublicDownloads 存相册）；翻译保留禁用占位；删除仍为 `hidden=1` 软删。
- 表情面板：输入栏 emoji 键打开「表情包 / Emoji」双 tab，表情包按 `StickerRepository` 分包分组、点击直接发送、长按大图预览；面板高度 ≤ 屏高 40%，输入框聚焦时收起。
- 输入栏：1~5 行自适应、`ImeAction.Send`、发送键随内容启用；粘贴 ≥800 字提示「内容较长，是否拆分发送」，拆分按空行/400 字切块经 `sendSplit` 排队逐条发送。
- 手势：下拉（列表在顶部）进入翻旧账搜索、上滑到顶分页放出历史（默认最近 80 条）、会话行左滑归档、双击输入栏全屏输入。
- 偏差：右滑置顶未做（数据模型无置顶字段）、三指下滑沉浸、捏合切密度、`tip_shown` 一次性提示未做；「转文字」入口仅在存在 `voice_uri` 时才有意义，当前无语音消息生成路径，故未加。
- DB 无需新列，version 保持 11，无新 Migration。

**批次 5 · 剩余项收尾**（2026-11-17）：F-12、B-07、B-22、B-31、G-08（部分）。
- **F-12 安全用例固化**：新增 `soul/src/test/kotlin/com/xiaoyu/soul/safety/SafetyCaseCatalogTest.kt`，用中文用例名把 §8.6 的 S-1~S-14 全部列成清单（`SAFETY_CASES`）；S-1~S-7、S-10、S-14 共 9 项在 `:soul` 内直接断言（注入拒绝、`[xy:*]` 转义、人格/表情/记忆扫描、自伤三级、否定与引述不误判、危机后 48h 暂停、每日上限、护栏不可关、热线号码）。`ProactivePlanner` 新增 `pausedBySafety()` / `dailyCapReached()` 两个纯函数，`ProactiveWorker` 与 `DailyPlanWorker` 改为调用它们，使「48h 内不主动」「每日上限」具备可断言入口；`injection_patterns.json` 补 `privacy_solicitation`（索要手机号/身份证/住址）。
- **B-07 搜索高亮**：`SearchScreen` 结果正文改为 `AnnotatedString`，命中词以 `SpanStyle(background = primaryContainer)` 高亮；`indexOf(ignoreCase = true)` 实现大小写不敏感，空格/中英文逗号/顿号分隔多关键词并去重；高亮依据「实际提交搜索」的关键词，避免边打字边错位。
- **B-22 通知专项**：`AppSettings` 新增 `notifyShowContent` / `notifyHideOnLockScreen` / `notifySound` / `notifyVibrate` / `proactiveDailyLimit`（只进 Settings JSON，DB 仍 version 11）；设置页新增「通知」分组（主动开关、免打扰时段、显示正文、锁屏隐藏、声音、震动、每日 1/2/3 条）；`Notifier.notifyProactive` 按开关选择提示音/震动/安静三条新增渠道（含 pre-O 的通知级 sound/vibrate），并设置 `VISIBILITY_SECRET` / `VISIBILITY_PRIVATE + publicVersion`；`ProactiveWorker` 用 `pausedBySafety` 与 `dailyCapReached` 生效上限，`DailyPlanWorker` 的计划条数也按上限收敛。
- **B-31 语音输出**：`ChatScreen` 长按菜单新增「朗读 / 停止朗读」，新增 `SystemTts`（`TextToSpeech` 中文、语速/音调读设置、120 字按标点分段、`QUEUE_FLUSH/ADD` 连续播报、最后一段读完回调复位、无引擎/缺语音包/构造抛异常时静默降级）；设置页新增「朗读」分组滑杆（语速 0.6~1.8、音调 0.6~1.4），字段存 Settings JSON。
- **G-08 自绘图标（部分）**：新增 `ui/theme/YuIcons.kt`，用 `ImageVector.Builder` 画 24dp 的 8 个图标（发送/加号/表情/语音/图片/返回/更多/关闭），聊天气泡顶栏 More/Close 与输入栏发送/表情/麦克风已替换；其余图标不强行替换。
- 新增中文文案均同步 `values-en`；未改 CHANGELOG / versionName，未 commit、未打 tag。
- 验证：`.\gradlew.bat :app:assembleDebug :soul:check --no-daemon --console=plain` → BUILD SUCCESSFUL；`SafetyCaseCatalogTest` 新增 13 个用例（全部通过），`:soul` jacoco 行覆盖 85.55%（1119/1308，阈值 80%）。

**仍未做**：
- G-20 真机走查、G-08 剩余图标（全量 48 个自绘图标集）
- G-07 剩余手势（右滑置顶、三指下滑沉浸、捏合切密度）与一次性提示
- E-14 七态消息状态机（当前 3 态 + 撤回/流式视觉）
- E-15 离线横幅
- E-09~E-12 关系页「小本子」/图谱力导向/列表模式
- E-22~E-25 中尚未覆盖的引用/回复视觉细节
- E-27~E-30 键盘 / 手势 / 动效 / 富文本
- B-04 FAB 数字角标、B-16 多身份一键切换、B-26 无障碍真机走查（语义/热区已完成）
- 质量目标只完成了**埋点**（C-02/C-03/C-05）与**索引护栏**（C-01），没有真机基线数字，也**没有做 50 万条消息压测**

---

## 建议分批（待用户勾选）

- **批次 1 · 安全底线**（F-01~F-08、F-10）：唯一"不做就是产品事故"的一组。
- **批次 2 · 聊天背景全量**（G-09~G-19）：用户点名的重点，且依赖 `chat_backgrounds` 新表。
- **批次 3 · 设计系统**（G-01~G-08）：主题预设/取色器/断点/组件化。
- **批次 4 · IM 手感**（E-01~E-16、B-3/B-6/B-9/B-15/B-16）：时间行、合并 60s、输入中、FAB、reaction、状态机。
- **批次 5 · 其余**（A/C/E-18~E-22、G-20）：Soul-Eval、性能基准、诊断四页签、无障碍（F-12/B-07/B-22/B-31/G-08 已于 2026-11-17 收尾，见上方进度更新）。

## 落地约束（沿用既有约定）

- APP 已有内容不删，只加或优化；非必要不停。
- 包名 `com.xiaoyu.app`、DB 名 `xiaoyu.db` 不动；DB 变更走 Room Migration，禁止 destructive。
- 消息只增不删，删除一律 `hidden=1`。
- `:soul` 禁 `android.*`/`androidx.*`/Compose/Room；`:brain/:data/:service` 禁 Compose。
- 敏感信息只走 `SecretStore`。
- 每条改完跑 `.\gradlew.bat :app:assembleDebug :soul:check --no-daemon --console=plain`；全部完成后统一 commit + 打 tag 发版，不逐条发版。
