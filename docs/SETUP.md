# 开发与构建指南（SETUP）

本文覆盖从零搭建环境、构建安装、配置模型服务、打发布包与常见问题。所有命令以 **Windows PowerShell** 为例，项目根目录为仓库根目录。

## 1. 环境要求

| 项目 | 版本 / 要求 | 说明 |
| --- | --- | --- |
| JDK | 21 | Gradle 9.9.0 与 AGP 9.5.1 的运行环境。`JAVA_HOME` 必须指向 JDK 21。 |
| 编译目标 | Java 17 | 各模块 `sourceCompatibility` / `jvmTarget` 均为 17，由 JDK 21 编译。 |
| Android SDK Platform | 37 | `compileSdk = 37` |
| Android SDK Build-Tools | 随 SDK Manager 安装最新稳定版 | 无需手工指定版本 |
| Gradle | 9.9.0（Wrapper 自带） | 不要用系统全局 Gradle 覆盖 |
| AGP | 9.5.1 | 由版本目录统一管理 |
| 设备 / 模拟器 | Android 8.0（API 26）及以上 | `minSdk = 26`，`targetSdk = 36` |
| Android Studio | 支持 AGP 9.5.1 的版本 | 可选，命令行同样可完整构建 |

## 2. 配置 SDK 与 JDK

### 2.1 设置 JAVA_HOME

```powershell
$env:JAVA_HOME = "<你的 JDK 路径>"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

期望输出包含 `21`。只在当前 PowerShell 会话有效；需要长期生效请在「系统属性 → 环境变量」中配置。

### 2.2 指向 Android SDK

在项目根目录创建 `local.properties`（该文件已被 `.gitignore` 忽略，不要提交）：

```properties
sdk.dir=C\:\\Users\\<用户名>\\AppData\\Local\\Android\\Sdk
```

或在 Android Studio 中打开项目，由 IDE 自动生成。

### 2.3 验证环境

```powershell
.\gradlew.bat --version
```

输出应显示 Gradle 9.9.0、JVM 21。

## 3. 构建与安装

| 目的 | 命令 |
| --- | --- |
| 编译 Debug | `.\gradlew.bat :app:assembleDebug` |
| 安装到设备 | `.\gradlew.bat :app:installDebug` |
| 只跑纯 JVM 单元测试 | `.\gradlew.bat :soul:test` |
| 全量检查 | `.\gradlew.bat check` |
| 模块边界检查 | `.\gradlew.bat assertModuleDeps` |

Debug APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

通过 adb 手动安装：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

macOS / Linux 使用 `./gradlew` 替代 `.\gradlew.bat`。

## 4. 配置模型服务

进入 App「设置 → 模型」。四个槽位互相独立，但「记忆主模型」未单独配置时会自动回退到聊天主模型。

| 槽位 | 何时调用 |
| --- | --- |
| 聊天主模型 | 用户发消息后的对话回复 |
| 聊天备用模型 | 主模型请求失败且尚未输出内容时自动切换 |
| 记忆主模型 | 会话提炼、记忆晋升、日记、生活碎片、显式记忆重试 |
| 记忆备用模型 | 记忆/后台任务失败时降级 |

可选服务商：

| 服务商 | Base URL 示例 | 模型示例 | 备注 |
| --- | --- | --- | --- |
| DeepSeek | `https://api.deepseek.com` | `deepseek-chat` | 需要 API Key |
| 阿里百炼 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen3.7-plus` | 需要 API Key |
| 家里电脑 Ollama | `http://192.168.1.100:11434/v1` | `qwen3:8b` | 无需 Key，手机与电脑需同一局域网 |
| 自定义 OpenAI 兼容端点 | 你的网关地址 | 你的模型名 | 需要 Key（若网关要求） |

配置步骤：

1. 点某槽位的「编辑」，选择服务商；切换服务商会填入该服务商的默认 Base URL 与模型。
2. 填写 API Key 与模型名（可覆盖默认值）。
3. 保存后点「检测连接」。检测只对聊天角色发起一次轻量请求。
4. 失败时按提示检查网络、Base URL 是否需要带 `/v1`、Key 是否有效、Ollama 是否监听局域网地址。

补充说明：

- Key 保存在设置表中，由 Android Keystore 的 AES-256-GCM 密钥加密；不会写入日志、备份中的提示词或导出文件。
- 没有配置任何聊天模型时，App 自动使用内置「示例模式」，可体验完整界面，但回复是固定文案，不产生费用。
- 生图 / 朗读需要在「设置 → 本地 AI 服务（生图 + 语音）」单独填写 DSHA Gateway 地址与 Token；网关负责转调 ComfyUI 与 CosyVoice3。
- 本地网关使用 HTTP 明文时，App 的 `usesCleartextTraffic` 已开启；仍建议只在可信局域网内使用。

## 5. 打发布包

### 5.1 准备 keystore（不要入库）

若还没有签名文件，可用 JDK 自带工具生成（示例，参数自拟）：

```powershell
keytool -genkeypair -v -keystore "<你的 keystore 路径>\xiaoyu-release.jks" `
  -alias xiaoyu -keyalg RSA -keysize 2048 -validity 10000
