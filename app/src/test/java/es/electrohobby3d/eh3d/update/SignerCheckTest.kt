package es.electrohobby3d.eh3d.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignerCheckTest {
    @Test fun aCertificateHasAStableSha256InLowercaseHexadecimal() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", SignerCheck.digest("abc".toByteArray()))
        assertEquals(64, SignerCheck.digest(ByteArray(0)).length)
    }

    @Test fun anotherKeyIsAMismatch() {
        assertTrue(SignerCheck.mismatch(setOf("aa"), setOf("bb")))
        assertTrue(SignerCheck.mismatch(setOf("aa", "cc"), setOf("bb")))
    }

    @Test fun theSameKeyOrARotatedLineageIsNot() {
        assertFalse(SignerCheck.mismatch(setOf("aa"), setOf("aa")))
        assertFalse(SignerCheck.mismatch(setOf("aa"), setOf("aa", "bb")))   // the new package was rotated from the key installed
        assertFalse(SignerCheck.mismatch(setOf("aa", "bb"), setOf("bb")))   // the installed one was rotated to the key of the package
    }

    @Test fun whatCannotBeReadIsLeftToAndroid() {
        assertFalse(SignerCheck.mismatch(emptySet(), setOf("bb")))
        assertFalse(SignerCheck.mismatch(setOf("aa"), emptySet()))
        assertFalse(SignerCheck.mismatch(emptySet(), emptySet()))
    }
}
