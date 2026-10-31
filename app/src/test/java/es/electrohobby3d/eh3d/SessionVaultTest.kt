package es.electrohobby3d.eh3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryStore : KeyValueStore {
    val values = mutableMapOf<String, String>()
    override fun get(key: String) = values[key]
    override fun put(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

/** A box that only opens what it sealed itself, like the Keystore's. */
private class ReversingBox(private val tag: String = "A") : SecretBox {
    override fun seal(plain: String) = "$tag:" + plain.reversed()
    override fun open(sealed: String): String? = if (sealed.startsWith("$tag:")) sealed.removePrefix("$tag:").reversed() else null
}

class SessionVaultTest {
    private val origin = "http://192.168.0.180:18200"

    @Test fun `keeps the session of the server and gives it back`() {
        val vault = SessionVault(MemoryStore(), ReversingBox())
        vault.save(origin, "token", "abc123")
        assertEquals(StoredSession(origin, "token", "abc123"), vault.load(origin))
    }

    @Test fun `writes nothing readable and never the password`() {
        val store = MemoryStore()
        SessionVault(store, ReversingBox()).save(origin, "token", "abc123")
        assertTrue(store.values.values.none { "abc123" in it })
    }

    @Test fun `a session kept for another server is not given to this one`() {
        val vault = SessionVault(MemoryStore(), ReversingBox())
        vault.save(origin, "token", "abc123")
        assertNull(vault.load("http://10.0.0.5:18200"))
    }

    @Test fun `what another key sealed cannot be read and is dropped`() {
        val store = MemoryStore()
        SessionVault(store, ReversingBox("A")).save(origin, "token", "abc123")
        assertNull(SessionVault(store, ReversingBox("B")).load(origin))
        assertTrue(store.values.isEmpty())
    }

    @Test fun `clear forgets the session and an empty one is not kept`() {
        val store = MemoryStore()
        val vault = SessionVault(store, ReversingBox())
        vault.save(origin, "token", "")
        assertTrue(store.values.isEmpty())
        vault.save(origin, "token", "abc123")
        vault.clear()
        assertNull(vault.load(origin))
    }
}
