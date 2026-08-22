# Komelia UI 二次优化视觉 QA

## 验收环境

- Android 模拟器：1080 × 2400 px，420 dpi（约 411 × 914 dp）
- 语言：简体中文
- 数据：`komga.nings.top` 真实书库会话；凭据未写入仓库或测试日志
- 状态覆盖：首页、搜索、设置、首页分组管理、系列详情、书籍详情、PDF 阅读、EPUB 阅读

## 对比输入

| 页面 | 参考图 | 实现截图 | 同屏对比 |
| --- | --- | --- | --- |
| 首页 | `/var/folders/6k/w12ht07j5p95l2qn8_63g1zm0000gn/T/codex-clipboard-bccdeb2e-0f51-4262-8688-53153be80851.png`（786 × 1548） | `/private/tmp/komelia-home-after-2.png`（1080 × 2400） | `/private/tmp/komelia-home-comparison.png` |
| 搜索 | `/var/folders/6k/w12ht07j5p95l2qn8_63g1zm0000gn/T/codex-clipboard-cd1d7bf9-6768-4515-b27c-3b463e6cc507.png`（742 × 1470） | `/private/tmp/komelia-search-after.png`（1080 × 2400） | `/private/tmp/komelia-search-comparison.png` |
| 设置 | `/var/folders/6k/w12ht07j5p95l2qn8_63g1zm0000gn/T/codex-clipboard-f313a805-219d-4836-8edb-997956f26738.png`（748 × 1050） | `/private/tmp/komelia-settings-after.png`（1080 × 2400） | `/private/tmp/komelia-settings-comparison.png` |
| 首页分组迭代 | 设置页圆角区块语义与首轮实现 | `/private/tmp/komelia-home-groups-after-fix-2.png`（1080 × 2400） | `/private/tmp/komelia-home-groups-iteration-comparison.png` |

参考图包含设备外框，实现图为模拟器完整屏幕，因此同屏图按内容区高度归一化；视觉判断只比较应用内容，不比较设备外框。

## 全屏与重点区域检查

- 首页：三列封面层级保持清晰；系列卡片统一使用固定 `TitleOnly` 槽位，首页书籍统一使用固定 `TitleWithSupporting` 槽位，同排底边对齐；单行标题垂直居中，无原 72dp 灰色空块。
- 首页导航：页面、卡片、底部导航分别使用近白、白色和极浅蓝紫表面；主色仅用于选中态。顶部只显示“全部 + 当前分组 + 更多”，设置入口已迁移。
- 搜索：分段切换紧凑；结果卡片使用白色高层表面和轻描边；标签可换行，最多显示三项并以 `+N` 汇总，未发现横向裁切或页面溢出。
- 设置：分组采用 16dp 圆角卡片；分隔线只存在于相邻行之间；新增“首页分组”入口。
- 阅读器：PDF 实际打开至 96/201 页；EPUB 实际打开 2184 页套装正文。两者均能显示真实内容、阅读进度与工具栏，未发现固定栏遮挡正文。

## 迭代记录

1. 首轮检查发现 P1：首页分组编辑页未消费系统顶部安全区，标题和返回按钮与状态栏重叠；列表仍使用整行分隔线，与新的圆角分组语义不一致。
2. 修复：增加 `WindowInsets.systemBars` 顶部占位；每个分组改为 16dp 圆角高层表面、1dp 描边和独立间距；拖动态使用 3dp 语义强调描边。
3. 二次同屏复核：状态栏重叠消失，返回/完成/重置操作完整可见；卡片圆角、间距和边界一致，无裁切、错位或横向溢出。

## 遗留问题

- 无阻断问题。真实 2184 页 EPUB 首次打开约需二十秒，加载期间显示进度状态；属于内容体积相关性能观察，不影响本次 UI 验收。

Final result: passed
