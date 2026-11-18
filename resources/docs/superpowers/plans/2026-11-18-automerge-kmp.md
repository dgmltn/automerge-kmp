# automerge-kmp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One Kotlin Automerge API for jvm, android, iosArm64, iosSimulatorArm64 and macosArm64, published from
this repo to Maven Local, with space-kmp's `:automerge` and `:sample:automerge` moved onto it and automerge-java
gone.

**Architecture:** This repo vendors automerge-swift's UniFFI crate (`uniffi_automerge`) and has Gobley generate
JNI/JNA-backed KMP bindings from it, on iroh-kmp's older toolchain, published with `publishToMavenLocal`. space-kmp
(Kotlin 2.4.20, AGP 9) consumes the artifact and adds a small hand-written API over the generated types in its
`:automerge` module; `SpaceDoc`, its chunk format and the Automerge sample move onto that API.

**Tech Stack:** Rust 1.98.0 + uniffi 0.29.4 + automerge 0.12.0 (vendored), Gobley 0.3.7, Kotlin 2.1.10, AGP 8.7.3
(`com.android.library` + `androidTarget()`), JDK 17 — in this repo. Kotlin 2.4.20, AGP 9.4.1
(`com.android.kotlin.multiplatform.library`) — in space-kmp.

**Spec:** `resources/docs/superpowers/specs/2026-11-18-automerge-kmp-design.md` (read it first; it is the authority).

## Global Constraints

- Repos: this one at `~/Developer/automerge-kmp`; space-kmp at `~/Developer/space-kmp`; iroh-kmp at
  `~/Developer/iroh-kmp` is the reference for every Gobley/Gradle/publishing choice and is **never modified**.
- Coordinates `com.dgmltn.automerge:automerge-kmp`, first version `0.1.0`, set in `gradle.properties`
  (`GROUP`, `POM_ARTIFACT_ID`, `VERSION_NAME`) as iroh-kmp does. Generated Kotlin package `com.dgmltn.automerge`.
- Toolchain here: Kotlin 2.1.10, AGP 8.7.3, Gobley 0.3.7, JDK 17 and Rust 1.98.0 pinned in `mise.toml`, uniffi 0.29.4,
  automerge 0.12.0. Do not move this repo onto space-kmp's Kotlin or AGP: Gobley 0.3.7 can't build on AGP 9.
- Vendored crate: automerge-swift `rust/` at commit `658cb91e914cabca4cba75557ed35a7de6ad1bfc`, into
  `src/commonMain/rust/`, with automerge-swift's LICENSE and a `VENDORED.md` that names the commit and lists every
  change made to it.
- Every document is created and loaded with `TextEncoding.Utf16CodeUnit`: Automerge text indices are Kotlin
  `String` / Compose indices.
- Neither space-kmp's nor wrtr's build may need Rust, cargo or the NDK. space-kmp takes the artifact from
  `mavenLocal()`, filtered to the group `com.dgmltn.automerge`; the version lives in space-kmp's
  `gradle/libs.versions.toml`.
- No core space-kmp module (`:space-core`, `:space-store`, `:space-sync`, the transports, `:vault`, `:mirror`)
  depends on `:automerge`. No generated (UniFFI) type appears in a public signature of space-kmp's `:automerge`.
- Doug's skills apply: kmp-preferences, kotlin-preferences, comment-preferences, git-preferences. In particular:
  branch before committing (`feat/…`), commit subjects `[tag] the problem, not the mechanics`, no AI trailer,
  squash-merge locally only when Doug says so, **never push**. Formatting is ktfmt's and lint is detekt's.
- Doug runs the Space Mac app from space-kmp every day. **Never run `runMacApp` or `buildMacApp`** (they stop his
  running app); build the Mac app with `xcodebuild` into a scratch `-derivedDataPath` instead.
- A personal Android phone may be on adb. Use an emulator (`adb -e`, or `-s emulator-…`) for any on-device check.

## Review Focus

- A document saved by automerge-java (what devices hold today) must load and read the same → Task 4
  `aDocumentSavedByAutomergeJava_loadsAndReadsTheSame` (fixture).
