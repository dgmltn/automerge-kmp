import com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
}

// The host's Skia runtime, for the UI tests composed headless on the JVM.
val composeDesktopForHost = run {
    val os = System.getProperty("os.name")
    val arm = System.getProperty("os.arch") in setOf("aarch64", "arm64")
    when {
        os.startsWith("Mac") ->
            if (arm) libs.compose.desktop.macos.arm64 else libs.compose.desktop.macos.x64
        os.startsWith("Windows") -> libs.compose.desktop.windows.x64
        else -> if (arm) libs.compose.desktop.linux.arm64 else libs.compose.desktop.linux.x64
    }
}

kotlin {
    jvmToolchain(providers.gradleProperty("sample.jdk").get().toInt())
    android {
        namespace = "com.dgmltn.automerge.sample.ui"
        compileSdk = providers.gradleProperty("sample.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("sample.minSdk").get().toInt()
    }
    jvm()
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        // What the SwiftUI app in app-ios links: the shared screen as a view controller.
        target.binaries.framework {
            baseName = "TwoPadsUI"
            isStatic = true
            binaryOption("bundleId", "com.dgmltn.automerge.sample.ios.ui")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.lifecycle.viewmodel)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.lifecycle.viewmodel.compose)
        }
        androidMain.dependencies {
            // ComposeViewAdapter for Android Studio's @Preview renderer; Android-only.
            implementation(libs.compose.ui.tooling)
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
        jvmTest.dependencies {
            implementation(libs.compose.ui.test)
            implementation(composeDesktopForHost)
        }
    }
}

// The UI tests render offscreen; nothing in them may reach for a display.
tasks.named<Test>("jvmTest") { systemProperty("java.awt.headless", "true") }

ktfmt { kotlinLangStyle() }

// The plugin finds a module's scripts by walking its whole directory, build/ included, and fails
// when a task running beside it changes what is there.
tasks
    .withType<KtfmtBaseTask>()
    .matching { it.name.endsWith("Scripts") }
    .configureEach { setSource(files("build.gradle.kts")) }

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom(files("src"))
}

dependencies { detektPlugins(libs.compose.rules.detekt) }
