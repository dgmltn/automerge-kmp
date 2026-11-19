package com.dgmltn.automerge.sample

import app.azula.iroh.ed25519Verify
import com.dgmltn.automerge.Doc
import com.dgmltn.automerge.TextEncoding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Both Rust libraries in one binary: their symbols must not collide when the test links. */
class LinkTest {
    @Test
    fun linksBesideIrohKmp() {
        Doc.newWithTextEncoding(TextEncoding.UTF16_CODE_UNIT).use { doc ->
            assertEquals(emptyList(), doc.heads())
        }
        // Calls into iroh-kmp's library without an endpoint or the network.
        assertFalse(ed25519Verify(ByteArray(32), ByteArray(0), ByteArray(64)))
    }
}
