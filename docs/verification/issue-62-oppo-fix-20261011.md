# Issue 62：视口请求竞态修复与 OPPO 回归

后续正式包验证见 [Android 0.29.0 发布前验证](release-029-20261011.md)。以下保留测试候选包的实际证据。

日期：2026-10-11。继续使用此前已复现问题的 OPPO PKJ110、Android 16，以及同一份原作者长图归档。复现记录见 [OPPO 再次复测](issue-62-oppo-recheck-20261011.md)。

结论：已修复并发提交时会丢失最终视口的代码缺陷。最终测试包在同一台 OPPO 上按原步骤完成三轮回归，停手 20 秒、40 秒均清晰，第三轮静置约 110 秒仍清晰。此结论限于本次连续长图路径，不替代所有 #62 场景的验收。

## 已证实的问题与修复

`TilingReaderImage.requestUpdate` 原先分别更新请求序号、最新请求，再向 `jobFlow` 写入请求。连续阅读的前台视口更新与后台下一页预加载都调用这个入口。

两个调用并发交错时，`jobFlow` 最后的请求可能不是 `latestVisibleRequest`。处理器检查两者不一致后丢弃队列请求，但此时最新请求已被覆盖出队列，没有新的滚动就不会再收到更新。这条代码路径能够使画面停留在低清预览。

临时 JVM 探针调用实际 `TilingReaderImage.requestUpdate`，以四个线程执行 500 轮并发提交。在停止提交、所有线程到达屏障后，有 2 轮仍观察到队列请求与最新视口不一致，断言失败。这个数字是本机探针结果，不是手机上的复现概率。失败报告及探针源文件保存在忽略的证据目录，临时反射探针不保留在正式测试集。

修复使用 `VisibleImageRequests` 将请求和序号放进同一个原子状态。渲染器直接消费该状态，不再另写一条可能与其分离的队列。相同几何参数去重；重载和失败重试仍能产生新版本。设置变化时重载当前请求，不能把旧的已渲染视口写回并覆盖新位置。

本次没有改变采样算法、画质、手势、持久化数据或下一页预加载策略。临时诊断日志已移除。

## 测试包

- 包名：`io.github.zhengningning.komelia.debug`，版本 `0.28.1-debug`，versionCode 56。
- 文件：`build/qa/oppo-62-recheck-20261011/Komelia-62-atomic-requests-debug.apk`。
- SHA-256：`bfbb848d85bfe5aa8843b6e4ee35eaa03f1e8fb0dc39ab8aedd7a914bb31dfd2`。
- 安装使用 `adb install -r`，数据保留；签名与原测试版一致。已读取手机实际安装 APK 的 SHA-256，结果一致。
- 正式版未更新。没有重启手机、重新录屏、清空数据或放宽系统权限。

## OPPO 回归

配置为连续、从上到下、侧边距 0%、Lanczos3 放大、Mitchell 缩小；原有裁边、放大小尺寸图像和单书颜色校正选项不变。已从设置截图核对。

每轮使用底部进度条跳转约第 10 → 15 → 8 → 20 → 10 页，间隔 140 ms 加系统调用耗时，静置观察 40 秒。然后执行 18 次往返快滑，每三次改变方向，单次 100 ms、间隔 35 ms 加系统调用耗时。坐标与此前复现相同。停手后只截屏，不打开控制栏、不轻滑触发恢复。

最终包完成三轮串行回归，均未出现停手后持续模糊。实际检查了以下截图中的文字和线条，不以文件存在或 PNG 摘要代替清晰度判断。

| 轮次 | 快滑后 20 秒 | 快滑后 40 秒 | 证据前缀 |
|---|---|---|---|
| 1 | 清晰 | 清晰 | `f62-1-zigzag` |
| 2 | 清晰 | 清晰 | `f62-2-zigzag` |
| 3 | 清晰 | 清晰 | `f62-3-zigzag` |

