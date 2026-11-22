# automerge-kmp

Kotlin Multiplatform bindings for [Automerge](https://automerge.org), the CRDT library, generated from its Rust
core: one API on the JVM, Android, iOS and macOS.

**Status: released.** Version `0.1.0` is on Maven Central as `com.dgmltn.automerge:automerge-kmp:0.1.0`. Its own
tests run on the JVM, macOS and the iOS simulator. Android has no test target here: it is tested through the sample's
minified release, on an emulator and a Pixel 10 Pro. The sample in [`sample/`](sample) has been run by hand on all
four of its platforms: desktop, Android, a native Mac app, and iOS on an iPhone. space-kmp's `:automerge` module is
built on it.

## Why

[space-kmp](../space-kmp) syncs spaces between devices, and its `:automerge` module keeps an Automerge document in
a space. That module ran on automerge-java, which has no iOS or macOS build, so neither wrtr on iOS nor the
Space Mac app (Kotlin/Native) could use it. automerge-kmp replaced it and runs on every target space-kmp has.

| Target | For | Native library |
|---|---|---|
| `jvm` | tests, desktop | in the jar: a release's holds macOS (arm64, x86-64), Linux (x86-64, arm64) and Windows (x86-64); a local build's holds the build host's only |
| `android` | phones and the emulator | arm64-v8a, x86_64 |
| `iosArm64`, `iosSimulatorArm64` | wrtr on iOS | static, in the klib |
| `macosArm64` | the Space Mac app, the mirror | static, in the klib |

## How it works

- **Rust:** automerge-swift's UniFFI crate (`uniffi_automerge`, MIT) is vendored at a pinned commit in
  `src/commonMain/rust/` and wraps the `automerge` crate (0.12.0). It keeps its own `Cargo.toml` there; Gradle points
  Gobley at it with `cargo { packageDirectory = … }`, because uniffi looks for the UDL beside the manifest. The few
  changes to it are listed in [`VENDORED.md`](VENDORED.md).
- **Kotlin:** [Gobley](https://gobley.dev) 0.3.7 generates JNA-backed KMP bindings from that crate, the way
  [iroh-kmp](../iroh-kmp) wraps iroh. The API is the crate's (`Doc`, `ObjId`, `ChangeHash`, `Cursor`, …) in the
  package `com.dgmltn.automerge`.
  - Bytes cross the FFI as `ByteArray`: `ObjId`, `ChangeHash`, `ActorId` and `Cursor` are typealiases of it, and
    `save()`, the change encodings and sync messages take and return it.
  - Text indices are UTF-16 code units, Kotlin `String` indices, in every document: the crate is built with
    automerge's `utf16-indexing` feature, which makes UTF-16 the default for `Doc()`, `Doc.newWithActor` and
    `Doc.load`.
  - Two additions to the upstream crate: `Doc.loadWithTextEncoding(bytes, encoding)`, to load with an encoding other
    than the default; and `Doc.rollback()`, which drops what was written since the last commit.
  - `ObjId`, `ChangeHash` and `Cursor` bytes are parsed as they cross into Rust, so malformed ones fail that one call
    and leave the document as it was.
  - Three names in Gobley's generated Kotlin are patched after generation (in `build.gradle.kts`): `ScalarValue.Int`
    and `ScalarValue.Boolean` shadow the return types of the generated `equals`/`hashCode`, and `Position.Cursor`
    shadows the `Cursor` type of its own field. Qualifying them keeps the UDL as upstream wrote it. The build fails if
    a pattern stops matching.
- **Android:** the AAR carries `libuniffi_automerge.so` for arm64-v8a and x86_64, and consumer R8 rules for JNA and
  the binding. It does not carry `libjnidispatch.so`: JNA's own AAR, a runtime dependency of this one, brings it.
- **Publishing:** to Maven Central, from the Release workflow when a `v*` tag is pushed. Gobley 0.3.7 can't build on AGP 9, so this repo stays on iroh-kmp's toolchain
  (Kotlin 2.1.10, AGP 8.7.3, JDK 17, Rust 1.98.0) and consumers on Kotlin 2.4 / AGP 9 take the published artifact.
  They need no Rust or NDK.

## Prerequisites

- **Rust** through rustup, which reads `rust-toolchain.toml` on its own. That pins Rust 1.98.0 and the five targets
  the build compiles for (`aarch64-apple-darwin`, `aarch64-linux-android`, `x86_64-linux-android`,
  `aarch64-apple-ios`, `aarch64-apple-ios-sim`), and rustup installs them with the toolchain. Where 1.98.0 was
  already installed without them, `rustup target add` the missing ones. [mise](https://mise.jdx.dev) is optional:
  `mise.toml` pins the same Rust and a JDK 17.
- **A JDK 17** anywhere Gradle can find it. `gradle/gradle-daemon-jvm.properties`
  asks for 17 for the Gradle daemon, whatever `java` on the path is, because Gradle 8.13 and detekt 1.23.8 can't run
  on newer JDKs. Without one, Gradle stops before configuring with "Unable to download toolchain matching the
  requirements ({languageVersion=17, …})". The sample's build asks for 17 the same way.
- **The Android SDK** through `ANDROID_HOME`, with NDK `29.0.13846066` and build-tools `35.0.0`.
- **Xcode**, for the Apple targets, and [xcodegen](https://github.com/yonaskolb/XcodeGen) for the Apple samples.

## Build, test, publish

```sh
./gradlew jvmTest macosArm64Test iosSimulatorArm64Test   # the tests, on three targets
./gradlew ktfmtCheck detekt                              # format and lint
./gradlew assembleRelease                                # the AAR, in build/outputs/aar/
./gradlew publishToMavenLocal                            # → ~/.m2/repository/com/dgmltn/automerge/
```

`./gradlew clean` also cleans the Rust build, so the next build compiles the crate for every target again (about two
minutes on this Mac with the Gradle build cache off; with it on, Gradle restores most of it).

## Using it

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// build.gradle.kts of a KMP module
kotlin {
    sourceSets {
        commonMain.dependencies { implementation("com.dgmltn.automerge:automerge-kmp:0.1.0") }
    }
}
```

Every document indexes text in UTF-16 code units by default, so Automerge's text indices are Kotlin `String` indices
(an emoji counts as two). `Doc.newWithTextEncoding` and `Doc.loadWithTextEncoding` remain for another encoding.

```kotlin
val doc = Doc()
val text = doc.putObjectInMap(root(), "text", ObjType.TEXT)
doc.spliceText(text, 0u, 0, "a😀b")
doc.commitWith(null, 0)

val loaded = Doc.load(doc.save())
loaded.spliceText(text, 3u, 1, "c")
check(loaded.text(text) == "a😀c")
```

- **Closing:** a `Doc` holds a Rust object: `close()` it (it is `AutoCloseable`) when done.
- **Threads:** each call is atomic and safe from any thread, since the crate locks the document for the call. A
  sequence of calls is not, so serialise your own edits. `a.merge(a)` can deadlock, and so can `a.merge(b)` on one
  thread while `b.merge(a)` runs on another.
- **Ids are bytes:** `ObjId`, `ChangeHash`, `Cursor` and `ActorId` are `ByteArray`, so anything holding one compares by
  reference: `doc.heads() == doc.heads()` is false once there is a head, and so is `==` between two `Value.Object`s,
  `Position.Cursor`s, `PathElement`s, `Change`s or `PatchAction`s naming the same object. Compare the bytes with
  `contentEquals`, and wrap an id in a type of your own with explicit equality to use it as a map key or in a set.
- **Malformed bytes:** an `ObjId`, `ChangeHash` or `Cursor` that does not parse throws (`DocException.Internal` from a
  call that declares `DocException`, `InternalException` from one that declares nothing) and leaves the `Doc`
  usable. Any bytes are a valid `ActorId`.

**Android apps:**

- Set `abiFilters` to the two ABIs the Rust library is built for. JNA's AAR ships `libjnidispatch.so` for more ABIs,
  so without the filter the app installs on, say, an armeabi-v7a phone and crashes on first use.
  ```kotlin
  android { defaultConfig { ndk { abiFilters += listOf("arm64-v8a", "x86_64") } } }
  ```
- No keep rules: the AAR's consumer rules keep JNA and the binding's package `com.dgmltn.automerge` (not its
  subpackages, which stay yours to shrink).
- No `pickFirsts` for this library alone. An app that also has iroh-kmp still needs
  `packaging { jniLibs.pickFirsts += "**/libjnidispatch.so" }`, because iroh-kmp's AAR bundles its own copy.

## The sample

[`sample/`](sample) is its own Gradle build on space-kmp's toolchain (Kotlin 2.4.20, AGP 9.4.1, Gradle 9.7.1) that
takes the library only from Maven Local, so **run `./gradlew publishToMavenLocal` here first**. It is two text areas
that merge: typing in one shows in the other while they are linked; unlinked, each is edited apart, and linking again
merges both edits, with each caret staying on its character.

| Module | |
|---|---|
| `shared` | the two pads' logic (`Pad`, `TwoPads`), no UI; also the `TwoPadsKit` framework the Mac app links |
| `shared-ui` | the Compose screen and view model; also the `TwoPadsUI` framework the iOS app links |
| `app-desktop` | Compose desktop window (JVM) |
| `app-android` | Android app, minified with R8 |
| `app-macos` | native SwiftUI Mac app over `TwoPadsKit` (xcodegen, not a Gradle module) |
| `app-ios` | SwiftUI iOS app hosting the Compose screen (xcodegen, not a Gradle module) |

In `sample/`:

```sh
./gradlew :shared:allTests :shared-ui:jvmTest :shared-ui:iosSimulatorArm64Test ktfmtCheck detekt
./gradlew :app-desktop:run                     # the desktop app
./gradlew :app-android:assembleRelease         # → app-android/build/outputs/apk/release/app-android-release.apk
adb -s <serial> install -r app-android/build/outputs/apk/release/app-android-release.apk
```

The Apple apps build their Kotlin framework from a pre-build script, so Xcode needs nothing else. Generate the
project, then open `TwoPads.xcodeproj` in Xcode or test from the command line:

```sh
cd sample/app-macos && xcodegen generate
xcodebuild test -project TwoPads.xcodeproj -scheme TwoPads -destination 'platform=macOS,arch=arm64'

cd sample/app-ios && xcodegen generate
xcodebuild test -project TwoPads.xcodeproj -scheme TwoPads -destination 'platform=iOS Simulator,name=<iPhone>'
```

where `<iPhone>` is any available iPhone simulator (`xcrun simctl list devices available`).

For a device, put a `DEVELOPMENT_TEAM` in `sample/app-ios/Local.xcconfig` (git-ignored).

What the sample proves:

- The artifact, built on Kotlin 2.1.10 / AGP 8.7.3, resolves, compiles and runs on Kotlin 2.4.20 / AGP 9.4.1 on every
  target.
- It links into one Apple binary beside iroh-kmp's Rust static library (`LinkTest`, on macosArm64 and the iOS
  simulator; iosArm64 links).
- A minified Android release works with only the AAR's own R8 rules (checked on an emulator and a Pixel 10 Pro).
- A real native Mac app (SwiftUI, Kotlin/Native) and a real iOS app use it, with XCTest and XCUITest runs.

## Updating Automerge

The crate is vendored, not depended on: `VENDORED.md` names the automerge-swift commit it came from and every change
made since. To move to a newer automerge-swift, diff its `rust/` between that commit and the new one, apply the diff
in `src/commonMain/rust/`, keep or redo each change `VENDORED.md` lists, and update it.

Any change to the Rust side or the generated bindings reaches a consumer only after a version bump (`VERSION_NAME` in
`gradle.properties`, and the consumer's `automerge-kmp` version, as in `sample/gradle/libs.versions.toml`) and a
release, or `./gradlew publishToMavenLocal` for a local consumer such as the sample.

## Releasing

Set `VERSION_NAME` in `gradle.properties`, commit, then tag that commit with the same version and push the tag:

```sh
git tag v0.2.0 && git push origin v0.2.0
```

The Release workflow refuses a tag that doesn't match `VERSION_NAME`. It tests, builds the jvm jar's Linux, Windows
and Intel macOS libraries on their own runners, publishes to Maven Central and creates the GitHub release. It needs
the repository secrets `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` (a Central Portal user token, not the
login) and `SIGNING_KEY` and `SIGNING_KEY_PASSWORD` (the GPG key `7F4F1163F2E3249A`, whose public half is on
keys.openpgp.org). A version on Maven Central can never be changed or removed, so a fix is always a new version.

## Not here

- **The layer over these bindings** that keeps a document in a space (`AutomergeDoc`, `SpaceDoc` and the rest) is
  space-kmp's `:automerge` module, not part of this library.
- **The Space Mac app's size** with this library linked beside iroh-kmp has not been measured.
- **Which `libjnidispatch.so` an Android app keeps** when it holds both iroh-kmp and this library is not settled
  here. iroh-kmp's AAR bundles its own, a different build from the one in JNA 5.18.1's AAR that this library
  depends on. space-kmp's Android samples hold both and set `jniLibs.pickFirsts += "**/libjnidispatch.so"`.

## Known limits

- A JVM artifact built locally carries only the build host's native library (macOS arm64), so it has nothing to
  load on any other desktop OS or CPU. A release's carries five: the release workflow builds the Linux and Windows
  libraries on their own runners and passes them in with `-PjvmNativesDir=<folder>`, which holds each library in
  its JNA resource folder (`linux-x86-64/libuniffi_automerge.so`). Those libraries are built, not tested: the tests
  run on macOS arm64 only. The Linux ones need glibc 2.35 or newer. Running the Release workflow by hand from the
  Actions tab does everything but publish, and keeps the jvm jar as a download.
- Android: arm64-v8a and x86_64 only.
- Automerge's sync protocol (`SyncState`, sync messages) is exposed but unused, and untested under R8.
- The sample's detekt (1.23.8) needs its Gradle daemon on a JDK 17; it fails on newer JDKs.
- Version `0.1.0` was republished in place to Maven Local during development, so a machine that built it then may
  hold a copy in `~/.m2/repository/com/dgmltn/automerge/` that differs from Maven Central's. A build that lists
  `mavenLocal()` first resolves that copy; delete the folder to get Central's.
- The iOS sample is UI-tested on iPhone only, not iPad, and its simulator build prints a linker warning that
  Compose's bundled libicu was built for a newer iOS than the app's 17.0.

## Record

The design ([`resources/docs/superpowers/specs/2026-11-18-automerge-kmp-design.md`](resources/docs/superpowers/specs/2026-11-18-automerge-kmp-design.md),
with its spike results) and the plan it was built from
([`resources/docs/superpowers/plans/2026-11-18-automerge-kmp.md`](resources/docs/superpowers/plans/2026-11-18-automerge-kmp.md)).

## License

The vendored crate is automerge-swift's, under the MIT license that ships with it (`src/commonMain/rust/LICENSE`).
The rest is Apache-2.0 ([`LICENSE`](LICENSE)), as space-kmp is.
