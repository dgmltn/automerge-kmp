package com.dgmltn.automerge.sample

import kotlin.test.Test
import kotlin.test.assertEquals

class TwoPadsTest {
    @Test
    fun linkedPads_showTheSameText() {
        TwoPads().use { pads ->
            pads.typeLeft("hello")

            assertEquals("hello", pads.right.text)
        }
    }

    @Test
    fun twoPadsEditedApart_mergeToTheSameText() {
        TwoPads().use { pads ->
            pads.typeLeft("one two three")
            assertEquals("one two three", pads.right.text)

            pads.linked = false
            pads.typeLeft("one TWO three")
            pads.typeRight("one two three four")
            assertEquals("one TWO three", pads.left.text)
            assertEquals("one two three four", pads.right.text)
            pads.linked = true

            assertEquals("one TWO three four", pads.left.text)
            assertEquals("one TWO three four", pads.right.text)
        }
    }

    @Test
    fun aCaret_staysOnItsCharacter_whenTheOtherPadInsertsBeforeIt() {
        TwoPads().use { pads ->
            pads.typeLeft("😀b")
            val caret = pads.left.cursorAt(2)

            pads.typeRight("XX😀b")

            assertEquals("XX😀b", pads.left.text)
            assertEquals(4, pads.left.indexOf(caret))
        }
    }
}
