# 视觉实现检查

final result: passed（代表状态的视觉复核；2026-10-26 透明材质与主导航补充检查。最终自动回归及包身份见 handoff.json，真机验收仍暂停）

范围为 38 张背景范围修正版视觉稿对应的 39 个 Screen 入口。实现沿用 Kotlin/Compose 应用和两种现有 EPUB 阅读器。视觉目标是中性墨蓝底色、天蓝操作色、独立玻璃导航、清晰表单与作品区域大图。

## 本轮发现与修复

| 级别 | 问题及证据 | 修复与复核 |
|---|---|---|
| P1 | 系列页滚动后，悬浮标题压在正文上；`60-series-list-scrolled-v4.png` | 滚动后使用单层完整工具栏，初始状态使用独立圆形按钮；`65-series-header-fixed.png` |
| P1 | 阅读列表仍为封面网格，与 09 号纵向列表稿不同；`72-readlist-detail.png` | 改为带顺序编号、页数、真实进度和操作菜单的纵向列表，保留分页、选择、排序及阅读上下文 |
| P1 | 从阅读器返回系列或阅读列表时，进度可能仍显示旧值；PDF 第 2 页重进正常，但系列仍显示未读 | 保留模型重新进入时刷新书籍列表；两项回归测试在旧行为下均报 expected 2 / actual 1，修复后通过；阅读列表实际返回从 50% 更新为 75%，系列 PDF 从 67% 更新为 100% |
| P2 | 360dp、150% 字体时续读按钮挤压书名，英文导航出现单字母换行；API35 `08-oled-360-large-home.png` | 续读标题与按钮按宽度、字号上下排列；导航标签单行截断，保留完整语义；三个 Compose 布局测试及 `41-large-home-fixed.png` |
| P2 | 宽屏通知页只显示编辑或预览；`21-notifications-v4-settled.png` | 宽屏并列编辑与真实模板预览，620px 窄屏保留分页；`24-notifications-wide-v6.png`、`25-notifications-narrow-v6.png` |
| P2 | 单行本、合集与阅读列表的顶部操作缺少独立圆形层次 | 复用同一悬浮按钮，保留各页菜单和权限条件；单行本 `96-oneshot-fixed.png`，合集 `65-collection-final.png`、阅读列表 `55-readlist-compact-final.png` |
| P2 | 离线账号选择页内容过度居中，在线登录品牌偏小 | 手机离线入口改为靠上排列，返回入口占满可用宽度；在线登录放大字标；`124-login-final.png`、`125-offline-login-final.png` |
| P2 | 账号页、默认色彩校正和服务器表单的分组层次不足 | 账号页区分身份、角色与密码操作；默认校正独立分组；服务器字段采用描边输入框；`127-account-final.png` 至 `129-server-settings-final.png` |
| P1 | 桌面下拉菜单关闭时立即跨越宽窄布局分界可触发 RootNodeOwner 已释放错误；旧版与更新前版本均复现 | 表单与侧栏采用可移动组合，跨断点保留焦点和弹层所有者；生命周期回归由重建 3 次变为 1 次，旧版 `26-base-escape-resize.png` 复现，修复版 `30-fixed-menu-ready.png` → `31-fixed-escape-resize.png` 正常，修复进程日志未再出现该错误 |

## 2026-10-26 透明材质与细节补充

本轮按用户明确选择调整为更透明的玻璃。首页、书库、本地、搜索和设置共用同一底栏形状与材质，移除移动搜索的单独 Classic 布局。模糊半径由 28dp 减至 12dp，并减轻边缘折射与阴影；浅色、深色、OLED 的色调分别校准。`GlassContrastTest` 同时约束正文对比度和可见的背景变化，旧的高遮盖方案在后一个检查中失败。滚动与编辑期间保留当前作用域的最近一帧，停止新采样，避免每次拖动都切成实色面板。

