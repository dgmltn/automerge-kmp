package com.dgmltn.automerge

import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun aTextSplicedIn_readsBack() {
        val doc = Doc.newWithTextEncoding(TextEncoding.UTF16_CODE_UNIT)
        val body = doc.putObjectInMap(root(), "body", ObjType.TEXT)
        doc.spliceText(body, 0u, 0, "hello")
        doc.commitWith(null, 0)

        assertEquals("hello", doc.text(body))

        val saved: ByteArray = doc.save()
        val loaded = Doc.load(saved)
        assertEquals("hello", loaded.text(body))
    }
}
