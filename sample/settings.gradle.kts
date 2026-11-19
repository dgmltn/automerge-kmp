rootProject.name = "automerge-kmp-sample"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // Only the binding comes from Maven Local, where the root build publishes it.
        mavenLocal { content { includeGroup("com.dgmltn.automerge") } }
        google()
        mavenCentral()
    }
}

include(":shared")
include(":shared-ui")
include(":app-desktop")
include(":app-android")