- API 37：`emulator-clear/16-final-home-light.png`、`19-final-home-oled.png`、`20-final-local-oled.png`、`21-final-search-oled.png`；默认 412dp、360dp/150% 字体与键盘展开分别检查。`23-final-360-large-home.png` 中续读按钮换行，导航文字可读；`17-final-search-ime.png` 中输入区位于键盘上方。
- API 35：`android-api35/70-clear-final-start.png` 至 `75-reduced-transparency.png`，检查英文、空本地库、搜索、600dp 导航栏及减少透明效果。截图只证明相应模拟器状态。
- 未知页数不再生成百分比；零进度或未知长度书籍不进入大型续读区。封面加载失败时详情缩为 136dp 占位区，EPUB 使用书籍标题。对应回归测试先失败后通过，`emulator-clear/22-final-missing-artwork.png` 记录了模拟器上的缺图详情。最终复核还发现有页数、无封面的书籍仍会占用首页大图区域，因此首页也按实际封面加载结果收缩为标题与续读控件，保留封面重载能力；补充前后截图与最终包检查记录在 handoff.json。
- TTU 顶栏旧的分组偏移把关闭按钮推出屏幕。移除偏移并允许按钮组换行，保留 48px 触控尺寸；`emulator-clear/26-epub-controls-oled.png` 为问题截图，`29-ttu-controls-visible-360.png` 记录窄屏修复。两种 EPUB 只改变控件材质，正文不参与过滤。
- EPUB 共享样式原先不在 Gradle 的 npm 构建输入中，导致仅修改样式时复用旧 HTML。两项构建任务已声明 `shared/` 输入，`clear-all-builds.log` 记录两种阅读器重新构建，产物包含 12px 模糊样式。
- Web：`web/33-clear-connection-settled.png`、`35-clear-escape-resize.png`、`36-clear-drawer.png`、`39-clear-notifications-ready.png`、`40-clear-notifications-narrow-ready.png` 检查最终材质、弹层关闭后的宽窄切换、透明侧栏及通知编辑/预览。未发送通知。

搜索滚动的一次模拟器诊断采样为 339 帧、P95 24ms，现代 jank 35.99%、legacy jank 10.32%。它来自候选包的 3 组往返滚动，保留在 `emulator-clear/search-scroll-gfx.txt`；没有同条件 Classic 对照，不能用来声称性能提升或通过真机性能门槛。

用户要求暂停真机后，本轮后续安装、截图和输入均改用专用模拟器。此前真机发现的问题已记录，包含私有书库信息的截图仅保存在忽略目录，未写入版本库。最终透明方案尚未获得真机确认。

## 对照方式与五项检查

共享磨砂、无边框高光阴影与顶部挖孔避让的后续调整，以 [2026-10-27 共享材质复核](docs/verification/shared-glass-20261027.md) 为准。此前截图仅代表对应阶段，不作为本轮最终版本的完整验收。

源图为 ImageGen 概念稿，手机图为 853×1844 像素，没有真实设备 DPR。Android 原图为 1080×2400；默认密度 420，另检查 360dp、600dp 宽度和 150% 系统字号。Web 检查 1280×900 与 620×900，DPR 1。桌面使用 Linux 开发运行环境检查 1280×910 客户区、840px 窄窗与键盘操作；对照图裁掉窗口标题栏和 Xvfb 外围黑边，原始截图保留。

`build/verification/immersive-glass/comparisons/` 保存同一图像内的参考稿与运行截图。比较图按等比缩放对齐密度，不拉伸原图；手机系统安全区与原图的构图留白单独判断。表中早期截图用于展示未再改动的区域，最后修复项另有前后对照。检查不等同于像素级复刻。

- **字体与排版**：界面沿用系统无衬线字体及中英文回退，字标使用统一字距。标题、正文、说明与字段层级分别核对。中文完整字形、英文长标题、150% 字体已检查；窄屏允许合理换行与截断。EPUB 正文字体由出版物和阅读偏好决定。
- **间距与布局**：沿用仓库的布局与触控尺寸。导航悬浮在独立层，书籍详情阅读条不随正文滚动；长页面可滚动到末项。表单使用实色分组，桌面侧栏有独立边界。阅读列表保留真实顺序，紧凑行在文字变长时可增高。
- **颜色与材质**：深色背景采用 `#0D131C`，主色 `#69C9FF`，正文 `#F4F7FC`。浅色、OLED、Classic 与减少透明效果的路径保留。边缘只绘制中性高光；玻璃采样真实内容，滚动和拖动期间沿用降级预算。对比度单元测试是模型约束，不能替代每种 GPU 和背景下的实测。
- **图片质量**：封面保留在作品卡片、续读区和书籍详情。运行素材为受控合成书库，只有雨夜封面是为本轮测试生成的素材。缺失封面显示中性书籍图标，不伪造作品图片；实际品牌保留项目 K 图标。缩略图采用平滑采样。阅读页、PDF、EPUB 正文未添加滤镜或场景背景。
- **文案与数据**：新增用户文案同步英文和简体中文。页数、进度、版本、任务状态、权限和公告来自实际状态；不照抄图中的虚构日期、字数、下载速度或成功提示。视觉稿中进度条图形与数字不一致时，按真实进度比例绘制。

## 保留的产品约束

视觉稿的作品、账号数量、公告长度、库配置和默认选项与测试数据不同。数据缺失、空列表、长英文标题和未配置状态不按“填满画面”处理。

