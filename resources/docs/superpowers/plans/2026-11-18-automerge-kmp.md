# automerge-kmp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A usable dependency: one Kotlin Automerge binding for jvm, android, iosArm64, iosSimulatorArm64 and
macosArm64, published from this repo to Maven Local, with a sample of its own that proves a project on space-kmp's
toolchain can consume it.

**Scope:** This repo only. space-kmp is **read-only reference** (which Automerge calls `SpaceDoc` and its sample
make); moving space-kmp onto this library is a later plan of its own. The design's "space-kmp changes" and its
hand-written `AutomergeDoc` layer are that later plan's.

**Architecture:** The root Gradle build vendors automerge-swift's UniFFI crate (`uniffi_automerge`) and has Gobley
generate JNI/JNA-backed KMP bindings from it, on iroh-kmp's older toolchain, published with `publishToMavenLocal`.
`sample/` is a **separate Gradle build** on space-kmp's toolchain that takes the published artifact from Maven
Local: a window with two text areas, each its own Automerge document, that merge, as desktop (JVM), Android, native Mac (SwiftUI) and iOS apps. It is the demo and the spike.

**Tech Stack:** Root build: Rust 1.98.0 + uniffi 0.29.4 + automerge 0.12.0 (vendored), Gobley 0.3.7, Kotlin 2.1.10,
AGP 8.7.3 (`com.android.library` + `androidTarget()`), JDK 17. `sample/`: Kotlin 2.4.20, AGP 9.4.1, Compose
Multiplatform, at the versions in space-kmp's `gradle/libs.versions.toml`.

**Spec:** `resources/docs/superpowers/specs/2026-11-18-automerge-kmp-design.md` (read it first; it is the authority,
as amended by its "Scope" note).

## Global Constraints

- Repos: this one at `~/Developer/automerge-kmp`. iroh-kmp at `~/Developer/iroh-kmp` is the reference for every
  Gobley/Gradle/publishing choice and space-kmp at `~/Developer/space-kmp` is the reference for the newer toolchain's
  versions and for what callers need; **neither is ever modified**, and no branch, worktree or build is made in them.
- Coordinates `com.dgmltn.automerge:automerge-kmp`, first version `0.1.0`, set in `gradle.properties`
  (`GROUP`, `POM_ARTIFACT_ID`, `VERSION_NAME`) as iroh-kmp does. Generated Kotlin package `com.dgmltn.automerge`.
  Maven Local only: no `publishToMavenCentral()`.
- Toolchain of the root build: Kotlin 2.1.10, AGP 8.7.3, Gobley 0.3.7, JDK 17, uniffi exactly `=0.29.4`, automerge
  0.12.0, Rust 1.98.0 pinned in both `mise.toml` and `rust-toolchain.toml` (this machine has rustup and no mise).
  `ndkVersion = "29.0.13846066"` (the installed one). Do not move the root build onto Kotlin 2.4 or AGP 9: Gobley
  0.3.7 can't build there.
- Vendored crate: automerge-swift `rust/` at commit `658cb91e914cabca4cba75557ed35a7de6ad1bfc`, into
  `src/commonMain/rust/`, with automerge-swift's LICENSE and a `VENDORED.md` that names the commit and lists every
  change made to it.
- Bytes cross the FFI as `ByteArray`: every `sequence<u8>` in the UDL (the four custom typedefs, `save`, `load`, the
  change encodings, sync messages, `ScalarValue.Bytes`, …) becomes `bytes`. The Rust type stays `Vec<u8>`.
- Every document is created and loaded with `TextEncoding.Utf16CodeUnit`: Automerge text indices are Kotlin
  `String` / Compose indices.
- `sample/` has its own `settings.gradle.kts` and wrapper, is not included in the root build, and gets the library
  only from `mavenLocal { content { includeGroup("com.dgmltn.automerge") } }`. Building it needs no Rust or NDK.
- Doug's skills apply: kmp-preferences, kotlin-preferences, comment-preferences, git-preferences,
  jetpack-compose-preferences (sample UI). In particular: branch before committing (`feat/…`), commit subjects
  `[tag] the problem, not the mechanics`, no AI trailer, squash-merge locally only when Doug says so, **never
  push**. Formatting is ktfmt's and lint is detekt's.
