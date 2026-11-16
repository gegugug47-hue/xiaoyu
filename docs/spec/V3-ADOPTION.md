# v3.0 工程执行书 · 待落地清单

> 来源：`小鱼App-工程执行书-v3.0.md`（2026-11-15 终稿）。
> 用途：把 v3 中值得借鉴的内容固化为可追踪的任务，防止上下文压缩导致丢失。
> 状态：`TODO` / `DOING` / `DONE` / `SKIP`。
> 优先级：`P0`（修正在用问题，尽快做）/ `P1`（体验与质量补强）/ `P2`（长期能力）。

## P0 · 修正在用问题

| # | 条目 | 来源 | 改动范围 | 状态 |
| --- | --- | --- | --- | --- |
| 1 | 成本护栏阈值修正（40万→120万 token/日，3元→30元/月）+ 三级动作且永不断聊天 | H-3 / 第2章 | `core-model` SettingsModels 默认值；`data` SettingsRepository.guardState 分级逻辑；UI 提示 | DONE (v1.1.0) |
| 2 | 推送调度主次反转：WorkManager 周期窗口为主轨，精确闹钟为可选增强 | H-5 / ADR-10 / 9.2 | `service` WorkScheduler：ProactiveWorker 改周期窗口；AlarmManager 只在有权限时叠加 | DONE (v2.0.0) |
| 3 | 危机关怀层最小版：关键词命中 → 关怀回复 + 不进记忆 + 停主动 | H-4 / 第8章 | `:soul` 新增 CrisisLadder；`ChatRepository` send 前置拦截；提炼排除危机消息 | DONE (v2.0.0) |
| 4 | 人格包 / 表情标签 / 记忆文本进 prompt 前的注入扫描 | H-7 / 8.3 | `:data` PersonaRepository 导入清洗；`:soul` InjectionGuard 接口 | DONE (v2.0.0) |

## P1 · 体验与质量补强

| # | 条目 | 来源 | 改动范围 | 状态 |
| --- | --- | --- | --- | --- |
| 5 | 增量提炼游标 T2：会话内累计≥60条或≥4小时触发提炼 | H-14 / ADR-12 / 4.7.1 | `:data` sessions 表加 extract_cursor；`MemoryRepository` 增量提炼；Worker 投递 | DONE (v2.0.0) |
| 6 | 表情短名制 `[xy:s=委屈巴巴]` 替代编号制 | H-6 / ADR-05 / 4.3.1 / 12.5 | `:soul` XyTokenParser + PostProcessPipeline；StickerCatalog 短名解析；prompt 模板；测试 | DONE (v2.0.0) |
| 7 | 禁词 HARD 档命中 → 强化人设重生成最多1次（不计好感度） | 4.3.2 | `:soul` ForbiddenGuard / PostProcessPipeline；`ChatRepository` 重试分支 | DONE (v2.0.0) |
| 8 | LengthGuard 自适应负反馈（连续3次超长 → 下一轮L1追加约束，不硬截断） | 4.4 | `:soul` LengthGuard；PromptAssembler L1 动态追加 | DONE (v2.0.0) |
| 9 | 提炼产物校验：content 3-120字、max=12条、无内容输出[] | 4.7.2 | `:soul` MemoryExtractionParser 校验逻辑 | DONE (v2.0.0) |
| 10 | 记忆去重 dedup_hash（normalize + tags） | 4.7.3 | `:data` MemoryRepository 插入前去重；DB 加 dedup_hash 列 + 索引 | DONE (已存在 content 去重) |
| 11 | token 跨 chunk 缓冲上限 maxHoldChars=24 | 4.3.1 | `:soul` XyTokenParser pending 缓冲 | DONE (已存在) |
| 12 | 空注入段连标题一起省略 + 字节级稳定 | 4.5.2 | `:soul` PromptAssembler 拼接逻辑 | DONE (已存在) |
| 13 | 变量作用域约束：`{{today}}` 等只允许在 L8，出现在 L0/L2 告警 | 4.1.3 | `:soul` PromptAssembler 检测 + 诊断事件 | DONE (已合规) |
| 14 | 价目表加 currency / checked_at / unpriced 字段 | H-3 / 2.4 | `:brain` cost_table.json 格式；CostTable 解析；用量 UI | DONE (v2.0.0) |
| 15 | Soul-Eval：幻觉个人记忆 = 0 条断言（可先加结构断言） | H-8 / 10.4 | `:soul:test` 新增 eval 场景集 + 断言 | DONE (v2.0.0) |
| 16 | 连续同作者消息合并（间隔<2分钟省略头像） | H-22 / 1.3#4 | `:app` ChatScreen 消息分组渲染 | DONE (v2.0.0) |
| 17 | 未读分割线「以下为新消息」 | H-22 / 1.3#5 | `:data` last_read_msg_id；`:app` 时间线插入分割线 | DONE (v2.0.0) |
| 18 | 对话收束：深夜/累了主动说晚安并结束会话 | H-10 / 4.14 | `:soul` WrapUpPolicy；`ChatRepository` 收尾分支 | DONE (v2.0.0) |
| 19 | 内置表情默认 none（空目录不硬塞）+ emoji 档 | H-23 / 12.9 | `:app` 出厂表情默认配置；StickerRepository | DONE (v2.0.0) |

