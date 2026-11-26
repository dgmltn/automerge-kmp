use automerge as am;

use super::ObjId;
use crate::index::to_index;

pub enum Prop {
    Key { value: String },
    Index { value: i32 },
}

impl From<am::Prop> for Prop {
    fn from(value: am::Prop) -> Self {
        match value {
            am::Prop::Map(k) => Prop::Key { value: k },
            am::Prop::Seq(i) => Prop::Index { value: to_index(i) },
        }
    }
}

pub struct PathElement {
    pub prop: Prop,
    pub obj: ObjId,
}

impl PathElement {
    pub fn new<P: Into<Prop>, O: Into<ObjId>>(prop: P, obj: O) -> Self {
        Self {
            obj: obj.into(),
            prop: prop.into(),
        }
    }
}
