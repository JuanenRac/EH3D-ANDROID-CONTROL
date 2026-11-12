// EH3D-ANDROID-CONTROL - keeps the server's session between launches, so the app opens already signed in.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
//
// What is kept is the session token the server handed out - never the user or the password, which are typed each time. The token is kept only when the person ticked "remember me"
// (off by default), sealed with a key that lives in the Android Keystore (it cannot be taken out of the phone) before it is written, and it carries its own expiry (30 days from
// the sign-in): an expired one is dropped without being used. The server renews a session that is in use and ends one that is not, and signing out ends the session at the server (it
// is revoked) and forgets the token here, so a token that is refused is simply dropped. The user and the password that an earlier version could keep are deleted by `purgeLegacy`.
package es.electrohobby3d.eh3d

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Seals and opens a text; what is sealed can only be opened by the same box. */
interface SecretBox {
    fun seal(plain: String): String
    /** The text, or null when it was not sealed by this box (a new phone, a restored backup, tampering). */
    fun open(sealed: String): String?
    /**
     * False while this box cannot open anything for a reason that will pass (a key tied to the unlocking of the phone, and the phone is locked): what is kept is then left alone,
     * not taken for unreadable and dropped.
     */
    fun usable(): Boolean = true
}

/** The few places the vault writes. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

class PreferencesStore(private val preferences: SharedPreferences) : KeyValueStore {
    override fun get(key: String): String? = preferences.getString(key, null)
    override fun put(key: String, value: String) { preferences.edit().putString(key, value).apply() }
    override fun remove(key: String) { preferences.edit().remove(key).apply() }
}

/**
 * AES-GCM with a key made inside the Android Keystore the first time it is needed. With [bindToUnlock] (an option of the person, off by default) the key is also one that Android lets
 * be used only while the phone is unlocked (from Android 9), so a stolen phone that is switched on but locked gives nothing; the price is that nothing sealed with it can be read in the
 * background until the phone is unlocked, which [deviceLocked] says.
 */
class KeystoreBox(private val alias: String = "eh3d-session", private val bindToUnlock: Boolean = false, private val deviceLocked: () -> Boolean = { false }) : SecretBox {
    companion object {
        /** Forgets a key (the one of the other way of keeping the secrets, once they have been sealed again with the new one). */
        fun deleteKey(alias: String) { runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) } }
    }

    override fun usable(): Boolean = !(bindToUnlock && deviceLocked())

    @Synchronized private fun key(): SecretKey {   // the app and its service may ask for it at the same moment, the first time
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256)
        if (bindToUnlock && android.os.Build.VERSION.SDK_INT >= 28) spec.setUnlockedDeviceRequired(true)
        generator.init(spec.build())
        return generator.generateKey()
    }

    override fun seal(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + sealed, Base64.NO_WRAP)
    }

    override fun open(sealed: String): String? = runCatching {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12)) }
        String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
    }.getOrNull()
}

/** The session of one server: which cookie, and its value. */
data class StoredSession(val origin: String, val name: String, val value: String, val expiresAtMs: Long)

class SessionVault(private val store: KeyValueStore, private val box: SecretBox, private val ttlMs: Long = DEFAULT_TTL_MS) {
    /** Remembers the session of [origin] until [ttlMs] after [nowMs]; replaces whatever was kept. */
    fun save(origin: String, name: String, value: String, nowMs: Long = System.currentTimeMillis()) {
        if (name.isBlank() || value.isBlank() || '\n' in origin || '\n' in name || '\n' in value) return
        store.put(KEY, box.seal("${origin}\n${name}\n${value}\n${nowMs + ttlMs}"))
    }

    /**
     * The session kept for [origin], or null when there is none, it is for another server, it cannot be read, or it has expired (an expired one, one made before the expiry existed
     * and one that is damaged are dropped).
     */
    fun load(origin: String, nowMs: Long = System.currentTimeMillis()): StoredSession? {
        val sealed = store.get(KEY) ?: return null
        if (!box.usable()) return null   // the phone is locked and the key wants it unlocked: what is kept stays kept
        val parts = box.open(sealed)?.split("\n", limit = 4) ?: run { store.remove(KEY); return null }
        val expires = parts.getOrNull(3)?.toLongOrNull()
        if (parts.size != 4 || expires == null || nowMs >= expires) { store.remove(KEY); return null }
        if (parts[0] != origin) return null
        return StoredSession(parts[0], parts[1], parts[2], expires)
    }

    /** True when a session is kept, whether or not it can be read now. */
    fun has(): Boolean = store.get(KEY) != null

    fun clear() { store.remove(KEY) }

    companion object {
        const val KEY = "session"
        /** How long a remembered session is kept: 30 days. */
        const val DEFAULT_TTL_MS: Long = 30L * 24 * 60 * 60 * 1000
        /** The key under which an earlier version kept the user and the password of "remember me". */
        const val LEGACY_CREDENTIALS_KEY = "credentials"

        /** Deletes what the earlier "remember me" kept (the user and the password); true when there was something. */
        fun purgeLegacy(store: KeyValueStore): Boolean {
            val had = store.get(LEGACY_CREDENTIALS_KEY) != null
            store.remove(LEGACY_CREDENTIALS_KEY)
            return had
        }
    }
}
