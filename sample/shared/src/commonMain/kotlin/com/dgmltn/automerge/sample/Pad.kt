package com.dgmltn.automerge.sample

import com.dgmltn.automerge.Doc
import com.dgmltn.automerge.ObjId
import kotlin.time.Clock

/**
 * One device's copy of a shared text: the [Doc] it holds and the text object in it. Indices are
 * UTF-16, a Kotlin `String`'s, because every document is created and loaded that way.
 */
class Pad internal constructor(private val doc: Doc, private val textId: ObjId) : AutoCloseable {
    val text: String
        get() = doc.text(textId)

    /** How many bytes the document saves to. */
    val byteSize: Int
        get() = doc.save().size

    /** How many changes the document holds, its own and those it received. */
    val changeCount: Int
        get() = doc.changes().size

    /**
     * Replaces the text with [newText], writing only what differs so a merge keeps others' edits.
     */
    fun type(newText: String) {
        doc.updateText(textId, newText)
        doc.commitWith(null, Clock.System.now().epochSeconds)
    }

    /** What this pad has that [other] does not, for [other] to [receive]. */
    fun changesSince(other: Pad): ByteArray = doc.encodeChangesSince(other.doc.heads())

    fun receive(changes: ByteArray) {
        doc.applyEncodedChanges(changes)
    }

    /**
     * A caret at [index] that stays on its character as the text changes around it. At the end of
     * the text Automerge would give a cursor that stays at the end, but a caret there is anchored
     * after the last character instead, so it stays after what was typed rather than jumping to the
     * end of what arrives.
     */
    fun cursorAt(index: Int): ByteArray {
        val now = text
        require(index in 0..now.length) { "Index $index is outside a text of length ${now.length}" }
        return when {
            now.isEmpty() -> byteArrayOf(IN_EMPTY_TEXT)
            index < now.length -> byteArrayOf(ON_CHARACTER) + doc.cursor(textId, index.toULong())
            else -> byteArrayOf(AFTER_CHARACTER) + doc.cursor(textId, now.lastCharacterStart())
        }
    }

    /** Where the caret from [cursorAt] is now. */
    fun indexOf(cursor: ByteArray): Int {
        val automergeCursor = cursor.copyOfRange(1, cursor.size)
        return when (cursor.first()) {
            IN_EMPTY_TEXT -> 0
            ON_CHARACTER -> doc.cursorPosition(textId, automergeCursor).toInt()
            else -> afterCharacter(automergeCursor)
        }
    }

    /**
     * Just after the character [cursor] is on. A deleted character's cursor reads as the index of
     * whatever followed it, which is where the caret belongs then; only a character still there
     * gives back the same cursor.
     */
    private fun afterCharacter(cursor: ByteArray): Int {
        val position = doc.cursorPosition(textId, cursor)
        if (!doc.cursor(textId, position).contentEquals(cursor)) return position.toInt()
        // A character outside the Basic Multilingual Plane is two UTF-16 units.
        val width = if (text[position.toInt()].isHighSurrogate()) 2 else 1
        return position.toInt() + width
    }

    override fun close() {
        doc.close()
    }

    private fun String.lastCharacterStart(): ULong {
        val pair =
            length >= 2 && this[length - 1].isLowSurrogate() && this[length - 2].isHighSurrogate()
        return (if (pair) length - 2 else length - 1).toULong()
    }

    private companion object {
        // The first byte of a caret says how its Automerge cursor, the rest, is read.
        const val IN_EMPTY_TEXT: Byte = 0
        const val ON_CHARACTER: Byte = 1
        const val AFTER_CHARACTER: Byte = 2
    }
}
