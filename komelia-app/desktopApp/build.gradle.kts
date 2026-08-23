import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

group = "io.github.snd-r.komelia"
version = libs.versions.app.version.get()

dependencies {

    implementation(projects.komeliaApp.shared)
    implementation(projects.komeliaUi)
    implementation(projects.komeliaDomain.core)
    implementation(projects.komeliaDomain.offline)
    implementation(projects.komeliaInfra.database.shared)
    implementation(projects.komeliaInfra.database.transaction)
    implementation(projects.komeliaInfra.webview)
    implementation(projects.komeliaInfra.database.sqlite)
    implementation(projects.komeliaInfra.imageDecoder.vips)
    implementation(projects.komeliaInfra.onnxruntime.jvm)
    implementation(libs.kotlin.logging)

    implementation(libs.jbr.api)
    implementation(libs.filekit.core)
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
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
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
                iconFile.set(project.file("src/main/resources/ic_launcher.ico"))
            }

            linux {
                iconFile.set(project.file("src/main/resources/ic_launcher.png"))
            }

            macOS {
                // jpackage requires CFBundleVersion to start with a positive integer.
                packageVersion = libs.versions.app.version.get().removePrefix("0.")
            }
        }

        buildTypes.release.proguard {
            // Compose 1.12.0-rc01's external runner is incompatible with Gradle 9.7
            // (getStandardOutput() is null). Keep the release jar task usable until
            // the upstream plugin fixes the runner; desktop.pro remains ready to re-enable.
            isEnabled.set(false)
            version.set("7.9.1")
            optimize.set(false)
            configurationFiles.from(project.file("desktop.pro"))
        }
    }
}

tasks.matching { it.name.startsWith("packageRelease") }.configureEach {
    dependsOn(rootProject.tasks.named("releaseVersionCheck"))
}

tasks.withType<Zip>().named {
    it.matches(Regex("package(Release)?UberJarForCurrentOS"))
}.configureEach {
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}
