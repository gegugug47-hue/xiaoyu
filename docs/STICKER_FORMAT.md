# 表情包格式（STICKER_FORMAT）

`.xystickers` 是表情包分享格式，本质是 ZIP：一个 `manifest.json` + 一组图片 + 可选封面。它不包含聊天记录、密钥或用户数据。

实现位置：`data/src/main/kotlin/com/xiaoyu/app/data/sticker/StickerPackArchive.kt`。

## 1. 目录结构

```text
my-stickers.xystickers      # ZIP 容器
├── manifest.json           # 必需，UTF-8 JSON，含元信息与表情清单
├── preview.png             # 可选封面（导出时必写）
├── stickers/
│   ├── 01.webp
│   ├── 02.webp
│   └── 03.gif
```

- 扩展名只是约定：导入按内容识别 ZIP，不校验后缀。
- 导出文件实际写入缓存目录并使用 `.zip` 后缀，MIME 为 `application/zip`。
- 压缩包内的路径必须使用正斜杠，不能有 `..`、绝对路径或盘符。

## 2. manifest.json

```json
{
  "format": 1,
  "name": "小猫日常",
  "author": "example",
  "version": "1.0.0",
  "license": "CC-BY-4.0",
  "source_url": "https://example.com/pack",
  "created_at": "2026-11-15T08:00:00Z",
  "cover": "preview.png",
  "stickers": [
    {
      "file": "stickers/01.webp",
      "name": "打招呼",
      "tags": ["开心", "招手"],
      "mood_tags": ["joy"],
      "mime": "image/webp"
    },
    {
      "file": "stickers/02.webp",
      "name": "无语",
      "tags": ["吐槽"],
      "mood_tags": ["speechless"],
      "mime": "image/webp"
    }
  ]
}
```

### 2.1 包级字段

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `format` | Int | 否 | `1` | 仅支持 `1` |
| `name` | String | **是** | 无 | 包名，trim 后不能为空；与作者一起作为重复包判定 |
| `author` | String | 否 | `""` | 作者；同名不同作者允许共存 |
| `version` | String | 否 | `"1.0.0"` | 包版本，不参与校验 |
| `license` | String | 否 | `"Unknown"` | 为 `Unknown` / 空时导入成功但会给出版权提醒 |
| `source_url` | String | 否 | `""` | 来源链接，仅展示 |
| `created_at` | String | 否 | `""` | ISO-8601 UTC，仅展示 |
| `cover` | String? | 否 | `null` | 封面路径；v1.0.0 导入不使用该字段，封面取第一张表情 |
| `stickers` | Item[] | **是** | `[]` | 非空，最多 100 条 |

### 2.2 单条表情字段

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `file` | String | **是** | 无 | 包内相对路径；文件不存在会导致整包拒绝 |
| `name` | String | 否 | 文件名 | 表情名，用于展示与提示词识别 |
| `tags` | String[] | 否 | `[]` | 语义标签，参与表情匹配 |
| `mood_tags` | String[] | 否 | `[]` | 心情标签，如 `joy`、`sad`、`tease` |
| `mime` | String | 否 | `image/webp` | 扩展名无法识别时用它推断格式 |

未知字段会被忽略，便于向前兼容。

## 3. 限制

| 项目 | 限制 |
| --- | --- |
| 格式版本 | `format = 1` |
| 包名 | trim 后不能为空 |
| 表情数量 | 1~100 张 |
| 单文件 | ≤ 16MB |
| 整包解压后 | ≤ 30MB |
| ZIP 条目数 | ≤ 600（`SafeZip` 通用限制） |
| ZIP 总解压体积 | ≤ 40MB（`SafeZip` 通用限制） |
| 图片格式 | `webp`、`png`、`jpg`、`jpeg`、`gif` |
| 封面 | 导出固定写 `preview.png`；导入忽略 `cover`，使用第一张表情 |
| 动图 | Telegram 的 `.webm`（视频表情）与 `.tgs`（动态表情）不支持，会在警告中跳过 |

任一清单项缺失文件或格式不支持时，**整包拒绝**，不会写入半成品；已解压的临时目录会被删除。

## 4. 导入流程

### 4.1 通过 `.xystickers` 压缩包

1. 打开「设置 → 表情包」→ 管理表情包。
2. 选择已有包 →「导入表情」→「选择压缩包」，或直接导入新包。
3. App 将文件复制到缓存，交给后台管线读取。
4. `SafeZip.open` 校验条目数、单文件与总体积、路径安全。
5. 读取 `manifest.json`，校验 `format`、包名、表情数量。
6. 若已存在「同名 + 同作者」的包，直接返回既有包，`alreadyExists = true`，不重复导入。
7. 逐条校验文件存在性与扩展名；复制到 `files/stickers/<packId>/`，读取宽高，写入 `stickers` 表。
8. 创建 `sticker_packs` 记录，`source = "zip"`。
9. 完成提示：「已导入表情包 <包名>，共 N 张」；许可证缺失时额外提示版权风险。

### 4.2 从相册 / Telegram 批量导入

- 入口：「导入表情」→「从相册选择」，单次最多 **30 个**文件。
- 也支持把 Telegram 导出的静态表情图片批量导入，`source = "telegram"`，上限 **100 张**，超出部分跳过并给出警告。
- 同名包会自动改名为 `名称 (2)`、`名称 (3)`，不会覆盖已有包。
- 可开启「自动打标」：配置了视觉模型时，后台为表情生成标签与心情标签；未配置时仅入库，不阻断导入。

### 4.3 导入后的行为

- 表情默认启用，按 `sort` 排序。
- 聊天提示词只携带最多 40 个表情的目录；她按标签与当前心情选择，并受单轮数量与冷却限制。
- 无效编号、冷却中的表情会被忽略，不影响文字回复。

## 5. 导出流程

1. 在表情包管理页选择「分享表情包」。
2. 只导出有真实文件路径且文件存在的表情；`builtin://` 内置表情不会导出。
3. 若包内没有可导出文件，导出失败并提示。
4. 清单按顺序重命名为 `stickers/01.webp`、`02.png`……
5. 封面固定为 `preview.png`，内容取封面表情的原始字节（因此扩展名可能仍是 webp 内容）。
6. 导出总体积超过 30MB 时失败，建议拆包。
7. 通过 `FileProvider` 分享，MIME 为 `application/zip`；可手动改名为 `.xystickers`。

## 6. 最小可用示例

目录：

```text
cat/
├── manifest.json
└── stickers/
    ├── 01.webp
    └── 02.png
```

`manifest.json`：

```json
{
  "format": 1,
  "name": "小猫日常",
  "author": "example",
  "license": "CC0-1.0",
  "stickers": [
    { "file": "stickers/01.webp", "name": "打招呼", "tags": ["开心"], "mood_tags": ["joy"] },
    { "file": "stickers/02.png", "name": "困了", "tags": ["睡觉"], "mood_tags": ["tired"] }
  ]
}
```

打包（Windows PowerShell）：

```powershell
Compress-Archive -Path .\cat\* -DestinationPath .\小猫日常.xystickers
```

macOS / Linux：

```bash
cd cat && zip -r ../小猫日常.xystickers manifest.json stickers
```

不要把 `cat` 文件夹本身压进包里，否则 `manifest.json` 会多一层目录，导入会报「压缩包里缺少 manifest.json」。