- Text with emoji and other non-BMP characters: indices are UTF-16 everywhere, including after `load` → Task 4
  `aLoadedDocument_indexesTextInUtf16`, Task 5 `spliceText_inUtf16_aroundAnEmoji`.
- Two edits to one text from a common base must both survive a merge, the mirror's future shape → Task 4
  `forkAt_updateText_merge_keepsBothEdits`.
- A release Android build must not break the FFI under R8 → Task 2 (consumer rules) and Task 7
  (`:sample:automerge:app-android` release build runs on the emulator).
- Two Rust static libraries (iroh-kmp's and this one) in one Apple binary → Task 3 spike, before anything builds on it.

---

### Task 1: The binding builds and works on the JVM

**Files (this repo):**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, the Gradle
  wrapper (same Gradle as iroh-kmp's), `mise.toml`, a root `Cargo.toml`, `src/commonMain/rust/**` (vendored),
  `src/commonMain/rust/LICENSE`, `VENDORED.md`, `.gitignore`. The root `Cargo.toml` is the vendored crate's own,
  moved to the root because Gobley reads the root one (iroh-kmp does the same): `[lib] path` and `build` point into
  `src/commonMain/rust/`, and the UDL path in `build.rs` follows.
- Test: `src/commonTest/kotlin/com/dgmltn/automerge/SmokeTest.kt`

**Interfaces:**
- Produces: the generated bindings in package `com.dgmltn.automerge` (`Doc`, `ObjId`, `ChangeHash`, `TextEncoding`,
  `Value`, `ScalarValue`, `ObjType`, `Cursor`, … as UniFFI names them from `automerge.udl`). Steps below use the UDL's
  snake_case names; the Kotlin is UniFFI's camelCase (`putObject`, `spliceText`, `forkAt`, …).

- [ ] **Step 1:** Branch `feat/bindings` (the repo's first commit holds only docs). Vendor the crate at the pinned
  commit. Changes to it, each listed in `VENDORED.md`: `crate-type = ["cdylib", "staticlib"]`; uniffi `0.28.2` →
  `0.29.4` in `[dependencies]` and `[build-dependencies]`; the `uniffi-bindgen` `[[bin]]` and `uniffi-bindgen.rs`
  removed; `thiserror` may stay at 1.x. Copy iroh-kmp's Gradle setup (cargo block, `uniffi { generateFromLibrary }`
  with `packageName = "com.dgmltn.automerge"`, the host-JVM-library embedding, the Linux/Windows cargo-task gates),
  with **only the `jvm()` target** for now. Add ktfmt and detekt with the kotlin-code-quality-setup skill, excluding
  generated sources.
- [ ] **Step 2: Write the failing test** `SmokeTest.aTextSplicedIn_readsBack`: a new `Doc` (with
  `TextEncoding.Utf16CodeUnit`), `put_object(root, "body", Text)`, `splice_text(body, 0, 0, "hello")`, commit,
  `text(body) == "hello"`; `save()` then `load` gives the same text.
- [ ] **Step 3: Run it:** `./gradlew jvmTest` → first fails to compile (no bindings yet), then the cargo build and
  codegen run; fix crate/UDL breakage from the uniffi bump until it compiles.
- [ ] **Step 4: Pass:** `./gradlew jvmTest` → `SmokeTest` PASS.
- [ ] **Step 5: Commit:** `[bindings] There is no Kotlin Automerge that runs everywhere space-kmp does`

---

### Task 2: Every target, packaged, published

**Files:** Modify `build.gradle.kts`; create `consumer-rules.pro`, `src/androidMain/jniLibs/**` (JNA dispatcher, as
iroh-kmp bundles it), `src/macosArm64Test/…` only if a target needs its own test source.

- [ ] **Step 1:** Add `androidTarget { publishLibraryVariants("release") }` (minSdk 26, ABIs `arm64-v8a` and
  `x86_64` at least, NDK r28+, `consumerProguardFiles("consumer-rules.pro")` with iroh-kmp's JNA rules plus
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

### Task 3: Spike — the artifact on space-kmp's toolchain, and two Rust libraries in one binary (throwaway)

In space-kmp, on a throwaway branch `spike/automerge-kmp-link`, deleted at the end. Nothing from it is kept except
the findings, written into the design's "Risks" section (in this repo) as a short "Spike results" list.

- [ ] **Step 1: Consume it:** space-kmp's `settings.gradle.kts` gets `mavenLocal { content { includeGroup("com.dgmltn.automerge") } }`
  in `dependencyResolutionManagement`; the catalog gets `automerge-kmp = "0.1.0"`. In `:automerge`, add the targets
  `iosArm64`, `iosSimulatorArm64`, `macosArm64` and the dependency, and a test in `commonTest` that makes a `Doc`
  and splices text. Pass: `./gradlew :automerge:jvmTest :automerge:macosArm64Test :automerge:iosSimulatorArm64Test`
  and `:sample:automerge:app-android:assembleRelease` BUILD SUCCESSFUL on Kotlin 2.4.20 / AGP 9.4.1.
- [ ] **Step 2: Two Rust static libraries, in test executables:** add `projects.transport.iroh` to `:automerge`'s
  `commonTest` dependencies and make the test also call something cheap from iroh-kmp that forces its library to
  link. Pass: `:automerge:macosArm64Test` and `:automerge:iosSimulatorArm64Test` link and run.
- [ ] **Step 3: In the Mac app:** add the dependency to `:sample:space-mac`'s `macosMain` with one call that
  reaches Automerge at startup; `xcodegen generate` in `sample/space-mac/app-macos/macApp`, then
  `xcodebuild -project Space.xcodeproj -scheme Space -configuration Debug -destination platform=macOS,arch=arm64 -derivedDataPath <scratch dir> build`.
  Pass: BUILD SUCCEEDED and the built app launches from the scratch path without crashing (quit it again).
  **Do not** use `runMacApp`/`buildMacApp`.
- [ ] **Step 4: Sizes:** record, before and after the dependency, the `:sample:automerge:app-android` release APK
  size and the Mac app binary size from Step 3.
- [ ] **Step 5: Decide.** All pass → write the results into the design and delete the branch. Duplicate-symbol link
  errors → try the design's fallbacks in order (an Apple dynamic framework for this library first). **Stop and ask
  Doug** before anything that would change iroh-kmp, or if no fallback links.

---

### Task 4: The binding does what the mirror and the scratchpad will need

**Files (this repo):** Modify `src/commonMain/rust/src/automerge.udl` and `doc.rs` (one new constructor); create
`src/commonTest/kotlin/com/dgmltn/automerge/BindingTest.kt` and `.../AutomergeJavaFixture.kt`.

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
  - `aDocumentSavedByAutomergeJava_loadsAndReadsTheSame`: `AutomergeJavaFixture.BYTES` (Base64 in a Kotlin `const`,
    so every target reads it) loads; root `"title"` text and `"body"` text read as the fixture's
    `TITLE`/`BODY` constants, and a root `"done"` boolean is `true`.
- [ ] **Step 2: Make the fixture:** a throwaway JVM program against `org.automerge:automerge:0.0.9` (space-kmp's
  `:automerge` `jvmTest` on a scratch branch is fine, discarded after) writes a document with a `title` text, a
  `body` text containing an emoji and a newline, and `done = true`, and prints `save()` as Base64. Paste it, with the
  expected strings, into `AutomergeJavaFixture.kt`, with a KDoc naming the automerge-java version that made it.
- [ ] **Step 3: Run:** `./gradlew jvmTest macosArm64Test iosSimulatorArm64Test` → the UTF-16 load test fails
  (no such constructor); the others may pass already — a test that passes first is fine here, as the binding's
  behaviour is the crate's, but read each one's assertions against the spec before accepting it.
- [ ] **Step 4: Add the constructor** `[Name=load_with_text_encoding, Throws=LoadError] constructor(sequence<u8> bytes, TextEncoding text_encoding);`
  built on automerge 0.12's load options if they take a text encoding; if they don't, load and then set the
  encoding as `new_with_text_encoding` does — read the crate's source to choose, and say which in `VENDORED.md`. Run the same command → all PASS.
- [ ] **Step 5:** Republish (`./gradlew publishToMavenLocal`), then commit:
  `[bindings] A loaded document counts text differently from a new one`

---

### Task 5: space-kmp's own Automerge API

In space-kmp, branch `feat/automerge-kmp`.

**Files:**
- Modify: `settings.gradle.kts` (mavenLocal filtered, as in Task 3), `gradle/libs.versions.toml` (`automerge-kmp = "0.1.0"`; keep automerge-java until Task 7), `automerge/build.gradle.kts` (targets + dependency)
- Create: `automerge/src/commonMain/kotlin/com/dgmltn/space/automerge/AutomergeDoc.kt`, `AmValue.kt`, `ObjId.kt`, `ChangeHash.kt`
- Test: `automerge/src/commonTest/kotlin/com/dgmltn/space/automerge/AutomergeDocTest.kt`

**Interfaces — Produces** (package `com.dgmltn.space.automerge`; no generated type in any public signature):
- `class AutomergeDoc : AutoCloseable` — `constructor()`; `companion fun load(bytes: ByteArray): AutomergeDoc`;
  `val heads: List<ChangeHash>`; `fun save(): ByteArray`; `fun encodeChangesSince(heads: List<ChangeHash>): ByteArray`;
  `fun applyEncodedChanges(bytes: ByteArray)`; `fun fork(): AutomergeDoc`; `fun forkAt(heads: List<ChangeHash>): AutomergeDoc`;
  `fun merge(other: AutomergeDoc)`; `fun commit()`;
  reads `get(obj: ObjId, key: String): AmValue?`, `get(list: ObjId, index: Long): AmValue?`, `values(list: ObjId): List<AmValue>`,
  `length(obj: ObjId, at: List<ChangeHash>? = null): Long`, `text(obj: ObjId, at: List<ChangeHash>? = null): String`,
  `cursorAt(obj: ObjId, index: Long, at: List<ChangeHash>? = null): Cursor`, `cursorIndex(obj: ObjId, cursor: Cursor): Long`;
  writes `put(obj: ObjId, key: String, value: String|Long|Boolean)` (three overloads), `putObject(obj: ObjId, key: String, type: ObjType): ObjId`,
  `insertObject(list: ObjId, index: Long, type: ObjType): ObjId`, `delete(obj: ObjId, key: String)`, `delete(list: ObjId, index: Long)`,
  `spliceText(text: ObjId, start: Long, delete: Long, insert: String)`, `updateText(text: ObjId, to: String)`; `close()` frees the Rust object.
  Every `AutomergeDoc` is UTF-16 (new ones and loaded ones).
- `class ObjId` (equality by value; `ObjId.ROOT`), `class ChangeHash(val bytes: ByteArray)` and `class Cursor` (equality
  and `hashCode` by content), `enum class ObjType { Map, List, Text }`.
- `sealed interface AmValue` — `Str(String)`, `Int(Long)`, `Bool(Boolean)`, `F64(Double)`, `Bytes(ByteArray)`, `Null`,
  `Other` (any scalar kind not listed), and the objects `Map(id: ObjId)`, `List(id: ObjId)`, `Text(id: ObjId)`.

- [ ] **Step 1: Write the failing tests** in `AutomergeDocTest` (commonTest: jvm, macosArm64, iosSimulatorArm64):
  `spliceText_inUtf16_aroundAnEmoji` (`"a😀b"`, splice at 3 → as Task 4); `mapsListsAndScalars_readBackAsAmValues`
  (a list of two maps with a `text` Text and a `done` Bool, read with `values` and `get`); `deletes_inMapsAndLists`;
  `aLoadedDocument_isUtf16Too`; `changeHashesAndObjIds_compareByContent`; `textAt_andLengthAt_readThePast`.
- [ ] **Step 2: Run:** `./gradlew :automerge:jvmTest :automerge:macosArm64Test :automerge:iosSimulatorArm64Test` → fails to compile.
- [ ] **Step 3: Implement** the API over the generated `Doc` (commit = `commit_with(null, now in millis)`).
- [ ] **Step 4: Run** the same → PASS.
- [ ] **Step 5: Commit:** `[automerge] space-kmp has no Automerge it can use on Apple platforms`

---

### Task 6: `SpaceDoc` and its chunks on every target

**Files:** Modify `automerge/src/commonMain/kotlin/com/dgmltn/space/automerge/data/SpaceDoc.kt`, `Chunks.kt`; move
the `SpaceDoc` tests that need no sample types into `automerge/src/commonTest/…` (they currently live in
`sample/automerge/src/jvmTest/…/data/SpaceDocTest.kt`); `automerge/README.md`.

**Interfaces:**
- Consumes: Task 5's API.
- Produces: `SpaceDoc.read { doc: AutomergeDoc -> T }` and `SpaceDoc.change { doc: AutomergeDoc -> Unit }` (the
  block's writes are committed as one change after it returns); `fun AutomergeDoc.applyChunks(leaves, skip)` — the
  same behaviour and names as today otherwise.

- [ ] **Step 1: Write the failing tests:** port the format tests from `SpaceDocTest` that use only `SpaceDoc` (a
  plain map document of their own, no `Docs`) into `:automerge`'s commonTest, so they run on macosArm64 and the iOS
  simulator. Run `./gradlew :automerge:macosArm64Test` → fails (`SpaceDoc` is jvm-only).
- [ ] **Step 2: Implement:** `SpaceDoc` and `Chunks` on `AutomergeDoc`; replace `synchronized(lock)` with a KMP lock
  (atomicfu's `SynchronizedObject`/`synchronized`, or a `Mutex` on suspend paths — builder's call per call site,
  noted in the ledger). If a `change { }` block throws, its writes are not committed by `change`; say in KDoc what
  happens to them (the binding autocommits, so they ride with the next commit unless rolled back).
- [ ] **Step 3: Run:** `./gradlew :automerge:jvmTest :automerge:macosArm64Test :automerge:iosSimulatorArm64Test` → PASS.
- [ ] **Step 4: Commit:** `[automerge] SpaceDoc only runs on the JVM`

---

### Task 7: The sample on the new API; automerge-java gone

**Files:** Modify every file under `sample/automerge/src/**` that imports `org.automerge` (`Docs.kt`, `SyncedText.kt`,
`DocumentViewModel.kt`, `AutomergeHost.kt` and their tests); `gradle/libs.versions.toml` (remove `automerge`
version and library); `ARCHITECTURE.md`, `automerge/README.md`, `sample/automerge/README.md`.

- [ ] **Step 1:** Port the sample's tests first (`DocsTest`, `SpaceDocTest`'s remaining cases, `SyncedTextTest`,
  `TwoPhonesTest`, `ViewModelsTest`, …) to the new API, so they fail to compile. Add
  `SyncedTextTest.anEmojiTypedBeforeText_keepsTheCaretAfterIt` (a field holding `"😀"` with the caret at 2, a remote
  insert at 0 → caret at 2 + the insert's length).
- [ ] **Step 2:** Port the code. `SyncedText` drops every `TextOffsets` code-point conversion: String indices are
  Automerge indices now. Remove automerge-java from the catalog; `grep -r org.automerge` in space-kmp finds nothing.
- [ ] **Step 3: Run:** `./gradlew :automerge:allTests :sample:automerge:jvmTest :sample:automerge:app-android:assembleRelease detekt` → PASS / BUILD SUCCESSFUL.
- [ ] **Step 4: On the emulator:** install the release APK of `:sample:automerge:app-android` on an emulator; make a
  text note and a checklist, type in both, reopen the app: what was typed is there. (Two emulators, or the JVM
  `TwoPhonesTest`, cover two devices; one emulator is enough here.)
- [ ] **Step 5: Docs:** `ARCHITECTURE.md`'s module table and the two READMEs say `:automerge` runs on jvm, android,
  iOS and macOS over `com.dgmltn.automerge:automerge-kmp` from Maven Local, and how to republish it.
- [ ] **Step 6: Commit:** `[automerge] The Automerge sample still needs automerge-java, which has no Apple build`
- [ ] **Step 7: Hand to Doug.** Report the spike results, sizes, rulings and anything deferred. Do not merge either
  repo's branch or push anything until he says so.
