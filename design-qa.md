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

## 下拉数据与 EPUB 设置汉化复核

- 首页分组编辑：`/private/tmp/komelia-home-dropdown-i18n.png`。真实配置中的 `Custom`、`ReadDate`、`DESC`、`All`、`ReadStatus`、`Equals`、`IN_PROGRESS` 已分别显示为“自定义、阅读日期、降序、全部、阅读状态、等于、阅读中”。保存值和服务端字段保持原枚举值，不受展示文案影响。
- EPUB 设置：`/private/tmp/komelia-epub-settings-i18n-final.png`。在真实 2184 页 EPUB 中验证“主题、阅读模式、连续滚动、分页阅读、衬线字体、无衬线字体、字体大小、行高、阅读器上下边距、阅读区域最大宽度”等文案；主题名、开关值、振假名模式和字体管理弹窗也使用同一语言表。
- 应用语言桥接：EPUB WebView 在加载设置前读取 Komelia 的 `SYSTEM/EN/ZH_CN` 选择；英文保持英文，简体中文使用 `zh-CN`，未显式选择时回退浏览器语言。语言仅影响阅读器界面，不修改出版物正文和字体名称。
- 构建复核发现 `buildEpubReaders` 与 `androidDebug` 同次调用时可能并行，导致 APK 打入上一版 EPUB 资源；最终验收按“先生成 EPUB、再单独执行 `:androidDebug`”完成，并直接检查 APK 内含“主题/阅读模式/连续滚动”后安装复验。
- 共享下拉覆盖：首页书籍/系列条件、阅读状态、排序方向、媒体状态之外，继续覆盖 Komf 媒体类型、匹配模式、作者角色、阅读方向、数据源、外部链接类型，以及色彩校正通道；用户或服务器自定义名称保持原样。
- 自动验证：EPUB `npm run check` 为 0 错误、0 警告，`npm run build` 通过；`:komelia-ui:allTests`、`:komelia-ui:compileAndroidMain`、`buildEpubReaders`、`:androidDebug`、`:desktopJar`、`:komfWebUI` 均通过。新增枚举映射测试覆盖截图中的原始值、扩展设置枚举、常见状态值和未知值回退。

## 自适应导航与动效专项复核

- 问题输入：`/Users/zhengningning/Library/Containers/com.tencent.xinWeChat/Data/Documents/xwechat_files/wxid_raat75q9njlc22_6b7a/temp/RWTemp/2026-08/b6eb15e960a92630050caddb6a9b3e2b/c08291242f15f801c83c1f22cf7b4cc0.mp4`。原全局抽屉只覆盖内容区，底部导航可穿透点击，导致抽屉后页面切换且书库无正确选中态。
- 修复后回归视频：`/private/tmp/komelia-adaptive-navigation-passed-capture.mp4`，12.67 秒、38 帧；由模拟器连续原始截图编码，规避 Android Emulator `screenrecord` 对 Compose 硬件层只记录关键帧的问题，未插入设计稿或静态替代页面。
- Compact 证据：首页 `/private/tmp/komelia-adaptive-main-restarted-loaded.png`；搜索 `/private/tmp/komelia-live-selection-search.png`；书库直达 `/private/tmp/komelia-live-selection-library.png`；书库范围底部面板 `/private/tmp/komelia-adaptive-final-scope-sheet.png`。
- Medium 证据：约 720dp 宽度 `/private/tmp/komelia-adaptive-final-rail-720dp.png`，一级导航切换为左侧 Navigation Rail；模拟器随后恢复 1080 × 2400 px、420 dpi。
- 快速切换压力回归：以 250ms 间隔连续切换首页、搜索和书库，最终截图 `/private/tmp/komelia-adaptive-rapid-switch-final.png` 中书库内容与书库选中态一致，`MainActivity` 保持前台，日志无重复 SaveableState key 或崩溃。

### Findings 与修复闭环

