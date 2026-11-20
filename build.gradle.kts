import com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import gobley.gradle.GobleyHost
import gobley.gradle.Variant
import gobley.gradle.cargo.dsl.jvm
import gobley.gradle.rust.CrateType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    // Gobley refuses to configure without it.
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.gobley.cargo)
    alias(libs.plugins.gobley.uniffi)
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
}

group = providers.gradleProperty("GROUP").get()

version = providers.gradleProperty("VERSION_NAME").get()

// Gobley ships the JVM library only in a classifier jar that its module metadata never puts on a
// consumer's runtime classpath, so the library rides in the main jvm jar at JNA's resource path.
// A Sync, so the jar carries exactly the library just built and nothing left from an earlier one.
val jvmNativeResDir = layout.buildDirectory.dir("jvmNativeResources")
val embedJvmNativeLib = tasks.register<Sync>("embedJvmNativeLib")

// A folder of libraries for the other desktop platforms, each in its JNA resource folder
// (`linux-x86-64/libuniffi_automerge.so`). The release workflow builds each on its own OS.
val jvmNativesDir = providers.gradleProperty("jvmNativesDir")

cargo {
    // The vendored crate keeps its own layout, where uniffi expects to find the UDL beside lib.rs.
    packageDirectory = layout.projectDirectory.dir("src/commonMain/rust")

    // The jvm jar carries the libraries itself (see embedJvmNativeLib), so Gobley's per-host
    // classifier jar would be a duplicate that no consumer's runtime classpath ever picks up.
    publishJvmArtifacts = false

    // A debug static library makes each Apple klib several times larger, and it is what ships.
    nativeVariant = Variant.Release

    // One profile for the JVM, so the library the tests load is the one the jar ships, and Gobley's
    // own test-classpath copy can't be a different build at the same resource path.
    jvmVariant = Variant.Release

    builds.jvm {
        val isHost = rustTarget == GobleyHost.current.rustTarget
        // Gobley would otherwise cross-build the desktop library for every host OS, and only the
        // current host's can be linked here.
        embedRustLibrary = isHost
        if (isHost) {
            val cargoBuild = variant(this@cargo.jvmVariant.get()).buildTaskProvider
            val jnaDir = rustTarget.jnaResourcePrefix
            embedJvmNativeLib.configure {
                dependsOn(cargoBuild)
                from(
                    cargoBuild.flatMap { task ->
                        task.libraryFileByCrateType.map {
                            it.getValue(CrateType.SystemDynamicLibrary)
                        }
                    }
                ) {
                    into(jnaDir)
                }
                jvmNativesDir.orNull?.let { dir -> from(dir) { exclude("$jnaDir/**") } }
                into(jvmNativeResDir)

                // Without this a jar with no native library builds fine and fails only at
                // runtime, with an UnsatisfiedLinkError.
                val embedded = jvmNativeResDir.map { it.dir(jnaDir).asFileTree }
                doLast {
                    check(!embedded.get().isEmpty) {
                        "embedJvmNativeLib: cargo produced no library for $rustTarget"
                    }
                }
            }
        }
    }
}

// Gobley still wires the Linux/Windows JVM cargo builds into the task graph, and on any other host
// they fail for want of a cross-linker.
tasks
    .matching { it.name.startsWith("cargoBuildLinux") }
    .configureEach { enabled = GobleyHost.Platform.Linux.isCurrent }

tasks
    .matching { it.name.startsWith("cargoBuildMinGW") }
    .configureEach { enabled = GobleyHost.Platform.Windows.isCurrent }

uniffi {
    generateFromLibrary {
        packageName = "com.dgmltn.automerge"
        // The UDL's namespace, which names the C header uniffi writes. Gobley otherwise assumes the
        // crate's name, and the Apple cinterop looks for a header that isn't there.
        namespace = "automerge"
        // The library every JVM build already needs, rather than a debug Android one built only
        // to read the bindings from.
        build = GobleyHost.current.rustTarget
        variant = Variant.Release
    }
}

/**
 * Gobley's Kotlin templates name types unqualified, and the UDL has enum variants that share those
 * names: `ScalarValue.Int` and `ScalarValue.Boolean` shadow the return types of the generated
 * `equals`/`hashCode`, and `Position.Cursor` shadows the `Cursor` typealias of its own field.
 * Qualifying them keeps the UDL as upstream wrote it.
 */
val shadowedTypeNames =
    mapOf(
        "override fun equals(other: Any?): Boolean" to
            "override fun equals(other: Any?): kotlin.Boolean",
        "override fun hashCode(): Int" to "override fun hashCode(): kotlin.Int",
        "val `position`: Cursor," to "val `position`: com.dgmltn.automerge.Cursor,",
    )

tasks
    .matching { it.name == "buildUniffiBindings" }
    .configureEach {
        doLast {
            val generated =
                fileTree(layout.buildDirectory.dir("generated/uniffi")) { include("**/*.kt") }
            val unmatched = shadowedTypeNames.keys.toMutableSet()
            generated.forEach { file ->
                val original = file.readText()
                var patched = original
                shadowedTypeNames.forEach { (from, to) ->
                    if (from in patched) {
                        unmatched -= from
                        patched = patched.replace(from, to)
                    }
                }
                if (patched != original) file.writeText(patched)
            }
            // A pattern that no longer matches means Gobley's output changed and this needs a look.
            check(unmatched.isEmpty()) { "Generated bindings no longer contain: $unmatched" }
        }
    }

kotlin {
    jvmToolchain(17)
    jvm()
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    // Apple targets link only with Xcode, so the build still configures on other hosts.
    if (GobleyHost.Platform.MacOS.isCurrent) {
        iosArm64()
        iosSimulatorArm64()
        macosArm64()
    }

    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
        jvmMain { resources.srcDir(embedJvmNativeLib) }
    }
}

android {
    namespace = "com.dgmltn.automerge"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    // AGP 8.7 defaults to build tools 34; this pins the version that matches compileSdk.
    buildToolsVersion = "35.0.0"
    ndkVersion = "29.0.13846066"

    defaultConfig {
        minSdk = 26
        ndk.abiFilters += listOf("arm64-v8a", "x86_64")
        // R8 renames what JNA looks up by name, and a consumer can't be expected to know that.
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

mavenPublishing {
    // Signing only when a key is configured, so publishing to Maven Local needs none.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()

    publishToMavenCentral()

    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Empty(),
            sourcesJar = true,
            androidVariantsToPublish = listOf("release"),
        )
    )

    pom {
        name = "automerge-kmp"
        description = "Kotlin Multiplatform bindings for Automerge, generated from its Rust core."
        inceptionYear = "2026"
        url = "https://github.com/dgmltn/automerge-kmp"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "dgmltn"
                name = "Doug Melton"
                url = "https://github.com/dgmltn"
            }
        }
        scm {
            url = "https://github.com/dgmltn/automerge-kmp"
            connection = "scm:git:https://github.com/dgmltn/automerge-kmp.git"
            developerConnection = "scm:git:ssh://git@github.com/dgmltn/automerge-kmp.git"
        }
    }
}

ktfmt { kotlinLangStyle() }

// The plugin finds the scripts by walking the whole project directory, build/ and sample/ included,
// and fails when a task running beside it changes what is there.
tasks
    .withType<KtfmtBaseTask>()
    .matching { it.name.endsWith("Scripts") }
    .configureEach { setSource(files("build.gradle.kts", "settings.gradle.kts")) }

detekt {
    buildUponDefaultConfig = true
    source.setFrom(files("src"))
}
