// EH3D-ANDROID-CONTROL - how the app talks to the server: the settings of the connection (time-out, retries, mDNS) and the rules of the client of the suite that the app follows, as plain logic with no
// clock or network in it so it is tested: the pauses between tries, which requests may be sent again, when a login is renewed, and the sessions that the server lists.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * The three settings of the connection that a phone can change. The ranges and the defaults are the ones of the client of the suite (`server_timeout_s`, `server_retries`, `server_mdns`);
 * a value out of its range is brought to the nearest end. The search for the server on the local network (mDNS) is off by default.
 */
data class ConnectionSettings(val timeoutS: Int = DEFAULT_TIMEOUT_S, val retries: Int = DEFAULT_RETRIES, val mdns: Boolean = false) {
    companion object {
        const val KEY_TIMEOUT = "server_timeout_s"
        const val KEY_RETRIES = "server_retries"
        const val KEY_MDNS = "server_mdns"
        const val DEFAULT_TIMEOUT_S = 15
        const val DEFAULT_RETRIES = 2
        val TIMEOUT_RANGE = 1..120
        val RETRIES_RANGE = 0..5

        /** The saved settings, read with the two readers of a preferences file; what is not saved is the default, and what is out of its range is brought into it. */
        fun read(int: (String, Int) -> Int, bool: (String, Boolean) -> Boolean) =
            of(int(KEY_TIMEOUT, DEFAULT_TIMEOUT_S), int(KEY_RETRIES, DEFAULT_RETRIES), bool(KEY_MDNS, false))

        /** The settings with every value inside its range. */
        fun of(timeoutS: Int, retries: Int, mdns: Boolean) =
            ConnectionSettings(timeoutS.coerceIn(TIMEOUT_RANGE), retries.coerceIn(RETRIES_RANGE), mdns)
    }

    fun withTimeout(value: Int) = of(value, retries, mdns)
    fun withRetries(value: Int) = of(timeoutS, value, mdns)
    fun withMdns(value: Boolean) = of(timeoutS, retries, value)
}

/** The rules for sending a request again after a failure. */
object RetryRules {
    private val REPEATABLE = setOf("GET", "HEAD", "PUT", "DELETE", "OPTIONS")

    /** The pause before the retry number [retry] (0 is the first retry): 200 ms, 400 ms, 800 ms... up to 2 s. */
    fun delayMs(retry: Int): Long = (200L shl retry.coerceIn(0, 4)).coerceAtMost(2_000L)

    /**
     * True when a request that failed may be sent again. [status] is 0 when there was no answer; [neverSent] is true when the connection could not even be made (nothing went out, so any method
     * is safe). A time-out or a broken connection, and a 502, 503 or 504, are tried again only for the requests that do no harm twice: a POST (a job, a login) is never sent twice.
     */
    fun mayRepeat(method: String, status: Int, neverSent: Boolean): Boolean {
        val safe = method.uppercase() in REPEATABLE
        return when {
            status == 0 -> neverSent || safe
            status == 502 || status == 503 || status == 504 -> safe
            else -> false
        }
    }
}

/** When a login is renewed: in the last quarter of its life, by itself, before the next request. */
object RefreshRule {
    /** [totalS] is how long a login lasts, [leftS] what is left; a login of unknown life, or one that is over, is not renewed here. */
    fun due(totalS: Long, leftS: Long): Boolean = totalS > 0 && leftS > 0 && leftS <= totalS / 4
}

/** One open login of the caller, as `GET /auth/sessions` lists it (the id is not the token). */
data class AuthSession(val id: String, val name: String, val role: String, val app: String, val ip: String, val current: Boolean)

/** The sessions of the answer `{"sessions": [...]}`; an item without an id is left out, and anything that is not a list is an empty list. */
fun parseSessions(text: String): List<AuthSession> {
    val list = JSONObject(text).optJSONArray("sessions") ?: JSONArray()
    return (0 until list.length()).mapNotNull { list.optJSONObject(it) }.filter { it.str("id").isNotEmpty() }
        .map { AuthSession(it.str("id"), it.str("name"), it.str("role", "viewer"), it.str("app"), it.str("ip"), it.optBoolean("current", false)) }
}

/**
 * A machine of a plugin of the server (`GET /work/machines`) with what the plugin says of it in `detail`, flattened to lines of text. [latched] is true when an interlock holds it ([interlocks]);
 * [simulatorOnly] says it has only run against a simulator; [job] is the id of the job that runs on it, or empty; [autoStart] says its queue starts jobs by itself (a warning, never set from the phone);
 * [limits] and [heaters] are the limits of temperature and the heaters of a 3D printer.
 */
data class WorkMachine(
    val id: String, val name: String, val plugin: String, val state: String, val latched: Boolean, val detail: List<Pair<String, String>>,
    val job: String = "", val simulatorOnly: Boolean = false, val interlocks: List<Interlock> = emptyList(), val autoStart: Boolean = false,
    val limits: TempLimits? = null, val heaters: List<Heater> = emptyList(),
)

