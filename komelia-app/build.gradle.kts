import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

group = "io.github.snd-r.komelia"
version = libs.versions.app.version.get()

kotlin {
    jvmToolchain(17) // max version https://developer.android.com/build/releases/gradle-plugin#compatibility
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "komelia-app"
        browser {
            commonWebpackConfig {
                outputFileName = "komelia-app.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer())
            }
        }
        browser()
        binaries.executable()
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.ExperimentalUnsignedTypes")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        commonMain.dependencies {
            implementation(projects.komeliaUi)
            implementation(projects.komeliaDomain.core)
            implementation(projects.komeliaDomain.offline)
            implementation(projects.komeliaInfra.database.shared)
            implementation(projects.komeliaInfra.database.transaction)
            implementation(projects.komeliaInfra.webview)
            implementation(libs.kotlin.logging)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.window)
            implementation(libs.androidx.workManager)
            implementation(libs.androidx.workManager.ktx)
            implementation(projects.komeliaInfra.database.sqlite)
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs)
            implementation(projects.komeliaInfra.onnxruntime.jvm)
        }
        jvmMain.dependencies {
            implementation(libs.jbr.api)
            implementation(projects.komeliaInfra.database.sqlite)
            implementation(projects.komeliaInfra.imageDecoder.vips)
            implementation(projects.komeliaInfra.onnxruntime.jvm)
            implementation(libs.filekit.core)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
            implementation(projects.komeliaInfra.imageDecoder.wasmImageWorker)
            implementation(projects.komeliaInfra.database.wasm)
        }
    }
}

enum class AndroidVariant {
    STANDALONE,
    FDROID,
    PLAY
}

val androidVariant = runCatching {
    AndroidVariant.valueOf(
        (project.extraProperties["snd.android.variant"] as String).uppercase()
    )
}.getOrDefault(AndroidVariant.STANDALONE)

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}

fun releaseSigningProperty(name: String): String? {
    return (project.findProperty(name) as? String)
        ?: localProperties.getProperty(name)
        ?: System.getenv(name)
}

val releaseSigningProperties = mapOf(
    "storeFile" to releaseSigningProperty("KOMELIA_RELEASE_STORE_FILE"),
    "storePassword" to releaseSigningProperty("KOMELIA_RELEASE_STORE_PASSWORD"),
    "keyAlias" to releaseSigningProperty("KOMELIA_RELEASE_KEY_ALIAS"),
    "keyPassword" to releaseSigningProperty("KOMELIA_RELEASE_KEY_PASSWORD"),
)
val hasReleaseSigningConfig = releaseSigningProperties.values.all { !it.isNullOrBlank() }

android {
    namespace = "io.github.snd_r.komelia"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    val manifestFile = when (androidVariant) {
        AndroidVariant.STANDALONE -> "AndroidManifest.xml"
        AndroidVariant.FDROID -> "AndroidManifestFdroid.xml"
        AndroidVariant.PLAY -> "AndroidManifestPlay.xml"
    }
    sourceSets["main"].manifest.srcFile("src/androidMain/$manifestFile")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "io.github.zhengningning.komelia"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 22
        versionName = libs.versions.app.version.get()

        val enableSelfUpdates = when (androidVariant) {
            AndroidVariant.STANDALONE -> (project.findProperty("snd.enable.self.updates") == "true").toString()
            AndroidVariant.FDROID -> "false"
            AndroidVariant.PLAY -> "false"
        }
        buildConfigField("boolean", "ENABLE_SELF_UPDATES", enableSelfUpdates)
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,README.txt}"
            pickFirsts += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
    dependenciesInfo {
        if (androidVariant != AndroidVariant.PLAY) {
            includeInApk = false
            includeInBundle = false
        }
    }
    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigningProperties["storeFile"]))
                storePassword = requireNotNull(releaseSigningProperties["storePassword"])
                keyAlias = requireNotNull(releaseSigningProperties["keyAlias"])
                keyPassword = requireNotNull(releaseSigningProperties["keyPassword"])
            }
        }
    }
    buildTypes {
        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            } else if (project.findProperty("requireReleaseSigning") == "true") {
                error(
                    "Release signing is required. Configure KOMELIA_RELEASE_STORE_FILE, " +
                        "KOMELIA_RELEASE_STORE_PASSWORD, KOMELIA_RELEASE_KEY_ALIAS, and " +
                        "KOMELIA_RELEASE_KEY_PASSWORD in local.properties, Gradle properties, or environment variables."
                )
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "android.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


compose.desktop {
    application {
        mainClass = "snd.komelia.MainKt"

        jvmArgs += listOf(
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+UseShenandoahGC",
            "-XX:ShenandoahGCHeuristics=compact",
            "-XX:ConcGCThreads=1",
            "-XX:TrimNativeHeapInterval=60000",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Komelia"
            packageVersion = libs.versions.app.version.get()
            description = "Komga media client"
            vendor = "Snd-R"
            appResourcesRootDir.set(
                project.projectDir.resolve("desktopUnpackedResources")
            )
            modules("jdk.security.auth", "java.sql")

            windows {
                menu = true
                upgradeUuid = "40E86376-4E7C-41BF-8E3B-754065032B22"
                iconFile.set(project.file("src/jvmMain/resources/ic_launcher.ico"))
            }

            linux {
                iconFile.set(project.file("src/jvmMain/resources/ic_launcher.png"))
            }
        }

        buildTypes.release.proguard {
            version.set("7.8.0")
            optimize.set(false)
            configurationFiles.from(project.file("desktop.pro"))
        }
    }
}
