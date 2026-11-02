# 现代阅读界面对齐验证

18页视觉方向已落实到共享Compose与两套EPUB界面。本次交付为本地可审阅实现与测试包。参考、尺寸与容差见[对齐标尺](../design/modern-ui-20261030/MEASUREMENTS.md)，样式基线运行图见[18页并排对照](modern-ui-alignment-20261102-assets/index.html)。

按用户确认的口径，验收对象为全页控件的位置、尺寸和间距，保留真实书籍内容；字形和图片不作整图像素比较。全页控件的2px验收仍未完成。当前证据只证明19项关键几何量最大偏差为2px，不能据此声称所有页面的控件都在2px以内。设计核对结果见[design-qa](../../design-qa.md)。

## 改动与入口

- `ReadingDesign`、`Theme`与共享chrome负责颜色、布局、导航和浮层。原生面板使用96%底色覆盖，亮书页不再把深色设置洗灰；缺失或暂停采样时保留实体底板。
- 首页续读区并排，近期阅读为三列短预览，新增内容为列表。书库将范围、搜索、状态、排序及网格/列表放在同一层；筛选面板采用横向表单，重置保留在高级筛选中。
- 全局搜索合并系列与书籍，也支持单独分类、实际菜单及分页。详情保留唯一固定阅读操作；相邻书籍保留前后方向。宽屏列表、网格与详情共享选中项，收回手机后恢复手机字号。
- TTU设置与目录使用真实组件，保留六主题、字体管理、搜索和标记。Komga设置沿用八个模型与父级写入路径。阅读主题、出版物内容、存储值和进度协议保持原有契约。

## 自动检查

`node scripts/regression.mjs run premerge`的8个自动检查均已执行通过：guard、harness、runner、core、offline、epub、ttu和build。逐次原始记录位于本地`build/verification/visual-alignment-20261102/regression-final/`。

| 检查 | 结果与范围 |
|---|---|
| core，含`:komelia-ui:allTests` | 577项，0失败、0错误、0跳过 |
| offline | 102项，0失败、0错误、1跳过；`OriginalArchiveAcceptanceTest`未获得可选原始档案fixture |
| Komga `npm test` | 29项通过；覆盖回调、主题对比度、目录、手势事件与键盘 |
| TTU `npm run check` | svelte-check与tsc通过，0错误、0警告 |
| TTU `npm run test:browser` | 8项通过；覆盖真实设置、目录切换、保存回调、Escape和窄屏 |
| `./gradlew androidDebug desktopJar komfWebUI` | 三个平台构建通过，包含两套EPUB构建及资源同步 |
| 几何与背景回归 | 19项实际布局/DOM量均在2px内；另检查亮书页上的深色面板、暂停采样、宽屏按钮主色及大字号标题 |

[实测JSON](modern-ui-alignment-20261102-assets/measurements.json)记录每项参考、实际值与偏差。Compose使用density1；浏览器组件使用390×844、DPR1。两套设置外框均实测为x0、y274、宽390、高570。原生运行图与浏览器组件的系统区域不同，分别记录。

预合并profile的29个人工条目仍为pending，整体`accepted`为false；自动命令通过不等于完整验收。通用DFA单文件检查因未安装ESLint而阻塞，未额外改动工具链；Vue使用项目的vue-tsc构建与浏览器回归。

## 原生复核与恢复

使用专用API37模拟器与合成书库，对18个页面/状态逐页取图，截图对应样式基线APK（a1eb8cde）。最终测试包补齐书库列表的原有菜单，另见[列表菜单运行图](modern-ui-alignment-20261102-assets/list-menu.png)；两组制品身份分别记录。观察了网格/列表切换、高级筛选开关、查询跨导航保留、详情与阅读返回、宽屏选择保留、两套阅读设置、TTU目录及主题/字号恢复。额外检查了浅色、深色、OLED、英文与360宽度130%字号；窄屏长导航标签按现有规则缩略，保留完整语义标签。

截图保留原始物理像素，展示时才归一化。手机780×1736、density320；平板1668×2480；1440逻辑宽度使用Android宽屏。后者不能替代原生Desktop安装与窗口验收。目录层级、封面缺失、章节数与进度均来自实际测试模型，不填入概念稿中的示例数字。

结束时已恢复1080×2400、density420、fontScale1.0、硬键盘显示输入法选项0、拼音键盘、应用语言SYSTEM、主题DARK、液态玻璃、未减少透明效果、书籍GRID布局、TTU阅读器及20号浅色阅读主题；Komga恢复滚动与日间。真机、TalkBack、PDF系统返回手势、API35完整流程与长期性能仍待独立验收。

## 测试包

应用名称为`Komelia Test / Komelia 测试版`，包名`io.github.zhengningning.komelia.debug`，版本`0.31.1-debug`（60），target SDK37。最终APK已通过aapt2身份检查、v2签名验证、覆盖安装与冷启动，保留原有测试数据。

文件为`Komelia-Test-modern-ui-aligned-20261102.apk`，位于本地忽略的`build/artifacts/`目录。SHA-256为`b03c90da9e238144ed1e9a596cbd6d594b2876c377edd5d3fc480a00eedab0dc`。完整身份与签名结果见[制品记录](modern-ui-alignment-20261102-assets/apk-identity.json)。