- Doug's Space Mac app is running from space-kmp. Never run anything from space-kmp, and never kill a process by a
  name that could match it (`Space`).
- Two personal Android phones are on adb. Use an emulator (`adb -e`, or `-s emulator-…`) for any on-device check.

## Review Focus

- A document saved by automerge-java (what devices hold today) must load and read the same → Task 3
  `aDocumentSavedByAutomergeJava_loadsAndReadsTheSame` (fixture).
- Text with emoji and other non-BMP characters: indices are UTF-16 everywhere, including after `load` → Task 3
  `aLoadedDocument_indexesTextInUtf16`.
- Two edits to one text from a common base must both survive a merge, the mirror's future shape → Task 3
  `forkAt_updateText_merge_keepsBothEdits`; Task 4 `twoPadsEditedApart_mergeToTheSameText`.
- The artifact, built on Kotlin 2.1.10 / AGP 8.7.3, consumed on Kotlin 2.4.20 / AGP 9.4.1 on every target → Task 4.
- Two Rust static libraries (iroh-kmp's and this one) in one Apple binary → Task 4 `linksBesideIrohKmp`.
- A release Android build must not break the FFI under R8 → Task 2 (consumer rules) and Task 6 (minified release on
  the emulator).
- The binding inside a real iOS app, linked through Xcode → Task 8 (the sample on the simulator).
- The binding inside a native Mac app, the Space Mac app's shape → Task 7 (SwiftUI over the `macosArm64` framework).

---

### Task 1: The binding builds and works on the JVM

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, the Gradle
  wrapper (same Gradle as iroh-kmp's), `mise.toml`, `rust-toolchain.toml`, `src/commonMain/rust/**` (vendored, with
  its own `Cargo.toml` and a `Cargo.lock`), `src/commonMain/rust/LICENSE`, `VENDORED.md`, `.gitignore`. The crate's
  manifest stays where upstream has it: uniffi's bindgen looks for the UDL beside the manifest, so Gobley is pointed
  at it with `cargo { packageDirectory }`, and cargo's output is `src/commonMain/rust/target/`.
- Test: `src/commonTest/kotlin/com/dgmltn/automerge/SmokeTest.kt`

**Interfaces:**
- Produces: the generated bindings in package `com.dgmltn.automerge` (`Doc`, `ObjId`, `ChangeHash`, `TextEncoding`,
  `Value`, `ScalarValue`, `ObjType`, `Cursor`, … as UniFFI names them from `automerge.udl`). Steps below use the UDL's
  snake_case names; the Kotlin is UniFFI's camelCase (`putObjectInMap`, `spliceText`, `forkAt`, …).

- [ ] **Step 1:** Branch `feat/bindings` (the repo's first commits hold only docs). Install the toolchain:
  `rustup toolchain install 1.98.0` and, for it,
  `rustup target add aarch64-apple-darwin aarch64-linux-android x86_64-linux-android aarch64-apple-ios aarch64-apple-ios-sim`.
  Vendor the crate at the pinned commit. Changes to it, each listed in `VENDORED.md`:
  `crate-type = ["cdylib", "staticlib"]`; uniffi `0.28.2` → `=0.29.4` in `[dependencies]` and
  `[build-dependencies]`; the `uniffi-bindgen` `[[bin]]` and `uniffi-bindgen.rs` removed; `sequence<u8>` → `bytes`
  throughout the UDL; `thiserror` may stay at 1.x. Copy iroh-kmp's Gradle setup (cargo block,
  `uniffi { generateFromLibrary }` with `packageName = "com.dgmltn.automerge"`, the host-JVM-library embedding, the
  Linux/Windows cargo-task gates), with **only the `jvm()` target** for now and no Dokka. Add ktfmt and detekt with
  the kotlin-code-quality-setup skill, excluding generated sources.
- [ ] **Step 2: Write the failing test** `SmokeTest.aTextSplicedIn_readsBack`: a new `Doc` (with
  `TextEncoding.Utf16CodeUnit`), `put_object_in_map(root, "body", Text)`, `splice_text(body, 0, 0, "hello")`,
  `commit_with`, `text(body) == "hello"`; `save()` (a `ByteArray`) then `load` gives the same text.
- [ ] **Step 3: Run it:** `./gradlew jvmTest` → first fails to compile (no bindings yet), then the cargo build and
  codegen run; fix crate/UDL breakage from the uniffi bump (0.29 replaced the custom-type converter trait with
  `uniffi::custom_type!`) until it compiles.
- [ ] **Step 4: Pass:** `./gradlew jvmTest` → `SmokeTest` PASS.
- [ ] **Step 5: Commit:** `[bindings] There is no Kotlin Automerge that runs everywhere space-kmp does`

---

### Task 2: Every target, packaged, published

**Files:** Modify `build.gradle.kts`; create `consumer-rules.pro`, `src/androidMain/jniLibs/**` (JNA dispatcher,
copied from iroh-kmp's for the ABIs built).

- [ ] **Step 1:** Add `androidTarget { publishLibraryVariants("release") }` (minSdk 26, ABIs `arm64-v8a` and
  `x86_64`, `consumerProguardFiles("consumer-rules.pro")` with iroh-kmp's JNA rules plus
  `-keep class com.dgmltn.automerge.** { *; }`), and, gated on `GobleyHost.Platform.MacOS.isCurrent`,
  `iosArm64()`, `iosSimulatorArm64()`, `macosArm64()`. `nativeVariant = Variant.Release`, `publishJvmArtifacts = false`.
- [ ] **Step 2: Run the smoke test everywhere:** `./gradlew jvmTest macosArm64Test iosSimulatorArm64Test` → PASS on
  all three (the same `commonTest`).
- [ ] **Step 3: Build the AAR:** `./gradlew assembleRelease` → BUILD SUCCESSFUL; the AAR holds `libuniffi_automerge.so`
  and `libjnidispatch.so` for each ABI and `proguard.txt` with the rules (`unzip -l`).
- [ ] **Step 4: Publish:** `./gradlew publishToMavenLocal` → `~/.m2/repository/com/dgmltn/automerge/automerge-kmp/0.1.0/`
  with the root module and one per target. Publishing with vanniktech is fine; signing only when a key is configured.
- [ ] **Step 5: Commit:** `[bindings] The binding builds only for the JVM`

---

### Task 3: The binding does what the mirror and the scratchpad will need

**Files:** Modify `src/commonMain/rust/src/automerge.udl` and `doc.rs` (a constructor and `rollback`), `VENDORED.md`;
create `src/commonTest/kotlin/com/dgmltn/automerge/BindingTest.kt` and `.../AutomergeJavaFixture.kt`.

- [ ] **Step 1: Write the failing tests** in `BindingTest` (commonTest, so they run on jvm, macos and the iOS simulator):
  - `aLoadedDocument_indexesTextInUtf16`: text `"a😀b"` saved, loaded with the new constructor
    `load_with_text_encoding(bytes, Utf16CodeUnit)`; `length(text) == 4`; `splice_text(text, 3, 1, "c")` gives `"a😀c"`.
  - `changesSince_applyToAnotherDoc`: doc B made from A's `save()`; A splices; `encode_changes_since(B.heads())`
    applied to B with `apply_encoded_changes` → B's text equals A's.
  - `forkAt_updateText_merge_keepsBothEdits`: base text `"one two three"`, heads `h`; the live doc changes it to
    `"one TWO three"`; a `fork_at(h)` is changed with `update_text` to `"one two three four"`; `merge` → text is
    `"one TWO three four"`.
  - `aCursor_followsItsCharacter_acrossARemoteInsert`: cursor at index 4 of `"abc def"`; another doc's insert of
    `"XX"` at 0 merged in; `cursor_position == 6`.
  - `rollback_dropsWritesNotYetCommitted`: committed text `"kept"`; a splice of `"lost"` then `rollback()`; the text
    is `"kept"` and `heads()` is what it was before the splice.
  - `aDocumentSavedByAutomergeJava_loadsAndReadsTheSame`: `AutomergeJavaFixture.BYTES` (Base64 in a Kotlin `const`,
    so every target reads it) loads; root `"title"` text and `"body"` text read as the fixture's
    `TITLE`/`BODY` constants, and a root `"done"` boolean is `true`.
- [ ] **Step 2: Make the fixture:** a throwaway JVM program in the scratch directory (not in this repo, not in
  space-kmp) against `org.automerge:automerge:0.0.9` writes a document with a `title` text, a `body` text containing
  an emoji and a newline, and `done = true`, and prints `save()` as Base64. Paste it, with the expected strings, into
  `AutomergeJavaFixture.kt`, with a KDoc naming the automerge-java version that made it.
- [ ] **Step 3: Run:** `./gradlew jvmTest macosArm64Test iosSimulatorArm64Test` → the UTF-16 load and rollback tests
  fail to compile; the others may pass already — a test that passes first is fine here, as the binding's behaviour is
  the crate's, but read each one's assertions against the spec before accepting it.
- [ ] **Step 4: Add** `[Name=load_with_text_encoding, Throws=LoadError] constructor(bytes bytes, TextEncoding text_encoding);`
  built on automerge 0.12's load options if they take a text encoding; if they don't, load and then set the encoding
  as `new_with_text_encoding` does — read the crate's source to choose, and say which in `VENDORED.md`. Add
  `u64 rollback();` over `AutoCommit::rollback` (the number of operations dropped). Run the same command → all PASS.
- [ ] **Step 5:** Republish (`./gradlew publishToMavenLocal`), then commit:
  `[bindings] A loaded document counts text differently from a new one, and a half-made change can't be dropped`

---

### Task 4: A consumer on space-kmp's toolchain — the sample's logic

`sample/` is its own Gradle build. This task is also the spike: it settles whether the artifact resolves and links on
the newer toolchain, on every target, beside iroh-kmp.

**Files:**
- Create: `sample/settings.gradle.kts` (root project `automerge-kmp-sample`; `mavenLocal` filtered to
  `com.dgmltn.automerge`, then google and mavenCentral), `sample/gradle/libs.versions.toml` (Kotlin 2.4.20, AGP 9.4.1,
  Compose and coroutines at space-kmp's catalog versions, `automerge-kmp = "0.1.0"`, `iroh-kmp` at space-kmp's
  version), `sample/gradle.properties`, the wrapper (space-kmp's Gradle version), `sample/build.gradle.kts`,
  `sample/shared/build.gradle.kts` (targets `jvm`, `android` via `com.android.kotlin.multiplatform.library`,
  `iosArm64`, `iosSimulatorArm64`, `macosArm64`),
  `sample/shared/src/commonMain/kotlin/com/dgmltn/automerge/sample/Pad.kt`, `TwoPads.kt`
- Test: `sample/shared/src/commonTest/kotlin/com/dgmltn/automerge/sample/TwoPadsTest.kt`, `LinkTest.kt`

**Interfaces — Produces** (package `com.dgmltn.automerge.sample`):
- `class Pad : AutoCloseable` — one device's copy of a shared text. `val text: String`; `fun type(newText: String)`
  (writes the difference with `update_text` and commits); `fun changesSince(other: Pad): ByteArray`
  (`encode_changes_since(other's heads)`); `fun receive(changes: ByteArray)`; `fun cursorAt(index: Int): ByteArray`
  and `fun indexOf(cursor: ByteArray): Int`, for keeping a caret on its character across a merge (the end of the
  text has no character: handle it as space-kmp's `SyncedText.indexNow` does).
- `class TwoPads : AutoCloseable` — `val left: Pad`, `val right: Pad`, made from one common document so both hold the
  same text object; `var linked: Boolean` (while true, an edit to either reaches the other at once; setting it true
  merges what was typed apart); `fun typeLeft(text: String)`, `fun typeRight(text: String)`.

- [ ] **Step 1: Write the failing tests:**
  - `TwoPadsTest.linkedPads_showTheSameText`: type `"hello"` on the left → the right reads `"hello"`.
  - `TwoPadsTest.twoPadsEditedApart_mergeToTheSameText`: both hold `"one two three"`; unlinked, the left becomes
    `"one TWO three"` and the right `"one two three four"`; linked again → both read `"one TWO three four"`.
  - `TwoPadsTest.aCaret_staysOnItsCharacter_whenTheOtherPadInsertsBeforeIt`: a caret at index 2 of `"😀b"`, the other
    pad inserts `"XX"` at 0 → the caret's index is 4.
  - `LinkTest.linksBesideIrohKmp` (commonTest, with iroh-kmp a `commonTest` dependency): makes a `Doc` and calls the
    cheapest iroh-kmp function that forces its Rust library to link (read iroh-kmp's generated API to pick one).
- [ ] **Step 2: Run:** `cd sample && ./gradlew :shared:jvmTest` → fails to compile.
- [ ] **Step 3: Implement** `Pad` and `TwoPads`.
- [ ] **Step 4: Run:** `./gradlew :shared:jvmTest :shared:macosArm64Test :shared:iosSimulatorArm64Test` → PASS.
- [ ] **Step 5: Decide.** A duplicate-symbol link error on an Apple target → try the design's fallbacks in order (an
  Apple dynamic framework for this library first). **Stop and ask Doug** before anything that would change iroh-kmp,
  or if no fallback links. An artifact that will not resolve or compile on the newer toolchain is fixed in the root
  build and republished.
- [ ] **Step 6: Commit:** `[sample] Nothing shows the binding working in a project on a newer toolchain`

---

### Task 5: The desktop sample — two text areas that merge

**Files:** Create `sample/shared/src/commonMain/kotlin/com/dgmltn/automerge/sample/ui/TwoPadsScreen.kt`,
`TwoPadsViewModel.kt`; `sample/app-desktop/build.gradle.kts`, `sample/app-desktop/src/main/kotlin/…/Main.kt`.
Test: `sample/shared/src/jvmTest/kotlin/com/dgmltn/automerge/sample/ui/TwoPadsScreenTest.kt`.

**Interfaces:** Consumes Task 4's `TwoPads`. Produces `@Composable fun TwoPadsScreen(...)`: two text areas side by
side (stacked when narrow), a "Linked" switch between them, and under each the document's size in bytes and number
of changes. Typing in one while linked shows in the other; unlinked, they drift; linking merges both, and each
caret stays on its character.

- [ ] **Step 1: Write the failing UI test** (Compose UI test on the JVM): type in the left area → the right shows it;
  switch Linked off, type different text in each, switch it on → both areas show the merged text.
- [ ] **Step 2: Run:** `./gradlew :shared:jvmTest` → fails. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: See it:** `./gradlew :app-desktop:run`, type in both areas linked and unlinked, take a screenshot
  for the report, and quit the window (by its own pid).
- [ ] **Step 6: Commit:** `[sample] The binding can only be seen working in tests`

---

### Task 6: The Android sample, minified

**Files:** Create `sample/app-android/build.gradle.kts`, `src/main/AndroidManifest.xml`,
`src/main/kotlin/…/MainActivity.kt` (hosts `TwoPadsScreen`).

- [ ] **Step 1:** The release build type has `isMinifyEnabled = true` and is signed with the debug key, so R8 runs
  against the library's consumer rules and the APK installs.
- [ ] **Step 2: Build:** `./gradlew :app-android:assembleRelease` → BUILD SUCCESSFUL, with no keep rules for
  `com.dgmltn.automerge` or JNA in the app itself.
- [ ] **Step 3: On the emulator:** start an AVD, install the release APK with `adb -e`, type in one pad and see it in
  the other, unlink, type in both, link: merged. Screenshot for the report. Record the release APK's size and how
  much of it is `libuniffi_automerge.so`.
- [ ] **Step 4: Commit:** `[sample] Nothing proves a release Android build keeps the binding working`

---

### Task 7: The Mac sample, in SwiftUI

The desktop app of Task 5 is a JVM app. This one is native: SwiftUI over the Kotlin/Native `macosArm64` framework,
the shape of the Space Mac app.

**Files:**
- Move the Compose screen, its view model and their tests out of `sample/shared` into a new `sample/shared-ui`
  module (targets `jvm`, `android`, `iosArm64`, `iosSimulatorArm64`; package unchanged), so `sample/shared` is logic
  only on every target and its macOS framework carries no Compose. `app-desktop` and `app-android` depend on
  `shared-ui`. The experimental Compose-on-macOS flag in `sample/gradle.properties` goes.
- Modify `sample/shared/build.gradle.kts`: `macosArm64` exports a static framework (`TwoPadsKit`) of the logic.
- Create `sample/app-macos/project.yml` (xcodegen; bundle id `com.dgmltn.automerge.sample.mac`, app name `TwoPads`,
  a regular windowed app; the generated `.xcodeproj` is git-ignored), `sample/app-macos/TwoPads/**` (SwiftUI `App`,
  a `TwoPadsModel: ObservableObject` over the Kotlin `TwoPads`, the two-pane view), with a pre-build script that runs
  the sample's `embedAndSignAppleFrameworkForXcode`. space-kmp's `sample/space-mac/app-macos/macApp` is the reference
  for the shape (read-only; never build or run it).

**Interfaces:** Consumes `TwoPads`/`Pad` from Swift. Produces the same screen as Task 5 in SwiftUI: two text areas
side by side, a "Linked" toggle between them, bytes and changes under each. Each caret stays on its character
across a merge (`cursorAt`/`indexOf`); if SwiftUI's `TextEditor` cannot hold a selection on the deployment target,
wrap `NSTextView`.

- [ ] **Step 1:** Split out `shared-ui`; from `sample/`,
  `./gradlew :shared:allTests :shared-ui:jvmTest :app-android:assembleRelease ktfmtCheck detekt` → PASS.
- [ ] **Step 2: Write the failing test:** an XCTest target for `TwoPadsModel`: typing in the left pad while linked
  shows in the right; unlinked edits to both merge to one text when linked again; a caret keeps its character.
  `xcodebuild test` into a scratch `-derivedDataPath` → fails (no model).
- [ ] **Step 3: Implement** the model and the views. **Step 4:** `xcodebuild test` → PASS; `xcodebuild build` →
  BUILD SUCCEEDED.
- [ ] **Step 5: See it:** launch the built `TwoPads.app` from the scratch path, confirm it stays up, quit it by its
  own pid. Record the app binary's size.
- [ ] **Step 6: Commit:** `[sample] The Mac sample runs on the JVM, not on the native target a Mac app would use`

---

### Task 8: The iOS sample

**Files:** Modify `sample/shared-ui/build.gradle.kts` (the iOS targets export a static framework holding
`TwoPadsScreen` as a `UIViewController`; put that entry point in `sample/shared-ui/src/iosMain/…/MainViewController.kt`);
create `sample/app-ios/project.yml` (xcodegen; the generated `.xcodeproj` is git-ignored), `sample/app-ios/iosApp/**`
(a SwiftUI `App` hosting the view controller, `Info.plist`), with a pre-build script that runs the sample's
`embedAndSignAppleFrameworkForXcode`. space-kmp's `sample/notes/app-ios` is the reference for the shape (read-only).

- [ ] **Step 1: Build:** `xcodegen generate` in `sample/app-ios`, then `xcodebuild` the scheme for an iOS simulator
  destination into a scratch `-derivedDataPath` → BUILD SUCCEEDED. Also build (not run) for `generic/platform=iOS`
  with code signing off, so the `iosArm64` slice is known to link.
- [ ] **Step 2: On the simulator:** boot a simulator, `xcrun simctl install` and `launch` the app; it starts without
  crashing and shows the two pads. Type in one pad if the simulator can be driven; a screenshot
  (`xcrun simctl io booted screenshot`) for the report either way. Record the app's size.
- [ ] **Step 3: Commit:** `[sample] Nothing shows the binding working in an iOS app`

---

### Task 9: Say what was built

**Files:** Modify `README.md` (status, how to build and publish, how to run the sample, how a consumer adds it,
what moved out to the later space-kmp plan), the design (a "Spike results" list under Risks: resolution on the newer
toolchain, the two-staticlib link result per target, sizes), `VENDORED.md` if anything is missing.

- [ ] **Step 1:** Write them; every command in the README is one that was run.
- [ ] **Step 2:** From a clean state (`./gradlew clean`, and the `0.1.0` directory removed from Maven Local):
  `./gradlew jvmTest macosArm64Test iosSimulatorArm64Test detekt publishToMavenLocal`, then in `sample/`
  `./gradlew :shared:allTests :app-android:assembleRelease detekt` the Task 7 `xcodebuild test` and the Task 8 simulator `xcodebuild`
  → PASS / BUILD SUCCESSFUL.
- [ ] **Step 3: Commit:** `[docs] The README still says nothing is built`
- [ ] **Step 4: Hand to Doug.** Report the spike results, sizes, rulings and anything deferred. Do not merge the
  branch or push anything until he says so.
