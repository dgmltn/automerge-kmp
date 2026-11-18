# automerge-kmp

Kotlin Multiplatform bindings for [Automerge](https://automerge.org), the CRDT library, generated from its Rust
core: one API on the JVM, Android, iOS and macOS.

> **Status: not built yet.** This repo holds the design and the build plan; the code comes next. Start at
> [Building it](#building-it).

## Why

[space-kmp](../space-kmp) syncs spaces between devices, and its `:automerge` module keeps an Automerge document in
a space. Today that module runs on automerge-java, which has no iOS or macOS build, so neither wrtr on iOS nor the
Space Mac app (Kotlin/Native) can use it. automerge-kmp is the replacement that runs on every target space-kmp has.
It is what wrtr's notes and the scratchpad will merge with, instead of the last writer winning.

## How it works

- **Rust:** automerge-swift's UniFFI crate (`uniffi_automerge`, MIT), vendored at a pinned commit, wraps the
  `automerge` crate (0.12). There is no Rust of our own beyond a few listed changes, in `VENDORED.md`.
- **Kotlin:** [Gobley](https://gobley.dev) generates JNI/JNA-backed KMP bindings from that crate, the way
  [iroh-kmp](../iroh-kmp) wraps iroh. The generated API is the crate's (`Doc`, `ObjId`, `ChangeHash`, …), in the
  package `com.dgmltn.automerge`, and every document indexes text in UTF-16, so Automerge indices are `String` indices.
- **Publishing:** to Maven Local as `com.dgmltn.automerge:automerge-kmp`. Gobley 0.3.7 can't build on AGP 9, so this
  repo stays on iroh-kmp's toolchain (Kotlin 2.1.10, AGP 8.7.3, JDK 17, Rust 1.98.0) while space-kmp, on Kotlin 2.4
  and AGP 9, takes the published artifact. Neither space-kmp nor wrtr needs Rust to build.
- **The friendly API** lives in space-kmp's `:automerge` (`AutomergeDoc`, `AmValue`, …), not here, so this repo only
  changes when the Rust side does.

| Target | |
|---|---|
| `jvm` | tests, desktop |
| `android` | arm64-v8a, x86_64 |
| `iosArm64`, `iosSimulatorArm64` | wrtr on iOS |
| `macosArm64` | the Space Mac app, the mirror |

## Building it

Hand an agent these two documents, in order:

1. The design: [`resources/docs/superpowers/specs/2026-11-18-automerge-kmp-design.md`](resources/docs/superpowers/specs/2026-11-18-automerge-kmp-design.md),
   what is being built and why, and what was decided.
2. The plan: [`resources/docs/superpowers/plans/2026-11-18-automerge-kmp.md`](resources/docs/superpowers/plans/2026-11-18-automerge-kmp.md),
   seven tasks across this repo and space-kmp, each with its tests. It starts with the binding on the JVM and
   a throwaway spike that proves the risky parts before anything builds on them.

A prompt that works: *"Build automerge-kmp: read `~/Developer/automerge-kmp/README.md`, then follow the plan it
points to with superpowers:subagent-driven-development."*

The plan's Global Constraints are the rules that matter most. Never modify iroh-kmp. Never run space-kmp's
`runMacApp` or `buildMacApp`, because they quit the Space app Doug is running. Use an emulator, not an attached
phone. Never push.

## Once built

Prerequisites: [mise](https://mise.jdx.dev) (it installs the pinned JDK and Rust), the Rust targets
(`rustup target add aarch64-linux-android x86_64-linux-android aarch64-apple-ios aarch64-apple-ios-sim aarch64-apple-darwin`),
Xcode, and an Android NDK r28+ through `ANDROID_HOME`.

```sh
./gradlew jvmTest macosArm64Test iosSimulatorArm64Test   # the tests, on three targets
./gradlew publishToMavenLocal                            # → ~/.m2/repository/com/dgmltn/automerge/automerge-kmp/<version>/
```

space-kmp takes it from Maven Local:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenLocal { content { includeGroup("com.dgmltn.automerge") } }
        // …
    }
}
```

A change here reaches space-kmp only after a version bump (`VERSION_NAME` in `gradle.properties`, and the
`automerge-kmp` entry in space-kmp's version catalog) and a republish.

## Updating Automerge

The crate is vendored, not depended on: `VENDORED.md` names the automerge-swift commit it came from and every change
made since. To move to a newer automerge-swift, diff its `rust/` between that commit and the new one, apply the diff
here, and update `VENDORED.md`.

## License

The vendored crate is automerge-swift's, under the MIT license that ships with it (`src/commonMain/rust/LICENSE`).
The rest is Apache-2.0, as space-kmp is.