```

将 keystore 放在仓库外，或放在已被忽略的 `keys/` 目录。**不要提交** `key.properties`、`keys/`、`*.jks`、`*.keystore`、`*.p12`、`*.pem`。

### 5.2 本地 key.properties

在项目根目录创建 `key.properties`：

```properties
storeFile=<你的 keystore 路径>
storePassword=<你的 store 口令>
keyAlias=xiaoyu
keyPassword=<你的 key 口令>
```

字段说明：

| 字段 | 含义 |
| --- | --- |
| `storeFile` | keystore 文件路径；相对路径以仓库根目录为基准 |
| `storePassword` | keystore 口令 |
| `keyAlias` | 密钥别名 |
| `keyPassword` | 该别名对应的口令 |

`storeFile` 存在时，`assembleRelease` 自动使用 release 签名；不存在时产物为未签名 APK，无法直接安装覆盖正式包。

### 5.3 本地构建

```powershell
.\gradlew.bat :app:assembleRelease
```

release 任务会启用 ABI 拆分，输出：

- `app/build/outputs/apk/release/app-armeabi-v7a-release.apk`
- `app/build/outputs/apk/release/app-arm64-v8a-release.apk`
- `app/build/outputs/apk/release/app-x86_64-release.apk`

可在命令行覆盖版本号（CI 也使用这两个参数）：

```powershell
.\gradlew.bat :app:assembleRelease `
  -PxiaoyuVersionName=1.0.0 `
  -PxiaoyuVersionCode=1
```

### 5.4 CI 发布

仓库包含 `.github/workflows/release.yml`。推送 `v*` 标签触发签名构建与 GitHub Release。

需要配置的仓库 Secrets：

| Secret | 内容 |
| --- | --- |
| `XIAOYU_KEYSTORE_BASE64` | keystore 文件的 Base64 文本 |
| `XIAOYU_KEYSTORE_PASSWORD` | store 口令 |
| `XIAOYU_KEY_ALIAS` | 密钥别名（为空时脚本回退为 `xiaoyu`） |
| `XIAOYU_KEY_PASSWORD` | key 口令 |

发布流程：

1. 确认 `CHANGELOG.md` 已准备好。
2. 打标签并推送：`git tag v1.0.0`、`git push origin v1.0.0`。
3. CI 依次执行 `:soul:test`、`:app:assembleRelease`，重命名产物并生成校验和。
4. 产物：`app-v1.0.0-armeabi-v7a.apk`、`app-v1.0.0-arm64-v8a.apk`、`app-v1.0.0-x86_64.apk`、`app-v1.0.0-SHA256SUMS.txt`。
5. GitHub Release 自动创建；随后 CI 尝试把版本条目写回 `CHANGELOG.md`。

本地校验下载的 APK：

```powershell
Get-FileHash .\app-v1.0.0-arm64-v8a.apk -Algorithm SHA256
```

与 `app-v1.0.0-SHA256SUMS.txt` 中的值比对。

## 6. 常见问题

### 6.1 首次构建卡在下载 Gradle / 依赖

- 现象：`Downloading https://services.gradle.org/...` 长时间无进度。
- 处理：确认网络可访问 Gradle 与 Maven 仓库；公司网络需配置代理；代理配置写在 `gradle.properties`（不要提交个人代理口令）。下载中断后重试即可，Gradle 会复用已下载部分。
- 确认没有混用系统 Gradle：始终使用 `.\gradlew.bat`。

### 6.2 安装失败：签名不一致

- 现象：`INSTALL_FAILED_UPDATE_INCOMPATIBLE` 或 `签名不一致`。
- 原因：设备上已装同包名的 Debug 包或另一份 keystore 签名的 Release 包。
- 处理：

```powershell
adb uninstall com.xiaoyu.app
.\gradlew.bat :app:installDebug
```

覆盖安装正式包时，必须使用同一份 keystore。卸载会删除本地数据库，请先在 App 内创建备份。

### 6.3 语音输入不可用

- 现象：点语音按钮提示「这台设备没有可用的语音输入」。
- 原因：语音输入走系统 `RecognizerIntent`（`zh-CN`），App 不申请录音权限，也不内置识别引擎。
- 处理：在系统设置中启用支持中文的语音服务 / 输入法语音输入；模拟器通常没有识别服务，建议用真机。
- 网络断开时，部分系统的在线识别也会失败。

### 6.4 收不到主动消息

- 确认「设置 → 相处方式 → 允许她主动找你」已开启，且 Android 13+ 已授予通知权限。
- 检查「免打扰时段」是否覆盖当前时间。
- Android 12+ 未授予精确闹钟权限时，系统会退化为 ±10 分钟窗口，可能延迟。
- 部分厂商系统会限制后台，需允许自启动 / 关闭省电限制。

### 6.5 生图或朗读没有反应

- 仅在「本地 AI 服务」配置成功且手机能访问网关时可用；聊天本身不依赖它。
- 检查网关地址、Token，以及电脑防火墙是否放行端口。
- 首次生成需要等待异步任务轮询，网络慢时耗时更长。

### 6.6 恢复备份后需要重新填 Key

备份中的数据库包含设置表，但设置表使用本机 Keystore 密钥加密。换机恢复后，旧密钥不可用，设置可能回退默认值，需要重新填写 API Key 与本地网关 Token。聊天、记忆、日记等数据不受影响。

### 6.7 静态检查命令没有失败

`ktlintCheck` 与 `detekt` 默认不阻断构建（`isIgnoreExitValue`），需要严格模式：

```powershell
.\gradlew.bat ktlintCheck detekt -PstrictStaticCheck
```

## 7. 相关文档

- 架构与数据流：[ARCHITECTURE.md](ARCHITECTURE.md)
- 人格包格式：[PERSONA_FORMAT.md](PERSONA_FORMAT.md)
- 表情包格式：[STICKER_FORMAT.md](STICKER_FORMAT.md)
- 隐私说明：[PRIVACY.md](PRIVACY.md)
- 贡献指南：[CONTRIBUTING.md](CONTRIBUTING.md)
