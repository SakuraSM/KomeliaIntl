# 阅读模式提示与视觉稿验证

日期：2026-10-29。分支 `feat/reading-experience-v2`，基线 `f02533b6e88909f0c8f5ac49f53a269ea9d372a3`。

本记录对应[阅读体验视觉方案](../design/reading-experience-v2-20261029/README.md)与模式提示持久化修复阶段。后续布局实施见[UI 对齐验证](reading-experience-ui-20261029.md)。本页 APK 与测试数字只属于提示修复阶段，未合并或发布。

## 提示行为与持久化

同一有效模式和方向使用设备级签名去重。旧版本只保留阅读会话标记，退出后再次提示；修复保存 `readerModeHintsEnabled` 和 `lastReaderModeHintSignature`。重新开启会清空签名，关闭当前通知只关闭该条通知。

V20 只追加两个字段。旧 JSON 缺字段时默认开启、签名为空。签名使用稳定枚举名，保留原有阅读配置、进度和其他通知。设置写入被接受后，即使离开页面也完成保存；保存失败保留真实的已保存值并报告错误。

## 自动检查

下列命令已经执行。完整原始日志位于工作区忽略的 `build/verification/reading-experience-v2/`。

| 检查 | 结果 | 证据 |
|---|---|---|
| 修改前行为回归 | 预期失败，同配置的新会话再次提示 | `hint-red.log` |
| 聚焦提示与设置测试 | 通过 | `hint-isolated-green.log` |
| UI JVM 测试 | 308 项，0 失败、0 跳过 | JUnit 报告及 core 日志 |
| UI Wasm 浏览器测试 | 181 项，0 失败、0 跳过 | JUnit 报告及 core 日志 |
| core、shared 与 UI 的完整核心自动测试 | 545 项，0 失败、0 跳过 | regression core 记录 |
| SQLite 测试 | 44 项，0 失败、0 跳过 | `sqlite-final.log`、JUnit 报告 |
| Android、桌面 JAR、Wasm 构建 | 通过 | `platform-matrix-verified.log` |
| Harness、差异空白与冲突标记检查 | 通过 | Harness 输出、`git diff --check`，未发现冲突标记 |
| 预览 JavaScript 语法 | 四个模块通过 `node --check` | 命令输出 |

平台命令：

```sh
./gradlew :komelia-ui:allTests :komelia-domain:core:allTests :komelia-app:shared:allTests :komelia-infra:database:sqlite:allTests androidDebug desktopJar komfWebUI --no-configuration-cache
node scripts/regression.mjs run core
```

新增和修订的测试覆盖跨会话、仓库重开、真实配置变化、同值选择、延迟加载、取消、并发消费、关闭与重开、保存失败、离开设置页、旧 SQLite 迁移及旧浏览器 JSON。历史迁移校验值未修改。全量检查曾发现测试共享协程作用域和旧迁移夹具使用当前仓库的问题，已隔离测试作用域并改为按旧列名构造夹具，随后全量通过。

`regression core` 的自动命令通过不代表整个人工矩阵通过。完整人工验收仍未完成，不能将该 profile 的整体结果写成通过。

## Android 实测

环境标注补充：主模拟器旧版本标签没有保留同次设备属性证据。2026-10-30 实际读取同名模拟器为 API 37，因此这里不把旧标签计为已核实的 API 35 覆盖。下面保留已观察到的行为，版本覆盖仍待补验。

最终调试包 SHA256：

```text
4770111e916fea699e465d6bc897de0f98c79016d5fec6cc2aebc91760a20cd0
```

使用专用 `Komelia_Regression_UI_20261028`，英文，以及 `Komelia_Regression_Visual37_20261025`，API 37、简体中文。两者均为 1080 × 2400 的受控合成书库，没有使用日常账号或私有书籍。覆盖安装保留了应用数据。

| 场景 | 已观察结果 |
|---|---|
| 真实方向变化 | 主模拟器实际出现 Paged / Right to left 提示，持久化签名同步变化 |
| 同方向重复选择、退出重入 | 未观察到重复提示；当前签名保持 |
| 应用进程重启 | 开关与签名保留；再次进入没有重复提示 |
| 关闭后立即离开设置 | 数据库保留关闭值；再次进入无提示 |
| 关闭后实际改变方向 | 阅读方向保存为 RIGHT_TO_LEFT，已显示签名仍为旧值，没有提示 |
| 重新开启 | 数据库签名清空；下一次进入重新消费并保存签名。该次提示的原生可视显示尚未取得确认，不计为视觉验收通过 |
| 主模拟器竖横屏往返十轮 | 未观察到重复提示，结束后仍为第 24 / 28 页 |
| API 37 切换到连续阅读 | 模式及签名变为 CONTINUOUS:TOP_TO_BOTTOM |
| API 37 从漫画换到 PDF | 同模式和方向不重复提示，PDF 初始恢复第 2 / 3 页 |
| API 37 连续 PDF 竖横屏往返十轮 | 结束时计数变为第 3 / 3 页，此项未通过，尚未确认来源；不能据此宣称完整阅读位置回归通过 |
| 简体中文设置 | 开关、说明及相关控件可见 |

[方向变化提示](assets/reader-mode-hints-20261029/direction-change.png) · [关闭后的阅读画面](assets/reader-mode-hints-20261029/disabled.png) · [主模拟器旋转后第 24 页](assets/reader-mode-hints-20261029/rotated-page24.png) · [中文设置开关](assets/reader-mode-hints-20261029/preference-zh.png)

原始截图、可访问性树、设置字段快照和录像保留在忽略的验证目录。部分转场中的 `uiautomator dump` 返回空树，随后使用有界只读检查和实际画面继续验证；没有把空树当作功能通过。

## 视觉稿验证

16 张独立 PNG 已逐页检查，分别覆盖 8 个手机主页面、4 类浮层、2 张冷白对照及平板、桌面。手机 1170 × 2532，平板 2502 × 3582，桌面 2880 × 2048。尺寸及摘要保存在[导出记录](../design/reading-experience-v2-20261029/generation-record.json)。

HTML 预览已检查 360 逻辑宽度和 130% 字号，没有测得横向溢出。筛选选中态、Escape 关闭、搜索过滤与空结果、章节展开、提示开关及浮层内键盘焦点已操作。固定颜色组合的最低对比度为 5.53:1，动态玻璃仍需按真实背景验证。

预览中的书籍、作者和进度都是设计示例。该检查不证明原生 Compose 的布局、手势、读屏或帧率已通过。真实设备验证按本轮约定保持暂停；未执行发布包升级或线上业务验收。
