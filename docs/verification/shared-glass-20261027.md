# 共享磨砂、高光阴影与顶部安全区复核

基线为 `7e74f654`。本轮将磨砂和选中阴影统一到主导航、设置导航、菜单、浮动按钮和 EPUB 控件，并处理顶部空白与挖孔避让。导航目的地、阅读进度、数据库迁移和包名保持原有契约。

## 材质与交互

`KomeliaChromeStyle` 统一使用 28dp 模糊、60% 中性底色。共享材质关闭折射细节、光学高光和内侧边缘。库默认 `edgeSoftness=1dp` 会在边缘混回清晰背景，产生细轮廓，因此设为零；形状覆盖仍由渲染器抗锯齿处理。玻璃下方不再重复绘制实色 Surface，避免抗锯齿处露出白边。`komeliaHighlight` 使用白色柔光表达选中、悬停、焦点和按下状态，不绘制黑色选中阴影或描边。主导航选中标签加粗，菜单保留选中语义。Classic 保留原有材质；减少透明效果和不支持采样时使用实色回退。

`KomeliaChromeContent` 只调整控件前景色，保留背景与阅读正文颜色。切换材质时保持内容组合和焦点。EPUB 两种实现使用同一份 CSS 参数，正文不应用模糊；TTU 侧边翻页按钮保留触摸与鼠标最小尺寸。

## 顶部与挖孔

主界面背景绘制到状态栏区域，`LocalPageTopInset` 将系统安全区传给页头。`KomeliaSafeTopBar` 仅给控件增加一次顶部安全距离，并向子层提供零 inset。页面内容仍可滚动到磨砂页头后方。Classic 与 Glass 的内容位置和滚动范围保持一致。

图片阅读器设置层先绘制背景，再对控件应用系统安全区。EPUB WebView 始终避开系统安全区，包括沉浸模式；默认额外顶部间距从 8dp 调整为 0，用户明确保存的自定义间距继续生效，不修改历史迁移。中英文说明已同步。

