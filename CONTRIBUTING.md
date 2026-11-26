# Contributing to automerge-kmp

## How it's built

- **Rust:** automerge-swift's UniFFI crate (`uniffi_automerge`, MIT) is vendored at a pinned commit in
  `src/commonMain/rust/` and wraps the `automerge` crate (0.12.0). It keeps its own `Cargo.toml` there; Gradle points
  Gobley at it with `cargo { packageDirectory = … }`, because uniffi looks for the UDL beside the manifest. The
  changes to it are listed in [`VENDORED.md`](VENDORED.md). They include:
  - automerge's `utf16-indexing` feature, which makes UTF-16 the default text encoding.
  - Two additions to the upstream API: `Doc.loadWithTextEncoding(bytes, encoding)` and `Doc.rollback()`.
  - `ObjId`, `ChangeHash` and `Cursor` bytes are parsed as they cross into Rust, so malformed ones fail that one call
    and leave the document as it was.
- **Kotlin:** [Gobley](https://gobley.dev) 0.3.7 generates JNA-backed KMP bindings from that crate. Three names in
  the generated Kotlin are patched after generation (in `build.gradle.kts`): `ScalarValue.Int` and
  `ScalarValue.Boolean` shadow the return types of the generated `equals`/`hashCode`, and `Position.Cursor` shadows
  the `Cursor` type of its own field. Qualifying them keeps the UDL as upstream wrote it. The build fails if a
  pattern stops matching.
- **Android:** the AAR carries `libuniffi_automerge.so` for arm64-v8a and x86_64, and consumer R8 rules for JNA and
  the binding. It does not carry `libjnidispatch.so`: JNA's own AAR, a runtime dependency of this one, brings it.
- **Toolchain:** Gobley 0.3.7 can't build on AGP 9, so this repo stays on Kotlin 2.1.10, AGP 8.7.3, JDK 17 and Rust
  1.98.0. Consumers on newer toolchains take the published artifact.

## Prerequisites

- **Rust** through rustup, which reads `rust-toolchain.toml` on its own. That pins Rust 1.98.0 and the five targets
  the build compiles for (`aarch64-apple-darwin`, `aarch64-linux-android`, `x86_64-linux-android`,
  `aarch64-apple-ios`, `aarch64-apple-ios-sim`), and rustup installs them with the toolchain. Where 1.98.0 was
  already installed without them, `rustup target add` the missing ones. [mise](https://mise.jdx.dev) is optional:
  `mise.toml` pins the same Rust and a JDK 17.
- **A JDK 17** anywhere Gradle can find it. `gradle/gradle-daemon-jvm.properties` asks for 17 for the Gradle daemon,
  whatever `java` on the path is, because Gradle 8.13 and detekt 1.23.8 can't run on newer JDKs. Without one, Gradle
  stops before configuring with "Unable to download toolchain matching the requirements ({languageVersion=17, …})".
  The sample's build asks for 17 the same way.
- **The Android SDK** through `ANDROID_HOME`, with NDK `29.0.13846066` and build-tools `35.0.0`.
- **Xcode**, for the Apple targets, and [xcodegen](https://github.com/yonaskolb/XcodeGen) for the Apple samples.

## Build, test, publish locally

```sh
./gradlew jvmTest macosArm64Test iosSimulatorArm64Test   # the tests, on three targets
./gradlew ktfmtCheck detekt                              # format and lint
./gradlew assembleRelease                                # the AAR, in build/outputs/aar/
./gradlew publishToMavenLocal                            # → ~/.m2/repository/com/dgmltn/automerge/
```

The README's examples are copied verbatim in `src/commonTest/kotlin/com/dgmltn/automerge/ReadmeExampleTest.kt`, so
change both together.

`./gradlew clean` also cleans the Rust build, so the next build compiles the crate for every target again (about two
minutes on an Apple silicon Mac with the Gradle build cache off; with it on, Gradle restores most of it).

A JVM jar built locally carries only the build host's native library (macOS arm64), so it has nothing to load on any
other desktop OS or CPU. A release's carries five: see [Releasing](#releasing).

## The sample

[`sample/`](sample) is its own Gradle build on a current toolchain (Kotlin 2.4.20, AGP 9.4.1, Gradle 9.7.1) that
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
- A minified Android release works with only the AAR's own R8 rules (checked on an emulator and a Pixel 10 Pro).
- A real native Mac app (SwiftUI, Kotlin/Native) and a real iOS app use it, with XCTest and XCUITest runs. The iOS
  app is UI-tested on iPhone only, not iPad.

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

The Release workflow (`.github/workflows/release.yml`) refuses a tag that doesn't match `VERSION_NAME`. It tests,
builds the jvm jar's Linux, Windows and Intel macOS libraries on their own runners, publishes to Maven Central and
creates the GitHub release. The extra libraries reach the jar through `-PjvmNativesDir=<folder>`, which holds each
library in its JNA resource folder (`linux-x86-64/libuniffi_automerge.so`). Running the workflow by hand from the
Actions tab does everything but publish, and keeps the jvm jar as a download.

It needs the repository secrets `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` (a Central Portal user token,
not the login) and `SIGNING_KEY` and `SIGNING_KEY_PASSWORD` (the GPG key `7F4F1163F2E3249A`, whose public half is on
keys.openpgp.org). A version on Maven Central can never be changed or removed, so a fix is always a new version.
