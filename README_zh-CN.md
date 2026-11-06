# Komelia Intl

[English](README.md) | [简体中文](README_zh-CN.md)

[![最新版本](https://img.shields.io/github/v/release/SakuraSM/KomeliaIntl?display_name=tag&sort=semver)](https://github.com/SakuraSM/KomeliaIntl/releases/latest)
[![许可证](https://img.shields.io/github/license/SakuraSM/KomeliaIntl)](LICENSE)

Komelia Intl 是 [Komelia](https://github.com/Snd-R/Komelia) 的 `SakuraSM/KomeliaIntl` 分支版本。Komelia 是 [Komga](https://komga.org/) 媒体服务器的跨平台客户端。本仓库增加了简体中文、自适应界面、阅读器问题修复和独立发布支持。

使用 Komga 相关功能时，你需要一个可以访问的 Komga 服务器和 Komga 账号。Android 与桌面端也可不连接服务器，直接进入本地书库模式并索引设备文件夹中的受支持书籍。

## 本仓库的重要改动

### 阅读界面

首页将封面、阅读进度和继续阅读放在一起。书库和本地页面提供常驻搜索与筛选，书籍详情保留一处主阅读操作。宽屏将书目列表或网格放在详情旁，缩放时保留所选书籍。

界面支持经典与液态玻璃外观、浅色、深色和 OLED 主题、减少透明效果、减少动态效果及键盘焦点。图文与 EPUB 阅读控件采用应用外观，TTU 和 Komga EPUB 正文保留独立阅读主题。

### 远程与本地网络动态切换

你可以配置主要远程地址和可选的局域网地址。启用自动切换后，Komelia 会检测局域网地址。局域网可用时优先连接，不可用时继续使用主要远程地址。Android 会在网络状态变化后重新检测。

### 本地下载与离线阅读

在受支持的原生平台，你可以下载书籍，按系列、书籍或媒体类型查看本地缓存，并删除缓存内容。下载后的 CBZ、CBR、PDF 和 EPUB 文件可在离线模式中继续阅读。

### 本地文件夹书库

Android 与桌面端无需登录 Komga 即可添加本地文件夹。Komelia 会递归索引受支持的文件，按父目录生成系列，重启后继续保留目录权限，并在应用启动或定时扫描时发现新增与删除的书籍。两端均支持 CBZ/ZIP、CBR/RAR、EPUB 和 PDF。书籍仍保存在原目录，从 Komelia 移除本地书库不会删除源文件。浏览器/Wasm 版本暂不提供持久化文件夹书库。

Windows 和 Linux 上的 PDF 若包含 JPEG 2000 图片，需要兼容的 ImageIO 解码器；当前安装包未内置该解码器。macOS 使用系统 PDF 渲染器。

## 其他改动

- 增加跟随系统、English 和简体中文三种应用内语言选项。Compose 界面、Android 系统页面和 EPUB 控件会使用所选语言。
- 修复图片与 EPUB 阅读器的控制栏、页面重试、进度拖动和返回行为。
- 应用更新使用本仓库的 Release，并同时展示本仓库和上游 Komelia 的公告。

## 主要功能

- 浏览书库、合集和阅读列表，搜索并筛选系列与书籍。
- 使用内置图片阅读器和 EPUB 阅读器打开 CBZ、CBR、PDF 与 EPUB 文件。
- 编辑系列和书籍元数据，并通过 Komf 完成受支持的元数据处理。

## 维护说明

本项目由我个人独立维护。我会持续跟进并同步上游 Komelia 的改动，以约一周一个迭代为目标。迭代时间会根据上游改动规模、测试结果和个人时间调整。感谢使用 Komelia Intl。

## 构建目标

源码包含下列构建目标。每个 GitHub Release 只会附带该版本实际构建的平台，请以 Release 页的 **Assets** 列表为准。

macOS 源码构建已接入原生图片解码、WebView 和 CPU 版 ONNX Runtime。Apple Silicon 已完成本机运行验证；Intel 构建仍待运行验证。本地构建的 DMG 使用临时签名，尚未公证。

| 平台 | Gradle 任务 | 常见产物 |
|---|---|---|
| Android | `androidRelease` | APK |
| Windows x86_64 | `desktopMsi` | MSI |
| Linux x86_64 | `desktopDeb` | DEB |
| macOS Apple Silicon | `desktopDmg` | DMG |
| macOS Intel（待运行验证） | `desktopDmg` | DMG |
| Windows、Linux 或 macOS 桌面端 | `desktopJar` | JAR |
| 浏览器、WebAssembly | `komfWebUI` | 静态网页文件 |
| Chrome 版 Komf 扩展 | `komfExtensionChrome` | ZIP |
| Firefox 版 Komf 扩展 | `komfExtensionFirefox` | ZIP |

## 下载应用

- [下载 Komelia Intl 最新版本](https://github.com/SakuraSM/KomeliaIntl/releases/latest)
- [查看 Komelia Intl 全部版本](https://github.com/SakuraSM/KomeliaIntl/releases)

本仓库的独立 Android 版本使用包名 `io.github.zhengningning.komelia` 和本仓库的签名证书。Android 无法直接覆盖由其他发布者签名的安装包。如果安装时提示签名冲突，请先卸载其他版本，或继续使用原来的发布渠道。

[上游 Releases](https://github.com/Snd-R/Komelia/releases)、[Google Play 版本](https://play.google.com/store/apps/details?id=io.github.snd_r.komelia)、[F-Droid 版本](https://f-droid.org/packages/io.github.snd_r.komelia/)和 [AUR 版本](https://aur.archlinux.org/packages/komelia)均为上游 Komelia，不包含本仓库的改动。

## 界面预览

下图为 Android 开发版简体中文界面的实际运行图，使用合成测试书籍。

<img src="screenshots/app-overview-20261103.png" alt="当前 Android 首页、书库、书籍详情与本地下载界面" width="100%">

TTU EPUB、漫画与 PDF、Komga EPUB 的阅读设置：

<img src="screenshots/reader-overview-20261103.png" alt="Android 开发版的 TTU EPUB、漫画与 PDF、Komga EPUB 阅读设置" width="100%">

[完整18页运行图](docs/verification/modern-ui-alignment-20261102-assets/screens/)还包含浅色主题与 Android 宽屏布局。构建与设备覆盖见[验证记录](docs/verification/modern-ui-alignment-20261102.md)。

macOS 本地书库，使用合成测试书籍：

<img src="screenshots/macos-local-library-20261106.png" alt="macOS 本地书库中的合成漫画、EPUB 和 PDF 书籍" width="100%">

## 从源码构建

Release 工作流使用 JDK 21 和 Node.js 24；本地 `.node-version` 当前声明 Node.js 20。在仓库统一版本前，请以 [Harness 评分表](docs/harness/scorecard.md)记录的工具链差异为准。Android 和桌面安装包还需要对应平台的原生图片库与 WebView 库。`cmake/` 目录提供 Linux 和 Windows 依赖的 Docker 构建文件。

克隆本仓库并初始化子模块：

```shell
git clone --recurse-submodules https://github.com/SakuraSM/KomeliaIntl.git
cd KomeliaIntl
```

如果已经克隆仓库，请先初始化子模块：

```shell
git submodule update --init --recursive
```

打包应用前先构建 EPUB 阅读器资源：

```shell
./gradlew buildEpubReaders
```

常用目标使用根项目的构建别名：

```shell
./gradlew androidDebug
./gradlew desktopJar
./gradlew komfWebUI
```

在 macOS 上，请先安装并构建原生依赖，再运行 `desktopJar` 或 `desktopDmg`。打包时使用适合生成安装包的 JDK，例如 Temurin 21：

```shell
brew install cmake meson ninja pkgconf glib jpeg-turbo libexif jpeg-xl highway libpng libtiff webp little-cms2 cgif libomp dav1d libde265
scripts/macos/build-native.sh
./gradlew desktopDmg
```

产物位于对应模块的 `build/` 目录。发布安装包和原生依赖的完整命令请参考[桌面端 Release 工作流](.github/workflows/release-desktop.yml)和[工程 Harness](docs/harness/README.md)。

## 参与贡献

提交 Pull Request 前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。翻译改动还需要遵循[简体中文贡献指南](docs/i18n/CONTRIBUTING_zh-CN.md)和[术语表](docs/i18n/glossary_zh-CN.md)。

请在 [GitHub Issues](https://github.com/SakuraSM/KomeliaIntl/issues) 报告问题或提出需求。请勿提交凭据、私有服务器地址或未打码的书库内容。

## 许可证与上游

Komelia Intl 使用 [Apache License 2.0](LICENSE)。本项目基于 [Snd-R/Komelia](https://github.com/Snd-R/Komelia)，上游与本仓库的改动分别保留各自的版权声明。