移动封面密度仍使用已有偏好，默认三列；来源筛选、页大小、批量操作与高级首页过滤条件完整保留。移动搜索与首页、本地、书库、设置共用玻璃导航；滚动和编辑时暂停新采样并保留当前作用域的最近一帧。阅读器保留已有目录、分享、模式、设置与书签入口及自动隐藏行为；两种 EPUB 不强制改写正文主题。Komf 每字段保存与连接状态由现有状态持有者负责，通知预览没有发送测试消息。

## 页面覆盖

运行截图相对目录为 `build/verification/immersive-glass/`。这是代表状态覆盖，包含正常、空数据、未配置、下载完成、设置展开和阅读控制栏状态，不代表每个平台上的所有组合都已执行。

| 编号 | 视觉目标 | 运行证据 |
|---|---|---|
| 01 | [首页与主导航](docs/design/immersive-glass-20261025/screens/01-home.png) | `android-first/39-home-v3.png` |
| 02 | [书库](docs/design/immersive-glass-20261025/screens/02-library.png) | `android-first/67-library-settled-v6.png` |
| 03 | [本地与下载](docs/design/immersive-glass-20261025/screens/03-local.png) | `android-first/56-local-downloads.png` |
| 04 | [全局搜索](docs/design/immersive-glass-20261025/screens/04-search.png) | `android-first/73-search.png` |
| 05 | [书籍详情](docs/design/immersive-glass-20261025/screens/05-book.png) | `android-first/40-book-v3.png` |
| 06 | [系列详情](docs/design/immersive-glass-20261025/screens/06-series.png) | `android-first/59-series-list-v4.png` |
| 07 | [单行本详情](docs/design/immersive-glass-20261025/screens/07-oneshot.png) | `android-first/96-oneshot-fixed.png` |
| 08 | [合集详情](docs/design/immersive-glass-20261025/screens/08-collection.png) | `android-api35/65-collection-final.png` |
| 09 | [阅读列表详情](docs/design/immersive-glass-20261025/screens/09-readlist.png) | `android-api35/55-readlist-compact-final.png` |
| 10 | [图片与 PDF 阅读器](docs/design/immersive-glass-20261025/screens/10-image-reader.png) | `android-first/44-reader-controls.png` |
| 11 | [EPUB 阅读器](docs/design/immersive-glass-20261025/screens/11-epub-reader.png) | `android-api35/33-komga-controls.png` |
| 12 | [色彩校正](docs/design/immersive-glass-20261025/screens/12-color-correction.png) | `android-first/104-color-final.png` |
| 13 | [首页分组编辑](docs/design/immersive-glass-20261025/screens/13-home-groups.png) | `android-first/85-home-group-expanded.png` |
| 14 | [在线登录](docs/design/immersive-glass-20261025/screens/14-login.png) | `android-first/124-login-final.png` |
| 15 | [离线账号选择](docs/design/immersive-glass-20261025/screens/15-offline-login.png) | `android-first/125-offline-login-final.png` |
| 16 | [手机设置入口](docs/design/immersive-glass-20261025/screens/16-mobile-settings.png) | `android-first/74-settings-v6.png` |
| 17 | [桌面设置入口](docs/design/immersive-glass-20261025/screens/17-desktop-settings.png) | `desktop/20-final-settings-client.png` |
| 18 | [外观设置](docs/design/immersive-glass-20261025/screens/18-appearance.png) | `android-first/27-appearance-matrix.png` |
| 19 | [网络连接](docs/design/immersive-glass-20261025/screens/19-network.png) | `android-first/28-network.png` |
| 20 | [图片阅读设置](docs/design/immersive-glass-20261025/screens/20-image-reader-settings.png) | `android-first/128-image-settings-final.png` |
| 21 | [EPUB 阅读设置](docs/design/immersive-glass-20261025/screens/21-epub-settings.png) | `android-first/31-epub-settings.png` |
| 22 | [应用更新](docs/design/immersive-glass-20261025/screens/22-updates.png) | `android-first/83-app-updates.png` |
| 23 | [本地目录设置](docs/design/immersive-glass-20261025/screens/23-local-folders.png) | `android-first/33-local-folders.png` |
| 24 | [离线管理](docs/design/immersive-glass-20261025/screens/24-offline-management.png) | `android-first/82-download-tasks.png` |
| 25 | [关于](docs/design/immersive-glass-20261025/screens/25-about.png) | `android-first/80-about.png` |
| 26 | [我的账号](docs/design/immersive-glass-20261025/screens/26-account.png) | `android-first/127-account-final.png` |
| 27 | [认证活动](docs/design/immersive-glass-20261025/screens/27-authentication.png) | `android-first/77-auth-activity.png` |
| 28 | [服务器设置](docs/design/immersive-glass-20261025/screens/28-server.png) | `android-first/129-server-settings-final.png` |
| 29 | [用户管理](docs/design/immersive-glass-20261025/screens/29-users.png) | `android-first/76-users.png` |
| 30 | [媒体管理](docs/design/immersive-glass-20261025/screens/30-media.png) | `android-first/78-media-management.png` |
| 31 | [公告](docs/design/immersive-glass-20261025/screens/31-announcements.png) | `android-first/79-announcements.png` |
| 32 | [Komf Web 入口](docs/design/immersive-glass-20261025/screens/32-komf-web.png) | `web/23-connection-v6.png` |
| 33 | [Komf 连接设置](docs/design/immersive-glass-20261025/screens/33-komf-connection.png) | `android-first/94-komf-connected-settled.png` |
| 34 | [Komf 处理设置](docs/design/immersive-glass-20261025/screens/34-komf-processing.png) | `web/26-processing-wide-v6.png` |
| 35 | [Komf 元数据源](docs/design/immersive-glass-20261025/screens/35-komf-providers.png) | `web/11-providers-settled.png` |
| 36 | [Komf 通知设置](docs/design/immersive-glass-20261025/screens/36-komf-notifications.png) | `web/24-notifications-wide-v6.png` |
| 37 | [Komf 任务历史](docs/design/immersive-glass-20261025/screens/37-komf-jobs.png) | `web/14-jobs.png` |
| 38 | [MangaBaka 设置](docs/design/immersive-glass-20261025/screens/38-komf-mangabaka.png) | `web/15-mangabaka.png` |

