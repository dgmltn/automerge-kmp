package com.dgmltn.automerge

import kotlin.test.Test

/** The README's examples, kept verbatim so they can't drift from the API. */
class ReadmeExampleTest {
    @Test
    fun merge() {
        Doc().use { doc1 ->
            val text = doc1.putObjectInMap(root(), "text", ObjType.TEXT)
            doc1.spliceText(text, 0, 0, "Hello world")

            // Another device loads its own copy from the saved bytes.
            Doc.load(doc1.save()).use { doc2 ->
                // Both copies are edited apart, at the same place.
                doc2.spliceText(text, 5, 0, " beautiful")
                doc1.spliceText(text, 5, 0, " there")

                // Each merges the other's edits, and both end up with the same text.
                doc1.merge(doc2)
                doc2.merge(doc1)
                check(doc1.text(text) == doc2.text(text))

                // "Hello there beautiful world" or "Hello beautiful there world": the order of
                // the two inserts depends on the actor ids, but every copy picks the same one.
                println(doc1.text(text))
            }
        }
    }

    @Test
    fun utf16() {
        Doc().use { doc ->
            val text = doc.putObjectInMap(root(), "text", ObjType.TEXT)
            doc.spliceText(text, 0, 0, "a😀b")
            // The emoji is two UTF-16 code units, so "b" is at index 3, as in a Kotlin String.
            doc.spliceText(text, 3, 1, "c")
            check(doc.text(text) == "a😀c")
        }
    }
}
