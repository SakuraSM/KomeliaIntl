# Komelia 中文术语表 (zh-CN Glossary)

本术语表记录 Komelia 简体中文翻译中使用的统一译法，供所有翻译贡献者遵循。
新增或修改翻译时，请优先使用本表中的术语；遇到表中未列出的概念时，请先与维护者讨论再确定译法，并将新术语补充到本表。

## 通用原则

- **保持中文语义易懂通顺**，优先使用动词短语而非生硬直译（如 "Reset filters" → "重置筛选" 而非 "重置过滤器"）。
- **按钮使用动词，标题使用名词**；同一概念在全应用统一译法。
- **数字与标点**：阿拉伯数字保留半角；句末标点中文场景用全角（"，。：；"），按钮/标签等通常不加句号。
- **占位符与变量**：保持 Kotlin 字符串模板顺序与英文一致；必要时用具名 lambda 形式 `(count: Int) -> String` 调整语序。
- **复数**：中文无单复数，无需特殊处理；只需保证模板可读。
- **不译内容**：
  - 品牌名：Komelia / Komga / Komf
  - 第三方元数据提供方：AniList / Bangumi / MangaDex / Kitsu / MyAnimeList / MangaUpdates / BookWalker / NamiComi / ComicVine / Hentag / Kodansha / Nautiljon / YenPress / Viz / Webtoons / MangaBaka 等
  - 协议关键字：HTTP、URL、SSL、API 等（首次出现可视情况补中文括注）
  - 文件格式名：CBZ、CBR、EPUB、PDF、ZIP、RAR、ONNX 等
  - 图像处理算法：Lanczos、Mitchell、Catmull-Rom、Magic Kernel Sharp、ESRGAN、MangaJaNai、Bilinear、Bicubic 等

## 核心术语

| 英文 | 简体中文 | 备注 |
|------|---------|------|
| Library | 书库 | 不译为"图书馆" |
| Series | 系列 | |
| Book | 书籍 | |
| Collection | 合集 | 不译为"收藏" |
| Read List | 阅读列表 | |
| Oneshot | 单本 | 不译为"独立卷" |
| Webtoon | 条漫 (Webtoon) | 首次出现保留括注 |
| Page | 页 / 页面 | |
| Thumbnail | 缩略图 | |
| Cover | 封面 | |
| Tag | 标签 | |
| Genre | 类型 | |
| Author | 作者 | |
| Publisher | 出版商 | |
| Age rating | 年龄分级 | |
| Reading direction | 阅读方向 | |
| Continuous reader | 连续阅读器 | |
| Paged reader | 分页阅读器 | |
| Panels reader | 分镜阅读器 | |
| Scan | 扫描 | |
| Scan interval | 扫描间隔 | |

## 状态与动作

| 英文 | 简体中文 |
|------|---------|
| Ongoing | 连载中 |
| Ended | 已完结 |
| Hiatus | 休刊 |
| Abandoned | 弃坑 |
| Read | 已读 |
| Unread | 未读 |
| In progress | 阅读中 |
| Reset | 重置 |
| Search | 搜索 |
| Save changes | 保存更改 |
| Discard | 放弃 |
| Show more / Show less | 展开 / 收起 |
| Filter | 筛选 |
| Sort by | 排序方式 |
| Ascending / Descending | 升序 / 降序 |

## 设置与偏好

| 英文 | 简体中文 |
|------|---------|
| App Theme | 应用主题 |
| Dark / Light / Darker | 深色 / 浅色 / 更暗 |
| Language | 语言 |
| System default | 跟随系统 |
| Server settings | 服务器设置 |
| Server port | 服务器端口 |
| Base URL | 基础 URL |
| Remember me | 记住登录 |
| Requires restart to take effect | 重启后生效 |

## 图像处理

图像处理参数原则上保留英文专有名词，避免误导用户：

| 英文 | 简体中文 |
|------|---------|
| Upsampling mode | 上采样模式 |
| Downsampling kernel | 下采样核 |
| Nearest neighbour | 最近邻 (Nearest neighbour) |
| Bilinear | 双线性 (Bilinear) |
| Bicubic | 双三次 |
| ONNX Runtime | ONNX Runtime（不译） |
| ONNX Runtime execution provider | ONNX Runtime 执行后端 |
| Upscale mode | 放大模式 |
| User specified model | 用户指定模型 |
| MangaJaNai preset | MangaJaNai 预设 |

## 错误信息

错误代码（如 `ERR_1000`）保持原样，仅翻译错误描述文本。详见 `EnStrings.kt` / `ZhCnStrings.kt` 中的 `errorCodes` 部分。
