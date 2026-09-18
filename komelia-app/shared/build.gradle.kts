import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

group = "io.github.snd-r.komelia.app.shared"
version = "unspecified"

kotlin {
    android {
        namespace = "io.github.snd_r.komelia.app.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        // Tests consume the same Compose/Skiko runtime as the shared UI dependency.
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.komeliaUi)
            implementation(projects.komeliaDomain.core)
            implementation(projects.komeliaDomain.offline)
            implementation(projects.komeliaInfra.database.shared)
            implementation(projects.komeliaInfra.database.transaction)
            implementation(projects.komeliaInfra.webview)
            implementation(libs.kotlin.logging)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
