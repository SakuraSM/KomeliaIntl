# 翻译贡献指南 (i18n Contributing Guide)

感谢你愿意帮助 Komelia 改进多语言支持！本文档说明如何新增或维护一种语言的翻译。

## 架构概览

Komelia 使用一套自定义的、基于 Kotlin `data class` 的字符串字典，而不是引入额外的 i18n 框架（如 moko-resources 或 Compose 资源）。这样做是为了：

- 避免在跨平台（Android / Desktop / Web (wasmJs)）项目中引入额外的代码生成复杂度。
- 让翻译以普通的 Kotlin 文件形式存在，便于 IDE 检查、PR 审查、`git blame` 与单文件回滚。

关键文件位于 `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/strings/`：

- `AppStrings.kt`：定义所有字符串分组的 `data class` 结构。
- `EnStrings.kt`：英文实例（项目默认语言）。
- `ZhCnStrings.kt`：简体中文实例。
- `AppStringsResolver.kt`：根据用户偏好与系统 locale 选择具体实例。

运行时由 `LocalStrings`（`komelia-ui/src/commonMain/kotlin/snd/komelia/ui/CompositionLocals.kt`）的 Composition Local 提供，UI 通过 `LocalStrings.current.<group>.<key>` 读取。

语言偏好存储在 `AppSettings.appLanguage`（`AppLanguage` 枚举：`SYSTEM` / `EN` / `ZH_CN`），由 `komelia-app/src/commonMain/kotlin/snd/komelia/AppModule.kt` 在启动时与设置变更时推送进 `appStrings: StateFlow<AppStrings>`。

## 新增一种语言

以新增繁体中文 `zh-TW` 为例：

1. **扩展 `AppLanguage` 枚举**（`komelia-domain/core/src/commonMain/kotlin/snd/komelia/settings/model/AppLanguage.kt`）：
   ```kotlin
   enum class AppLanguage { SYSTEM, EN, ZH_CN, ZH_TW }
   ```

2. **创建对应的字符串文件**：
   - 复制 `EnStrings.kt`，重命名为 `ZhTwStrings.kt`，将顶层常量改名为 `ZhTwStrings`。
   - 翻译每个字段。务必保证 `data class` 中所有字段都被赋值；新增字段时也要同步更新所有语言文件。

3. **在 `AppStringsResolver.kt` 中注册**：
   ```kotlin
   AppLanguage.ZH_TW -> ZhTwStrings
   ```
   并在 `SYSTEM` 分支中添加合理的回退规则（如 `tag.startsWith("zh-tw") || tag.startsWith("zh-hant")` → `ZhTwStrings`）。

4. **在 `AppStrings.kt` 的 `SettingsStrings.forAppLanguage` 中添加分支**，并在 `SettingsStrings` 数据类中新增 `appLanguageChineseTraditional: String` 字段，再让 `EnStrings` 与所有现有语言文件给该字段赋值。

5. **（可选）补充元信息本地化**：参考 `README_zh-CN.md`、`PRIVACY_POLICY_zh-CN.MD`、`fastlane/metadata/android/zh-CN/` 复制对应文件。

## 翻译规范

请遵循 [`docs/i18n/glossary_zh-CN.md`](./glossary_zh-CN.md) 中的术语表与规范。其它语言可在 `docs/i18n/` 下创建对应的 `glossary_<lang>.md`。核心要点：

- 优先意译，保持自然通顺；按钮使用动词、标题使用名词。
- 品牌名、协议关键字、文件格式、图像算法专有名词不译；可在首次出现时中文括注。
- 保持占位符 / 模板变量顺序与英文一致；语序差异较大时改用具名 lambda（已在 `AppStrings` 中预留模式）。

## 当前覆盖范围说明

`AppStrings` 目前仅覆盖应用中的一部分字符串（设置页、阅读器设置、筛选器、错误码、Komf 提供方等约 268 个键）。
应用 UI 中仍有大量硬编码的英文字面量（约 580 处 `Text("...")`）尚未迁移到 `AppStrings`。这部分将在后续 PR 中按目录分批迁移；翻译工作会随之同步扩展。

如果你在某个屏幕上看到未被翻译的英文，欢迎：

1. 把对应字符串加入 `AppStrings.kt` 中合适的分组（必要时新建分组）。
2. 在 `EnStrings.kt`、`ZhCnStrings.kt` 与所有其他语言文件中添加对应字段。
3. 在 UI 文件中将 `Text("...")` 替换为 `Text(LocalStrings.current.<group>.<key>)`。

提交 PR 时请按目录拆分 commit，便于审查。

## 校验

修改后请确保：

- `./gradlew build` 通过（多平台编译）。
- 切换设置中的"语言"下拉项，UI 文案立即更新。
- 切换系统语言至目标语言后，启动应用并保持 `跟随系统` 选项，UI 文案为目标语言。
