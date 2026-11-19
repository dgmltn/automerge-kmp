package com.dgmltn.automerge.sample

import com.dgmltn.automerge.Doc
import com.dgmltn.automerge.ObjType
import com.dgmltn.automerge.TextEncoding
import com.dgmltn.automerge.root

/**
 * Two devices editing one text. Both start from one saved document, so they hold the same text
 * object; two documents that each made their own would merge into a conflict, not one text.
 */
class TwoPads : AutoCloseable {
    val left: Pad
    val right: Pad

    /**
     * While true, an edit to either pad reaches the other at once. Linking merges what was typed
     * apart.
     */
    var linked: Boolean = true
        set(value) {
            field = value
            if (value) {
                left.receive(right.changesSince(left))
                right.receive(left.changesSince(right))
            }
        }

    init {
        val common = Doc.newWithTextEncoding(TextEncoding.UTF16_CODE_UNIT)
        val (textId, copy) =
            runCatching {
                val textId = common.putObjectInMap(root(), "text", ObjType.TEXT)
                common.commitWith(null, 0)
                textId to Doc.loadWithTextEncoding(common.save(), TextEncoding.UTF16_CODE_UNIT)
            }
                .onFailure { common.close() }
                .getOrThrow()
        left = Pad(common, textId)
        right = Pad(copy, textId)
    }

    fun typeLeft(text: String) {
        left.type(text)
        if (linked) right.receive(left.changesSince(right))
    }

    fun typeRight(text: String) {
        right.type(text)
        if (linked) left.receive(right.changesSince(left))
    }

    override fun close() {
        left.close()
        right.close()
    }
}
