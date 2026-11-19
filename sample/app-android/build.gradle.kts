import com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask

plugins {
    // AGP 9 compiles Kotlin itself, so the Kotlin Android plugin is not applied.
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.dgmltn.automerge.sample.android"
    compileSdk = providers.gradleProperty("sample.compileSdk").get().toInt()

    defaultConfig {
        applicationId = "com.dgmltn.automerge.sample"
        minSdk = providers.gradleProperty("sample.minSdk").get().toInt()
        targetSdk = providers.gradleProperty("sample.targetSdk").get().toInt()
        versionCode = 1
        versionName = "1.0"
        // The binding's Rust library is built only for these; JNA's AAR brings more.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildTypes {
        release {
            // Only the binding's own consumer rules keep its FFI alive here; the app adds none.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // So the minified build installs without a release keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

kotlin { jvmToolchain(providers.gradleProperty("sample.jdk").get().toInt()) }

dependencies {
    implementation(project(":shared-ui"))
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    detektPlugins(libs.compose.rules.detekt)
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
