# Vendored crate

`src/commonMain/rust/` is the `rust/` directory of
[automerge-swift](https://github.com/automerge/automerge-swift) at commit
`658cb91e914cabca4cba75557ed35a7de6ad1bfc` (automerge 0.12.0), with automerge-swift's top-level
`LICENSE`. A resync is a diff against that commit.

## Changes from upstream

- `Cargo.toml`: `crate-type` is `["cdylib", "staticlib"]` (was `["staticlib", "rlib"]`); the JVM and
  Android load a shared library, Apple targets link the static one.
- `Cargo.toml`: uniffi `0.28.2` → `=0.29.4` in `[dependencies]` and `[build-dependencies]`, the
  version Gobley 0.3.7's bindgen is built against.
- `Cargo.toml` and `uniffi-bindgen.rs`: the `uniffi-bindgen` `[[bin]]` and its source are removed;
  Gobley brings its own bindgen.
- `Cargo.lock`: re-resolved for uniffi 0.29.4 (which also moves askama, cargo_metadata and
  serde_json and their dependencies); automerge stays 0.12.0.
- `src/automerge.udl`: all 18 `sequence<u8>` are `bytes`, so Kotlin sees `ByteArray`. The Rust
  types stay `Vec<u8>`. They are:
  - the custom typedefs `ObjId`, `ChangeHash`, `ActorId`, `Cursor`;
  - `ScalarValue.Bytes(value)` and `ScalarValue.Unknown(type_code, data)`'s `data`;
  - `SyncState`'s `decode` constructor argument and `encode()`'s return;
  - `Change.bytes`;
  - on `Doc`: the `load` constructor argument; the returns of `save`, `generate_sync_message`
    (optional), `encode_new_changes` and `encode_changes_since`; and the message or changes argument
    of `receive_sync_message`, `receive_sync_message_with_patches`, `apply_encoded_changes` and
    `apply_encoded_changes_with_patches`.
- `src/obj_id.rs`, `src/change_hash.rs`, `src/actor_id.rs`, `src/cursor.rs`: the
  `UniffiCustomTypeConverter` impls, which uniffi 0.29 removed, are replaced by
  `uniffi::custom_type!(Name, Vec<u8>, { lower, try_lift })`, and their
  `use super::UniffiCustomTypeConverter;` imports are dropped.
- `Cargo.toml`: automerge's `utf16-indexing` feature, beside upstream's `wasm`. It changes only
  `TextEncoding::platform_default()`, which `AutoCommit::default()` (so `Doc::new` and
  `Doc::new_with_actor`) and `LoadOptions::default()` (so `Doc::load`) take their encoding from: every
  document indexes text in UTF-16 code units unless given another encoding.
- `src/obj_id.rs`, `src/change_hash.rs`, `src/cursor.rs`: `ObjId`, `ChangeHash` and `Cursor` hold
  the parsed `automerge` value instead of its bytes. Their `try_lift` parses the bytes and returns
  `DocError::Internal` when they don't parse, and the conversions to the `automerge` types, which
  unwrap that parse upstream, are infallible. Upstream, a malformed id from the foreign side panics
  inside the call, and in the methods that convert it while holding the document's write lock
  (`fork_at`, `encode_changes_since`, `change_by_hash`, `marks_at`, `split_block`, `join_block`)
  the panic poisons the lock, so every later call on that `Doc` panics. Here the call fails before
  the method runs: with `DocError` from a method that throws it, and with uniffi's internal error
  from one that throws nothing. `ActorId` is unchanged: automerge takes any bytes as one.
- `src/automerge.udl` and `src/doc.rs`: a `load_with_text_encoding(bytes, text_encoding)`
  constructor, for a document loaded with an encoding other than the default. It passes the
  encoding through automerge 0.12's `LoadOptions::text_encoding` to `AutoCommit::load_with_options`,
  so the document is built with it rather than having it set after the load.
- `src/automerge.udl` and `src/doc.rs`: `rollback()` over `AutoCommit::rollback`, returning the
  number of operations dropped from the change not yet committed.
- `src/automerge.udl`, `src/doc.rs`, `src/mark.rs`, `src/patches.rs`, `src/path.rs`, `src/cursor.rs`,
  `src/lib.rs` and a new `src/index.rs`: every index, length and delete count is `i32` (was `u64`, `u32` for
  `split_block` and `join_block`, and `i64` for the delete counts), so Kotlin sees `Int`, as its strings and lists
  use, instead of `ULong`. They are the index arguments of the list, text, mark, block and cursor methods; the
  returns of `length`, `length_at`, `cursor_position`, `cursor_position_at` and `rollback`; `Position.Index`,
  `Prop.Index`, `Mark`'s `start` and `end`; and `PatchAction`'s `Insert`, `SpliceText` and `DeleteSeq`. A
  negative index fails the call with a new `DocError::NegativeIndex` (upstream casts with `as usize`), and
  `split_block`/`join_block` no longer panic on an index that doesn't fit. Values from automerge saturate at
  `i32::MAX`, which no Kotlin `String` or list can exceed. The unused `From<Prop> for am::Prop` is removed rather
  than given a lossy cast. `ScalarValue.Uint`, counters and timestamps stay 64-bit.

Every other file (`build.rs` and the rest of `src/`) is byte-for-byte upstream's.
