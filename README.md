# automerge-kmp

[![Maven Central][badge]][central]

[badge]: https://img.shields.io/maven-central/v/com.dgmltn.automerge/automerge-kmp
[central]: https://central.sonatype.com/artifact/com.dgmltn.automerge/automerge-kmp

Kotlin Multiplatform bindings for [Automerge](https://automerge.org), the CRDT library, generated from its Rust
core: one API on the JVM, Android, iOS and macOS.

Automerge's JVM binding, automerge-java, has no iOS or macOS build, so Kotlin Multiplatform code that shares an
Automerge document across platforms can't use it on Apple targets. automerge-kmp gives that code one API on all of
them.

## Install

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

The artifact carries its native libraries, so you need no Rust or NDK. It is built with Kotlin 2.1.10 and AGP 8.7.3,
and works in projects on Kotlin 2.4 and AGP 9.

## Example

Two copies of a document, edited apart and merged:

```kotlin
Doc().use { doc1 ->
    val text = doc1.putObjectInMap(root(), "text", ObjType.TEXT)
    doc1.spliceText(text, 0u, 0, "Hello world")

    // Another device loads its own copy from the saved bytes.
    Doc.load(doc1.save()).use { doc2 ->
        // Both copies are edited apart, at the same place.
        doc2.spliceText(text, 5u, 0, " beautiful")
        doc1.spliceText(text, 5u, 0, " there")

        // Each merges the other's edits, and both end up with the same text.
        doc1.merge(doc2)
        doc2.merge(doc1)
        check(doc1.text(text) == doc2.text(text))

        // "Hello there beautiful world" or "Hello beautiful there world": the order of
        // the two inserts depends on the actor ids, but every copy picks the same one.
        println(doc1.text(text))
    }
}
```

The API is that of the Automerge Rust crate (`Doc`, `ObjId`, `ChangeHash`, `Cursor`, …), in the package
`com.dgmltn.automerge`.

## Usage notes

- **Text indices are UTF-16 code units**, Kotlin `String` indices, in every document: an emoji counts as two. That
  is the default for `Doc()`, `Doc.newWithActor` and `Doc.load`. `Doc.newWithTextEncoding` and
  `Doc.loadWithTextEncoding` remain for another encoding.
  ```kotlin
  Doc().use { doc ->
      val text = doc.putObjectInMap(root(), "text", ObjType.TEXT)
      doc.spliceText(text, 0u, 0, "a😀b")
      // The emoji is two UTF-16 code units, so "b" is at index 3, as in a Kotlin String.
      doc.spliceText(text, 3u, 1, "c")
      check(doc.text(text) == "a😀c")
  }
  ```
- **`Doc.rollback()`** drops what was written since the last commit.
- **Closing:** a `Doc` holds a Rust object: `close()` it (it is `AutoCloseable`) when done.
- **Threads:** each call is atomic and safe from any thread, since the library locks the document for the call. A
  sequence of calls is not, so serialise your own edits. `a.merge(a)` can deadlock, and so can `a.merge(b)` on one
  thread while `b.merge(a)` runs on another.
- **Ids are bytes:** `ObjId`, `ChangeHash`, `Cursor` and `ActorId` are typealiases of `ByteArray`, and `save()`, the
  change encodings and sync messages take and return `ByteArray` too. So anything holding an id compares by
  reference: `doc.heads() == doc.heads()` is false once there is a head, and so is `==` between two `Value.Object`s,
  `Position.Cursor`s, `PathElement`s, `Change`s or `PatchAction`s naming the same object. Compare the bytes with
  `contentEquals`, and wrap an id in a type of your own with explicit equality to use it as a map key or in a set.
- **Malformed bytes:** an `ObjId`, `ChangeHash` or `Cursor` that does not parse throws (`DocException.Internal` from a
  call that declares `DocException`, `InternalException` from one that declares nothing) and leaves the `Doc`
  usable. Any bytes are a valid `ActorId`.

### Android apps

- Set `abiFilters` to the two ABIs the Rust library is built for. JNA's AAR ships `libjnidispatch.so` for more ABIs,
  so without the filter the app installs on, say, an armeabi-v7a phone and crashes on first use.
  ```kotlin
  android { defaultConfig { ndk { abiFilters += listOf("arm64-v8a", "x86_64") } } }
  ```
- No keep rules: the AAR's consumer rules keep JNA and the binding's package `com.dgmltn.automerge` (not its
  subpackages, which stay yours to shrink).

## Platforms

| Target | For | Native library |
|---|---|---|
| `jvm` | desktop, tests | in the jar, for macOS (arm64, x86-64), Linux (x86-64, arm64) and Windows (x86-64) |
| `android` | phones and the emulator | arm64-v8a, x86_64 |
| `iosArm64`, `iosSimulatorArm64` | iPhone, iPad and the simulator | static, in the klib |
| `macosArm64` | native Mac apps (Kotlin/Native) | static, in the klib |

The library's tests run on the JVM (macOS arm64), macOS and the iOS simulator. Android is tested through the sample
app's minified release, on an emulator and a device.

## Known limits

- The JVM's Linux, Windows and Intel macOS libraries are built but not tested. The Linux ones need glibc 2.35 or
  newer.
- Android: arm64-v8a and x86_64 only.
- Automerge's sync protocol (`SyncState`, sync messages) is exposed but untested under R8.

## Contributing

Building from source, the sample app, updating the vendored Automerge crate and releasing are in
[`CONTRIBUTING.md`](CONTRIBUTING.md).

## License

The vendored Rust crate is automerge-swift's, under the MIT license that ships with it
(`src/commonMain/rust/LICENSE`). The rest is Apache-2.0 ([`LICENSE`](LICENSE)).
