// EH3D-ANDROID-CONTROL - keeps the server's session between launches, so the app opens already signed in.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
//
// What is kept is the session cookie the server handed out - never the password, which is typed once and forgotten. The cookie is sealed with a key that lives in the Android
// Keystore (it cannot be taken out of the phone) before it is written. The server renews a session that is in use and ends one that is not, so a phone that is not opened for
// a long time asks for the password again, and so does a session the server has ended (or a different password): the stored cookie is then simply refused and dropped.
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
data class StoredSession(val origin: String, val name: String, val value: String)

class SessionVault(private val store: KeyValueStore, private val box: SecretBox) {
    /** Remembers the session of [origin]; replaces whatever was kept. */
    fun save(origin: String, name: String, value: String) {
        if (name.isBlank() || value.isBlank()) return
        store.put(KEY, box.seal("${origin}\n${name}\n${value}"))
    }

    /** The session kept for [origin], or null when there is none, it is for another server, or it cannot be read. */
    fun load(origin: String): StoredSession? {
        val sealed = store.get(KEY) ?: return null
        if (!box.usable()) return null   // the phone is locked and the key wants it unlocked: what is kept stays kept
        val parts = box.open(sealed)?.split("\n", limit = 3) ?: run { store.remove(KEY); return null }
        if (parts.size != 3 || parts[0] != origin) return null
        return StoredSession(parts[0], parts[1], parts[2])
    }

    /** True when a session is kept, whether or not it can be read now. */
    fun has(): Boolean = store.get(KEY) != null

    fun clear() { store.remove(KEY) }

    companion object { const val KEY = "session" }
}