/** The machines of the answer `{"machines": [...]}` that are not of the `bungard` plugin (those are the fleet the app already shows). Nested objects are shown as `a.b`, lists by their size. */
fun parseWorkMachines(text: String): List<WorkMachine> {
    val list = JSONObject(text).optJSONArray("machines") ?: JSONArray()
    return (0 until list.length()).mapNotNull { list.optJSONObject(it) }.filter { it.str("id").isNotEmpty() && it.str("plugin") != "bungard" }.map { m ->
        val detail = m.optJSONObject("detail")
        val lines = ArrayList<Pair<String, String>>()
        if (detail != null) flatten("", detail, lines, 0)
        val held = MachineSafety.interlocks(detail)
        WorkMachine(
            m.str("id"), m.str("name", m.str("id")), m.str("plugin"), m.str("state", "idle"), held.isNotEmpty(), lines,
            job = m.str("job"), simulatorOnly = MachineSafety.simulatorOnly(detail, m.optJSONObject("capabilities")), interlocks = held,
            autoStart = MachineSafety.startsByItself(detail), limits = MachineSafety.limits(detail), heaters = MachineSafety.heaters(detail),
        )
    }
}

/**
 * A feeder or head node of a pick-and-place machine as `GET /pnp/nodes` lists it. The phone only reads them (EH3D-STUDIO registers, changes and removes them). [ready] is the verdict of the server:
 * only the healths `ok` and `warn` are ready and every other one is not, so an answer that does not say is not ready. The token of a node never comes in the answer (the server sends `********`) and is
 * not read here. [facts] are the few numbers the node said last, as lines of text. [slow] is the warning of the server when it asks the node too slowly to keep it out of its safe state.
 */
data class PnpNode(
    val id: String, val kind: String, val name: String, val address: String, val machine: String, val enabled: Boolean,
    val health: String, val ready: Boolean, val lastError: String, val simulatorOnly: Boolean, val facts: List<Pair<String, String>>, val slow: PnpSlow? = null,
)

/** The server asks a node every [pollS] seconds and the node goes to its safe state after [linkLostMs] without being asked; [declared] is false when that time is the default of its contract. */
data class PnpSlow(val pollS: Long, val linkLostMs: Long, val declared: Boolean)

/** The warnings `poll_too_slow` of the answer, by node; a warning of another kind or without its numbers is left out. */
private fun slowWarnings(root: JSONObject): Map<String, PnpSlow> {
    val list = root.optJSONArray("warnings") ?: return emptyMap()
    return (0 until list.length()).mapNotNull { list.optJSONObject(it) }.mapNotNull { w ->
        val poll = w.opt("poll_s") as? Number
        val lost = w.opt("link_lost_ms") as? Number
        if (w.str("code") != "poll_too_slow" || w.str("node").isEmpty() || poll == null || lost == null) null
        else w.str("node") to PnpSlow(poll.toLong(), lost.toLong(), w.optBoolean("link_lost_declared", false))
    }.toMap()
}

/** The keys of what a node said last that the phone lists, in this order. */
private val PNP_FACT_KEYS = listOf("safe", "selfcheck", "online", "slots", "low", "empty", "faulted", "nozzle_count", "pump")

/** The nodes of the answer `{"nodes": [...]}`; an item without an id is left out, and anything that is not a list is an empty list. */
fun parsePnpNodes(text: String): List<PnpNode> {
    val root = JSONObject(text)
    val list = root.optJSONArray("nodes") ?: JSONArray()
    val slow = slowWarnings(root)
    return (0 until list.length()).mapNotNull { list.optJSONObject(it) }.filter { it.str("id").isNotEmpty() }.map { n ->
        val status = n.optJSONObject("status")
        val facts = ArrayList<Pair<String, String>>()
        if (status != null) for (key in PNP_FACT_KEYS) if (status.has(key) && !status.isNull(key)) facts.add(key to status.opt(key).toString())
        PnpNode(
            n.str("id"), n.str("kind"), n.str("name", n.str("id")), n.str("address"), n.str("machine"), n.optBoolean("enabled", true),
            n.str("health", "unknown"), n.optBoolean("ready", false), n.str("last_error"), n.str("evidence") == "simulator" || n.optBoolean("simulator_only", false), facts, slow[n.str("id")],
        )
    }
}

private fun flatten(prefix: String, json: JSONObject, out: MutableList<Pair<String, String>>, depth: Int) {
    val keys = json.keys().asSequence().sorted().toList()
    for (key in keys) {
        if (out.size >= 24) return
        val name = if (prefix.isEmpty()) key else "$prefix.$key"
        when (val value = json.opt(key)) {
            null, JSONObject.NULL -> Unit
            is JSONObject -> if (depth < 2) flatten(name, value, out, depth + 1) else out.add(name to "{…}")
            is JSONArray -> out.add(name to "[${value.length()}]")
            else -> out.add(name to value.toString())
        }
    }
}

/** A server found on the local network: shown to the person, who chooses; nothing connects by itself. */
data class Candidate(val host: String, val port: Int) {
    val label: String get() = "$host:$port"
}

/** The candidates found so far: no repeats, and no more than [MAX] (any device of the network can answer, so the list is short). */
fun addCandidate(found: List<Candidate>, host: String?, port: Int): List<Candidate> {
    val clean = host?.trim()?.trimEnd('.').orEmpty()
    if (clean.isEmpty() || port !in 1..65535 || clean.any { it.isWhitespace() || it == '/' }) return found
    val item = Candidate(clean, port)
    return if (item in found || found.size >= MAX_CANDIDATES) found else found + item
}

const val MAX_CANDIDATES = 8

/** The service type the server announces. */
const val SERVICE_TYPE = "_eh3d._tcp."
