# 回归测试集

本目录是每次改动的验收入口。自动命令复用现有 Kotlin、EPUB 和 Android 测试；22 条人工用例覆盖真实用户流程。人工用例尚未自动化，命令成功不会把它们标为通过。

## 查看计划并执行

从仓库根目录执行：

```sh
node scripts/regression.mjs plan core
node scripts/regression.mjs run guard
node scripts/regression.mjs run login
```

`plan` 只输出计划，不执行测试。`run` 按顺序执行并在首次失败后阻止后续命令，记录写入忽略的 `build/regression/`。执行器不安装依赖、不启动模拟器、不创建 release、不改变远端，也不自动重试失败测试。先按项目说明准备 JDK、Node、子模块、Android SDK 和 Chrome。

| Profile | 何时执行 | 自动化范围 | 额外人工验收 |
|---|---|---|---|
| `guard` | 文档、工具改动；每次改动的基础检查 | 仓库检查、检查器测试、执行器测试 | 无应用验收结论 |
| `core` | 每次应用代码改动 | UI、core、shared 的 JVM/Wasm 测试 | 启动返回、三种格式阅读与进度、语言尺寸 |
| `login` | 登录、网络、权限、账号切换 | core | 登录、API Key、LAN 拒绝/授予/撤销 |
| `reader` | 图片/PDF/EPUB、手势、生命周期 | core、EPUB、TTU | 阅读、目录、进度、迟到响应、分镜 |
| `offline` | 本地书库、下载、持久化、解析 | core、offline、SQLite | 导入、重扫、删除、进度保留 |
| `premerge` | 合并前；上游、公共依赖或跨模块改动 | 所有上述命令及 Android/Desktop/Wasm 构建 | 全部核心流程 |
| `release` | 发布前 | premerge、发布策略检查 | 最终签名包升级、原始样本、真实服务端 |

按影响面选择一个或多个专项 profile，不用 profile 名称替代影响分析。纯文档和测试工具改动可只跑 `guard`。应用代码至少跑 `core` 和受影响专项。依赖、导航、共享状态、上游合并跑 `premerge`。HarmonyOS 使用独立验证流程，本集合不覆盖它。

执行器对 Gradle 测试加 `--rerun-tasks`，读取 JUnit 的失败和跳过数，不复用旧成功记录。重型构建可能按 Gradle 正常增量执行。修复不稳定测试，不通过无限重试刷绿；隔离用例仍保留编号、原因、责任人及恢复条件，不能删除或伪装为通过。

## 验收记录

`run` 的退出码为 0：所选范围验收完整；1：执行失败/环境阻塞；2：自动命令结束但人工验收、跳过测试或源码一致性未满足。即使所有命令通过，`core` 等带人工用例的 profile 仍默认返回 2。

每次记录包含提交和当前源码指纹，包括未提交文件。完成模拟器用例后，在该次目录中保存脱敏证据，再填写 `record.json` 中对应条目：

```json
{
  "id": "LAN-01",
  "status": "passed",
  "reason": "",
  "operator": "执行人",
  "environment": "API 37；AVD 型号；屏幕尺寸；语言；主题",
  "artifact": "sha256:填写最终APK或Web构建文件的64位SHA256",
  "completedAt": "填写实际UTC时间",
  "evidence": [{ "path": "lan-dialog.png", "sha256": "填写该证据文件的64位SHA256" }]
}
```

示例不是可通过的记录。使用 `shasum -a 256 FILE` 获取实际摘要。证据路径必须位于该次目录内；校验器拒绝外部路径、目录、越界符号链接及摘要不匹配。查看截图和日志后再填写，不只确认文件存在。

```sh
node scripts/regression.mjs check build/regression/实际目录/record.json
```

`check` 重新计算是否通过，不信任旧的 `acceptance` 字段。源码变化后重跑；不能修改旧指纹来绕过。证据校验只证明文件和记录一致，不能自动判断截图是否满足业务预期，也不能证明任意 APK 来自当前源码。执行人必须核对构建记录与 APK 摘要。`passed`、`failed`、`blocked`、`skipped`、`pending` 都要保留；未通过项填写原因。跳过不能满足完整验收，有豁免时另行说明范围和批准人，不能把总结果改为全通过。

## Android 原生测试

先准备专用 AVD，命名以 `Komelia_Regression_` 开头。API 35 覆盖旧平台，API 37 覆盖 LAN 权限。不要使用日常账号、真实书库或共享模拟器。

```sh
./gradlew androidDebug :komelia-app:androidApp:assembleDebugAndroidTest --no-configuration-cache
node scripts/android-regression.mjs emulator-端口 \
  komelia-app/androidApp/build/outputs/apk/debug/androidApp-debug.apk \
  komelia-app/androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
```

将 `emulator-端口` 替换为实际序列号。脚本要求 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT`。它验证 AVD 名称和两个 APK 的 debug 包名，然后仅对指定模拟器覆盖安装、执行原生测试；不会清除数据、卸载或停止模拟器。保留原生输出及两个 APK 摘要。原生测试不是完整 UI 冒烟，仍须逐项执行目录里的人工用例。

## LAN 权限固定夹具

```sh
node tests/regression/permission-server.mjs
```

该服务仅绑定本机回环地址，默认端口 18764，可通过 `PORT` 改变。Android 模拟器使用 `http://10.0.2.2:18764`。它始终返回 401，只记录方法和路径，不记录请求头、凭据或正文。

执行 `LAN-01`：确认系统弹窗真实出现，拒绝后请求未到达，允许后请求到达且显示凭据错误。这个 401 仅证明授权后的连通性，不证明成功登录。执行完停止自己启动的服务。成功登录、重试和 EPUB 延迟响应仍需要独立的受控服务，不能用这个夹具替代。

## 用例维护

权威目录为 [catalog.json](catalog.json)，每条包含前置条件、操作、预期、issue 和证据要求。新增修复时优先增加最低成本的自动回归测试，再为生命周期、原生渲染或跨组件问题补端到端用例。记录预期与真实结果，不只勾选步骤。

- 原始漫画、模型和真机验收不能由合成数据替代。缺少数据时记录阻塞。
- 测试数据只能使用合成或已授权样本；原始用户文件、Cookie、Token、签名密钥不得入库。
- 图片/PDF/EPUB 必须覆盖返回、重进和进度恢复；授权与取消应覆盖失败路径。
- 最终签名包升级单独验收，不用 debug 包或编译结果代替。
- 现阶段没有新增 CI 阻断。先积累耗时、失败率和环境稳定性，再决定接入。

## 社区依据

资料核查日期：2026-09-14。

- [Android Testing strategies](https://developer.android.com/training/testing/fundamentals/strategies)：分开单元、组件、功能、应用和候选发布包测试；按反馈成本和真实性选择层级。本文采用其分层原则，并为媒体原生渲染保留设备验收。
- [The Practical Test Pyramid](https://martinfowler.com/articles/practical-test-pyramid.html)：快速测试覆盖大部分行为，少量端到端测试覆盖关键用户路径；避免重复测试同一逻辑。本文复用现有测试，不另建平行框架。
- [Testing Compose Multiplatform UI](https://kotlinlang.org/docs/multiplatform/compose-test.html)：共享 UI 测试有平台执行差异。本文分别记录 JVM、Wasm 和 Android，不把一个目标的通过外推到其他目标。
