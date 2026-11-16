# 贡献指南（CONTRIBUTING）

感谢你愿意一起把小鱼做得更好。提交代码前请先读本页；涉及安全问题时请改用 [SECURITY.md](SECURITY.md) 的私密流程。

## 1. 开始之前

- 使用 JDK 21 与仓库自带 Gradle Wrapper，环境细节见 [SETUP.md](SETUP.md)。
- 不要提交 `key.properties`、`key.properties.*`、`keys/`、`*.jks`、`*.keystore`、`*.p12`、`*.pem`。
- 不要提交真实 API Key、聊天记录、备份文件、导出的 Markdown、截图中的私人内容。
- 不要修改版本号、发布 workflow 或 CHANGELOG 的已发布段落，除非 PR 明确针对发布流程。

## 2. 分支与提交

### 2.1 分支命名

| 前缀 | 用途 | 示例 |
| --- | --- | --- |
| `feat/` | 新功能 | `feat/memory-recall-weight` |
| `fix/` | 缺陷修复 | `fix/sticker-cooldown` |
| `refactor/` | 不改变行为的重构 | `refactor/prompt-assembler` |
| `docs/` | 文档 | `docs/privacy-permissions` |
| `test/` | 测试 | `test/forgetting-curve` |
| `chore/` | 构建、依赖、工具 | `chore/bump-agp` |

从 `main` 切出，保持一个 PR 只做一件事。大改动请先开 Issue 描述动机与方案。

### 2.2 Conventional Commits

格式：

```text
<type>(<scope>): <subject>

<body 可选>

<footer 可选>
```

| type | 含义 |
| --- | --- |
| `feat` | 新功能 |
| `fix` | 修复 |
| `refactor` | 重构 |
| `perf` | 性能 |
| `docs` | 文档 |
| `test` | 测试 |
| `build` | 构建 / 依赖 |
| `ci` | CI |
| `chore` | 杂项 |

示例：

```text
feat(chat): 支持引用回复时携带被引用消息
fix(memory): 回收站恢复后未重置删除时间
docs(setup): 补充 CI Secrets 配置说明
```

scope 建议使用模块或页面名：`soul`、`data`、`brain`、`service`、`app`、`chat`、`memory`、`settings`、`sticker`、`persona`、`backup`。

## 3. 启用提交前钩子

仓库自带 `.githooks/pre-commit`，会拒绝暂存区中的签名文件、密钥配置与常见密钥模式（`sk-...`、AWS、Google API Key、私钥头、Slack Token 等）。启用：

```bash
git config core.hooksPath .githooks
```

- Windows 下请确保 Git 使用可执行 `sh`（Git for Windows 自带）。
- 安装了 `gitleaks` 时优先调用 `gitleaks protect --staged`；未安装时使用内置正则兜底。
- 钩子失败时**不要**用 `--no-verify` 绕过；先移除敏感内容。确属误报时请在 PR 中说明并调整规则。
- 钩子只检查暂存区，不会扫描历史提交。若曾误提交密钥，请立即在服务商侧吊销并清理历史。

## 4. 代码风格

### 4.1 工具

| 工具 | 配置 / 命令 | 说明 |
| --- | --- | --- |
| ktlint | `./gradlew ktlintCheck` | 1.8.0，CLI 形式；默认不阻断构建 |
| detekt | `./gradlew detekt` | 1.23.8 |
| Android Lint | `./gradlew lint` | AGP 自带；当前 `abortOnError = false`，不会因警告阻断构建 |
| 模块依赖守卫 | `./gradlew assertModuleDeps` | 检查禁止依赖与跨模块引用 |

Windows PowerShell：

```powershell
.\gradlew.bat ktlintCheck detekt lint
.\gradlew.bat assertModuleDeps
```

严格模式（ktlint / detekt 检查失败即构建失败，提交 PR 前建议执行）：

```powershell
.\gradlew.bat ktlintCheck detekt -PstrictStaticCheck
```

`lint` 的 `abortOnError` 为 `false`，请打开 `app/build/reports/lint-results.html` 自行确认没有新增问题。

### 4.2 约定

