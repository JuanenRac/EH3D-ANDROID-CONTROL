// EH3D-ANDROID-CONTROL - the rules of the orders that set a machine going: sent once, never from old data, and an order with no answer is not told as one that did not happen.
// Pure rules with no clock or screen in them, so they are tested; the iPhone app has the same rules.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.model

/**
 * One order at a time under each key: while it is on its way, and for [holdMs] after the server took it, the same order is not sent again (the second touch of a double touch must not answer the next
 * question of the machine). A refused or lost one is free at once. Used from the main thread only.
 */
class OrderGate(private val clock: () -> Long, private val holdMs: Long = 1_000) {
    private val until = HashMap<String, Long>()

    /** True when the order may be sent now; the key is then taken until [done]. */
    fun take(key: String): Boolean {
        val held = until[key]
        if (held != null && clock() < held) return false
        until[key] = Long.MAX_VALUE
        return true
    }

    fun done(key: String, taken: Boolean) { if (taken) until[key] = clock() + holdMs else until.remove(key) }
}

/** True when nothing says whether the order got to the server: it went out and no answer came, or a gateway lost it. [neverSent]: the connection could not even be made. */
fun mayHaveArrived(status: Int, neverSent: Boolean): Boolean = (status == 0 && !neverSent) || status == 502 || status == 504

/** Whether a machine may be set going (started, confirmed, let go): an operator, and only while what is on the screen is what the server says now. A stop never asks this. */
fun mayOrder(operator: Boolean, link: LinkState): Boolean = operator && link == LinkState.Connected

/** The refusals whose detail names the line and the reason: the sentence of the code alone does not say where to look. */
fun keepsDetail(code: String): Boolean = code == "bad_gcode"

/** The sentence of a refusal with what the server added (the line and the reason of a refused program), when it added something. */
fun withDetail(words: String, code: String, detail: String?): String = detail?.trim()?.takeIf { it.isNotEmpty() && it != code }?.let { "$words ($it)" } ?: words
