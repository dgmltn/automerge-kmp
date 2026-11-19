import com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
}

kotlin {
    jvmToolchain(providers.gradleProperty("sample.jdk").get().toInt())
    android {
        namespace = "com.dgmltn.automerge.sample"
        compileSdk = providers.gradleProperty("sample.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("sample.minSdk").get().toInt()
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    macosArm64 {
        // What the SwiftUI Mac app in app-macos links: the pads, without any UI.
        binaries.framework {
            baseName = "TwoPadsKit"
            isStatic = true
            binaryOption("bundleId", "com.dgmltn.automerge.sample.mac.kit")
        }
    }

    sourceSets {
        commonMain.dependencies { api(libs.automerge.kmp) }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // Its Rust static library goes into the same Apple binaries as this one's.
            implementation(libs.iroh.kmp)
        }
    }
}

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
