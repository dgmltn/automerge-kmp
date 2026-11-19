package com.dgmltn.automerge.sample.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.dgmltn.automerge.sample.Pad
import com.dgmltn.automerge.sample.TwoPads

/** What one pad shows: its text and caret, and how big its document is. */
@Immutable
data class PadUiState(
    val field: TextFieldValue,
    val byteSize: Int,
    val changeCount: Int,
)

@Immutable
data class TwoPadsUiState(
    val left: PadUiState,
    val right: PadUiState,
    val linked: Boolean,
)

/**
 * Two pads on one screen. [state] is Compose state rather than a flow so that a keystroke is shown
 * in the same frame it is typed; a text field that waits a frame for its value loses input.
 */
class TwoPadsViewModel : ViewModel() {
    private val pads = TwoPads()

    var state by
        mutableStateOf(
            TwoPadsUiState(
                left = pads.left.uiState(TextFieldValue()),
                right = pads.right.uiState(TextFieldValue()),
                linked = pads.linked,
            )
        )
        private set

    init {
        addCloseable(pads)
    }

    fun onLeftChange(value: TextFieldValue) {
        val rightCaret = pads.right.caretAt(state.right.field.selection)
        if (value.text != pads.left.text) pads.typeLeft(value.text)
        state =
            state.copy(
                left = pads.left.uiState(value),
                right = pads.right.uiState(pads.right.fieldAt(rightCaret)),
            )
    }

    fun onRightChange(value: TextFieldValue) {
        val leftCaret = pads.left.caretAt(state.left.field.selection)
        if (value.text != pads.right.text) pads.typeRight(value.text)
        state =
            state.copy(
                left = pads.left.uiState(pads.left.fieldAt(leftCaret)),
                right = pads.right.uiState(value),
            )
    }

    fun onLinkedChange(linked: Boolean) {
        val leftCaret = pads.left.caretAt(state.left.field.selection)
        val rightCaret = pads.right.caretAt(state.right.field.selection)
        pads.linked = linked
        state =
            TwoPadsUiState(
                left = pads.left.uiState(pads.left.fieldAt(leftCaret)),
                right = pads.right.uiState(pads.right.fieldAt(rightCaret)),
                linked = linked,
            )
    }
}

/** A selection held as cursors, so each end stays on its character while the text changes. */
private class Caret(val start: ByteArray, val end: ByteArray)

private fun Pad.caretAt(selection: TextRange) =
    Caret(cursorAt(selection.start), cursorAt(selection.end))

private fun Pad.fieldAt(caret: Caret) =
    TextFieldValue(text, TextRange(indexOf(caret.start), indexOf(caret.end)))

private fun Pad.uiState(field: TextFieldValue) = PadUiState(field, byteSize, changeCount)
