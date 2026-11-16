# 更新日志

## v1.2.0 (2026-11-16)

落地工程执行书 v3.0 的 30 项改进，一次性大版本更新。

### 新增

- **三级成本护栏（v3 §2.5 / H-3）**：日 token 120 万、月费用 30 元；L1 提示（>60%）仅黄条、L2 限流（>100%）停 VISION、L3 停后台（月费>150%）；聊天与记忆提炼永不中断。
- **危机关怀（v3 §8.2 / H-4）**：`CrisisDetector` 命中自伤关键词直接关怀回复、不调模型，记忆提炼时过滤。
- **注入防御（v3 H-24 / §14）**：`InjectionGuard.sanitize` 剥离 `[xy:*]` 协议 token，人格包导入命中注入直接拒绝。
- **连续超长回复自适应（v3 §4.4）**：`LengthGuard` 连续 3 条超长回复自动注入「简短点」约束。
- **记忆提炼防编造与限流（v3 H-14）**：提炼 prompt 加「绝对不要编造」+ 最多 12 条；`MemoryExtractionParser` 过滤 3~120 字、按重要性取前 12。
- **增量提炼游标**：`sessions.extract_cursor`，≥60 条或 ≥4h 且 ≥10 条触发，提炼后前移游标（DB v6→7）。
- **归因情绪事件（v3 H-9 / 4.10.3）**：`EmotionAttributor` 检测触发词落 `emotion_events` 表，强度半衰期衰减、被再次提及时回升（DB v7→8）。
- **纠偏条（v3 H-11 / 4.11）**：`CorrectionEngine` 检测「不像她」落 `corrections` 表，风格补丁注入 prompt，顶部纠偏条可撤销（DB v8→9）。
- **记忆图谱（v3 H-12 / 7.5）**：关系页 Canvas 绘制记忆节点，strength 映射颜色深浅、importance 映射节点大小。
- **她的一天（v3 H-12 / 7.6）**：独立页面，按时间线展示今日生活碎片。
- **卡片消息（v3 ADR-15 / 7.4.5）**：`CardFactory` 生成日记/约定/她的一天/记忆回溯结构化卡片，`CardBubble` 渲染，`type=card`。
- **聊天背景 WCAG 对比度守卫（v3 H-24 / 14.6）**：`LegibilityGuard` 采样背景亮度，自动抬升暗化蒙层到满足 WCAG AA（4.5:1）的下限。
- **设计系统 Token 化（v3 H-24 / 14.2）**：`Tokens.kt` 统一间距/圆角/海拔/动效时长；`HardcodeCheck` 扫描硬编码 `.dp`。
- **在线状态推导（v3 1.3#35）**：`PresenceEngine` 基于精力+时段+生活碎片推导在线/忙碌/睡觉/离开，顶栏色点展示。
- **消息密度三档（v3 H-22 / 1.3#48）**：紧凑/标准/宽松，控制气泡上下间距。
- **语音转文字输入（v3 H-21 / 7.9）**：`RecognizerIntent` 按住说话，结果回填输入框。
- **断点续跑机制（v3 H-1 / P-4）**：`docs/spec/STATUS.json` + `TASKS.json` 记录任务进度，跨会话不丢。

### 变更

- **表情短名制（v3 H-6）**：`[xy:s=名字]` 替代编号制，prompt 列表与模板同步更新，`StickerRepository.resolve` 已支持按名查找。
- **价目表外置（v3 H-3 / §2.5）**：`cost_table.json` 记录各模型单价，ollama 标 `unpriced`，`CostTable.isPriced()` 检查。
- **调度主轨（v3 §11）**：`WorkScheduler` 注释改为 WorkManager 主轨；无精确闹钟权限时跳过主动消息排期。
- **连续同作者消息合并（v3 H-22）**：间隔<2分钟省略头像、收紧间距。
- **未读分割线（v3 H-22）**：发言时标记 `lastReadMessageId`，时间线插入「以下为新消息」。

### 安全

- 人格包导入时对 identity/style 做 `InjectionGuard.sanitize`，命中注入模式直接跳过。

## v1.1.0 (2026-11-16)

- Release: https://github.com/gegugug47-hue/xiaoyu/releases/tag/v1.1.0


## v1.1.0 (2026-11-16)

- Release: https://github.com/gegugug47-hue/xiaoyu/releases/tag/v1.1.0

### 修复

- **成本护栏阈值修正（v3 §2.5 / H-3）**：原默认值日 token 上限 40 万、月费用上限 3 元，与「月成本 ≤10 元」自相矛盾，实际会导致上线第一周就天天触发熔断、日记/主动聊天/生活模拟全部停摆。现调整为日 token 120 万、月费用 30 元（正常日均 24 万 token 给 5 倍余量）。
- **记忆提炼永不中断**：原实现超限后会停掉所有非聊天角色（含 EXTRACT 记忆提炼），违反「记忆是支柱一」。现仅暂停可选角色（VISION：表情打标 / 生图描述），聊天与记忆提炼始终保留。

### 变更

- **成本护栏改为三级动作**：
  - L1 提示（>60%）：仅用量页黄条提示，不停任何任务；
  - L2 限流（>100%）：暂停表情打标 / 生图描述，聊天与记忆提炼不受影响；
  - L3 停后台（月费 >150%）：暂停全部可选后台，聊天与记忆提炼仍不受影响。
