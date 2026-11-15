# 小鱼（Xiaoyu）

一个只属于你的、会记事也会闹小情绪的陪伴式 AI 聊天 App：纯 Kotlin + Jetpack Compose，数据全部留在本机，聊天模型由你自己配置。

> 包名 `com.xiaoyu.app` · 版本 `1.0.0` · Android 8.0+（minSdk 26）· MIT License

## 功能特性

- **像真人一样的聊天**：流式回复、长回复分段连发、思考延迟（可调强度）、深夜犯困、引用回复、撤回、编辑、重新生成多个版本、戳一戳、表情回应。
- **长期关系**：好感度分级与事件记录、每日心情与精力、生活碎片、关系页；会话自动标题、置顶与新建。
- **会记事**：每轮按相关度召回短期记忆；切换或离开会话后在后台提炼；重要内容晋升为长期卡片，长期卡片可手动增删改；淘汰的记忆进入回收站，可恢复。
- **日记**：夜间自动写日记，支持「加密日记」；写完后的下一轮她会自然问一句要不要看。日记可导出 Markdown。
- **人格与风格**：身份、说话风格可编辑；支持 `.xypersona` 人格包（ZIP 容器）导入 / 导出，导入时只追加、不覆盖。
- **表情包**：内置表情包 + `.xystickers` 包（ZIP 容器）导入 / 导出 + Telegram 静态表情批量导入；表情按标签与心情匹配，带冷却。
- **生图与语音（可选）**：连接家里电脑的本地 AI 网关（DSHA Gateway）后，可生图到「她的画」图库、朗读消息；电脑关机不影响聊天。
- **用量与成本护栏**：按天统计 Token 与花费，保留 35 天；单日 40 万 token、单月 3 元的上限触发后，聊天仍可用，后台自动生成直接跳过。
- **搜索**：全文搜索历史消息，支持「翻旧账」——把旧对话临时带进当前这一轮。
- **备份与导出**：本地备份数据库与自定义提示词（可选用口令 AES-GCM 加密），最新保留 4 份；支持合并恢复与覆盖恢复；聊天 / 日记可导出为 Markdown。
- **隐私与安全**：应用锁（PIN / 生物识别）、通知内容隐藏、API Key 由 Android Keystore 加密、关闭系统自动备份。
- **后台与系统入口**：每日早安（08:05）与晚安（23:50）、夜间清算、主动消息、通知栏直接回复、精确闹钟、开机恢复排期、桌面小组件。
- **无 Key 也能跑**：未配置任何模型时自动进入「示例模式」，界面与流程可完整体验。

## 模块结构

| 模块 | 类型 | 职责 | 依赖约束 |
| --- | --- | --- | --- |
| `:soul` | 纯 JVM Kotlin | 人格 / 提示词装配、记忆召回与遗忘曲线、情绪与好感度、日记规划、回复节奏、后处理、Token 估算。不依赖 Android。 | 只允许依赖 `:core-model`；禁止 `android.*` / `androidx.*` / Compose / Room / OkHttp / Retrofit / WorkManager |
| `:core-model` | 纯 JVM Kotlin | 跨层共享的数据契约：`ProviderKind`、`BrainSettings`、`AppSettings`、用量与成本护栏模型等。 | 不依赖任何模块；禁止 Android 生态依赖 |
| `:data` | Android Library | Room 数据库（17 张表，schema v6）、仓库层、备份 / 恢复、Markdown 导出、表情包 / 人格包归档、密钥存储、本地 AI 网关客户端。 | 只允许依赖 `:core-model`、`:soul`；禁止 Compose |
| `:brain` | Android Library | OpenAI 兼容的模型接入（DeepSeek / 阿里百炼 / Ollama / 自定义端点）、主备降级路由、示例模式、用量计费。 | 只允许依赖 `:core-model`、`:soul`、`:data`；禁止 Compose / Room |
| `:service` | Android Library | WorkManager 后台任务、通知渠道与通知回复、精确闹钟、开机广播、备份 Worker、表情导入 Worker。 | 只允许依赖 `:core-model`、`:soul`、`:data`、`:brain`；禁止 Compose |
| `:app` | Android Application | Compose 界面、导航、ViewModel、Hilt 组装、桌面小组件、启动自检。 | 可依赖全部模块 |

依赖守卫由根目录 `assertModuleDeps` 任务执行，构建时不会触发网络解析：

```bash
./gradlew assertModuleDeps
```

## 快速开始

### 环境要求

| 项目 | 要求 |
| --- | --- |
| JDK | 21（Gradle / AGP 运行环境）；源码编译目标为 Java 17 |
| Android SDK | compileSdk 37、targetSdk 36 |
| 构建工具 | 仓库自带 Gradle Wrapper 9.9.0、AGP 9.5.1，无需单独安装 |
| 设备 | Android 8.0（API 26）及以上 |

### 构建与安装（Windows PowerShell）

