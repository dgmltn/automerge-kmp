use automerge as am;

use crate::doc::DocError;

pub struct ChangeHash(am::ChangeHash);

impl From<ChangeHash> for am::ChangeHash {
    fn from(value: ChangeHash) -> Self {
        value.0
    }
}

impl From<am::ChangeHash> for ChangeHash {
    fn from(value: am::ChangeHash) -> Self {
        Self(value)
    }
}

impl<'a> From<&'a am::ChangeHash> for ChangeHash {
    fn from(value: &'a am::ChangeHash) -> Self {
        Self(*value)
    }
}

// Parsed on lift, so a malformed hash fails the call before it reaches a document.
uniffi::custom_type!(ChangeHash, Vec<u8>, {
    lower: |obj| obj.0 .0.to_vec(),
    try_lift: |val| {
        am::ChangeHash::try_from(val.as_slice())
            .map(ChangeHash)
            .map_err(|e| DocError::Internal(am::AutomergeError::from(e)).into())
    },
});
