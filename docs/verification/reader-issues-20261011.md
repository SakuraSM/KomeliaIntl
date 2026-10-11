# 阅读器 Issue 优化验证

## 结果与范围

本次基于 `88272f5d`，在 `feat/reader-issue-optimizations` 独立工作树完成三项改动。未修改版本号，未推送、合并或发布新版本。

| Issue | 本次改动 | 验证边界 |
|---|---|---|
| [#62](https://github.com/SakuraSM/KomeliaIntl/issues/62) | 连续阅读器同时观察页索引、偏移、视口和可见项尺寸，补齐相同偏移切页及重测量后的清晰图块更新 | 两项真实 Compose 列表回归均旧实现失败、新实现通过；原作者持续模糊现象未完整复现，保持打开 |
| [#72](https://github.com/SakuraSM/KomeliaIntl/issues/72) | Android 阅读控制增加当前页分享，读取点击时书籍和页码的原始编码数据，通过系统分享面板导出 | 状态、失败重试、取消、连续请求、语义和 URI 权限测试通过；CBZ、PDF 的系统面板实际打开并取消，未发送 |
| [#60](https://github.com/SakuraSM/KomeliaIntl/issues/60) | 实际滚动已到章边界时直接切资源，不再等待 SDK 边界状态；保留一手势一次接续的限制 | Node 测试及 Android 慢拖、反向、惯性到边界和短章停留通过；不等同于把所有章节拼接成单一滚动文档 |

[#71](https://github.com/SakuraSM/KomeliaIntl/issues/71) 使用已发布版本的原有实现与验证记录完成回复，并关闭为 completed。本次没有为它新增代码或重新发布版本。

## 构建与自动化

最终 APK 为 `komelia-app/androidApp/build/outputs/apk/debug/androidApp-debug.apk`，包名 `io.github.zhengningning.komelia.debug`，SHA-256：

```text
5ca1642b850b124dfc7e115829f0dc5017dff046585d3dac696cfb32e99658dd
```

调试包与正式版共存。版本仍为现有 `0.28.1-debug`，不是新 Release。APK 已安装到独立 API37 模拟器，保留应用数据。

执行的 Gradle 检查如下。Android SDK 由运行环境提供；重试使用 `--max-workers=1 -Pkotlin.daemon.jvmargs=-Xmx8096m`，没有修改仓库的 JVM 参数。

```shell
./gradlew androidDebug desktopJar komfWebUI buildEpubReaders --no-configuration-cache
./gradlew :komelia-ui:allTests :komelia-domain:core:allTests \
  :komelia-domain:offline:allTests :komelia-infra:database:sqlite:allTests \
  :komelia-app:shared:allTests :komelia-infra:image-decoder:shared:allTests \
  --no-configuration-cache
./gradlew :komelia-app:androidApp:assembleDebugAndroidTest --no-configuration-cache
```

Android、Desktop、Wasm 和两个 EPUB 包构建通过。最后一次分享状态收尾修改后，重新执行 Android、Desktop、Wasm、UI/shared-app allTests 和最终包原生分享检查。

| 模块 | JUnit 用例 | 失败 / 错误 | 跳过 |
|---|---:|---:|---:|
| UI，JVM 与 Wasm 浏览器 | 393 | 0 | 0 |
| Domain core | 19 | 0 | 0 |
| Offline | 58 | 0 | 1 |
| SQLite | 34 | 0 | 0 |
| Shared app | 12 | 0 | 0 |
| Shared image decoder | 14 | 0 | 0 |
| 合计 | 530 | 0 | 1 |

529 项通过。跳过项为未提供原始/参考归档对的可选 `OriginalArchiveAcceptanceTest`，不是通过；本次 #62 的单份 CBZ 不替代该测试所需的另一组样本。

另外执行并通过：

- `komga-webui` 的 `npm test`，21 项。新增 DOM 边界与 SDK 状态滞后、章内不跳章、无 DOM 回退及惯性后短章只接续一次的测试。
- TTU 的 `npm run check`，0 errors / 0 warnings。
- `node --test scripts/regression.test.mjs scripts/check-harness.test.mjs tests/regression/fixtures.test.mjs`，22 项。
- API37 上 `AndroidPageShareTest`，2 项。实际 PNG 字节、MIME、content URI、只读授权、provider 路径边界和非法图片均有断言。
- `node scripts/check-harness.mjs`、`git diff --check` 与冲突标记检查。

首次综合检查先因未提供 SDK 路径退出；配置运行环境后又在 Wasm 测试编译出现 `GC overhead limit exceeded`。明确配置 8096 MiB 编译堆并串行重跑后通过。没有把首次失败记录当作成功。

EPUB 内嵌 HTML 的二进制 diff 超过 Node 子进程默认 1 MiB 缓冲时，源码指纹测试曾报 `ENOBUFS`。验证脚本现使用有界的 64 MiB 缓冲，原先失败的实际工作树指纹测试已通过。

## 原生验证

环境为独立 Android API37 模拟器，1080×2400，中文应用界面，合成 CBZ/PDF/EPUB。ADB 提供实际触摸输入，WebView 调试接口只用于观测、安装触摸记录器及准备合成章节的滚动位置，没有调用阅读器切章方法来替代触摸。

最终 APK 上完成：

- 冷启动、进入合成漫画和 PDF，正确显示阅读页。PDF 保持第 2/3 页及详情页 67% 进度。
- CBZ 和 PDF 均由“分享当前页”打开系统 `Sharing image` 面板；取消后仍能返回阅读和详情，未选择收件人或发送。
- Komga EPUB 长章顶部慢拖 1100 ms 返回短章，再慢拖进入长章。记录到 `touchstart`、`touchend` 的 `isTrusted=true`。
- 长章靠近底部后，以 100 ms 快滑触发惯性。最终包记录的抬手位置为 `10884.19`，章末为 `10943` CSS 像素。抬手尚距章末约 59 像素，随后进入第 3 章，不需要第二次手势。
- 短章停留不自动连跳。测试完成后恢复原先的 TTU 阅读器偏好；未更改真实服务器或清空应用数据。

#62 原始样本单独检查：CBZ 为 12,314,997 字节，SHA-256 `d9ed6368fa608bf11b5eb17bc9da45142cd3f5416bbd0f19fa61de50b6126ed3`，25 个 WebP 页面，包含 900×16000 长图。录像时长 26.61 秒。代理只用于下载公开附件。

旧包及首个验证包在 Lanczos3 放大、Mitchell 缩小、无颜色校正的连续模式下，快速翻动都出现低清预览，停留 20 秒后恢复清晰。首个验证包 SHA-256 为 `c799604a6c72fa6a3f950830d908a17c9f8844334d7bc33c9289d0e8d847333a`，其后只有分享忙碌状态收尾逻辑调整，连续阅读和 EPUB 代码未变。这不是最终 APK 对原作者持续模糊场景的完整验收，也没有证明已消除所有低清停留。原始图片、录像和截图未加入仓库。模拟器中新建的原始样本副本及其索引已移除，主机下载原件仍保留。

## 尚未覆盖

实机/OEM、原作者持续模糊及分镜模式、所有设备尺寸和主题、在线服务器分享、具体接收应用、分享在横屏及英文界面的原生验收仍待补充。EPUB 双指、取消和过期保护已有 Node 测试，本次未在最终 APK 上重跑多指输入。完整 `CONTINUOUS-01`、`SHARE-01`、`EPUB-02` 与全量 core 手工验收不标记为全部通过。

除已授权处理的 #71 外，其他 Issue 保持打开。后续合并、发布或关闭需要单独授权及相应验收。