```powershell
# 0. 克隆仓库
git clone https://github.com/gegugug47-hue/xiaoyu.git
cd xiaoyu

# 1. 指定 JDK 与 SDK
$env:JAVA_HOME = "<你的 JDK 路径>"
# 在项目根目录创建 local.properties，写入：
# sdk.dir=C\:\\Users\\<用户名>\\AppData\\Local\\Android\\Sdk

# 2. 编译 Debug 包
.\gradlew.bat :app:assembleDebug

# 3. 安装到已连接的设备 / 模拟器
.\gradlew.bat :app:installDebug
```

macOS / Linux 将 `.\gradlew.bat` 换成 `./gradlew`。

产物路径：`app/build/outputs/apk/debug/app-debug.apk`。

### 首次运行

1. 启动后完成引导：选择是否现在配置模型、挑一个身份与表达风格、填写她怎么称呼你、决定是否允许主动消息。
2. 进入「设置 → 模型」，为 **聊天主模型** 填写 Base URL、API Key、模型名，点「检测连接」。
3. 回到聊天页发一句话即可。未配置任何 Key 时，App 会进入示例模式，回复由本地固定文案生成。
4. 需要生图或朗读时，再配置「本地 AI 服务」（见下文）。

更细的环境、签名与排错说明见 [docs/SETUP.md](docs/SETUP.md)。

## 模型服务配置

设置页提供 4 个可独立配置的槽位：

| 槽位 | 用途 | 说明 |
| --- | --- | --- |
| 聊天主模型 | 日常对话 | 必填（或改用示例模式） |
| 聊天备用模型 | 主模型失败时降级 | 可选 |
| 记忆主模型 | 会话提炼、记忆晋升、日记、生活碎片等后台任务 | 默认沿用 DeepSeek 配置；未单独填写时回退到聊天主模型 |
| 记忆备用模型 | 后台任务降级 | 可选 |

内置服务商（`ProviderKind`）共 4 种：

| 服务商 | 默认 Base URL | 默认模型 | 是否需要 API Key |
| --- | --- | --- | --- |
| DeepSeek | `https://api.deepseek.com` | `deepseek-chat` | 需要 |
| 阿里百炼 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen3.7-plus` | 需要 |
| 家里电脑 Ollama | `http://192.168.1.100:11434/v1` | `qwen3:8b` | 不需要 |
| 自定义 OpenAI 兼容端点 | 空 | 空 | 需要 |

- 所有 API Key 与本地网关 Token 都保存在设置表中，并用 Android Keystore 的 AES-256-GCM 密钥加密（`data/.../secure/SecretStore.kt`）；**不要把密钥写进代码、截图或 Issue**。
- 请求直接由手机发往你配置的端点，不经过任何中转服务器。
- 生图 / 语音是可选的本地 AI 网关能力（DSHA Gateway：ComfyUI 生图、CosyVoice3 语音），在「设置 → 本地 AI 服务（生图 + 语音）」填写网关地址与 Token 即可，与聊天模型互不影响。
- 示例模式、Ollama 与本地网关不计入费用估算；DeepSeek / 阿里百炼的估算金额仅供参考，实际以服务商账单为准。

## 数据与隐私

聊天记录、记忆、日记、表情、备份与 API Key 全部保存在本机；只有组装好的提示词与最近对话会发送到**你自己配置**的模型服务。详见 [docs/PRIVACY.md](docs/PRIVACY.md)。

## 目录结构

```text
.
├── app/                    # Compose UI、导航、ViewModel、Hilt 组装、小组件
│   ├── src/main/assets/    # 内置人格与可编辑的提示词模板（.md）
│   ├── src/main/kotlin/com/xiaoyu/app/
│   │   ├── ui/             # 聊天、日记、图库、搜索、关系、设置、诊断等页面
│   │   ├── widget/         # 桌面小组件与锁屏入口辅助
│   │   └── di/             # Hilt 模块
│   └── src/main/res/
├── core-model/             # 跨层数据契约（纯 JVM）
├── soul/                   # 对话灵魂：提示词、记忆、情绪、日记、节奏（纯 JVM）
├── data/                   # Room、仓库、备份、导出、归档、Keystore、本地网关
├── brain/                  # OpenAI 兼容接入与主备路由
├── service/                # WorkManager、通知、闹钟、广播接收器
├── docs/                   # 文档（本目录）
├── gradle/libs.versions.toml
├── .github/workflows/      # release.yml（tag 发布）、security.yml（gitleaks）
├── .githooks/pre-commit    # 提交前密钥与签名文件拦截
└── CHANGELOG.md
```

## 截图

仓库不附带截图，避免泄露真实聊天内容。你可以在自己的设备上运行后自行截图并放入 `docs/images/`，再在此处引用。请勿提交包含 API Key、真实聊天记录或私人照片的截图。

## 贡献与许可

- 参与开发前请阅读 [CONTRIBUTING.md](docs/CONTRIBUTING.md) 与 [CODE_OF_CONDUCT.md](docs/CODE_OF_CONDUCT.md)。
- 安全漏洞请按 [SECURITY.md](docs/SECURITY.md) 私密上报，不要公开 Issue。
- 本项目使用 [MIT License](LICENSE)，版权归许缘凯（Xu Yuankai）所有。

AI 生成内容存在不确定性，使用前请阅读 [DISCLAIMER.md](docs/DISCLAIMER.md)。
