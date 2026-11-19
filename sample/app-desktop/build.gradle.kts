import com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
}

// The host's Skia runtime: what the deprecated compose.desktop.currentOs accessor picked.
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
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared-ui"))
            implementation(composeDesktopForHost)
            implementation(libs.compose.material3)
            implementation(libs.lifecycle.viewmodel.compose)
        }
    }
}

compose.desktop { application { mainClass = "com.dgmltn.automerge.sample.desktop.MainKt" } }

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
