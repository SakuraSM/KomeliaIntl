# Komelia UI 二次优化视觉 QA

## 验收环境

- Android 模拟器：1080 × 2400 px，420 dpi（约 411 × 914 dp）
- 语言：简体中文
- 数据：`komga.nings.top` 真实书库会话；凭据未写入仓库或测试日志
- 状态覆盖：首页、搜索、设置、首页分组管理、系列详情、书籍详情、PDF 阅读、EPUB 阅读

## 对比输入

| 页面 | 参考图 | 实现截图 | 同屏对比 |
| --- | --- | --- | --- |
| 首页 | `/var/folders/6k/w12ht07j5p95l2qn8_63g1zm0000gn/T/codex-clipboard-bccdeb2e-0f51-4262-8688-53153be80851.png`（786 × 1548） | `/private/tmp/komelia-home-after-2.png`（1080 × 2400） | `/private/tmp/komelia-home-comparison.png` |
| 搜索 | `/var/folders/6k/w12ht07j5p95l2qn8_63g1zm0000gn/T/codex-clipboard-cd1d7bf9-6768-4515-b27c-3b463e6cc507.png`（742 × 1470） | `/private/tmp/komelia-search-after.png`（1080 × 2400） | `/private/tmp/komelia-search-comparison.png` |
| 设置 | `/var/folders/6k/w12ht07j5p95l2qn8_63g1zm0000gn/T/codex-clipboard-f313a805-219d-4836-8edb-997956f26738.png`（748 × 1050） | `/private/tmp/komelia-settings-after.png`（1080 × 2400） | `/private/tmp/komelia-settings-comparison.png` |
| 首页分组迭代 | 设置页圆角区块语义与首轮实现 | `/private/tmp/komelia-home-groups-after-fix-2.png`（1080 × 2400） | `/private/tmp/komelia-home-groups-iteration-comparison.png` |

参考图包含设备外框，实现图为模拟器完整屏幕，因此同屏图按内容区高度归一化；视觉判断只比较应用内容，不比较设备外框。

## 全屏与重点区域检查

- 首页：三列封面层级保持清晰；系列卡片统一使用固定 `TitleOnly` 槽位，首页书籍统一使用固定 `TitleWithSupporting` 槽位，同排底边对齐；单行标题垂直居中，无原 72dp 灰色空块。
- 首页导航：页面、卡片、底部导航分别使用近白、白色和极浅蓝紫表面；主色仅用于选中态。顶部只显示“全部 + 当前分组 + 更多”，设置入口已迁移。
- 搜索：分段切换紧凑；结果卡片使用白色高层表面和轻描边；标签可换行，最多显示三项并以 `+N` 汇总，未发现横向裁切或页面溢出。
- 设置：分组采用 16dp 圆角卡片；分隔线只存在于相邻行之间；新增“首页分组”入口。
- 阅读器：PDF 实际打开至 96/201 页；EPUB 实际打开 2184 页套装正文。两者均能显示真实内容、阅读进度与工具栏，未发现固定栏遮挡正文。

## 迭代记录

1. 首轮检查发现 P1：首页分组编辑页未消费系统顶部安全区，标题和返回按钮与状态栏重叠；列表仍使用整行分隔线，与新的圆角分组语义不一致。
2. 修复：增加 `WindowInsets.systemBars` 顶部占位；每个分组改为 16dp 圆角高层表面、1dp 描边和独立间距；拖动态使用 3dp 语义强调描边。
3. 二次同屏复核：状态栏重叠消失，返回/完成/重置操作完整可见；卡片圆角、间距和边界一致，无裁切、错位或横向溢出。

## 遗留问题

- 无阻断问题。真实 2184 页 EPUB 首次打开约需二十秒，加载期间显示进度状态；属于内容体积相关性能观察，不影响本次 UI 验收。

## EPUB 阅读控制专项复核

- 源视觉真值：`/var/folders/6k/w12ht07j5p95l2qn8_63g1zm0000gn/T/codex-clipboard-b706ed70-13aa-4983-8362-92cea5e3ef3f.png`，742 × 1572 px，底部控制栏显示状态。
- 实现证据：`/private/tmp/komelia-epub-chrome-visible-after.png` 与 `/private/tmp/komelia-epub-chrome-hidden-retoggle.png`，均为 1080 × 2400 px、420 dpi；原生 Compose/WebView 屏幕无 CSS viewport 或浏览器缩放。
- 归一化：源图去除设备外框，裁剪为 678 × 1506 px 后缩放至 1080 × 2400 px；实现图保持原始像素。全屏同屏对比为 `/private/tmp/komelia-epub-chrome-full-comparison.png`。
- 重点区域：底部 480 px 控件对比为 `/private/tmp/komelia-epub-chrome-controls-comparison.png`，可清楚判断控件边界、阴影、正文遮挡及独立按钮移除情况。
- 状态：在真实 2184 页 EPUB 中完成“沉浸态 → 中央轻触显示顶部/底部 chrome → 再次中央轻触收起”的完整交互；目录、书签、设置和翻页入口保持可用。

