# 更新日志

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
