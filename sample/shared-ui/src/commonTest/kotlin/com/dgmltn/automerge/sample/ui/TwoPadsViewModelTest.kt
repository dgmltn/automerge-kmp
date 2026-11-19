package com.dgmltn.automerge.sample.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class TwoPadsViewModelTest {
    @Test
    fun typingInOnePad_keepsTheOtherCaretOnItsCharacter() {
        withViewModel { vm ->
            vm.onLeftChange(TextFieldValue("world", TextRange(5)))
            vm.onRightChange(TextFieldValue("world", TextRange(2)))

            vm.onLeftChange(TextFieldValue("hello world", TextRange(6)))

            assertEquals(TextFieldValue("hello world", TextRange(8)), vm.state.right.field)
            assertEquals(TextFieldValue("hello world", TextRange(6)), vm.state.left.field)
        }
    }

    @Test
    fun relinking_showsTheMergedText_withEachCaretOnItsCharacter() {
        withViewModel { vm ->
            vm.onLeftChange(TextFieldValue("two", TextRange(3)))
            vm.onLinkedChange(false)
            vm.onLeftChange(TextFieldValue("two!", TextRange(4)))
            vm.onRightChange(TextFieldValue("one two", TextRange(3)))

            vm.onLinkedChange(true)

            assertEquals(TextFieldValue("one two!", TextRange(8)), vm.state.left.field)
            assertEquals(TextFieldValue("one two!", TextRange(3)), vm.state.right.field)
        }
    }

    @Test
    fun aSelection_movesWithItsCharacters() {
        withViewModel { vm ->
            vm.onLeftChange(TextFieldValue("one two", TextRange(7)))
            vm.onRightChange(TextFieldValue("one two", TextRange(4, 7)))

            vm.onLeftChange(TextFieldValue("zero one two", TextRange(5)))

            assertEquals(TextRange(9, 12), vm.state.right.field.selection)
        }
    }

    @Test
    fun movingTheCaret_makesNoChange() {
        withViewModel { vm ->
            vm.onLeftChange(TextFieldValue("abc", TextRange(3)))
            val changes = vm.state.left.changeCount

            vm.onLeftChange(TextFieldValue("abc", TextRange(1)))

            assertEquals(changes, vm.state.left.changeCount)
            assertEquals(TextRange(1), vm.state.left.field.selection)
        }
    }

    @Test
    fun unlinkedPads_countTheirOwnChanges() {
        withViewModel { vm ->
            val start = vm.state.right.changeCount
            vm.onLinkedChange(false)

            vm.onLeftChange(TextFieldValue("a", TextRange(1)))

            assertEquals(start + 1, vm.state.left.changeCount)
            assertEquals(start, vm.state.right.changeCount)
            assertEquals("", vm.state.right.field.text)
        }
    }

    /** Clears the view model afterwards, as its owner would, which closes its documents. */
    private fun withViewModel(block: (TwoPadsViewModel) -> Unit) {
        val store = ViewModelStore()
        val factory = viewModelFactory { initializer { TwoPadsViewModel() } }
        try {
            block(ViewModelProvider.create(store, factory)[TwoPadsViewModel::class])
        } finally {
            store.clear()
        }
    }
}