### Findings

- 无 P0/P1/P2。原 P1 问题是左下角“隐藏”按钮脱离底部导航胶囊、重复表达控制栏状态并遮挡正文；修复后独立按钮已移除，阅读控制作为单一 chrome 同步显示和隐藏。
- 字体与文案：删除孤立“隐藏”文案后，底部只保留章节、进度和导航信息，字号与字重未发生回归。
- 间距与布局：底部导航胶囊保持居中，左右留白对称；沉浸态不再有左下角悬浮层，正文可使用完整宽度和底部区域。
- 色彩与令牌：保留既有 `slate-950/90` 半透明阅读控制表面、白色前景和焦点环，未引入新的强调色。
- 图像质量：EPUB 原始封面与正文图片未做重采样或替换；本轮没有新增图像资产或自绘图标。
- 图标与无障碍：沿用 Font Awesome 同族图标；现有控制保持语义标签、焦点环和 40px 视觉按钮，移动端整体胶囊提供足够触控空间。

### Comparison history

1. 修复前：底部控制栏显示时，左下角额外出现“隐藏”胶囊，形成两个视觉中心并覆盖正文。
2. 修复：移除 `ReaderChrome` 的独立 `onToggleFooter` 按钮；将阅读区中央点击改为统一切换顶部与底部 chrome。翻页、切章和自动滚动后仍调用 `hideReaderChrome()`。
3. 修复后：全屏及底部重点区域复核均无独立按钮；连续两次中央点击分别显示和收起整套控件，没有裁切、溢出、残影或无法恢复的问题。

## 首页标签自适应与全局间距复核

### 响应式证据

| 可用宽度 | 截图 | 验收结果 |
| --- | --- | --- |
| 360dp | `/private/tmp/komelia-home-360.png` | 单行显示“全部 + 当前分组 + 更多”；当前长中文标签受剩余宽度约束，无裁切或横向滚动。 |
| 600dp | `/private/tmp/komelia-home-600.png` | 可见普通分组数量随宽度增加；当前分组保持顶部可见，被替换分组进入“更多”。 |
| 1280dp | `/private/tmp/komelia-home-1280.png` | 全部分组可容纳，“更多”自动隐藏；页面内容居中并保持 1200dp 最大宽度。 |

- 412dp 真实设备密度基线：`/private/tmp/komelia-home-spacing.png`；“更多”底部面板：`/private/tmp/komelia-home-more.png`；溢出分组选中并提升后：`/private/tmp/komelia-home-promoted.png`。
- “更多”只显示顶部未出现的两个分组，并保留原配置顺序与项目数量；选择“最近添加的系列”后，该分组提升到顶部选中态，原末尾普通分组返回“更多”。
- 窗口从 360dp、600dp 切换到 1280dp 时没有保留横向滚动状态，也没有出现换行、标签重叠或布局闪烁。

### 页面族与间距

- 搜索：`/private/tmp/komelia-search-spacing.png`。页面边距、结果项间隔与卡片内边距使用响应式布局令牌；标签保持三项加 `+N` 汇总，无横向溢出。
- 设置：`/private/tmp/komelia-settings-spacing.png`。页面水平边距统一，16dp 圆角分组保持完整；分隔线只位于相邻行之间，退出登录维持独立危险操作区。
- 共享组件：书库/首页网格、书籍与系列详情、收藏/阅读列表、登录、批量操作、编辑弹窗、设置容器和图片阅读控制均消费 `KomeliaLayoutSpec` 的页面、章节、列表项、控件、卡片和弹窗语义间距。
- 触控目标：移动端 48dp、桌面与 Web 40dp；图片阅读顶部控制同步使用平台触控目标。功能尺寸（封面比例、滑块厚度、缩略图尺寸）未机械替换。
- 主题：Light 实机复核通过；布局令牌不依赖色值，既有 Dark/OLED 主题映射测试继续通过。中英文超长标签、当前项提升、空分组过滤和极窄宽度由公共测试覆盖。

### 自动验证

- `git diff --check`：通过。
- `./gradlew :komelia-ui:allTests`：通过，包含 JVM 与 Wasm 浏览器测试。
- EPUB `npm run check && npm run build`：0 错误、0 警告，生产构建通过。
- `./gradlew buildEpubReaders :androidDebug :desktopJar :komfWebUI`：通过；Android APK、macOS arm64 JAR 和 Wasm WebUI 均生成成功。
- APK：`komelia-app/androidApp/build/outputs/apk/debug/androidApp-debug.apk`，已安装到 `emulator-5554` 并使用真实书库会话完成回归；测试凭据未写入仓库或日志。

### Findings

- 无 P0/P1/P2。构建仍报告既存 Skiko 依赖版本兼容提示、Wasm 资源体积提示和 Browserslist 数据过期提示；均未由本轮 UI 变更引入，也未阻断测试或产物生成。
- 响应式截图后模拟器已恢复为物理参数 1080 × 2400 px、420 dpi。

final result: passed
