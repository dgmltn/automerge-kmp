use automerge as am;

use crate::doc::DocError;

#[derive(Debug, Clone)]
pub struct ObjId(am::ObjId);

impl From<ObjId> for automerge::ObjId {
    fn from(value: ObjId) -> Self {
        value.0
    }
}

impl From<am::ObjId> for ObjId {
    fn from(value: am::ObjId) -> Self {
        ObjId(value)
    }
}

pub fn root() -> ObjId {
    am::ROOT.into()
}

// Parsed on lift, so a malformed id fails the call before it reaches a document.
uniffi::custom_type!(ObjId, Vec<u8>, {
    lower: |obj| obj.0.to_bytes(),
    try_lift: |val| {
        am::ObjId::try_from(val.as_slice())
            .map(ObjId)
            .map_err(|e| {
                DocError::Internal(am::AutomergeError::InvalidObjIdFormat(e.to_string())).into()
            })
    },
});
