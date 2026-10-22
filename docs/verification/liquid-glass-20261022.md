# 页面整改与液态玻璃验证记录

执行时间：2026-10-22 至 23（Asia/Shanghai）。收尾时重新检查，上游 `main` 仍为 `9216fd18`。

本变更基于上游同步分支 `chore/upstream-sync-20261022` 的 `077680c8`，默认仍为经典模式。液态玻璃作为独立材质选项，与浅色、深色和 OLED 主题组合使用。HarmonyOS、应用版本、发布策略和子模块版本不在本次变更中。

代码、自动化与 Web 运行验证已完成。后续发布验收将单独记录安装包、桌面启动与 Android 升级结果。Android 真机和真实 MangaBaka 数据库关联内容仍待验收。

## 已完成的整改

- 新增 `AppSurfaceStyle` 和减少透明效果设置。V19 追加两列，保留历史迁移及主题枚举；旧 SQLite／Web 数据默认使用经典模式。保存失败恢复已保存值并提供重试，较早的失败不会覆盖后续选择。
- 将 Haze 2.0.1 的实验性 API 限制在共享材质组件。导航、工具栏和阅读控制使用独立背景采样，正文、封面及输入区域保持实色。材质使用应用主题，默认 Regular／Balanced；系统减少动态效果同时关闭 Material 动画与阅读标题跑马灯。
- Android 不支持渲染、低内存或用户减少透明效果时使用实色。首页、详情及设置滚动期间暂停采样，最后一次滚动结束 250ms 后恢复。移动端搜索页整体采用经典实色布局，包含底部导航；离开搜索后恢复已选择的材质，保存的设置不变。桌面搜索仍按滚动与输入焦点暂停。阅读控制隐藏时移除采样源。
- 参照 [FlClash v0.9.1](https://github.com/chen08209/FlClash/releases/tag/v0.9.1) 的悬浮圆角导航、选中态和短动效，保留 Komelia 的顶级导航顺序、阅读手势与快捷键。没有复制 Flutter 组件或着色器。
- 修复手机新安装默认五列与界面说明不符的问题。旧的封面密度不被迁移重写；提供恢复默认入口。手机默认三列，840dp 平板四列，1200dp 以上移动窗口六列，密度滑块继续生效。130% 字号下，封面副标题不再被固定高度裁掉。
- 为表单、搜索、菜单、编辑、下载、字段锁定和离线删除操作补充读屏标签；密码可见性描述按中英文切换。下拉选择支持键盘打开和关闭后恢复焦点。对话框增加屏幕边距，Komf 错误默认显示恢复提示，原始详情按需展开。
- Web 保持现有 Komf 管理范围，新增外观与 MangaBaka 入口。窄屏使用导航抽屉，长地址和保存按钮分行排布；窗口缩放同步更新宽度分级。
- 两套 EPUB 仅通过可选宿主桥接调整已标记的应用控件，缺省参数保持经典模式，正文 CSS 不变。修复切换 EPUB 实现后重新打开同一本书仍沿用旧实现的问题，替换时释放旧实例和加载任务。
- 批量操作改为列表布局内的操作区，为内容和导航预留空间，避免浮层坐标及两组控件重叠。
- 阅读页面等待视口完成测量后再请求键盘焦点，修复新增容器后方向键未交给页面的问题；JVM 延迟子节点测试和 Android 方向键翻页均复验。
- 运行走查发现首页尚无工具栏时会发生空列表异常；共享页面容器改为允许空工具栏，并补充真实 Compose 布局回归测试。
- 修复 Web 弹层关闭后主页面读屏树停止更新的问题。仅 Wasm 的 Compose 核心切换到 `1.13.0-alpha02`，Skiko 同步为 `0.153.0`；Android／JVM 保持 `1.12.1`，Material3 保持原版本。新增真实浏览器弹层生命周期及 Coil 图片解码测试。

Haze 的版本与接口依据：[2.0.1 源码](https://github.com/chrisbanes/haze/tree/2.0.1)、[性能配置](https://github.com/chrisbanes/haze/blob/2.0.1/haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazePerformanceMode.kt)。

## 自动化与构建

环境为 macOS arm64、Node 20.20.1、Gradle wrapper 9.7.1。初轮使用 JDK 17.0.18，发布前以 Temurin 21.0.13+9 重新通过完整矩阵，最后的搜索页修改后再次执行 UI 全测试及三端构建。Release CI 结果单独记录。

| 检查 | 已执行结果 |
|---|---|
| UI JVM／Wasm | 432 项通过 |
| Core JVM／Wasm | 42 项通过 |
| Shared app JVM／Wasm | 12 项通过 |
| Offline | 57 项通过；1 项原始归档对照样本测试跳过 |
| SQLite | 39 项通过 |
| Wasm 数据库模块 | 无独立测试；旧 Web 存储和持久化由 UI 浏览器测试覆盖 |
| Android debug 与 instrumentation APK | 构建通过 |
| Desktop JAR | 构建通过；不计作桌面运行验收 |
| Wasm／Komf Web UI | 生产构建通过，并通过真实浏览器走查 |
| Komga EPUB | 25 项 Node 测试及生产构建通过 |
| TTU EPUB | `svelte-check` 0 errors、0 warnings；生产构建通过 |
| Harness、回归执行器与夹具 | 22 项通过 |
| 发布策略与差异检查 | 通过 |

共 583 项 JVM／Wasm 测试，582 项通过、1 项跳过、0 失败。完整矩阵含增量任务复用，`UP-TO-DATE` 不代表重新执行。原始样本未提供，未将该用例计为通过。

关键回归先验证失败，再修复并通过：空工具栏、平板网格两列、大字体副标题裁切、阅读列表数量使用了合集文案，同书切换 EPUB 实现、批量操作区域覆盖内容/导航、Web 弹层关闭后的读屏树，以及移动端搜索的布局回退和材质恢复。字体测试首次编译错误已修正，只有随后发生的布局断言失败计作行为复现。

执行命令：

```sh
./gradlew :komelia-ui:allTests :komelia-domain:core:allTests \
  :komelia-app:shared:allTests :komelia-domain:offline:allTests \
  :komelia-infra:database:sqlite:allTests :komelia-infra:database:wasm:allTests \
  androidDebug :komelia-app:androidApp:assembleDebugAndroidTest desktopJar komfWebUI \
  --max-workers=1 '-Pkotlin.daemon.jvmargs=-Xmx8096m -XX:+UseG1GC' \
  --no-configuration-cache --console=plain
# 最后的共享 UI 修改后，再执行 UI allTests 与三端构建。
node --test scripts/check-harness.test.mjs scripts/regression.test.mjs tests/regression/fixtures.test.mjs
bash scripts/test-release-policy.sh
node scripts/check-harness.mjs
git diff --check
```

两个 EPUB 包还分别执行 `npm test`／`npm run check` 及 `npm run build`；应用构建执行了 `buildEpubReaders`。新增手工用例沿用现有回归目录：`APPEARANCE-01`、`APPEARANCE-02`、`APPEARANCE-03`、`EPUB-IMPL-01`。

## 运行证据

使用独立 API 37 模拟器、隔离的 Komga 1.26.3 和 Komf 2.1.0 本机服务。内容为七本合成远端 CBZ／PDF／EPUB、三本通过系统文件选择器导入的本地书籍，以及一份已下载书籍。账号与媒体均为测试数据，不使用私人书库。

- 保留数据从 V18 升级到 V19 后，经典模式、旧密度 150、账号、本地目录、下载和阅读记录均保留。随后手工恢复默认密度并启用玻璃，用于效果验证。
- Android 走查覆盖手机及 600／840dp 模拟窗口、中英文、130% 字号、加载、空、错误和离线入口。模拟窗口不等同真实平板或 OEM 系统栏验收。
- 图片书籍从第 2 页返回后显示 50%，重开仍为第 2 页。PDF 实际使用屏幕边缘返回手势，第一次显示控制，第二次退出。两套 EPUB 的目录、设置和正文能打开；同书完成 Komga → TTU → Komga 切换，继续保存阅读进度。
- 旋转实际切换了2400×1080和1080×2400视口，返回后仍显示第2页。系统动画倍率归零时，间隔2秒的阅读控件截图逐字节相同；测试后已恢复系统设置。手动发送 `RUNNING_CRITICAL` 内存事件后，外观页显示实色回退提示。
- TalkBack 服务实际绑定后，系统提供 10 秒交互时限。6 秒后阅读控件仍可见，绿色读屏焦点落在返回按钮；Tab／Enter 能激活返回。验证的是焦点、操作和时限，不包含语音播报质量评测。
- 使用两个合成账号完成管理员→普通用户→管理员的登录切换，普通用户设置菜单不出现服务器管理和Komf管理入口；离线账号选择页仍能列出已有下载账号。
- Web 实际覆盖连接、两种处理设置、数据源、两种通知表单、任务历史、MangaBaka 和外观。经典／玻璃与浅色／深色／OLED 六种组合均检查；宽度覆盖 360、412、600、840 和 1280。浏览器持久化、中文切换、抽屉和减少透明效果均实际操作。
- Web 初轮使用 Edge，修复后的最终验证使用 ego-browser 的 Chromium。实际 Chrome Accessibility 树确认弹层消失后主页面恢复；对话框打开时背景标记为 inert，关闭后解除；窄屏抽屉支持 Esc。六种主题／材质组合、八个管理页面和中文 360／412／600／840px 布局重新检查。控制台依赖提示与失败候选的记录保留，未据此宣称“零错误”。

逐页与弹层的状态、整改依据和证据编号见[页面走查清单](liquid-glass-page-audit-20261022.md)。该清单不把未执行的分支记作通过。

## 前后截图

截图均为未经编辑的运行画面。走查期间阅读进度、下载状态和测试数据有所变化，前后图用于比较布局；性能比较使用单独的固定序列。手机密度对照中，升级先保留五列，再手工点击恢复默认密度。

| 场景 | 整改前 | 整改后 |
|---|---|---|
| 手机首页 | [经典基线](assets/liquid-glass-20261022/home-before.png) | [玻璃与默认密度](assets/liquid-glass-20261022/home-after.png) |
| 外观设置 | [原表单](assets/liquid-glass-20261022/appearance-before.png) | [独立材质与透明度](assets/liquid-glass-20261022/appearance-after.png) |
| 360px Web | [固定侧栏挤压表单](assets/liquid-glass-20261022/web-narrow-before.png) | [抽屉与分行表单](assets/liquid-glass-20261022/web-narrow-after.png) |
| PDF 控制 | [经典控制条](assets/liquid-glass-20261022/pdf-before.png) | [玻璃控制与返回](assets/liquid-glass-20261022/pdf-after.png) |

补充证据：[搜索实色布局](assets/liquid-glass-20261022/search-solid-fallback.png)、[离开搜索后恢复玻璃](assets/liquid-glass-20261022/search-material-restored.png)、[批量操作与导航不重叠](assets/liquid-glass-20261022/bulk-actions-inline.png)、[840dp 四列](assets/liquid-glass-20261022/tablet-four-columns.png)、[130% 字号](assets/liquid-glass-20261022/font130-caption.png)、[TalkBack 6 秒后仍可操作](assets/liquid-glass-20261022/talkback-after6s.png)、[Komf 错误恢复提示](assets/liquid-glass-20261022/komf-error.png)。

## 性能结果与限制

设备为同一 API 37 软件 GPU 模拟器，1080×2400、420dpi，约412dp，字号100%，深色主题。初轮每个场景、每种模式各三轮预热、三轮测量；搜索后续采用下文注明的四轮交替测量。滚动每轮四组往返，各500ms；弹层每轮六组打开/Back；缓存图片阅读每轮八组左右边缘点击，各间隔200ms。没有后台编译与这些计时测量并行。

P95 采用各轮中位数；现代卡顿比例按各轮卡顿帧数除以总帧数汇总。完整重复值、包哈希和原始统计见[性能摘要](assets/liquid-glass-20261022/performance.json)及本地原始记录。未挑选最好的一轮；未触发翻页的零帧键盘采样、以及一次错误停在首页的搜索采样，按无效测量单独保留，未进入比较。

| 场景/策略 | 经典 P95 | 玻璃 P95 | P95 增幅 | 现代卡顿增量 | 初始门槛 |
|---|---:|---:|---:|---:|---|
| 首页滚动，滚动后250ms再恢复采样 | 23ms | 22ms | -4.35% | +1.78个百分点 | 此组样本在门槛内 |
| 首页分组弹层 | 27ms | 26ms | -3.70% | +0.44个百分点 | 此组样本在门槛内 |
| 缓存图片边缘点击翻页 | 22ms | 21ms | -4.55% | -2.08个百分点 | 此组样本在门槛内 |
| 搜索，输入焦点尚未暂停采样 | 22ms | 30ms | +36.36% | -0.16个百分点 | 未通过，随后整改 |
| 搜索，输入焦点期间暂停采样 | 23ms | 24ms | +4.35% | +2.39个百分点 | 未通过，随后整改 |
| 搜索，仅结果区域持续暂停 | 20ms | 23ms | +15.00% | +11.84个百分点 | 未通过，随后整改 |
| 搜索，整页持续暂停 | 19.5ms | 24ms | +23.08% | +20.74个百分点 | 未通过，随后整改 |
| 搜索，最终实色布局 | 20ms | 20.5ms | +2.50% | -3.74个百分点 | 此组样本在门槛内 |

前四组使用同一份 `2113ec9e…` 候选包；焦点暂停使用 `aa768864…`，结果区域暂停使用 `09acb343…`，整页暂停使用 `02eedee6…`。最终实色布局使用 `28845cc7…`。每次比较的两种模式使用相同 APK。后三轮各预热三次，再按 C／G／G／C／C／G／G／C 测量四次；P95 取中位数，现代卡顿仍按总帧数加权。早期候选之后的阅读焦点修复已另做方向键验证；上述统计不是最终正式包或真机性能。

最早持续采样版本的首页 P95 为53／31／32ms，曾超过门槛；加入暂停与250ms稳定间隔后明显下降。不同阶段的同一经典模式也出现较大的现代卡顿统计波动，因此不能依据某一组通过就宣布稳定达标，也不能用较好看的 legacy 计数替代现代计数。仅暂停结果区域、随后覆盖整页的两轮实验仍未达标。实际运行诊断确认整页方案的采样源与玻璃效果已关闭，故最后改用包含导航在内的经典实色布局。该结果不足以把剩余开销归因于某一条阴影或合成代码。最终布局的经典／玻璃设置分别记录 978／973 帧，现代卡顿率为 11.35%／7.61%，本组通过；此前失败数据全部保留。

同一合成漫画反复进入、退出20轮，PSS在0／5／10／20轮分别为296769／306433／304305／298625KiB；RSS为451408／461312／459440／454224KiB。预热后回落，未见持续增长；这不是长时或真机泄漏证明。新会话提示退出后，隐藏控制时连续5秒统计到0个新帧。刚结束新会话提示的一次窗口记录了19帧，未将其当作稳态采样。

`GlassScrollBudgetTest` 覆盖多个所有者、250ms稳定间隔和释放，`SearchGlassSamplingTest` 覆盖实际输入焦点与暂停策略的绑定及恢复。`MainDestinationSurfaceTest` 覆盖搜索的实色回退、主题与已保存材质不变、离开搜索后的恢复及桌面材质。`ReaderKeyboardFocusEffectTest`及Android方向键实测覆盖延迟布局后的键盘输入。颜色对比测试采用中性背景上下界建模，不等同所有页面逐像素实测。

## 已修复问题、依赖风险与待验收

| 项目 | 状态与原因 |
|---|---|
| Web 弹层关闭后的读屏树 | 已修复并复验。[可访问树复测记录](assets/liquid-glass-20261022/web-accessibility.json)。原 `1.12.1` 的真实浏览器测试先失败，Web 单独使用包含 [CMP-9368](https://youtrack.jetbrains.com/issue/CMP-9368) 修复的 `1.13.0-alpha02` 后通过。该 Web 依赖仍属预发布版本；已验证主页面恢复、模态隔离、图片解码和页面操作，不能视为所有浏览器的兼容保证。 |
| 性能门槛 | 搜索最终实色布局通过本轮模拟器比较，首页／菜单／缓存阅读的记录亦在门槛内。场景和候选包对应关系见上表；真机仍待测。 |
| Windows／Linux 桌面完整运行 | 待验收。当前宿主为 macOS，仓库既有桌面原生解码器不支持该系统；只执行 JAR 构建与 JVM Compose 测试。 |
| Android 真机、API 35、OEM 系统栏 | 待验收；本轮设备证据来自 API 37 模拟器。 |
| MangaBaka 已关联作品、解绑及完整数据库内容 | 待验收。未下载真实数据库；已覆盖默认关闭、缺失数据／服务失败的自动化以及实际设置入口。 |
| 原始归档、真实分镜／ONNX 全组合、真实接收应用分享 | 待验收。原始样本与设备未提供；合成内容和原生测试不替代这些场景。 |

本轮最终候选包为 `Komelia-0.29.0-ui-search-solid-layout.apk`，在 2026-10-23 完成构建和签名校验。应用 ID 为 `io.github.zhengningning.komelia.debug`，版本 `0.29.0-debug`、versionCode 57。SHA-256：`28845cc77dd712d823cbf30a96d192d4b55f1b46aff6dea78d723b44d5ab3acc`。原生 instrumentation 在这份 APK 上执行 26 项，全部通过；安装保留了合成测试账号和数据。正式签名发布包的证据另行记录。

最终增量构建包含 Android debug／instrumentation、UI 全部测试、Desktop JAR 和 Wasm，使用 JDK 21 耗时 3 分 47 秒。书籍批量选择及编辑另在模拟器复验，Web 最终产物重新加载后确认中文外观入口与已保存设置，见[最终 Web 外观页](assets/liquid-glass-20261022/web-final-appearance.png)。性能测量对应前文注明的候选包，不能替代正式包与真机复验。

构建日志、原始帧数据和完整截图保存在忽略的 `build/verification/ui/`；凭据文件不进入版本控制或证据包。
