# 页面与弹层走查清单

此清单按代码中的 39 个 Screen 入口核对。Android 证据来自隔离 API 37 模拟器，Web 来自实际浏览器；桌面完整运行仍受宿主原生依赖限制。每行仅覆盖列出的状态，不代表该页面所有数据分支、全部平台都已验收。

截图编号对应本地证据包中的 `before/`、`native-after/` 和 `web-after/`。`web:` 表示 Web 截图。精选前后对照见[验证报告](liquid-glass-20261022.md)。公告正文不进入公开图片附件。

| Screen / 入口 | 已走查状态 | 整改或保留依据 | 基线 → 整改后证据 | 状态 |
|---|---|---|---|---|
| `MainScreen` · 顶级导航 | 正常、手机/平板 | 共享悬浮导航、底部安全区；顺序和目的地不变 | `16-current-home` → `66-final-home` | 已运行走查 |
| `HomeScreen` · 首页 | 长标题、空工具栏、字号130% | 默认密度、平板四列、大字体副标题、滚动采样；空工具栏崩溃已修复 | `16-current-home` → `74-font130-fixed360` | 已运行走查 |
| `LibraryScreen` · 书库及筛选 | 系列/合集/阅读列表、空书库 | 共享导航与网格；工具栏标签和筛选布局；原筛选业务保留 | `37-library-root` → `85-library-current` | 已运行走查 |
| `LocalContentScreen` · 本地/下载 | 3本导入、1本下载 | 共享密度和底部留白；保留来源筛选与索引规则 | `77-local-page` → `76-local-imports-and-download` | 已运行走查 |
| `SearchScreen` · 全局搜索 | 空结果、系列和书籍结果 | 持久读屏标签、底部留白与滚动采样；保留搜索范围和排序 | `78-search` → `80-search-books` | 已运行走查 |
| `BookScreen` · 书籍详情 | 合成CBZ/PDF/EPUB | 顶部材质、菜单/编辑标签、安全区；下载与阅读入口不变 | `44-book-detail` → `29-book-detail` | 已运行走查 |
| `SeriesScreen` · 系列详情 | 1/1.5/2章节、批量选择 | 顶部采样与菜单标签；章节顺序保留 | `43-series-detail` → `87-series-final` | 已运行走查 |
| `OneshotScreen` · 单行本 | 合成单行本详情与菜单 | 补充菜单/编辑标签和导航留白；既有封面/元数据布局保留 | `70-oneshot-detail` → `82-oneshot-detail` | 已运行走查 |
| `CollectionScreen` · 合集详情 | 3系列、编辑弹层 | 长标题与操作标签；原排序和选择行为保留 | `42-collection-detail` → `22-collection-detail` | 已运行走查 |
| `ReadListScreen` · 阅读列表详情 | 6书籍、编辑弹层 | 长标题与操作标签；修正数量使用了合集文案 | `41-readlist-detail` → `24-readlist-detail` | 已运行走查 |
| `ImageReaderScreen` · 图片与PDF阅读 | 第2页接续、控件、边缘返回 | 阅读控件材质、自动隐藏遵循无障碍时限、减少动态；正文渲染不改 | `46-reader-controls` → `31-reader-controls` | 已运行走查 |
| `EpubScreen` · Komga/TTU EPUB | 正文、目录、设置、同书切换 | 共享可选控件桥接；同书切换实现修复；不写入正文CSS | `54-ttu-controls` → `56-ttu-controls` | 已运行走查 |
| `ColorCorrectionScreen` · 色彩校正 | 继承全局默认 | 保留独立编辑布局与值；不改变色彩算法或预设 | `71-color-correction` → `34-color-correction` | 已运行走查 |
| `FilterEditScreen` · 首页分组编辑 | 7组、长组名 | 保留已有分组/拖动/删除布局，使用共享导航；无结构调整依据 | `72-home-group-editor` → `settings-home-groups` | 已运行走查 |
| `LoginScreen` · 在线登录/切换 | 升级后登录与错误恢复 | 保留明确输入与重试布局；共享密码描述中英文同步 | `01-login` → `login-upgraded` | 已运行走查 |
| `OfflineLoginScreen` · 离线账号选择 | 下载后可用的账号列表 | 保留账号与本地模式入口；共享弹层间距和按钮语义 | `100-offline-login` → `offline-login-upgraded` | 已运行走查 |
| `MobileSettingsScreen` · 手机设置入口 | 完整菜单 | 共享导航材质、选中态、长行与最小触控区域 | `17-settings-menu` → `15-settings-menu` | 已运行走查 |
| `DesktopSettingsScreen` · 桌面设置入口 | JVM布局/构建 | 共享响应抽屉与侧栏；完整桌面运行待验收（当前宿主原生解码器不支持） | `—` → `—` | 待桌面设备 |
| `AppSettingsScreen` · 外观设置 | 6组合、持久化、重启 | 新增材质、透明度、保存重试；表单满宽及恢复默认密度 | `08-appearance` → `110-final-appearance` | 已运行走查 |
| `NetworkSettingsScreen` · 网络连接 | LAN开关与地址 | 共享设置容器、标签/开关；不改地址选择规则 | `18-network` → `settings-network` | 已运行走查 |
| `ImageReaderSettingsScreen` · 图片阅读设置 | 无预设、选项、ONNX入口 | 共享设置容器与开关语义；真实ONNX模型组合待验收 | `19-image-reader` → `settings-image-reader-settings` | 已运行走查 |
| `EpubReaderSettingsScreen` · EPUB偏好 | 两种实现、沉浸/边距 | 响应表单、共享容器；阅读实现设置在下一次进入生效 | `20-epub-settings` → `settings-epub-settings` | 已运行走查 |
| `AppUpdatesScreen` · 应用更新 | 当前版本/已是最新 | 共享容器；版本、来源和更新策略保持不变 | `21-updates` → `settings-updates` | 已运行走查 |
| `LocalLibrarySettingsScreen` · 本地目录设置 | SAF导入的目录 | 共享容器；已存在的刷新/删除标签及目录操作保留 | `22-local-folders` → `settings-local-folders` | 已运行走查 |
| `OfflineSettingsScreen` · 离线管理 | 账号/下载/缓存/日志 | 共享容器和删除标签；保留下载/缓存边界 | `23-offline` → `settings-offline-settings` | 已运行走查 |
| `AboutSettingsScreen` · 关于 | 版本和项目链接 | 共享容器；已有信息层次与链接足够清晰，未重写内容 | `24-about` → `settings-about` | 已运行走查 |
| `AccountSettingsScreen` · 我的账号 | 账号、角色、密码弹层 | 共享容器和密码描述；不改变账号设置 | `25-account` → `settings-account` | 已运行走查 |
| `AuthenticationActivityScreen` · 我的/服务器认证活动 | 合成账号登录记录 | 共享容器；列表字段与来源保留 | `26-auth-activity` → `settings-account-activity` | 已运行走查 |
| `ServerSettingsScreen` · 服务器设置 | 配置表单和任务区 | 共享容器/开关；未保存或变更服务器配置 | `27-server-general` → `settings-server-general` | 已运行走查 |
| `UsersScreen` · 用户管理 | 2个合成账号、权限弹层 | 共享容器与弹层；保留角色与权限业务 | `29-users` → `99-users-two-fixtures` | 已运行走查 |
| `MediaAnalysisScreen` · 媒体管理 | 空态 | 共享容器；空态清晰，无单独业务修改 | `31-media` → `settings-media-management` | 已运行走查 |
| `AnnouncementsScreen` · 公告 | 服务端公告/加载 | 共享容器；保留服务端内容来源，未重写公告 | `83-announcements-ready` → `settings-announcements` | 已运行走查 |
| `KomfMainScreen` · Web Komf入口 | 宽屏/抽屉、中文 | 新增外观/MangaBaka入口，窄屏抽屉、图标、选中态 | `web-01-connections` → `web:13-glass-connection360` | 已运行走查 |
| `KomfSettingsScreen` · Komf连接 | 已连接、失败、展开详情、恢复 | 简化错误并保留详情，输入标签和窄屏分行 | `11-komf-enabled` → `107-komf-error-ready` | 已运行走查 |
| `KomfProcessingSettingsScreen` · Komga/Kavita处理 | 默认配置/长表单 | 共享表单与开关、配置标签触控区；不改变处理规则 | `33-processing` → `settings-processing` | 已运行走查 |
| `KomfProvidersSettingsScreen` · 元数据源 | 空配置/添加菜单/未下载DB | 共享表单、按钮与源名称；真实提供方鉴权待验收 | `34-providers` → `settings-providers` | 已运行走查 |
| `KomfNotificationSettingsScreen` · 通知 | Discord/Apprise空目的地 | 共享表单和响应布局；未发送通知 | `35-notifications` → `settings-notifications` | 已运行走查 |
| `KomfJobsScreen` · 任务历史 | 空态和筛选 | 共享容器；任务来源和删除行为不变 | `36-job-history` → `settings-jobs` | 已运行走查 |
| `KomfMangaBakaSettingsScreen` · MangaBaka设置 | 默认关闭/缺少数据库 | 中英文标签、共享容器及Web入口；已关联数据分支待验收 | `14-mangabaka` → `settings-mangabaka` | 已运行走查 |

