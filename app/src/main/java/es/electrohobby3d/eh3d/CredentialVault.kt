// EH3D-ANDROID-CONTROL - the optional "remember me" of the sign-in: the user and the password, kept only when the person asked for it.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
//
// Off by default. When it is on, the user and the password are sealed with a key of their own that lives in the Android Keystore (it cannot be taken out of the phone) and are
// kept for ONE server; the sign-in fills them in again when the session has ended. Turning the box off forgets them at once, and they are only kept after the server accepted the
// sign-in. Nothing is ever sent anywhere but to the server that was typed, and the password is never written without being sealed.
package es.electrohobby3d.eh3d

/** What "remember me" keeps: the server, the user and the password. */
data class StoredCredentials(val origin: String, val username: String, val password: String)

class CredentialVault(private val store: KeyValueStore, private val box: SecretBox) {
    /** Remembers the sign-in of [origin]; replaces whatever was kept. A blank user or password keeps nothing. */
    fun save(origin: String, username: String, password: String) {
        if (origin.isBlank() || username.isBlank() || password.isBlank()) return
        if ('\n' in username || '\n' in origin) return   // the fields are joined by line breaks: a user with one cannot be kept faithfully
        store.put(KEY, box.seal("$origin\n$username\n$password"))
    }

    /** The sign-in kept for [origin], or null when there is none, it is for another server, or it cannot be read (then it is dropped). */
    fun load(origin: String): StoredCredentials? {
        val sealed = store.get(KEY) ?: return null
        if (!box.usable()) return null   // the phone is locked and the key wants it unlocked: what is kept stays kept
        val parts = box.open(sealed)?.split("\n", limit = 3) ?: run { store.remove(KEY); return null }
        if (parts.size != 3 || parts[0] != origin) return null
        return StoredCredentials(parts[0], parts[1], parts[2])
    }

    /** True when something is kept, whichever server it is for. */
    fun has(): Boolean = store.get(KEY) != null

    fun clear() { store.remove(KEY) }

    companion object { const val KEY = "credentials" }
}
