# 全页控件对齐验证

更新：2026-11-03。

18页按确认的控件口径完成一轮完整校准，保留真实书籍、封面、目录和阅读进度。[逐页对照](modern-ui-alignment-20261102-assets/index.html)包含同一安装包的18张原始运行图。[413项实测](modern-ui-alignment-20261102-assets/measurements.json)覆盖Compose布局、浏览器DOM和Android WebView，最大偏差2个逻辑像素。

[对齐标尺](../design/modern-ui-20261030/MEASUREMENTS.md)定义画布、父容器和安全边界。[设计核对](../../design-qa.md)记录已处理的差异。真实内容换行、缺失摘要与可用能力引起的顺延，按相邻控件和所属容器检查。

## 改动范围

共享Compose统一导航、搜索、详情、列表、设置和阅读操作条。底栏五项等分，侧栏位置不再依赖字体测量。详情使用一处主阅读操作，紧凑绘制区域保留48dp触控；平板列表保留排序、筛选和每页数量入口。

图文设置对齐模式、方向、开关和高级项。顶栏显示真实系列与卷名，页码胶囊读取已有页组、分镜或连续页状态，只显示当前位置。分镜入口仍受实际能力控制。

两套EPUB保留各自主题和排版模型。TTU补齐生产构建的CSS顺序检查，修正原生包中主题间距和阅读模式宽度偏移。设置面板保持96%底色覆盖，白书页上的深色控件保持清晰。

本次未改变接口、数据库、存储枚举、Android身份与签名要求，也未增加进度写入路径。英文和简体中文资源同步。

## 自动检查

完整入口为`node scripts/regression.mjs run premerge`。最终指纹、原始命令日志和JUnit汇总位于本地忽略目录`build/verification/visual-alignment-20261102/regression-final/`。

| 检查 | 执行结果 |
|---|---|
| guard、harness、runner | 通过 |
| core，含`:komelia-ui:allTests` | 593项，0失败、0错误、0跳过 |
| offline | 102项，0失败、0错误、1跳过；缺少可选原始档案fixture |
| Komga `npm test` | 29项通过 |
| TTU `npm run check` | 0错误、0警告 |
| TTU `npm run test:browser` | 8项通过，含生产构建的设置几何 |
| `androidDebug desktopJar komfWebUI` | 构建通过，包含两套EPUB构建与资源同步 |
| 聚焦布局、触控与工具栏 | 23项通过，413项坐标和尺寸在2px内 |
| 对照页 | 18页、36张对照图片，窄屏无页面溢出，图片与脚本检查通过 |

几何JSON保留每项参考、实际值和偏差。绘制区域、点击范围和文字实际行边界分别检查；不把整张图片的内容差异当作控件位移。

预合并profile的29个人工条目保持`pending`，整体`accepted`为`false`。可选原始档案用例跳过。通用DFA单文件lint因本机缺少ESLint而阻塞，使用项目的vue-tsc、Svelte检查和浏览器回归验证受影响的前端。

## 原生复核

专用API37模拟器使用合成书库，density320、fontScale1。截图逐次记录物理尺寸、切口与导航区域，按实际应用区域展示。18张图均关联同一APK SHA-256；[清单](modern-ui-alignment-20261102-assets/manifest.json)保留每张图的原始像素与摘要。

复核路径包括五个主导航、书籍和丛书返回、宽窄转换保留选中书籍、实际查询、阅读设置与目录、主题和字号恢复。正文、缺失封面、目录数量、相邻书籍边界和下载状态均使用实际测试模型。

宽屏菜单中的每页数量实际执行20→50→20并恢复。补查360dp、130%字号、英文、Darker与减少透明效果，以及412、600、840和1280dp设置布局。合成PDF实际显示第2页，冷启动后恢复到2/3；这些观察保留在补充图中。

结束时恢复1080×2400、density420、fontScale1、硬键盘显示输入法选项0、语言SYSTEM、主题DARK、液态玻璃、减少透明效果关闭、书籍GRID布局、TTU阅读器20号浅色主题和Komga滚动/日间。[恢复记录](modern-ui-alignment-20261102-assets/device-restored.json)关联实际观察。

12的测试书不提供分镜。该行省略后，标题和模式组随内容高度排列，方向、提示、音量与高级入口按共同父容器和底边核对。14使用与03相同的共享布局。16由Android宽屏核对共享几何，不能代替原生Desktop窗口验收。

真机、TalkBack、API35完整流程、原生Desktop窗口、PDF系统手势与长期性能继续单列验收。运行观察不把这些条目标为通过。

## 测试包

文件为本地`build/artifacts/Komelia-Test-modern-ui-aligned-20261102.apk`。名称`Komelia Test / Komelia 测试版`，包名`io.github.zhengningning.komelia.debug`，版本`0.31.1-debug`（60），target SDK37。aapt2身份、v2签名、保留数据覆盖安装及冷启动均通过，[制品身份](modern-ui-alignment-20261102-assets/apk-identity.json)记录检查结果。

SHA-256：`5547a96031c2791e50ea317060a0d1f021fdffc70f18b37996ab92059f17e23d`。本地工作树保留供真机验证；外部发布不在本次操作范围。
