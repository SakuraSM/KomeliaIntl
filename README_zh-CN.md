# Komelia — Komga 媒体客户端

[English](README.md) | [简体中文](README_zh-CN.md)

Komelia 是 [Komga](https://komga.org/) 媒体服务器的跨平台客户端，支持桌面（Linux / Windows / macOS）、Android 与浏览器（WebAssembly），主要用于阅读漫画与电子书。

### 下载

- 最新预构建版本：<https://github.com/Snd-R/Komelia/releases>
- Google Play：<https://play.google.com/store/apps/details?id=io.github.snd_r.komelia>
- F-Droid：<https://f-droid.org/packages/io.github.snd_r.komelia/>
- AUR：<https://aur.archlinux.org/packages/komelia>

## 截图

<details>
  <summary>移动端</summary>
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="Komelia" width="270">
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" alt="Komelia" width="270">
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" alt="Komelia" width="270">
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" alt="Komelia" width="270">
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" alt="Komelia" width="270">
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" alt="Komelia" width="270">
</details>

<details>
  <summary>平板</summary>
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/1.jpg" alt="Komelia" width="400" height="640">
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/2.jpg" alt="Komelia" width="400" height="640">
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/3.jpg" alt="Komelia" width="400" height="640">
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/4.jpg" alt="Komelia" width="400" height="640">
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/5.jpg" alt="Komelia" width="400" height="640">
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/6.jpg" alt="Komelia" width="400" height="640">
</details>

<details>
  <summary>桌面</summary>
   <img src="/screenshots/1.jpg" alt="Komelia" width="1280">
   <img src="/screenshots/2.jpg" alt="Komelia" width="1280">
   <img src="/screenshots/3.jpg" alt="Komelia" width="1280">
   <img src="/screenshots/4.jpg" alt="Komelia" width="1280">
   <img src="/screenshots/5.jpg" alt="Komelia" width="1280">
</details>

## 语言切换

应用启动后默认跟随系统语言；也可在 **设置 → 外观 → 语言 / Language** 中手动选择。当前内置语言：

- English（默认）
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

- `./gradlew :komelia-app:run`：启动桌面应用
- `./gradlew :komelia-app:packageReleaseUberJarForCurrentOS`：打包为 jar（输出于 `komelia-app/build/compose/jars`）
- `./gradlew :komelia-app:packageReleaseDeb`：打包为 Linux deb（输出于 `komelia-app/build/compose/binaries`）
- `./gradlew :komelia-app:packageReleaseMsi`：打包为 Windows msi 安装包（输出于 `komelia-app/build/compose/binaries`）

## Android 端构建

使用 Docker 容器构建（将 `<arch>` 占位符替换为目标架构，可选 `aarch64`、`armv7a`、`x86_64`、`x86`）：

- `docker build -t komelia-build-android . -f ./cmake/android.Dockerfile`
- `docker run -v .:/build komelia-build-android <arch>`
- `./gradlew <arch>_copyJniLibs`：将构建好的共享库复制到将随应用一起打包的资源目录
- `./gradlew buildWebui`：构建并复制 EPUB 阅读器 webui（构建需要 npm）

随后选择构建选项：

- `./gradlew :komelia-app:assembleDebug`：debug APK 构建（输出于 `komelia-app/build/outputs/apk/debug`）
- `./gradlew :komelia-app:assembleRelease`：未签名 release APK 构建（输出于 `komelia-app/build/outputs/apk/release`）

## Komf 扩展构建

```
./gradlew :komelia-komf-extension:app:packageExtension
```

输出归档位于 `./komelia-komf-extension/app/build/distributions`。
