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
        vault.save(origin, "token", "abc123", nowMs = 1_000)
        assertEquals(StoredSession(origin, "token", "abc123", 1_000 + SessionVault.DEFAULT_TTL_MS), vault.load(origin, nowMs = 2_000))
    }

    @Test fun `a session expires 30 days after the sign-in and is dropped`() {
        val store = MemoryStore()
        val vault = SessionVault(store, ReversingBox())
        vault.save(origin, "token", "abc123", nowMs = 0)
        assertEquals("abc123", vault.load(origin, nowMs = SessionVault.DEFAULT_TTL_MS - 1)?.value)
        assertNull(vault.load(origin, nowMs = SessionVault.DEFAULT_TTL_MS))
        assertTrue(store.values.isEmpty())   // the expired one is forgotten, not kept
    }

    @Test fun `a session kept before the expiry existed is dropped and asks for the sign-in again`() {
        val store = MemoryStore()
        store.put(SessionVault.KEY, ReversingBox().seal("$origin\ntoken\nabc123"))
        assertNull(SessionVault(store, ReversingBox()).load(origin, nowMs = 5))
        assertTrue(store.values.isEmpty())
    }

    @Test fun `the user and the password that an earlier version kept are deleted`() {
        val store = MemoryStore()
        store.put(SessionVault.LEGACY_CREDENTIALS_KEY, ReversingBox().seal("$origin\nana\nsecret pass"))
        store.put("origin", origin)
        assertTrue(SessionVault.purgeLegacy(store))
        assertNull(store.get(SessionVault.LEGACY_CREDENTIALS_KEY))
        assertEquals(origin, store.get("origin"))   // what is not a secret stays
        assertTrue(!SessionVault.purgeLegacy(store))
    }

    @Test fun `a value with a line break is not kept because it could not come back whole`() {
        val store = MemoryStore()
        SessionVault(store, ReversingBox()).save(origin, "token", "a\nb")
        assertTrue(store.values.isEmpty())
    }

    @Test fun `writes nothing readable and never the password`() {
        val store = MemoryStore()
        SessionVault(store, ReversingBox()).save(origin, "token", "abc123")
        assertTrue(store.values.values.none { "abc123" in it })
    }

    @Test fun `a session kept for another server is not given to this one`() {
        val vault = SessionVault(MemoryStore(), ReversingBox())
        vault.save(origin, "token", "abc123", nowMs = 0)
        assertNull(vault.load("http://10.0.0.5:18200", nowMs = 1))
    }

    @Test fun `what another key sealed cannot be read and is dropped`() {
        val store = MemoryStore()
        SessionVault(store, ReversingBox("A")).save(origin, "token", "abc123", nowMs = 0)
        assertNull(SessionVault(store, ReversingBox("B")).load(origin, nowMs = 1))
        assertTrue(store.values.isEmpty())
    }

    @Test fun `clear forgets the session and an empty one is not kept`() {
        val store = MemoryStore()
        val vault = SessionVault(store, ReversingBox())
        vault.save(origin, "token", "")
        assertTrue(store.values.isEmpty())
        vault.save(origin, "token", "abc123", nowMs = 0)
        vault.clear()
        assertNull(vault.load(origin, nowMs = 1))
    }
}
