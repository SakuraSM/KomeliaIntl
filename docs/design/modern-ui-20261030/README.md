# Komelia 现代阅读界面完整视觉稿

2026-10-30。沿用已确认三页，整理18张：16页主流程与两页阅读补充。设计前的应用代码检查点为 `68b2daf7`。原生与EPUB实现已完成控件校准，2026-11-03更新的[对齐标尺](MEASUREMENTS.md)和[验证记录](../../verification/modern-ui-alignment-20261102.md)记录当前实现与运行边界。

- [实现后的18页运行对照](../../verification/modern-ui-alignment-20261102-assets/index.html)
- [逐页浏览](index.html)
- [完整总览](overview.png)
- [主页面总览](main-overview.png)
- [交互对应](INTERACTIONS.md)
- [设计规则](DESIGN-SYSTEM.md)
- [整理与校对边界](VISUAL-REVIEW.md)
- [生成与尺寸记录](generation-record.json)

| 编号 | 页面 | 目标逻辑尺寸 | 独立原图 |
|---|---|---|---|
| 01 | 首页 | 390 × 844 | [PNG](screens/01-home.png) |
| 02 | 书库 | 390 × 844 | [PNG](screens/02-library.png) |
| 03 | 丛书卷册 | 390 × 844 | [PNG](screens/03-series.png) |
| 04 | 文字书详情 | 390 × 844 | [PNG](screens/04-ebook-detail.png) |
| 05 | 漫画单册详情 | 390 × 844 | [PNG](screens/05-comic-detail.png) |
| 06 | 本地与下载 | 390 × 844 | [PNG](screens/06-local-downloads.png) |
| 07 | 搜索结果 | 390 × 844 | [PNG](screens/07-search.png) |
| 08 | 设置首页 | 390 × 844 | [PNG](screens/08-settings.png) |
| 09 | 筛选与显示 | 390 × 844 | [PNG](screens/09-filters.png) |
| 10 | EPUB 目录 | 390 × 844 | [PNG](screens/10-epub-contents.png) |
| 11 | 文字阅读设置 · TTU | 390 × 844 | [PNG](screens/11-text-reader-settings.png) |
| 12 | 漫画与 PDF 设置 | 390 × 844 | [PNG](screens/12-image-reader-settings.png) |
| 13 | 文字书详情 · 浅色 | 390 × 844 | [PNG](screens/13-ebook-light.png) |
| 14 | 丛书卷册 · 浅色 | 390 × 844 | [PNG](screens/14-series-light.png) |
| 15 | 平板适配 | 834 × 1194 | [PNG](screens/15-tablet.png) |
| 16 | 桌面适配 | 1440 × 1024 | [PNG](screens/16-desktop.png) |
| 17 | Komga EPUB 设置 | 390 × 844 | [PNG](screens/17-komga-settings.png) |
| 18 | 纯阅读状态 | 390 × 844 | [PNG](screens/18-immersive-reading.png) |

## 依据与交付边界

设计参考 [Material3](https://developer.android.com/develop/ui/compose/designsystems/material3)、[自适应列表详情](https://developer.android.com/develop/ui/compose/layouts/adaptive/list-detail)与 [Materials](https://developer.apple.com/design/human-interface-guidelines/materials)。内容优先、操作层分离是本产品的取舍，视觉稿没有使用平台专属 UI 资源包。

前三页参考中的首页、书库、TTU设置按原图保留，其余使用内置 ImageGen 分别生成。书籍、章节、数字与正文均为示例，不是用户真实数据。PNG保留原始生成尺寸，没有拉伸成目标尺寸；目标与实际像素分别记录。

静态稿和浏览页不代表应用实现、精确像素、可访问性、性能或阅读进度已经验收。各页实际提示词保存在 `prompts/`。