## P2 · 长期能力

| # | 条目 | 来源 | 改动范围 | 状态 |
| --- | --- | --- | --- | --- |
| 20 | 归因情绪事件（带触发词、半衰期、提起回升） | H-9 / 4.10.3 | `:soul` EmotionAttributor；`:data` emotion_events 表 | DONE (v2.0.0) |
| 21 | 纠偏条：用户说「不像她」→ 可撤销风格补丁 | H-11 / 4.11 | `:soul` CorrectionEngine；`:data` corrections 表；UI 入口 | DONE (v2.0.0) |
| 22 | 记忆图谱（强度→颜色深浅可视化） | H-12 / 7.5 | `:app` 关系页 Canvas 绘制 | DONE (v2.0.0) |
| 23 | 她的一天（生活碎片时间线） | H-12 / 7.6 | `:app` 独立页面；`:data` life 表查询 | DONE (v2.0.0) |
| 24 | 卡片消息（日记/约定/她的一天/记忆回溯，引擎生成非模型自由文本） | ADR-15 / 7.4.5 | `:soul` CardFactory；`:app` CardBubble；`:data` card 类型 | DONE (v2.0.0) |
| 25 | 聊天背景自定义 + WCAG 对比度守卫 | H-24 / 14.6 | `:app` ChatBackground 四层 + LegibilityGuard + BlurRender 双 API | DONE (v2.0.0) |
| 26 | 设计系统 token 化（Tokens.kt 唯一数值真源 + 硬编码 lint） | H-24 / 14.2 | `:app` ui/theme/Tokens.kt；YuHardcodeCheck 自定义 lint | DONE (v2.0.0) |
| 27 | 在线状态由精力+时段+生活碎片推导 | 1.3#35 | `:soul` PresenceEngine；顶栏展示 | DONE (v2.0.0) |
| 28 | 消息密度三档（紧凑/标准/宽松） | H-22 / 1.3#48 | `:app` 设置 + 气泡 padding | DONE (v2.0.0) |
| 29 | 语音转文字输入（RecognizerIntent 零依赖） | H-21 / 7.9 | `:app` InputBar 麦克风入口 | DONE (已存在) |
| 30 | docs/spec/STATUS.json + TASKS.json 断点续跑机制 | H-1 / P-4 | 新建状态文件结构 + 每卡更新流程 | DONE (v2.0.0) |

## 不采纳（与现有硬约束冲突）

| # | 条目 | 原因 |
| --- | --- | --- |
| X1 | 包名改为 `com.xiaoyu.xyk.app` | 硬约束：不改包名 `com.xiaoyu.app` |
| X2 | 应用名改为「小小鱼」 | 现有应用名「小鱼」，改了老用户桌面图标变 |
| X3 | 许可改为 AGPL-3.0-only | 需另行评估，不在本次范围 |
| X4 | 提示词改放 `:soul/src/main/resources` | 现有约束 assets/prompts，且当前是 :data 加载后注入，无需改 |

## 执行规则

1. 按 P0 → P1 → P2 顺序，同优先级内按编号。
2. 每条改动必须：`./gradlew :app:assembleDebug :soul:check` 通过 + 相关单测通过。
3. 每完成一条，更新本表状态为 `DONE` 并记录 commit。
4. 改动只增不删（APP 已有内容不允许删除）。
5. 涉及 DB 变更必须走 Room Migration。
