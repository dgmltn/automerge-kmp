use automerge as am;

use crate::doc::DocError;

pub struct Cursor(am::Cursor);

pub enum Position {
    Cursor { position: Cursor },
    Index { position: i32 },
}

impl From<Cursor> for am::Cursor {
    fn from(value: Cursor) -> Self {
        value.0
    }
}

impl From<am::Cursor> for Cursor {
    fn from(value: am::Cursor) -> Self {
        Cursor(value)
    }
}

// Parsed on lift, so a malformed cursor fails the call before it reaches a document.
uniffi::custom_type!(Cursor, Vec<u8>, {
    lower: |obj| obj.0.to_bytes(),
    try_lift: |val| {
        am::Cursor::try_from(val)
            .map(Cursor)
            .map_err(|e| DocError::Internal(e).into())
    },
});