## 弹层与菜单

编辑、删除、重置和用户管理弹层均只查看与取消。未保存元数据、删除账号或触发通知发送。系统文件选择器在合成目录上验证；真实数据提供方和系统安装器的分支保留为待验收。

| 组件族 | 运行记录 | 边界 |
|---|---|---|
| 通用确认/下载/退出 | 基线93-download-confirm、98-logout-confirm；整改后103-delete-user-dialog | 共享边距、实色、长文本；只取消删除 |
| 单行本菜单/编辑 | 83-oneshot-menu、84-oneshot-edit | 查看General字段，未保存 |
| 系列菜单/编辑 | 89-series-actions、88-series-edit | 查看General字段，未保存 |
| 书籍批量选择/编辑 | 90-book-selection、91-books-selected、92-book-bulk-edit | 3本书，未保存作者变更；操作区布局另有回归 |
| 添加到阅读列表/合集 | dialog-oneshot-0、dialog-oneshot-1 | 打开与取消，不改变归属 |
| Komf识别/重置 | dialog-oneshot-2、dialog-oneshot-3 | 仅初始界面，不触发重置或真实提供方搜索 |
| 合集菜单/编辑 | 94-collection-actions、95-collection-edit | 名字、排序和海报入口；未保存 |
| 阅读列表菜单/编辑 | 96-readlist-actions、97-readlist-edit | 名字、说明、排序；未保存 |
| 用户编辑/改密/删除/新增 | 101-user-edit 至 104-add-user-dialog | 两个合成账号；未填写新密码、未提交删除或新增 |
| 外观/语言/主题下拉 | 10-style-menu、54-epub-options、70-theme-options、73-language-options | 实际选择、重启与Web Esc；Web关闭后读屏树失败 |
| 书库筛选 | 86-library-filters | 筛选表单与下拉；未更改持久业务数据 |
| 图片阅读设置 | 32-image-options、33-image-settings-panel | 模式与图像页签 |
| EPUB目录/设置/导航 | 48-komga-toc、51-komga-settings-open、56-ttu-controls、57-ttu-settings | 两套实现，正文与控件分别检查 |
| 书库编辑/服务器目录浏览 | 143-library-edit、144-server-file-browser | 仅查看与取消，不改目录 |
| 本地文件选择器 | 基线73-folder-picker-ready 至77-local-page | 真实SAF授权与3个合成文件；原生26项测试覆盖provider权限/取消 |
| Web提供方菜单/抽屉 | 基线web-03-add-provider；web-after12-glass-drawer360 | 实际打开与关闭；不发送外部请求 |
| MangaBaka链接/解绑与下载进度 | 待真实数据库/已关联数据 | 缺少相应数据，未记为通过 |
| 更新安装/ONNX安装及真实系统分享接收端 | 待目标平台/模型/接收应用 | 本轮不发布、不安装Release或下载真实模型；未记为通过 |

补充运行记录：离线下载、缓存、日志分别为 `146-offline-downloads`、`147-offline-cache`、`148-offline-logs`；普通账号权限与身份为 `152-secondary-settings`、`153-secondary-account`；内存回退为 `138-low-memory-appearance`；旋转与减少动态为 `132-reader-landscape` 至 `136-reduced-motion-header-b`。

最终批量操作区记录：`161-inline-bulk-final` 的操作标签已进入同一可访问树，操作条位于列表和完整导航之间；`162-inline-bulk-edit` 验证四个系列的编辑入口与取消；`164-series-inline-book-actions`、`165-book-edit-final` 验证系列详情内的书籍批量操作区及单本编辑入口，未保存。
