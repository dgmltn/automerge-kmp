package com.dgmltn.automerge.sample

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PadTest {
    @Test
    fun aCaretAtTheEnd_staysAtTheEnd_whenTheOtherPadInsertsBeforeIt() {
        TwoPads().use { pads ->
            pads.typeLeft("abc")
            val caret = pads.left.cursorAt(3)

            pads.typeRight("XXabc")

            assertEquals(5, pads.left.indexOf(caret))
        }
    }

    @Test
    fun aCaretAfterAnEmoji_staysAfterIt_whenTheOtherPadInsertsBeforeIt() {
        TwoPads().use { pads ->
            pads.typeLeft("a😀")
            val caret = pads.left.cursorAt(3)

            pads.typeRight("XXa😀")

            assertEquals(5, pads.left.indexOf(caret))
        }
    }

    @Test
    fun aCaretAtTheEnd_staysAfterWhatWasTyped_whenTheOtherPadAppends() {
        TwoPads().use { pads ->
            pads.typeLeft("abc")
            val caret = pads.left.cursorAt(3)

            pads.typeRight("abc def")

            assertEquals(3, pads.left.indexOf(caret))
        }
    }

    @Test
    fun aCaretAtTheEnd_movesBack_whenTheOtherPadDeletesTheLastCharacter() {
        TwoPads().use { pads ->
            pads.typeLeft("a😀")
            val caret = pads.left.cursorAt(3)

            pads.typeRight("a")

            assertEquals(1, pads.left.indexOf(caret))
        }
    }

    @Test
    fun aCaretAtTheEnd_movesToTheStart_whenTheOtherPadDeletesEverything() {
        TwoPads().use { pads ->
            pads.typeLeft("abc")
            val caret = pads.left.cursorAt(3)

            pads.typeRight("")

            assertEquals(0, pads.left.indexOf(caret))
        }
    }

    @Test
    fun aCaretInAnEmptyText_staysAtTheStart() {
        TwoPads().use { pads ->
            val caret = pads.left.cursorAt(0)

            pads.typeRight("abc")

            assertEquals(0, pads.left.indexOf(caret))
        }
    }

    @Test
    fun aCaretOutsideTheText_isRefused() {
        TwoPads().use { pads ->
            pads.typeLeft("abc")

            assertFailsWith<IllegalArgumentException> { pads.left.cursorAt(-1) }
            assertFailsWith<IllegalArgumentException> { pads.left.cursorAt(4) }
        }
    }

    @Test
    fun aPad_countsTheChangesItHolds() {
        TwoPads().use { pads ->
            val start = pads.left.changeCount

            pads.typeLeft("a")
            pads.linked = false
            pads.typeRight("ab")

            assertEquals(start + 1, pads.left.changeCount)
            assertEquals(start + 2, pads.right.changeCount)
        }
    }

    @Test
    fun aPad_growsAsItIsTypedIn() {
        TwoPads().use { pads ->
            val empty = pads.left.byteSize

            pads.typeLeft("hello, world")

            assertTrue(pads.left.byteSize > empty, "${pads.left.byteSize} is not over $empty")
        }
    }
}
