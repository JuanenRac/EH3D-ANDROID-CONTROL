// EH3D-ANDROID-CONTROL - how honest the app is about its link to the server: connected, data that is getting old, no connection, or an address that answers another service.
// One failure does not make the app say "no connection" (the Wi-Fi of a shop blinks); two in a row do, and the retries slow down while the server is away.
// Pure rules with no clock or screen in them, so they are tested; the iPhone app has the same rules and both read the same test cases.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.model

/** Why a request failed as far as the link goes. */
enum class LinkFailure { Unreachable, WrongServer }

/** What the screen says about the link. */
enum class LinkState { Connected, Stale, Offline, WrongServer }

/** The numbers of the link: how many failures in a row make "no connection", how old the data may be before it is called old, and the waits between retries. */
data class LinkPolicy(val offlineAfter: Int = 2, val staleAfterS: Int = 5, val retryMs: List<Long> = listOf(1_500L, 3_000L, 6_000L, 10_000L)) {
    /** The wait before the next request: the normal pace while all goes well and after one failure, then longer up to the last of [retryMs]. */
    fun retryDelayMs(failures: Int): Long = retryMs[(failures - 1).coerceIn(0, retryMs.size - 1)]
}

/** The link as far as it is known: the failures in a row, when the last good answer came and when the last request was made (milliseconds of any steady clock). */
data class ServerLink(val failures: Int = 0, val lastOkMs: Long? = null, val atMs: Long = 0, val failure: LinkFailure? = null) {
    fun succeeded(nowMs: Long) = ServerLink(0, nowMs, nowMs, null)
    fun failed(why: LinkFailure, nowMs: Long) = ServerLink(failures + 1, lastOkMs, nowMs, why)

    /** How old the last good answer is, in seconds; null when there has been none. */
    fun ageS(): Long? = lastOkMs?.let { ((atMs - it) / 1000).coerceAtLeast(0) }

    fun state(policy: LinkPolicy = LinkPolicy()): LinkState {
        if (failures <= 0) return LinkState.Connected
        if (failure == LinkFailure.WrongServer) return LinkState.WrongServer
        if (failures >= policy.offlineAfter) return LinkState.Offline
        val age = ageS()
        return if (age != null && age >= policy.staleAfterS) LinkState.Stale else LinkState.Connected
    }
}

/**
 * What a refused or lost request says about the link, or null when the server answered as itself (a 404 for a job that is gone, a 403 for a viewer: the link is fine).
 * No answer at all and a 5xx are an unreachable server; an answer that is not the JSON of EH3D-SERVER, or an error without the `error` member of the API, is another service on that address.
 */
fun classifyFailure(status: Int, code: String): LinkFailure? = when {
    status == 0 -> LinkFailure.Unreachable
    code == "bad_json" -> LinkFailure.WrongServer
    status >= 500 -> LinkFailure.Unreachable
    code.startsWith("http_") && status in 400..499 && status != 401 && status != 403 && status != 429 -> LinkFailure.WrongServer
    else -> null
}
