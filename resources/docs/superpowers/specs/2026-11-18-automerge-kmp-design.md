# automerge-kmp

Date: 2026-11-18. Status: approved by Doug for building, not built. Plan:
`resources/docs/superpowers/plans/2026-11-18-automerge-kmp.md`.

## Why

wrtr's note bodies are one leaf each, last writer wins. A phone that has not caught up and then writes a note
(typing, or an append from the share sheet) replaces a peer's edit it never saw, and nothing keeps the loser.
Doug chose to move note bodies to Automerge so concurrent edits merge. A new Mac-app kind, the scratchpad, is
Automerge too. space-kmp's `:automerge` runs on automerge-java, which has no iOS or macOS build, so neither the
wrtr iOS app nor the Mac app (Kotlin/Native `macosArm64`) nor the mirror's macOS target can use it.

This is the first of the Automerge projects: **automerge-kmp** → the scratchpad (a Mac-app and wrtr kind) →
notes on Automerge (vault layout B, the mirror merging disk edits) → pinned notes as share destinations. (Sharing
text to a new wrtr note needed none of this and shipped on its own, wrtr `708dc31`.) Only automerge-kmp is designed
here. It spans two repos: this one, and space-kmp's `:automerge` and `:sample:automerge`.

## Goals

- One Kotlin Automerge API in `commonMain` for `jvm`, `android`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`.
- space-kmp's `:automerge` (`SpaceDoc`, `applyChunks`) runs on every one of those targets.
- automerge-java leaves space-kmp entirely; the Automerge sample moves to the new API and keeps working.
- Documents saved by automerge-java load in automerge-kmp (Automerge's binary format is stable), so nothing a
  device already holds needs converting.

## Non-goals

- Automerge's own sync protocol (`SyncState`, sync messages). Spaces already sync chunks; the binding may expose
  it because the crate does, but nothing uses it.
- Publishing to Maven Central (for now: Maven Local only, see Repository).
- Anything in wrtr. The scratchpad and notes are projects 2 and 3. wrtr's build must not need Rust or the NDK.
- Using automerge-swift's Swift package. Kotlin/Native cannot call Swift-only APIs.

## Anti-goals

- Two Automerge implementations behind one interface (Doug weighed and rejected the Swift-bridge approach). One
  binding, one Rust core, tests on every target.
- A Kotlin API that leaks UniFFI's generated types into space-kmp's public API.
- Any core module (`:space-core`, `:space-store`, `:space-sync`, the transports, `:vault`, `:mirror`) depending on
  `:automerge`; it stays an optional module an app adds.

## Approaches considered

- **A. Bridge:** a ~15-call Kotlin interface, automerge-java on JVM/Android, a Swift class over automerge-swift
  injected by each Apple host. No Rust build, but Swift glue in every Apple host (later the iOS Share Extension
  too), and Kotlin/Native tests (the mirror's macOS tests) can never run Automerge.
- **B. automerge-kmp via Gobley (chosen):** Gobley generates JNI-backed KMP bindings from a UniFFI crate, as
  iroh-kmp does for iroh. One implementation everywhere; costs a Rust cross-build and slower clean builds.
- **B′, which B uses:** instead of writing a UniFFI wrapper over the `automerge` crate, reuse automerge-swift's
  `rust/` crate (`uniffi_automerge`, MIT, UDL-defined), which already wraps everything needed.

Doug picked B ("probably b"), knowing A is the faster start.

**How space-kmp consumes it.** Gobley 0.3.7 (newest, 2025-10-08) supports only AGP 8 and `com.android.library`;
AGP 9 and `com.android.kotlin.multiplatform.library` support is open upstream (gobley #153, #282, #288). space-kmp
is on Kotlin 2.4.20 / AGP 9.4.1, and one composite build cannot hold two AGP versions, so an `includeBuild` of
automerge-kmp would not build. Considered: (a) publish it and consume the artifact, as space-kmp already consumes
iroh-kmp (built on Kotlin 2.1.10 / AGP 8.7.3); (b) an unreleased Gobley fork with AGP 9 work; (c) Gobley for
bindgen, JVM and Apple, plus a hand-made cargo-ndk Android build. **Doug chose (a).**

## Design

### Repository

A new repo `~/Developer/automerge-kmp`, beside iroh-kmp and space-kmp; Gradle root project `automerge-kmp`,
one library module, on iroh-kmp's toolchain (Kotlin 2.1.10, AGP 8.7.3 with `com.android.library` +
`androidTarget()`, Gobley 0.3.7, JDK 17 and Rust 1.98.0 pinned in `mise.toml`). It is published with
`publishToMavenLocal` as `com.dgmltn.automerge:automerge-kmp:<version>`; space-kmp adds `mavenLocal()` filtered
to the group `com.dgmltn.automerge` and pins the version in its catalog. Neither space-kmp nor wrtr builds Rust.
A change to the Rust side or the generated bindings means a version bump and a republish before space-kmp sees it.
Moving to Maven Central later is a publishing change only.

automerge-kmp holds only the generated bindings (Kotlin package `com.dgmltn.automerge`), so it changes rarely. The
hand-written Kotlin layer lives in space-kmp's `:automerge`, where it changes with the code that uses it.

### Rust side

- Vendor automerge-swift's `rust/` crate (upstream `main` at `658cb91e91`, automerge 0.12.0) into
  `src/commonMain/rust/`, with its LICENSE and a `VENDORED.md` naming the upstream commit, so a later resync is a
  diff against that commit.
- Changes to the vendored crate, each listed in `VENDORED.md`: `crate-type` gains `cdylib` (JVM and Android load a
  shared library); uniffi bumped from 0.28.2 to 0.29.4 (iroh-kmp's, which Gobley 0.3.7 builds); the Swift-only
  `uniffi-bindgen` binary dropped; a `load` constructor that takes a `TextEncoding` (the vendored `Doc.load` uses
  the default code-point encoding, and a loaded document must index text in UTF-16 like a new one).
- Rust targets added with `rustup target add` (mise manages toolchains, not targets).
- Gradle: `dev.gobley.cargo` and `dev.gobley.uniffi` plugins configured after iroh-kmp's `build.gradle.kts` (embed
  the host's JVM library, skip Linux/Windows cargo builds on a Mac, JNA consumer rules for R8).

### Kotlin side

Gobley generates the bindings in automerge-kmp. A thin, hand-written layer in space-kmp's `:automerge`
(package `com.dgmltn.space.automerge`) is the API the rest of space-kmp and the apps use, shaped for what they
need, not a 1:1 mirror of the UDL; no generated type appears in its public signatures:

- `AutomergeDoc` (wraps the UniFFI `Doc`; `Closeable`, frees the Rust object): `AutomergeDoc()`,
  `AutomergeDoc.load(bytes)`, `save()`, `heads`, `encodeChangesSince(heads)`, `applyEncodedChanges(bytes)`,
  `fork()`, `forkAt(heads)`, `merge(other)`, `commit(message?, time)`, `actorId`.
- Text: `text(obj)`, `textAt(obj, heads)`, `spliceText(obj, start, delete, chars)`, `updateText(obj, chars)`,
  `cursorAt(obj, position)`, `cursorPosition(obj, cursor)`. Every document is created with
  `TextEncoding.Utf16CodeUnit`, so indices are Kotlin `String`/Compose indices.
- Maps and lists as the Automerge sample uses them today: `putObject`, `put`, `get`, `keys`, `length`, `insert`,
  `delete`, with a small `AmValue`-like sealed type.
- `ChangeHash` and `ObjId` as Kotlin value types over the bytes.
- Everything a `SpaceDoc` call site needs is covered: the hand-written layer is grown by what the sample and
  `SpaceDoc` call, nothing more.

### space-kmp changes

- `:automerge` targets `jvm`, `android`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, depends on the published
  automerge-kmp instead of automerge-java, and gains the hand-written layer above. `SpaceDoc`'s `synchronized(lock)` (JVM-only) becomes a KMP lock
  (a `Mutex` around suspend paths, or atomicfu's lock where it must stay non-suspending).
- `:sample:automerge` moves to the new API; its targets stay android and jvm (an iOS app for it is not in scope).
  automerge-java indexes text in code points, and the sample converts to and from them (`TextOffsets` in
  `SyncedText`); with UTF-16 documents those conversions go, and String indices are Automerge indices.
- Gobley's bindings call through JNA. iroh-kmp's AAR bundles `libjnidispatch.so` and ships JNA consumer R8 rules;
  automerge-kmp does the same, and an app that holds both (wrtr) keeps its existing
  `jniLibs.pickFirsts += "**/libjnidispatch.so"`.
- automerge-java is removed from `libs.versions.toml`.
- `ARCHITECTURE.md` and the module READMEs say what runs where now.

### Tests

- automerge-kmp: `commonTest` run on `jvmTest` and `macosArm64Test` (and the iOS simulator): create, splice,
  save/load, `encodeChangesSince`/`applyEncodedChanges` between two docs, `forkAt` + `updateText` + `merge` (the
  mirror's merge shape: two concurrent edits to one text both survive), cursors across a remote insert, UTF-16
  indices on emoji text.
- A compatibility test: a document saved by automerge-java 0.0.9 (a fixture checked in) loads and reads the same.
- space-kmp: the existing `SpaceDoc` and sample tests pass unchanged on the JVM; `SpaceDoc`'s tests also run on
  `macosArm64Test`.

## Risks to settle first (the plan's first task, a spike)

- **The published artifact on space-kmp's toolchain.** automerge-kmp built on Kotlin 2.1.10 / AGP 8.7.3, consumed
  from Maven Local by space-kmp on Kotlin 2.4.20 / AGP 9.4.1, on jvm, android and the Apple targets. iroh-kmp shows
  the shape works; the spike proves it for this artifact before anything else.

- **Two Rust static libraries in one Apple binary.** wrtr iOS and the Mac app already link iroh-kmp's Rust
  `staticlib`; automerge-kmp adds a second. Two Rust staticlibs can collide on runtime symbols at link time.
  The spike links both into the Mac app (`macosArm64`) and an iOS simulator build. If they collide, fallbacks in
  order: build automerge-kmp's Apple library as a dynamic framework; or build both crates with the same toolchain
  and `-C prefer-dynamic`-free LTO settings that keep runtime symbols private; asking Doug before anything that
  touches iroh-kmp.
- **uniffi 0.28 → 0.29.4 bump** on the vendored UDL: expected to be mechanical; the spike runs Gobley's codegen.
- **App size:** Automerge adds a native library per Android ABI and to each Apple binary; the spike records the
  release APK and Mac app size before and after.

## Left to the builder

- Exact Gobley configuration details, names of internal files, and which UDL calls the Kotlin layer skips.
- Whether `SpaceDoc`'s lock is a `Mutex` or atomicfu, per call site.
- What replaces automerge-java's `Transaction` in `SpaceDoc.change { }` (the UniFFI `Doc` autocommits until
  `commit_with`), and whether a failed `change` rolls back.
- Moving `SpaceDocTest` off any JVM-only harness so it runs on `macosArm64Test`.
- Which Android ABIs to build (at least arm64-v8a and x86_64, for the Pixel and the emulator).
- The order of tasks after the spike.