WebView 的安全区由原生布局负责，CSS 不再额外强制 8px 顶部距离。原生 padding 与 WebView CSS 安全区不能重复计算，参见 [Android WebView 安全区说明](https://developer.android.com/develop/ui/views/layout/webapps/understand-window-insets)。

## 验证与证据边界

本地证据保存在 `build/verification/glass-clarity-20261026/`，使用合成书库。最终自动检查记录和包摘要见该目录 `handoff.json`；旧导航阶段记录另存为 `navigation-only-handoff.json`，不能证明本轮全局改动。

- `global-material-red.log` 与 `top-insets-red.log` 保留旧实现的行为失败。后续局部测试和 Android 构建分别记录于 `borderless-highlight.log`、`immersive-top-build-fixed.log` 和 `immersive-final-build.log`。
- `GlassContrastTest` 覆盖三种主题的前景对比度和背景变化模型；`ChromeContentTest` 覆盖材质切换不重建内容；菜单测试覆盖选中语义和返回焦点。
- `KomeliaPageScaffoldTest` 覆盖背景从零位置绘制、控件避开安全区、嵌套页头只消费一次 inset，以及有安全区时两种材质的滚动范围一致。EPUB 测试覆盖新默认值和显式自定义值保留。
- API 37 的 `20-borderless-shadow-home.png`、`22-shadow-light-home.png`、`28-ttu-borderless-controls.png` 及 Web 的 `web-05-frosted-drawer.png`、`web-06-selected-shadow.png` 记录共享材质。顶部修改后的首页和详情见 `36-immersive-launch.png`、`37-immersive-book.png`。最终去除边缘白圈的详情截图为 `49-no-border-book.png`，横屏安全区见 `40-landscape-cutout.png`。
- 测试设备实际顶部挖孔安全区为 132px。详情返回图标位于 y=164–227px，背景延伸至 y=0。安全区不是需要删除的额外空白。
- EPUB 检查期间一次 UIAutomator 进程因 `Bad file descriptor` 退出；应用进程仍存活且截图成功。该记录是自动化工具故障，不能写成应用无崩溃的空日志。

之前的 `classic-vs-glass.json` 仅属于导航阶段，不作为最终全局版本性能结论。真机操作继续按用户要求暂停，TalkBack、真实硬件 GPU 和完整人工回归未由模拟器截图证明。自动命令、模拟器局部检查与用户验收分别记录。

## 顶部菜单一致性跟进

首页、书库、本地、搜索与移动设置统一使用 `BrowsePageHeader`。标题采用同一字号、单行省略和行高，图标共享间距与触控尺寸；首页选择分组后仍显示当前分组名称。设置子页面的返回操作使用同一标题栏。`KomeliaSafeTopBar` 在嵌套时只绘制一次材质，避免首页与其他页面叠加不同数量的玻璃层。五个主入口进一步统一使用现有 `KomeliaPageScaffold`，使页头与背景采样源成为兄弟节点，消除首页独立采样、其他页面在自身采样源中绘制页头造成的深浅差别。原有内容仍保留对应的顶部布局距离。

`KomeliaTopBarSurface` 在实际玻璃模式中不随滚动增加 tonal/shadow elevation，工具栏改为全宽。Classic 的滚动 elevation 仍保留。首页搜索按钮和设置子页不再单独叠加滚动阴影。

书库的范围/内容筛选与本地来源选项使用 `ChromeFilterChip`，实际玻璃模式中移除默认选中填色与边框，保留原生选择、点击语义和加粗文字，选中光效复用共享白光。搜索标签和首页分组菜单也使用同一白光。深色白光强度由前景对比度模型约束，三种主题仍需达到 4.5；EPUB CSS 同步去掉黑色选中阴影。

本轮证据位于 `build/verification/top-menu-20261027/`。`top-menu-red.log` 保留滚动 elevation 与灰色阴影的两个行为失败；`top-menu-green.log` 记录修正后的局部测试和 Android 构建。`BrowsePageHeaderTest` 检查有无操作按钮的五个入口高度一致以及筛选项的选择/点击语义。最终跨平台检查、APK 摘要和模拟器截图另存该目录交付记录。

## 阅读顶栏一致性跟进

本次跟进基于 `3e3ff4d0`。漫画与 PDF 共用的顶栏原来只有设置操作使用实心圆按钮。顶栏提取为 `ImageReaderToolbar` 后，返回和设置复用现有标准图标按钮，分享同步采用 20dp 图标。手机保留 48dp 点击区域，指针平台保留 40dp 最小尺寸。编辑表单里的新增按钮及筛选编辑状态仍保留其填色用途。

两套 EPUB 顶栏改为与图片/PDF 阅读器相同的全宽、方形顶栏，返回和两行标题靠左，目录、设置及更多操作靠右。移动端标题行高和按钮尺寸对齐原生阅读器，底部进度控件继续浮动。Komga 的全屏、帮助及 TTU 的书签、已读、定位、全屏、图片浏览收入更多菜单；原有动作回调与阅读进度由父阅读器管理。菜单支持键盘、Escape 和关闭后恢复焦点。原生 WebView 安全区与正文样式职责保持不变。

证据目录为 `build/verification/reader-chrome-20261027/`。`native-toolbar-red-behavior.log` 记录旧实心按钮与返回按钮的像素差异；`web-toolbar-red-behavior.log` 记录旧 EPUB 顶栏两侧 12px 缩进和 28px 圆角。局部修正通过了相同检查。渲染测试挂载真实组件并覆盖菜单动作，最终 Android 包摘要、浏览器和模拟器检查范围由该目录的交付记录保存。

规范检查脚本在 macOS 自带 Bash 与 UTF-8 环境下发生变量解析错误；改用 `LC_ALL=C` 后可执行，但仓库未安装 ESLint，检查未完成。没有为此安装依赖或修改技能。包内 Vue/Svelte 类型检查、构建、渲染测试及平台矩阵分别记录，不能把它们写成 ESLint 已通过。

Komga 的更多菜单在生产样式加载顺序下仍会出现 Vuetify 默认的深色投影。渲染夹具对齐生产入口的 CSS 导入顺序后，`komga-menu-shadow-red-production-order.log` 复现了该差异；菜单显式设为零 elevation，避免覆盖共享磨砂样式。打开菜单时由菜单处理键盘事件，Escape 先关闭菜单并保留顶栏与焦点；关闭顶栏时清理菜单状态。
