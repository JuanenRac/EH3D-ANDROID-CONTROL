// EH3D-ANDROID-CONTROL - who signed an APK, compared before Android's installer is opened.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.update

import java.security.MessageDigest

/**
 * Android's package installer refuses an update signed by another key than the installed app, but only after the person has gone through the permission screens and the installer
 * itself, with a message that says little. Comparing the certificates first lets the app say it plainly. The sets hold the SHA-256 of every certificate in the signing history of each
 * package, so an app whose key was rotated still matches its older self.
 */
object SignerCheck {
    /** The SHA-256 of a certificate, in lowercase hexadecimal. */
    fun digest(certificate: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(certificate).joinToString("") { "%02x".format(it) }

    /** True only when both sides are known and share no certificate; when either is unknown the decision is left to Android, which makes the same check. */
    fun mismatch(installed: Set<String>, candidate: Set<String>): Boolean = installed.isNotEmpty() && candidate.isNotEmpty() && installed.intersect(candidate).isEmpty()
}
