use automerge as am;

use crate::index::to_index;
use crate::{ScalarValue, Value};

pub enum ExpandMark {
    Before,
    After,
    None,
    Both,
}

impl From<ExpandMark> for am::marks::ExpandMark {
    fn from(value: ExpandMark) -> Self {
        match value {
            ExpandMark::Before => am::marks::ExpandMark::Before,
            ExpandMark::After => am::marks::ExpandMark::After,
            ExpandMark::None => am::marks::ExpandMark::None,
            ExpandMark::Both => am::marks::ExpandMark::Both,
        }
    }
}

pub struct Mark {
    pub start: i32,
    pub end: i32,
    pub name: String,
    pub value: ScalarValue,
}

impl<'a> From<&'a am::marks::Mark> for Mark {
    fn from(am_mark: &'a am::marks::Mark) -> Mark {
        Mark {
            start: to_index(am_mark.start),
            end: to_index(am_mark.end),
            name: am_mark.name().to_string(),
            value: am_mark.value().into(),
        }
    }
}

pub struct KeyValue {
    pub key: String,
    pub value: Value,
}

impl Mark {
    pub fn from_markset(mark_set: am::marks::MarkSet, index: i32) -> Vec<Mark> {
        let mut result = Vec::new();
        for (key, value) in mark_set.iter() {
            let mark = Mark {
                start: index,
                end: index,
                name: key.to_string(),
                value: value.into(),
            };
            result.push(mark);
        }
        result
    }
}
