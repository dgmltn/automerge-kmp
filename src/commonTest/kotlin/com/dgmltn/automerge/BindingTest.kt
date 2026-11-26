package com.dgmltn.automerge

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BindingTest {
    @Test
    fun aLoadedDocument_indexesTextInUtf16() {
        val original = newDoc()
        val text = original.putObjectInMap(root(), "text", ObjType.TEXT)
        original.spliceText(text, 0, 0, "a😀b")
        original.commitWith(null, 0)

        val loaded = Doc.loadWithTextEncoding(original.save(), TextEncoding.UTF16_CODE_UNIT)

        assertEquals(4, loaded.length(text))
        loaded.spliceText(text, 3, 1, "c")
        assertEquals("a😀c", loaded.text(text))
    }

    @Test
    fun changesSince_applyToAnotherDoc() {
        val a = newDoc()
        val text = a.putObjectInMap(root(), "text", ObjType.TEXT)
        a.spliceText(text, 0, 0, "hello")
        a.commitWith(null, 0)
        val b = Doc.loadWithTextEncoding(a.save(), TextEncoding.UTF16_CODE_UNIT)

        a.spliceText(text, 5, 0, " world")
        a.commitWith(null, 0)
        b.applyEncodedChanges(a.encodeChangesSince(b.heads()))

        assertEquals("hello world", b.text(text))
        assertEquals(a.text(text), b.text(text))
    }

    @Test
    fun forkAt_updateText_merge_keepsBothEdits() {
        val doc = newDoc()
        val text = doc.putObjectInMap(root(), "text", ObjType.TEXT)
        doc.spliceText(text, 0, 0, "one two three")
        doc.commitWith(null, 0)
        val base = doc.heads()

        doc.spliceText(text, 4, 3, "TWO")
        doc.commitWith(null, 0)
        val fork = doc.forkAt(base)
        fork.updateText(text, "one two three four")
        fork.commitWith(null, 0)
        doc.merge(fork)

        assertEquals("one TWO three four", doc.text(text))
    }

    @Test
    fun aCursor_followsItsCharacter_acrossARemoteInsert() {
        val doc = newDoc()
        val text = doc.putObjectInMap(root(), "text", ObjType.TEXT)
        doc.spliceText(text, 0, 0, "abc def")
        doc.commitWith(null, 0)
        val cursor = doc.cursor(text, 4)

        val other = doc.fork()
        other.spliceText(text, 0, 0, "XX")
        other.commitWith(null, 0)
        doc.merge(other)

        assertEquals("XXabc def", doc.text(text))
        assertEquals(6, doc.cursorPosition(text, cursor))
    }

    @Test
    fun rollback_dropsWritesNotYetCommitted() {
        val doc = newDoc()
        val text = doc.putObjectInMap(root(), "text", ObjType.TEXT)
        doc.spliceText(text, 0, 0, "kept")
        doc.commitWith(null, 0)
        val before = doc.heads()

        doc.spliceText(text, 4, 0, "lost")
        val dropped = doc.rollback()

        assertEquals(4, dropped)
        assertEquals("kept", doc.text(text))
        assertHeadsEqual(before, doc.heads())
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun aDocumentSavedByAutomergeJava_loadsAndReadsTheSame() {
        val doc =
            Doc.loadWithTextEncoding(
                Base64.decode(AutomergeJavaFixture.BYTES),
                TextEncoding.UTF16_CODE_UNIT,
            )

        assertEquals(AutomergeJavaFixture.TITLE, doc.text(textIn(doc, "title")))
        assertEquals(AutomergeJavaFixture.BODY, doc.text(textIn(doc, "body")))
        assertEquals(
            AutomergeJavaFixture.BODY.length,
            doc.length(textIn(doc, "body")),
        )
        assertEquals(Value.Scalar(ScalarValue.Boolean(true)), doc.getInMap(root(), "done"))
    }

    @Test
    fun aNewDocument_indexesTextInUtf16() {
        val doc = Doc()
        val text = doc.putObjectInMap(root(), "text", ObjType.TEXT)
        doc.spliceText(text, 0, 0, "a😀b")

        assertEquals(4, doc.length(text))
    }

    @Test
    fun aPlainlyLoadedDocument_indexesTextInUtf16() {
        val original = Doc()
        val text = original.putObjectInMap(root(), "text", ObjType.TEXT)
        original.spliceText(text, 0, 0, "a😀b")
        original.commitWith(null, 0)

        val loaded = Doc.load(original.save())

        assertEquals(4, loaded.length(text))
        loaded.spliceText(text, 3, 1, "c")
        assertEquals("a😀c", loaded.text(text))
    }

    @Test
    fun everyConstructor_reportsUtf16() {
        val actor = byteArrayOf(1, 2, 3, 4)

        assertEquals(TextEncoding.UTF16_CODE_UNIT, Doc().textEncoding())
        assertEquals(TextEncoding.UTF16_CODE_UNIT, Doc.newWithActor(actor).textEncoding())
        assertEquals(TextEncoding.UTF16_CODE_UNIT, Doc.load(Doc().save()).textEncoding())
    }

    @Test
    fun garbageBytes_failToLoad() {
        val garbage = byteArrayOf(1, 2, 3, 4, 5)

        assertFailsWith<LoadException> { Doc.load(garbage) }
        assertFailsWith<LoadException> {
            Doc.loadWithTextEncoding(garbage, TextEncoding.UTF16_CODE_UNIT)
        }
    }

    @Test
    fun splicingTextIntoAMap_isTheWrongObjectType() {
        assertFailsWith<DocException.WrongObjectType> { Doc().spliceText(root(), 0, 0, "x") }
    }

    @Test
    fun scalarsInAMap_readBack() {
        val doc = Doc()
        doc.putInMap(root(), "name", ScalarValue.String("pad"))
        doc.putInMap(root(), "count", ScalarValue.Int(3))
        doc.putInMap(root(), "done", ScalarValue.Boolean(false))

        assertEquals(Value.Scalar(ScalarValue.String("pad")), doc.getInMap(root(), "name"))
        assertEquals(Value.Scalar(ScalarValue.Int(3)), doc.getInMap(root(), "count"))
        assertEquals(Value.Scalar(ScalarValue.Boolean(false)), doc.getInMap(root(), "done"))
        assertNull(doc.getInMap(root(), "missing"))
        assertEquals(listOf("count", "done", "name"), doc.mapKeys(root()))
        assertEquals(3, doc.length(root()))

        doc.deleteInMap(root(), "done")

        assertNull(doc.getInMap(root(), "done"))
        assertEquals(listOf("count", "name"), doc.mapKeys(root()))
        assertEquals(2, doc.length(root()))
    }

    @Test
    fun aListInAMap_holdsScalarsAndObjects() {
        val doc = Doc()
        val items = doc.putObjectInMap(root(), "items", ObjType.LIST)
        doc.insertInList(items, 0, ScalarValue.String("a"))
        doc.insertInList(items, 1, ScalarValue.String("b"))
        val nested = doc.insertObjectInList(items, 2, ObjType.MAP)
        doc.putInMap(nested, "k", ScalarValue.Uint(7u))

        assertEquals(3, doc.length(items))
        val values = doc.values(items)
        assertEquals(Value.Scalar(ScalarValue.String("a")), values[0])
        assertEquals(Value.Scalar(ScalarValue.String("b")), doc.getInList(items, 1))
        assertIsObject(ObjType.MAP, nested, values[2])
        assertEquals(Value.Scalar(ScalarValue.Uint(7u)), doc.getInMap(nested, "k"))

        doc.deleteInList(items, 0)

        assertEquals(2, doc.length(items))
        assertEquals(Value.Scalar(ScalarValue.String("b")), doc.getInList(items, 0))
        assertIsObject(ObjType.MAP, nested, doc.getInList(items, 1))
    }

    @Test
    fun anObjectValue_matchesItsIdByContent() {
        val doc = Doc()
        val list = doc.putObjectInMap(root(), "list", ObjType.LIST)
        val value = doc.getInMap(root(), "list")

        // An id is a ByteArray, so == on a Value holding one compares references, not bytes.
        assertTrue(
            value is Value.Object && value.typ == ObjType.LIST && value.id.contentEquals(list)
        )
        assertHeadsEqual(doc.heads(), doc.heads())
    }

    @Test
    fun aMalformedHash_toEncodeChangesSince_throws_andTheDocKeepsWorking() {
        val (doc, text) = docWithText()

        assertFailsWith<DocException.Internal> { doc.encodeChangesSince(listOf(ByteArray(3))) }

        assertStillWorks(doc, text)
    }

    @Test
    fun aMalformedHash_toForkAt_throws_andTheDocKeepsWorking() {
        val (doc, text) = docWithText()

        assertFailsWith<DocException.Internal> { doc.forkAt(listOf(ByteArray(3))) }

        assertStillWorks(doc, text)
    }

    @Test
    fun aMalformedHash_toACallThatDeclaresNoError_throws_andTheDocKeepsWorking() {
        val (doc, text) = docWithText()

        assertFailsWith<InternalException> { doc.changeByHash(ByteArray(3)) }

        assertStillWorks(doc, text)
    }

    @Test
    fun aMalformedObjId_throws_andTheDocKeepsWorking() {
        val (doc, text) = docWithText()

        assertFailsWith<DocException.Internal> { doc.spliceText(MALFORMED, 0, 0, "x") }
        assertFailsWith<DocException.Internal> { doc.splitBlock(MALFORMED, 0) }
        assertFailsWith<InternalException> { doc.length(MALFORMED) }

        assertStillWorks(doc, text)
    }

    @Test
    fun aMalformedCursor_throws_andTheDocKeepsWorking() {
        val (doc, text) = docWithText()

        assertFailsWith<DocException.Internal> { doc.cursorPosition(text, MALFORMED) }
        assertFailsWith<DocException.Internal> {
            doc.marksAtPosition(text, Position.Cursor(MALFORMED), doc.heads())
        }

        assertStillWorks(doc, text)
    }

    @Test
    fun aNegativeIndex_throws_andTheDocKeepsWorking() {
        val (doc, text) = docWithText()

        assertFailsWith<DocException.NegativeIndex> { doc.spliceText(text, -1, 0, "x") }
        assertFailsWith<DocException.NegativeIndex> { doc.cursor(text, -1) }
        assertFailsWith<DocException.NegativeIndex> {
            doc.mark(text, -1, 2, ExpandMark.NONE, "bold", ScalarValue.Boolean(true))
        }
        assertFailsWith<DocException.NegativeIndex> {
            doc.marksAtPosition(text, Position.Index(-1), doc.heads())
        }

        assertStillWorks(doc, text)
    }

    private fun docWithText(): Pair<Doc, ObjId> {
        val doc = Doc()
        val text = doc.putObjectInMap(root(), "text", ObjType.TEXT)
        doc.spliceText(text, 0, 0, "hello")
        doc.commitWith(null, 0)
        return doc to text
    }

    private fun assertStillWorks(doc: Doc, text: ObjId) {
        assertEquals("hello", doc.text(text))
        doc.spliceText(text, 5, 0, "!")
        assertEquals("hello!", doc.text(text))
    }

    private fun assertIsObject(type: ObjType, id: ObjId, value: Value?) {
        assertTrue(
            value is Value.Object && value.typ == type && value.id.contentEquals(id),
            "$value",
        )
    }

    private fun newDoc(): Doc = Doc.newWithTextEncoding(TextEncoding.UTF16_CODE_UNIT)

    private fun textIn(doc: Doc, key: String): ObjId {
        val value = doc.getInMap(root(), key)
        assertTrue(value is Value.Object && value.typ == ObjType.TEXT, "$key is $value")
        return value.id
    }

    private fun assertHeadsEqual(expected: List<ChangeHash>, actual: List<ChangeHash>) {
        assertEquals(expected.size, actual.size, "head count")
        expected.zip(actual).forEach { (e, a) -> assertTrue(e.contentEquals(a), "heads differ") }
    }

    private companion object {
        /** Too short to hold even a version tag, so no id or cursor can parse from it. */
        val MALFORMED = ByteArray(0)
    }
}
