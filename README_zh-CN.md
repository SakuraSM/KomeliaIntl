# Komelia 中文增强版

[English](README.md) | [简体中文](README_zh-CN.md)

本仓库基于 [Snd-R/Komelia](https://github.com/Snd-R/Komelia) 修改。项目保留原版 Komga 客户端能力，并补充中文界面、自适应导航、移动端 UI、阅读器交互和多平台发布。

Komelia 是 [Komga](https://komga.org/) 媒体服务器的跨平台客户端，支持桌面（Linux / Windows / macOS）、Android 与浏览器（WebAssembly），主要用于阅读漫画与电子书。

## 本仓库的改动

- 使用底部导航和 Navigation Rail 替代全局抽屉。手机、平板和桌面按窗口宽度切换导航方式，并保留各页面的浏览状态。
- 统一 Light、Dark、OLED 三种主题的颜色、圆角、间距、触控目标和动效。页面支持 reduced motion，并改善键盘焦点与返回逻辑。
- 重做首页分组、书库范围选择、筛选、搜索、设置、系列详情和书籍详情。首页标签在可用宽度内显示，其余分组进入“更多”。
- 调整封面卡片的固定标题槽位。单行和双行标题保持同排等高，状态信息改为轻量角标。
- 优化图片和 EPUB 阅读器控制栏。阅读控件自动隐藏，拖动进度后不会误触翻页或工具栏。
- 补充主应用、Android 系统页面和 EPUB 阅读器的简体中文。应用内可选择跟随系统、English 或简体中文。
- 保留离线 PDF、RAR、EPUB、页面重试、局域网地址切换和数据库迁移兼容。
- 发布 Android 通用 APK、Windows MSI、Linux DEB、macOS ARM64 DMG/JAR 和 Wasm WebUI。

## 下载

- 本仓库发布页：<https://github.com/SakuraSM/KomeliaIntl/releases>
- 最新版本：<https://github.com/SakuraSM/KomeliaIntl/releases/latest>
- 官方上游发布页：<https://github.com/Snd-R/Komelia/releases>
- 官方 Google Play：<https://play.google.com/store/apps/details?id=io.github.snd_r.komelia>
- 官方 F-Droid：<https://f-droid.org/packages/io.github.snd_r.komelia/>
- 官方 AUR：<https://aur.archlinux.org/packages/komelia>

> 本 fork 与官方应用的包名或签名证书可能不同。如果 Android 安装时提示签名不一致，请先卸载旧包，或安装同一发布渠道的包。

## App 截图

<img src="/screenshots/app-overview-v0.18.13.png" alt="Komelia Android 首页、书库筛选、书籍详情和设置页面" width="100%">

> 截图来自 Android App。书籍详情中的资源文件名和来源域名已打码，截图不包含账号、服务器地址或测试凭据。

## 语言切换

应用默认跟随系统语言，也可在 **设置 → 外观 → 语言 / Language** 中手动选择。当前内置选项：

- 跟随系统
- English
- 简体中文 (Simplified Chinese)

如需贡献其它语言或改进现有翻译，请参阅 [`docs/i18n/CONTRIBUTING_zh-CN.md`](docs/i18n/CONTRIBUTING_zh-CN.md) 与术语表 [`docs/i18n/glossary_zh-CN.md`](docs/i18n/glossary_zh-CN.md)。

## 原生库构建说明

Android 与 JVM 目标需要 C/C++ 编译器以构建原生库；EPUB 阅读器还需要 Node.js。

推荐使用包含全部构建依赖的 Docker 镜像构建原生库。
若希望直接使用系统工具链与依赖，可尝试运行（仅限 Linux）：

```
./gradlew komeliaBuildNonJvmDependencies
```

## 桌面端构建

需要 JDK 17 或更高版本。

使用 Docker 容器构建（将 `<platform>` 占位符替换为目标平台，可选 `linux-x86_64`、`windows-x86_64`）：

- `docker build -t komelia-build-<platform> . -f ./cmake/<platform>.Dockerfile`
- `docker run -v .:/build komelia-build-<platform>`
- `./gradlew <platform>_copyJniLibs`：将构建好的共享库复制到将随应用一起打包的资源目录
- `./gradlew buildWebui`：构建并复制 EPUB 阅读器 webui（构建需要 npm）

随后选择打包方式：

- `./gradlew :desktopRun`：启动桌面应用
- `./gradlew :desktopJar`：打包为 jar（输出于 `komelia-app/desktopApp/build/compose/jars`）
- `./gradlew :desktopDeb`：打包为 Linux deb（输出于 `komelia-app/desktopApp/build/compose/binaries`）
- `./gradlew :desktopMsi`：打包为 Windows msi 安装包（输出于 `komelia-app/desktopApp/build/compose/binaries`）
- `./gradlew :desktopDmg`：打包为 macOS dmg 安装包（输出于 `komelia-app/desktopApp/build/compose/binaries`）

## Android 端构建

使用 Docker 容器构建（将 `<arch>` 占位符替换为目标架构，可选 `aarch64`、`armv7a`、`x86_64`、`x86`）：

- `docker build -t komelia-build-android . -f ./cmake/android.Dockerfile`
- `docker run -v .:/build komelia-build-android <arch>`
- `./gradlew <arch>_copyJniLibs`：将构建好的共享库复制到将随应用一起打包的资源目录
- `./gradlew buildWebui`：构建并复制 EPUB 阅读器 webui（构建需要 npm）

随后选择构建选项：

- `./gradlew androidDebug`：debug APK 构建（输出于 `komelia-app/androidApp/build/outputs/apk/debug`）
- `./gradlew androidRelease`：release APK 构建（输出于 `komelia-app/androidApp/build/outputs/apk/release`）
- `./scripts/build-release.sh`：本 fork 的 Android release 辅助脚本，本地签名配置完成后可用于构建并整理发布产物

## Komf 扩展构建

```
./gradlew :komelia-komf-extension:app:packageExtension
```

输出归档位于 `./komelia-komf-extension/app/build/distributions`。
