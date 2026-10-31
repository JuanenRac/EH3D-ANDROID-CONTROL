package es.electrohobby3d.eh3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class CredentialMemoryStore : KeyValueStore {
    val values = mutableMapOf<String, String>()
    override fun get(key: String) = values[key]
    override fun put(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

/** A box that only opens what it sealed itself, like the Keystore's. */
private class CredentialBox(private val tag: String = "A") : SecretBox {
    override fun seal(plain: String) = "$tag:" + plain.reversed()
    override fun open(sealed: String): String? = if (sealed.startsWith("$tag:")) sealed.removePrefix("$tag:").reversed() else null
}

class CredentialVaultTest {
    private val origin = "http://192.168.0.180:18080"

    @Test fun `keeps the user and the password of the server and gives them back`() {
        val vault = CredentialVault(CredentialMemoryStore(), CredentialBox())
        vault.save(origin, "ana", "s3cret pass")
        assertEquals(StoredCredentials(origin, "ana", "s3cret pass"), vault.load(origin))
        assertTrue(vault.has())
    }

    @Test fun `a password with line breaks or spaces comes back whole`() {
        val vault = CredentialVault(CredentialMemoryStore(), CredentialBox())
        vault.save(origin, "ana", "a\nb c")
        assertEquals("a\nb c", vault.load(origin)?.password)
    }

    @Test fun `writes nothing readable`() {
        val store = CredentialMemoryStore()
        CredentialVault(store, CredentialBox("X")).save(origin, "carlos", "s3cret")
        assertTrue(store.values.values.none { "s3cret" in it || "carlos" in it })
    }

    @Test fun `what is kept for another server is not given to this one`() {
        val vault = CredentialVault(CredentialMemoryStore(), CredentialBox())
        vault.save(origin, "ana", "s3cret")
        assertNull(vault.load("http://10.0.0.5:18080"))
    }

    @Test fun `what another key sealed cannot be read and is dropped`() {
        val store = CredentialMemoryStore()
        CredentialVault(store, CredentialBox("A")).save(origin, "ana", "s3cret")
        assertNull(CredentialVault(store, CredentialBox("B")).load(origin))
        assertTrue(store.values.isEmpty())
    }

    @Test fun `clear forgets everything and an empty sign-in is not kept`() {
        val store = CredentialMemoryStore()
        val vault = CredentialVault(store, CredentialBox())
        vault.save(origin, "ana", "s3cret")
        vault.clear()
        assertFalse(vault.has())
        assertNull(vault.load(origin))
        vault.save(origin, "", "s3cret")
        vault.save(origin, "ana", "")
        vault.save("", "ana", "s3cret")
        assertTrue(store.values.isEmpty())
    }

    @Test fun `a user that holds a line break is not kept because it could not come back the same`() {
        val store = CredentialMemoryStore()
        CredentialVault(store, CredentialBox()).save(origin, "an\na", "s3cret")
        assertTrue(store.values.isEmpty())
    }
}