输入时序和 0、2、5、20、40 秒截图保存在 `build/qa/oppo-62-recheck-20261011/`，摘要清单为 `final-oppo-evidence.json`。静置期间没有额外轻滑或切换设置来触发恢复。

第三轮继续静置到约 110 秒，画面仍清晰；截图从估算停手时间后 110,075 ms 开始、110,472 ms 完成。`f62-3-idle-110.png` 与该轮 40 秒截图的 SHA-256 相同，为 `81289698fc7479a227ab17318813e6c460ab02513138a045a2400d096aa94155`。

## 设置恢复与基本阅读检查

已恢复分页、适应屏幕、从左到右、单页，放大和缩小均为 Lanczos3，并检查设置截图。旋转仍为竖屏，自动旋转仍关闭，密度覆盖仍为 420。

最终包已分别打开并通过工具栏退出三份合成 QA 样本：漫画显示第 3/4 页、PDF 显示第 2/4 页、EPUB 显示 Chapter 1 第 1/1 页。只操作本轮 QA 样本，不扫描或修改其他书库。

随后仅停止并冷启动测试应用，重新打开三份样本，仍分别显示相同页码和章节，并正常返回首页。手机没有重启，EPUB 的分页设置未改变。冷启动截图以 `f62-cold-` 开头。最后清理了本轮 UI 检查生成的单个临时 XML；备份、样本和截图保留。

## 自动检查

已通过 Android、Desktop、Wasm 构建，以及 UI、core、shared 的 `allTests`。新增完整图块发布测试后，再次执行这三个模块的 `allTests`，共 434 条通过、0 失败、0 跳过。UI 为 394 条，core 为 28 条，shared 为 12 条，包含 JVM 与 Wasm 浏览器结果。

新增测试覆盖最后视口合并、A → B → A 的版本隔离、同参数去重、显式重载、重载不覆盖更新位置、200,000 次并发提交，以及真实图块管线中暂停旧处理后发布最终视口。已有预览图、分镜预渲染和原生图片生命周期测试同时运行。

命令与结果日志均保存在上述证据目录。仓库检查器测试和回归执行器测试共 21 条通过；Komga EPUB 测试 21 条通过。检查器、`git diff --check` 和冲突标记检查通过。

构建和测试命令如下，Android SDK 环境沿用本机配置：

```shell
./gradlew androidDebug :komelia-ui:allTests :komelia-domain:core:allTests :komelia-app:shared:allTests desktopJar komfWebUI --max-workers=1 -Pkotlin.daemon.jvmargs=-Xmx8096m --no-configuration-cache --console=plain
./gradlew :komelia-ui:allTests :komelia-domain:core:allTests :komelia-app:shared:allTests --max-workers=1 -Pkotlin.daemon.jvmargs=-Xmx8096m --no-configuration-cache --console=plain
node --test scripts/check-harness.test.mjs scripts/regression.test.mjs
node scripts/check-harness.mjs
git diff --check
```

构建日志为 `atomic-full-matrix.log`，最后一次测试日志为 `final-tests.log`。Komga EPUB 的 `npm test` 从其包目录执行，日志为 `epub-tests.log`。

## 证据边界

此前无日志包已两次复现持续模糊，详见原记录。本轮带日志的诊断包及随后重建的无日志基线包也出现了正常恢复，不能宣称旧代码每次必现。诊断初期有采样窗口与后续输入重叠，以及读取器初始化时过早点击的尝试，均不作为正式回归结果；最终包三轮按完整串行序列执行。

并发探针确认了代码缺陷，但尚未从原手机模糊现场取得这次线程交错的直接追踪，不能断言它是 #62 所有表现的唯一原因。这里验证的是已报告的连续长图路径，不包含分镜检测、ONNX、所有设备或所有采样配置。

修复和证据留在本地工作树，未推送、合并、发布或关闭 Issue。此前录屏辅助进程仍由用户稍后手动清理，本轮不操作该进程。