- 使用 4 空格缩进、Kotlin 官方代码风格；不提交格式化噪声。
- 新增依赖前先确认版本目录 `gradle/libs.versions.toml` 中是否已有；优先复用。
- 保持分层：UI 不直接访问 Room DAO 或 OkHttp；`:soul` 不出现 `android.*` / `androidx.*`。
- 不要为了通过检查而全局关闭规则；必要豁免使用最小范围的 `@Suppress` 并写明原因。
- 新增用户可见文案放进 `app/src/main/res/values/strings.xml`（或对应语言资源），不要在 Compose 里散落硬编码。
- 注释只写“为什么”，不写“做了什么”。

## 5. 测试

纯 JVM 测试是当前的主要测试面：

```powershell
.\gradlew.bat :soul:test
.\gradlew.bat :data:testDebugUnitTest
```

覆盖率与门禁：

```powershell
.\gradlew.bat :soul:check
```

`check` 会跑测试、生成报告并执行 `jacocoTestCoverageVerification`：`:soul` 行覆盖低于 80% 直接失败（当前 97.2%）。报告位置：`soul/build/reports/jacoco/test/html/index.html`。CI 的发布流水线执行的就是 `:soul:check`。

现有测试覆盖提示词库、记忆与遗忘曲线、情绪与好感度、主动消息与日记节奏、分段后处理、表情提示词、备份加密等。新增逻辑请优先在 `:soul` 写纯 JVM 测试；涉及 Room、网络、WorkManager 的改动至少补一个可复现的手动验证步骤写在 PR 里。

仓库当前没有 `androidTest`，请勿在 PR 中声称已跑过仪器测试。

## 6. 数据库与提示词变更

### 6.1 Room

- 提高 `XiaoyuDatabase` 的 `version`，并在 `MIGRATIONS` 中新增 `MIGRATION_N_N+1`。
- 保数据优先，不要使用 `fallbackToDestructiveMigration`。
- 新增表 / 字段时同步更新 `Entities.kt`、DAO、`ARCHITECTURE.md` 的表清单。
- 在 PR 描述中写清旧版本升级路径与回滚影响。

### 6.2 提示词

- 内置模板在 `app/src/main/assets/prompts/*.md`；新增 key 时同步 `PromptKeys` 与默认文案。
- 修改提示词属于行为变更，请在 PR 中贴出前后差异与预期影响。
- 不要在模板里写死具体用户信息或密钥。

## 7. PR 要求

提交 PR 前确认：

- [ ] 从最新 `main` 切出，已 rebase / 合并最新代码。
- [ ] `.\gradlew.bat :soul:check` 通过（含 80% 覆盖率门禁）。
- [ ] `.\gradlew.bat assertModuleDeps` 通过。
- [ ] 静态检查无新增问题；严格模式下通过。
- [ ] 没有提交 `key.properties`、`keys/`、签名文件、真实密钥、聊天记录、备份或私人截图。
- [ ] 新增 / 修改的用户文案已进资源文件。
- [ ] 行为变更已更新相关文档（README、SETUP、ARCHITECTURE、格式文档、CHANGELOG 的 `Unreleased`）。
- [ ] PR 描述包含：动机、实现要点、验证方式、风险与回滚方案。
- [ ] 涉及 UI 的改动附截图或录屏（请先脱敏）。

PR 标题遵循 Conventional Commits；描述使用中文或英文均可，但同一 PR 内保持一致。维护者可能要求拆分过大的 PR。

## 8. 发布相关（仅维护者）

- 版本号由 `-PxiaoyuVersionName` / `-PxiaoyuVersionCode` 注入；CI 使用 tag 与 run number。
- 推送 `v*` 标签会触发 `.github/workflows/release.yml`，需要 `XIAOYU_KEYSTORE_BASE64`、`XIAOYU_KEYSTORE_PASSWORD`、`XIAOYU_KEY_ALIAS`、`XIAOYU_KEY_PASSWORD` 四个 Secrets。
- `CHANGELOG.md` 由发布流程尝试写回；冲突时以 Release 产物与 tag 为准，手动修正后再提交。
- `security.yml` 会在 push / PR 上运行 gitleaks，请确保没有历史密钥泄露。

## 9. 行为准则

参与本项目即表示同意遵守 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。技术分歧可以争论，人身攻击、骚扰、歧视与泄露他人隐私一律不接受。