1. 移除全局抽屉、`DrawerState`、汉堡按钮与 `toggleNavBar()`；书库按钮直接进入全部书库，一级导航在 Compact 使用底部栏，其余断点使用约 80dp Rail。
2. 书库范围选择降级为页面内部控件：手机使用阻断下层点击的底部面板，桌面/Wasm 使用锚定弹层，Full 使用 232dp supporting pane。返回或 Esc 只关闭顶层面板。
3. 一级目的地使用单层 200ms fade-through；详情使用单层 8dp/200ms 淡入位移。单层过渡避免 Compose 在快速往返时同时组合两个相同 `Screen.key`，reduced-motion 下直接切换。
4. 首轮压力录屏发现并修复 `HomeScreen:screen was used multiple times` 竞态；同时将导航点击改为读取 Voyager 当前目的地，而非使用可能过期的组合闭包，消除“选中书库但仍显示首页”的瞬时错配。
5. 顶部工具栏、窗口标题栏、内容区和导航使用连续 `surface`；首页分组标题不再用整行分隔线。卡片、弹窗和必要数据行仍保留语义边界。
6. 自动验证：`git diff --check`、`:komelia-ui:allTests`、EPUB `npm run check && npm run build`、`buildEpubReaders :androidDebug :desktopJar :komfWebUI` 全部通过。APK 已单独执行 `:androidDebug`，确认包含简中 Compose Resources 后安装到 `emulator-5554`，并使用真实 Komga 内容完成上述路径复验。

## 首页/书库职责与 Tab 双闪复核

- 职责边界：首页负责“继续阅读、待读、最近添加/更新”等动态聚合和快捷恢复；书库负责全部系列、合集、阅读列表、书库范围、筛选排序与批量管理。两者共享内容卡片但服务于不同任务，保留为独立一级入口。
- 语义修订：首页聚合入口由“全部”改为“概览”，避免与书库中的“全部书库”混淆；筛选结果、排序和业务数据均未改变。
- 根因证据：将系统动画时长临时放大 10 倍后，一次“首页 → 书库”点击先执行目的地容器淡出/淡入，再执行书库根页面首次挂载的详情淡出/淡入，形成两个连续明暗脉冲。修复前序列保存在 `/private/tmp/komelia-double-flash-01.png` 至 `/private/tmp/komelia-double-flash-14.png`，动画倍率已恢复系统默认。
- 修复策略：保留一次一级目的地 fade-through；详情过渡仅在已显示页面的 `Screen.key` 真正变化时执行。根页面首次挂载和同 key 重置直接显示，详情前进/返回仍保留 8dp/200ms 克制动效，reduced-motion 行为不变。
- 回归保护：纯状态决策测试覆盖首次挂载、同页面重置和详情切换三条路径，防止未来再次把根页面初始化当成详情导航动画。
- 修复后证据：同样以 10 倍时长采集 `/private/tmp/komelia-single-transition-01.png` 至 `/private/tmp/komelia-single-transition-10.png`，书库根页面随一级容器单次出现，未再发生第二次淡出；恢复正常时长后快速切换书库、首页、搜索、书库、首页，最终截图 `/private/tmp/komelia-tab-fix-settled.png` 仅首页保持选中，正文与选中态一致。
- 真实数据说明：书库范围选择器出现后，11 个系列需要等待服务端请求完成才显示；最终证据为 `/private/tmp/komelia-tab-fix-library-loaded.png`。该阶段透明度不再变化，属于数据加载而非第二段页面动画。
- 自动验证：`git diff --check`、`:komelia-ui:allTests`、`:androidDebug` 均通过；修复后的 APK 已覆盖安装到 `emulator-5554`。压力回归日志无重复 `Screen.key`、SaveableState、`IllegalArgumentException` 或崩溃。

## 首页顶部标签与内容节奏复核

- 首页顶部筛选标签与首个内容章节原仅使用 8dp 控件间距，两个不同层级在视觉上紧贴。现改用响应式章节间距：Compact 16dp、Medium 20dp、Expanded/Full 24dp。
- 间距复用 `KomeliaLayoutSpec.sectionSpacing`，未新增固定 dp、分隔线、背景色块或额外阴影；标签栏、章节标题和网格仍处于同一连续表面。
- Compact 实机密度证据：`/private/tmp/komelia-home-section-spacing.png`，1080 × 2400 px、420 dpi。顶部标签与“继续阅读”标题层级清晰，首屏仍完整容纳两排卡片，无水平溢出或底部导航遮挡。
- 自动验证：`git diff --check`、`:komelia-ui:allTests`、`:androidDebug` 均通过；新 APK 已覆盖安装到 `emulator-5554` 并使用真实首页内容复核。

final result: passed