- 用量页护栏提示条按级别显示不同颜色（黄 / 橙 / 红），文案明确说明哪些功能暂停、哪些保留。
- 新增 `docs/spec/V3-ADOPTION.md`：记录从工程执行书 v3.0 中筛选出的 30 条待落地改进，跨会话追踪，防止上下文压缩丢失。

## v1.0.2 (2026-11-16)

- Release: https://github.com/gegugug47-hue/xiaoyu/releases/tag/v1.0.2


## v1.0.1 (2026-11-16)

- Release: https://github.com/gegugug47-hue/xiaoyu/releases/tag/v1.0.1

## v1.0.0 (2026-11-15)

- Release: https://github.com/gegugug47-hue/xiaoyu/releases/tag/v1.0.0


本文件格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## Unreleased

### 变更

- 国际化（M5-07）：界面文案全部抽到 Android 资源文件，按领域拆分为 `strings_chat` / `strings_settings` / `strings_stickers` / `strings_insight` / `strings_app`（app 模块）、`data` 与 `service` 各一份；`:data` 中会展示给用户的提示改为经 `@ApplicationContext` 读取资源。prompt、日志、SQL 与持久化值保持原样。
- 覆盖率门禁（M5-08）：`:soul` 增加 `jacocoTestCoverageVerification`，行覆盖低于 80% 即失败（当前 97.2%），CI 改为执行 `:soul:check`。
- 英文界面：为全部 631 条界面文案补齐 `values-en` 翻译，跟随系统语言自动切换；好感度等级名也改为走资源，英文界面不再出现中文。

## v1.0.0 (2026-11-15)

首次开源发布。

### 新增

- 6 模块架构：`:soul`（纯 JVM 对话灵魂）、`:core-model`（数据契约）、`:data`（Room / 仓库 / 备份）、`:brain`（模型接入）、`:service`（后台任务）、`:app`（Compose UI），并加入 `assertModuleDeps` 依赖守卫。
- 陪伴式聊天：流式回复、长回复分段连发、引用回复、编辑、撤回（2 分钟）、重新生成多版本、失败重试与继续生成、戳一戳、表情回应。
- 沉浸模式：可配置 10 秒 ~ 5 分钟的思考延迟与 0.2 ~ 2.0 强度，深夜与低精力自动放慢，低精力时可能直接回一句「困了」。
- 记忆系统：每轮按相关度召回 6 条短期记忆；切换或离开会话后后台提炼；长期事实卡；同一事实出现 3 次以上自动晋升；回收站可恢复，30 天后物理删除；支持「记住……」显式记忆。
- 情绪与关系：每日心情与精力、好感度等级与事件历史、生活碎片、关系页；夜间清算与情绪/精力恢复。
- 日记：每日 23:50 自动生成，支持「加密日记」与好感度解锁分享；日记可导出 Markdown。
- 人格与风格：内置 1 套初始身份与 3 种表达风格，支持编辑身份、称呼、签名、核心设定与多种表达风格；支持 `.xypersona` 人格包导入 / 导出，导入为追加式。
- 表情包：内置表情包、`.xystickers` 包导入 / 导出、相册批量导入、Telegram 静态表情导入；标签与心情匹配、单轮数量限制与冷却；可配置视觉模型自动打标。
- 生图与语音：接入本地 AI 网关（DSHA Gateway / ComfyUI / CosyVoice3），生图进入「她的画」图库，消息可朗读；网关离线不影响聊天。
- 搜索：FTS4 全文搜索历史消息；「翻旧账」把旧对话仅带入当前一轮，不写入记忆。
- 用量与成本：按天统计 token 与费用并保留 35 天；单日 40 万 token、单月 3 元护栏，触发后聊天仍可用、后台自动生成直接跳过。
- 备份与恢复：本地备份数据库与自定义提示词，可选 PBKDF2 + AES-GCM 口令加密，保留最新 4 份；支持合并恢复与覆盖恢复。
- 聊天与日记 Markdown 导出到 `Download/小鱼导出/`，图片资源单独复制到 `assets/images/`。
- 隐私与安全：应用锁（4~6 位 PIN / 生物识别 / 回前台自动锁定）、通知与锁屏隐藏正文、API Key 经 Android Keystore AES-256-GCM 加密、关闭系统自动备份。
- 系统集成：每日 08:05 早安、23:50 晚安、按好感度每日 1~3 次主动消息、通知栏直接回复、精确闹钟、开机恢复排期、Glance 桌面小组件、通知直达会话。
- 界面：Material 3 明暗主题、动态取色、三档气泡样式、四档字号、聊天背景（模糊 / 暗化）、消息入场与面板动效。
- 诊断：最近 50 次请求的 prompt 分段、模型原文、后处理事件与错误记录，可一键清空。
- 无 Key 示例模式：未配置模型时使用内置回复，完整走通界面与数据流程。
- 纯 JVM 测试：覆盖提示词、记忆与遗忘曲线、情绪与好感度、主动消息与日记节奏、分段后处理、表情提示词；备份加密另有单元测试。

### 安全

- 提交前钩子拦截 `key.properties`、`keys/`、签名文件与常见密钥模式；CI 运行 gitleaks 扫描。
- 发布工作流在 tag 上使用 GitHub Secrets 签名，产物附带 `SHA256SUMS`。
- 限制本地网关明文 HTTP 的使用范围，公网模型端点使用 HTTPS。

### 说明

- 备份不包含表情图片、生成图片、头像、聊天背景与语音缓存。
- 换机恢复后，设置表因 Keystore 密钥不随备份迁移，API Key 等设置可能需要重新填写。
- 模型费用由对应服务商计费，应用内金额为估算值。
