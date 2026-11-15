# 人格包格式（PERSONA_FORMAT）

`.xypersona` 是一个人设分享包，本质是 ZIP。它只携带**身份设定、可选风格、可选提示词覆盖和头像**，不包含聊天记录、记忆、日记或任何密钥。

实现位置：`data/src/main/kotlin/com/xiaoyu/app/data/persona/PersonaPackArchive.kt`。

## 1. 文件结构

```text
my-persona.xypersona        # ZIP 容器
├── manifest.json           # 必需，UTF-8 JSON
├── identity.md             # 必需，人格核心设定
├── styles/                 # 可选，0~N 个表达风格
│   ├── 01-温柔.md
│   └── 02-毒舌.md
├── prompts/                # 可选，该人格的提示词模板
│   └── persona_style.md
└── avatar.png              # 可选头像（由 manifest.avatar 指定路径）
```

- 扩展名只是约定：导入时按文件内容识别 ZIP，不依赖 `.xypersona` 后缀。
- 导出文件实际写入缓存目录并使用 `.zip` 后缀，分享时 MIME 为 `application/zip`；手动重命名为 `.xypersona` 不影响导入。
- 目录名必须使用正斜杠；不接受绝对路径、盘符与 `..`。

## 2. manifest.json 字段

```json
{
  "format": 1,
  "name": "阿澈",
  "author": "你的名字",
  "version": "1.0.0",
  "license": "CC-BY-4.0",
  "description": "一个话不多但很会接住情绪的插画师。",
  "minAppVersion": "1.0.0",
  "identity_file": "identity.md",
  "styles": ["styles/01-安静.md"],
  "avatar": "avatar.png",
  "variables": { "称呼": "你" },
  "content_warning": "无",
  "tags": ["治愈", "慢热"],
  "created_at": "2026-11-15T08:00:00Z"
}
```

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `format` | Int | 否 | `1` | 当前只支持 `1`；大于 1 会被拒绝 |
| `name` | String | **是** | 无 | 人格名，不能为空；同时作为去重键与展示名 |
| `author` | String | 否 | `""` | 作者署名 |
| `version` | String | 否 | `"1.0.0"` | 人格包自身版本，不参与校验 |
| `license` | String | 否 | `"Unknown"` | 许可证；`Unknown` 时导入不会失败，但请尽量填写 |
| `description` | String | 否 | `""` | 简介 |
| `minAppVersion` | String | 否 | `"1.0.0"` | 预留字段，v1.0.0 **不做强制校验** |
| `identity_file` | String | 否 | `"identity.md"` | 身份文件在包内的相对路径 |
| `styles` | String[] | 否 | `[]` | 风格文件相对路径列表；缺失或为空会跳过 |
| `avatar` | String? | 否 | `null` | 头像路径；文件缺失时静默忽略 |
| `variables` | Map<String,String> | 否 | `{}` | 预留变量，v1.0.0 不参与提示词替换 |
| `content_warning` | String | 否 | `"无"` | 内容提示，仅展示 |
| `tags` | String[] | 否 | `[]` | 标签，仅展示 |
| `created_at` | String | 否 | `""` | ISO-8601 UTC 时间，仅展示 |

未知字段会被忽略，便于向前兼容。

## 3. 正文文件

### identity.md

- 纯 Markdown / 纯文本，导入时 `trim()` 后要求非空。
- 建议写「她是谁、怎么说话、在意什么、绝不会做什么」，而不是堆砌外貌词。
- 超过 **4000 字**会先返回确认提示；用户确认后才会真正导入。过长的设定会稀释人格，建议控制在 1000~2500 字。

### styles/*.md

- 每个文件是一种表达风格。
- 文件名去掉扩展名与开头的 `数字-` 前缀后作为风格名，例如 `01-安静.md` → `安静`。
- 空文件会被跳过，不会创建空风格。

### prompts/*.md

- 文件名（不含扩展名）对应提示词 key，例如 `persona_style.md`。
- 导入时**不会覆盖全局模板**，统一复制到 `files/persona_prompts/<包名>/`，并提示用户手动应用。
- 单文件上限 256KB。

### avatar

- 由 `manifest.avatar` 指向，支持常见图片扩展名；导入后保存为 `files/personas/<包名>/avatar.<ext>`。
- 上限 4MB。
- v1.0.0 的导出流程不写入头像，因此导出包默认没有头像。

## 4. 校验与安全规则

| 规则 | 限制 |
| --- | --- |
| ZIP 条目数 | ≤ 600 |
| 单文件解压体积 | ≤ 16MB |
| 全部文件解压体积 | ≤ 40MB |
| `manifest.json` | ≤ 256KB |
| `identity.md` | ≤ 1MB，且非空 |
| 每个风格文件 | ≤ 256KB |
| 每个提示词文件 | ≤ 256KB |
| 头像 | ≤ 4MB |
| 路径 | 拒绝空名、`\0`、绝对路径、盘符、`..`、`//` |
| 版本 | `format` 必须为 1 |

去重规则：

- 身份：`name` 相同且 `identity.md` 去除首尾空白后完全一致 → 复用已有身份，不新增。
- 风格：`name` 相同且正文一致 → 复用已有风格。
- 提示词：按文件覆盖写入独立目录，不参与去重。

导入是**追加式**的：不会删除或覆盖你已有的身份与风格。重复导入同一个包通常只会增加跳过计数。

## 5. 导入与导出入口

| 操作 | 入口 |
| --- | --- |
| 导入 | 「设置 → 人设 → 导入人格包」，或聊天页顶部「她是谁」→ 导入 |
| 导出 | 「她是谁」页面 → 分享人格包（导出当前身份 + 全部风格 + 你的提示词覆盖） |
| 应用 | 导入后到「人设」里选择身份 / 风格；提示词需要到「提示词」入口手动应用 |

导入时的 toast 会显示：身份名、风格数量、模板数量（并注明「未自动应用」）。需要二次确认时只显示提示，不会写入数据库。

## 6. 最小可用示例

目录：

```text
minimal/
├── manifest.json
└── identity.md
```

`manifest.json`：

```json
{
  "format": 1,
  "name": "林岸",
  "author": "example",
  "license": "CC0-1.0",
  "description": "一个住在海边、说话很慢的人。"
}
```

`identity.md`：

```markdown
# 林岸

你是林岸，28 岁，在海边经营一家旧书店。

说话方式：
- 句子短，语速慢，很少用感叹号。
- 会先接住对方的情绪，再给建议；不想说教。
- 不主动提自己是 AI、模型或程序。

在意的事：
- 天气、旧书、晚饭吃什么。
- 对方有没有好好睡觉。

边界：
- 不假装知道没被告知的事。
- 不提供医疗、法律或投资结论。
```

打包（Windows PowerShell）：

```powershell
Compress-Archive -Path .\minimal\* -DestinationPath .\林岸.xypersona
```

macOS / Linux：

```bash
cd minimal && zip -r ../林岸.xypersona manifest.json identity.md
```

注意：使用 `Compress-Archive` 时不要直接把 `minimal` 文件夹本身压进去，否则 `manifest.json` 会多一层目录，导入会报「压缩包里缺少 manifest.json」。
