use crate::doc::DocError;

// Kotlin indexes strings and lists with `Int`, so indices and lengths cross the FFI as `i32`.

/// An index from the foreign side, which fails the call when it is negative.
pub(crate) fn from_index(index: i32) -> Result<usize, DocError> {
    usize::try_from(index).map_err(|_| DocError::NegativeIndex(index))
}

/// An index or length for the foreign side. Nothing a Kotlin `String` or list can hold is longer
/// than `i32::MAX`, so the saturation is never reached in practice.
pub(crate) fn to_index(index: usize) -> i32 {
    i32::try_from(index).unwrap_or(i32::MAX)
}