## 执行证据

- `platform-matrix-4.log`：共享 UI 全量 JVM/Wasm 测试及 Android、桌面 JAR、Komf Web、两种 EPUB 构建通过。该轮为 271 个 JVM、170 个 Wasm 用例，失败及跳过均为 0；随后增加的两项目录进度测试另有先失败再通过的记录。
- `catalog-progress-red-3.log`、`readlist-progress-fix.log`：进度回归红绿记录。早期测试夹具配置失败与缺失导入的编译失败另保留，不计为行为复现。
- `visual-candidate-10.log`：布局、进度回归及 Android/桌面候选构建记录；`settings-resize-red-2.log`、`settings-resize-fix.log` 记录断点切换的失败、修复和重新构建。最终完整回归以 `premerge-final.log` 及其指向的 `build/regression/` 记录为准；自动命令与人工条目分开记录。
- Android：专用 API 35 与 API 37 AVD，覆盖安装保留数据。实际检查三种阅读格式、进度返回/重进、无痕阅读、下载与离线账号入口。冷启动恢复曾出现连接失败，实际点击重试可恢复；原因尚未由本轮验证确定，不能声称所有冷启动均成功。
- 桌面：当前 UI/shared JAR 在受控 Linux 容器中运行，复用既有原生依赖，使用系统 JDK 启动。已检查宽窄窗口、弹层 Esc、键盘焦点与开关。此证据属于开发运行环境，不是新安装包验收；复用包的启动器曾退出 139，未把替代启动方式描述为安装器修复。
- Web：使用现有 Ego 浏览器任务空间检查完整打包产物、中文显示、侧栏、宽窄布局、菜单与通知预览；调试记录保留在 `web/`。

## 尚未覆盖的验收

最终透明材质的真机验收、硬件 GPU 的 P95/jank、TalkBack 完整操作、所有平台的安装器、原始用户书籍、全部离线异常分支和正式签名包升级未由本轮证明。回归目录中的人工用例只有在其完整步骤和证据齐备时才可标记通过；不能把模拟器截图或绿色构建替代这些门槛。

本次不变更数据库、服务端 API、依赖版本、子模块、包名、正式签名或 Release 策略。代码与本地测试包留在隔离工作区，未执行推送、PR、合并或发布。

## 交付检查清单

- [x] 38 张参考图与对应入口逐项核对，保留代表状态及数据差异说明。
- [x] 统一颜色、表单、导航和封面展示，移除全局插画与虚构反光。
- [x] 发现的标题遮挡、大字体挤压、通知预览和列表进度问题已修复并添加或复用验证。
- [x] 透明材质、统一导航、缺图详情与 EPUB 工具栏已完成代表状态复核；最终包与完整检查结果单独记录。

最终构建、APK 身份/签名/摘要、实际安装和测试服务清理结果写入忽略目录中的 `build/verification/immersive-glass/handoff.json`。完整回归日志与人工条目以对应运行记录为准，保留源码、包与证据供后续真机检查。
