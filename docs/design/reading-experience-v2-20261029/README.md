# Komelia 阅读体验 v2 视觉稿

深色磨玻璃主稿，冷白对照，紧凑布局。此处包含 16 张独立高清 PNG、总览、可点击预览和组件规范。共享 UI 已按此方向实施，实际检查见[UI 对齐验证](../../verification/reading-experience-ui-20261029.md)。本目录的图片仍是设计稿。

- [主页面总览](main-overview.png)
- [全部页面总览](overview.png)
- [组件规范](DESIGN-SYSTEM.md)
- [设计依据与能力边界](RESEARCH.md)
- [生成与导出记录](generation-record.json)

| 编号 | 页面 | 独立 PNG |
|---|---|---|
| 01 | 首页 | [查看](screens/01-home.png) |
| 02 | 书库与丛书总览 | [查看](screens/02-library.png) |
| 03 | 丛书卷册页 | [查看](screens/03-series-detail.png) |
| 04 | 文字书详情 | [查看](screens/04-ebook-detail.png) |
| 05 | 漫画单册详情 | [查看](screens/05-comic-detail.png) |
| 06 | 本地与下载 | [查看](screens/06-local.png) |
| 07 | 搜索结果 | [查看](screens/07-search.png) |
| 08 | 设置首页 | [查看](screens/08-settings.png) |
| 09 | 筛选面板 | [查看](screens/09-filter.png) |
| 10 | EPUB 目录 | [查看](screens/10-toc.png) |
| 11 | 文字阅读设置 | [查看](screens/11-text-settings.png) |
| 12 | 漫画与 PDF 阅读设置 | [查看](screens/12-image-settings.png) |
| 13 | 文字书详情，冷白对照 | [查看](screens/13-ebook-light.png) |
| 14 | 丛书页，冷白对照 | [查看](screens/14-series-light.png) |
| 15 | 平板适配 | [查看](screens/15-tablet.png) |
| 16 | 桌面适配 | [查看](screens/16-desktop.png) |

## 打开预览

在仓库根目录运行以下命令，然后打开终端显示地址下的 `/preview/`：

```sh
python3 -m http.server 18879 --bind 127.0.0.1 --directory docs/design/reading-experience-v2-20261029
```

预览使用普通 HTML、CSS 和 JavaScript，无需安装前端依赖。点击总览图片可以打开页面。筛选选项、搜索示例过滤、章节展开和开关可操作，未接入的管理功能会显示说明。`?screen=ebook&fontScale=1.3` 可查看大字号。

## 交付边界

页面使用一致的设计示例，核心封面由内置 ImageGen 生成，其他示例采用排版封面。Komelia 图标来自仓库。PNG 按逻辑尺寸的整数倍直接渲染，没有拉伸画面；手机为 3 倍，平板为 3 倍，桌面为 2 倍。

应用沿用真实书籍、目录、下载和阅读状态，重组首页、详情、卷册列表、设置与阅读浮层。静态稿和 HTML 预览不作为真实 Compose、原生读屏、帧率或手势验收。未合并或发布。
