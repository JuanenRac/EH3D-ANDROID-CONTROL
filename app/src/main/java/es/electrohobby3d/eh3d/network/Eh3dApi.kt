// EH3D-ANDROID-CONTROL - the client of the API of EH3D-SERVER (docs/API.md of that project): plain java.net, JSON by hand, one request at a time on the IO dispatcher.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.network

import es.electrohobby3d.eh3d.BuildConfig
import es.electrohobby3d.eh3d.model.AuthSession
import es.electrohobby3d.eh3d.model.ConnectionSettings
import es.electrohobby3d.eh3d.model.Fleet
import es.electrohobby3d.eh3d.model.HeadStatus
import es.electrohobby3d.eh3d.model.LIGHT_MAX_BYTES
import es.electrohobby3d.eh3d.model.LIGHT_PATH
import es.electrohobby3d.eh3d.model.LightReply
import es.electrohobby3d.eh3d.model.Quality
import es.electrohobby3d.eh3d.model.RingLight
import es.electrohobby3d.eh3d.model.SpindleInfo
import es.electrohobby3d.eh3d.model.parseSpindle
import es.electrohobby3d.eh3d.model.parseQuality
import es.electrohobby3d.eh3d.model.parseRingLight
import es.electrohobby3d.eh3d.model.Job
import es.electrohobby3d.eh3d.model.JobSummary
import es.electrohobby3d.eh3d.model.Measure
import es.electrohobby3d.eh3d.model.parseAllowDollar
import es.electrohobby3d.eh3d.model.parseFleet
import es.electrohobby3d.eh3d.model.parseHead
import es.electrohobby3d.eh3d.model.parseJob
import es.electrohobby3d.eh3d.model.parseJobs
import es.electrohobby3d.eh3d.model.parseMeasure
import es.electrohobby3d.eh3d.model.JobView
import es.electrohobby3d.eh3d.model.RefreshRule
import es.electrohobby3d.eh3d.model.RetryRules
import es.electrohobby3d.eh3d.model.MachineSafety
import es.electrohobby3d.eh3d.model.PnpNode
import es.electrohobby3d.eh3d.model.PolicySwitch
import es.electrohobby3d.eh3d.model.WorkJob
import es.electrohobby3d.eh3d.model.WorkMachine
import es.electrohobby3d.eh3d.model.parsePnpNodes
import es.electrohobby3d.eh3d.model.parsePolicySwitches
import es.electrohobby3d.eh3d.model.parseWorkJobs
import es.electrohobby3d.eh3d.model.parsePreview
import es.electrohobby3d.eh3d.model.parseSessions
import es.electrohobby3d.eh3d.model.parseWorkMachines
import es.electrohobby3d.eh3d.model.readCapped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The application this app says it logs in for (`POST /auth/login`): the server keeps it with the login and in the audit. */
const val LOGIN_APP = "android"

/**
 * The server refused, or could not be reached: [status] is the HTTP status (0 when there was no answer) and [code] the `error` code of the server, if it sent one.
 * [neverSent] is true when the connection could not even be made, so nothing of the request went out.
 */
class ApiException(val status: Int, val code: String, message: String, val neverSent: Boolean = false) : IOException(message) {
    val unauthorized get() = status == 401

    /** The server knows who it is but the role does not allow it: the login is kept. */
    val forbidden get() = status == 403

    /** At start-up the kept session is deleted only when the server itself refused it (401); a server out of reach, a 5xx or a page of another service keeps it. */
    val endsKeptSession get() = unauthorized
}

/** Who is signed in: the token (a login's or a program's), the name and the role (`viewer`, `operator` or `admin`); the id of the login and the seconds it lasts, when the server says them. */
data class Login(val token: String, val name: String, val role: String, val sessionId: String = "", val expiresS: Long = 0)

/** What a role may do: an operator also plans, starts, confirms and cancels jobs; a viewer only watches. */
object Roles {
    fun canOperate(role: String) = role == "operator" || role == "admin"
    /** Only an administrator sets the spindle of a machine. */
    fun canAdmin(role: String) = role == "admin"
}

/** True when `text` is one JSON value (an object or an array): what the API always answers with. */
fun isJson(text: String): Boolean = runCatching { val v = org.json.JSONTokener(text).nextValue(); v is JSONObject || v is org.json.JSONArray }.getOrDefault(false)

/** [url] with [ticket] as its query. */
fun ticketUrl(url: String, ticket: String): String = url + (if (url.contains('?')) "&" else "?") + "ticket=" + URLEncoder.encode(ticket, "UTF-8")

/** The pure part of the error of the server: its code and its detail, or what the status says. */
fun describeError(status: Int, body: String): Pair<String, String> {
    val json = runCatching { JSONObject(body) }.getOrNull()
    val code = json?.optString("error").orEmpty().ifBlank { "http_$status" }
    val detail = json?.optString("detail").orEmpty()
    return code to detail
}

/**
 * The client of the API. It follows the client of the suite (docs/SERVER_CLIENT.md of EH3D-COMMON): every try has [settings] `timeoutS` seconds; after a failure on the network it waits 200 ms, 400 ms... and tries
 * again up to `retries` more times, and a POST is never sent twice (only a connection that could not be made is tried again for any method); a login that is in the last quarter of its life is renewed by itself
 * before the next request; a 401 makes it forget the token. [pause] and [clockMs] are the waiting and the clock, so the tests do not wait.
 */
open class Eh3dApi(
    private val origin: String, var token: String = "", var settings: ConnectionSettings = ConnectionSettings(),
    private val pause: suspend (Long) -> Unit = { delay(it) }, private val clockMs: () -> Long = System::currentTimeMillis,
) {
    /** How long the login lasts and when it ends (milliseconds of [clockMs]); 0 while the app does not know. */
    @Volatile private var lifeTotalS = 0L
    @Volatile private var expiresAtMs = 0L
    @Volatile private var renewing = false

    /** Whether a request renews a login that is in the last quarter of its life; the tests turn it off. */
    var autoRefresh = true

    /** Takes the life of a login the server has just told (`expires_s`), counted from now. */
    private fun adoptLife(expiresS: Long) {
        if (expiresS <= 0) return
        if (lifeTotalS <= 0 || expiresS > lifeTotalS) lifeTotalS = expiresS
        expiresAtMs = clockMs() + expiresS * 1000
    }

    private fun forgetToken() { token = ""; lifeTotalS = 0; expiresAtMs = 0 }

    private fun configure(connection: HttpURLConnection, method: String, body: String?) {
        connection.requestMethod = method
        connection.connectTimeout = settings.timeoutS * 1000
        connection.readTimeout = settings.timeoutS * 1000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "EH3D-ANDROID-CONTROL/${BuildConfig.VERSION_NAME}")
        if (token.isNotEmpty()) connection.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
        }
    }

    /** One try: the connection is made first, so a failure there is known to have sent nothing. */
    private suspend fun once(path: String, method: String, body: String?): String = withContext(Dispatchers.IO) {
        val connection = try { URL(origin.trimEnd('/') + "/api/v1" + path).openConnection() as HttpURLConnection } catch (error: IOException) { throw ApiException(0, "unreachable", error.message ?: "no answer", neverSent = true) }
        try {
            configure(connection, method, body)
            try { connection.connect() } catch (error: IOException) { throw ApiException(0, "unreachable", error.message ?: "no answer", neverSent = true) }
            if (body != null) try { connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) } } catch (error: IOException) { throw ApiException(0, "unreachable", error.message ?: "no answer") }
            val status = try { connection.responseCode } catch (error: IOException) { throw ApiException(0, "unreachable", error.message ?: "no answer") }
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val (code, detail) = describeError(status, text)
                // the server refused the login: the token is forgotten here, and nothing else asks with it (the person signs in again)
                if (status == 401 && token.isNotEmpty() && !path.endsWith("/login")) forgetToken()
                throw ApiException(status, code, detail.ifBlank { code })
            }
            // another web service (a router, a proxy page) answers 200 with something that is not JSON: that is an error to show, not a crash
            if (!isJson(text)) throw ApiException(status, "bad_json", "the answer is not JSON")
            text
        } finally { connection.disconnect() }
    }

    /**
     * One try of the light view of the settings: `If-None-Match` carries the ETag of the last answer, a `304` is [LightReply.NotModified], and an answer longer than [LIGHT_MAX_BYTES] is never read to the end
     * (it ends as the error `download_too_big`). The text is not checked here: the model reads it whole or refuses it.
     */
    private suspend fun onceLight(etag: String?): LightReply = withContext(Dispatchers.IO) {
        val connection = try { URL(origin.trimEnd('/') + "/api/v1" + LIGHT_PATH).openConnection() as HttpURLConnection } catch (error: IOException) { throw ApiException(0, "unreachable", error.message ?: "no answer", neverSent = true) }
        try {
            configure(connection, "GET", null)
            if (etag != null) connection.setRequestProperty("If-None-Match", etag)
            try { connection.connect() } catch (error: IOException) { throw ApiException(0, "unreachable", error.message ?: "no answer", neverSent = true) }
            val status = try { connection.responseCode } catch (error: IOException) { throw ApiException(0, "unreachable", error.message ?: "no answer") }
            if (status == 304) return@withContext LightReply.NotModified
            if (status !in 200..299) {
                val text = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val (code, detail) = describeError(status, text)
                if (status == 401 && token.isNotEmpty()) forgetToken()
                throw ApiException(status, code, detail.ifBlank { code })
            }
            val declared = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: 0L
            val bytes = try { if (declared > LIGHT_MAX_BYTES) null else connection.inputStream.use { readCapped(it, LIGHT_MAX_BYTES) } } catch (error: IOException) { throw ApiException(0, "unreachable", error.message ?: "no answer") }
            if (bytes == null) throw ApiException(status, "download_too_big", "the answer is longer than $LIGHT_MAX_BYTES bytes")
            LightReply.Fresh(bytes.toString(Charsets.UTF_8), connection.getHeaderField("ETag"))
        } finally { connection.disconnect() }
    }

    /** Renews the login first when it is in the last quarter of its life; a failure here is left to the request itself to find out. */
    private suspend fun renewIfDue(path: String) {
        if (!autoRefresh || renewing || token.isEmpty() || path.startsWith("/auth/") || path.endsWith("/login")) return
        if (!RefreshRule.due(lifeTotalS, (expiresAtMs - clockMs()) / 1000)) return
        renewing = true
        try { refresh() } catch (_: ApiException) { } finally { renewing = false }
    }

    protected suspend fun call(path: String, method: String = "GET", body: String? = null): String {
        renewIfDue(path)
        var retry = 0
        while (true) {
            try { return once(path, method, body) } catch (error: ApiException) {
                if (retry >= settings.retries || !RetryRules.mayRepeat(method, error.status, error.neverSent)) throw error
                pause(RetryRules.delayMs(retry))
                retry += 1
            }
        }
    }

    private fun q(value: String) = URLEncoder.encode(value, "UTF-8")

    suspend fun health(): String = JSONObject(call("/health")).optString("version")
    suspend fun setupNeeded(): Boolean = JSONObject(call("/setup")).optBoolean("needed")

    suspend fun setup(code: String, name: String, password: String): Unit {
        call("/setup", "POST", JSONObject().put("code", code).put("name", name).put("password", password).toString())
    }

    suspend fun login(name: String, password: String): Login {
        // The single login of the server: the application it is for is kept with the login and in the audit.
        val j = JSONObject(call("/auth/login", "POST", JSONObject().put("name", name).put("password", password).put("app", LOGIN_APP).toString()))
        val expires = j.optLong("expires_s", 0)
        val result = Login(j.optString("token"), j.optString("name", name), j.optString("role", "viewer"), j.optString("session_id"), expires)
        token = result.token
        adoptLife(expires)
        return result
    }

    /** Makes the login last again (`POST /auth/refresh`): the token stays the same; answers the seconds that are left. */
    suspend fun refresh(): Long {
        val left = JSONObject(call("/auth/refresh", "POST", "{}")).optLong("expires_s", 0)
        adoptLife(left)
        return left
    }

    /** The open logins of the person who asks (`GET /auth/sessions`), the newest first. */
    suspend fun sessions(): List<AuthSession> = parseSessions(call("/auth/sessions"))

    /** Closes one login of the list (a phone that was lost): its token stops working at once. */
    suspend fun closeSession(id: String) { call("/auth/sessions/${q(id)}", "DELETE") }

    /** Ends every login of the person, this one too. */
    suspend fun logoutAll() { call("/auth/logout-all", "POST", "{}") }

    /** The machines of the plugins of the server (laser, pick-and-place, printer...) with what each says of itself in `detail`; the ones of the drilling and routing machines are in the fleet. */
    suspend fun workMachines(): List<WorkMachine> = parseWorkMachines(call("/work/machines"))

    /** The feeder and head nodes of the pick-and-place machines, with their health as the server last saw it (read only; a server where the nodes are switched off answers 501). */
    suspend fun pnpNodes(): List<PnpNode> = parsePnpNodes(call("/pnp/nodes"))

    /** The jobs of the generic queue of the server (the newest first, at most 100): the ones that wait, run or are held are the ones the app shows. */
    suspend fun workJobs(): List<WorkJob> = parseWorkJobs(call("/work/jobs?limit=100"))

    /**
     * Starts a job that waits in the queue. Only ever called from a button that a person pressed twice: nothing in the app starts a job by itself. With [hash] the start names the hash the person was
     * shown, so a job that changed since is refused (`stale_authorization`) and nothing starts; without it the body is empty, as before.
     */
    suspend fun startWorkJob(id: String, hash: String = "") { call("/work/jobs/${q(id)}/start", "POST", if (hash.isEmpty()) "{}" else JSONObject().put("toolpath_sha256", hash).toString()) }

    /** Authorizes the hash of a job that waits (scope `jobs:authorize`): the server records who authorised which hash. Only ever called from the second touch of a button. */
    suspend fun authorizeWorkJob(id: String, hash: String) { call("/work/jobs/${q(id)}/authorize", "POST", JSONObject().put("toolpath_sha256", hash).toString()) }

    /** Cancels a job of the queue that waits or is held. */
    suspend fun cancelWorkJob(id: String) { call("/work/jobs/${q(id)}/cancel", "POST", "{}") }

    /**
     * The emergency stop of a machine of a plugin, at once and with no question: the route of its plugin when it has one (CNC and 3D printer: heaters off, motors released, machine latched), else the
     * cancel of the job that runs on it (a laser: the beam goes off and the machine latches). A machine with neither a route nor a job asks for nothing. A stop that got no answer is sent once more:
     * the server takes a stop twice without harm, and one that never arrived is worse than one that arrives twice. Returns false when there was nothing to send.
     */
    suspend fun emergencyStopWork(machine: WorkMachine): Boolean {
        val route = MachineSafety.stopRoute(machine.plugin)
        val path = if (route != null) "/work/machines/${q(machine.id)}/$route" else if (machine.job.isNotEmpty()) "/work/jobs/${q(machine.job)}/cancel" else return false
        try { call(path, "POST", "{}") } catch (error: ApiException) { if (error.status == 0) call(path, "POST", "{}") else throw error }
        return true
    }

    /** Releases the latch of an emergency stop or of a fault (an administrator); a plugin that does not latch has no such route and nothing is asked. Returns false when there was nothing to send. */
    suspend fun releaseWork(machine: WorkMachine): Boolean {
        val route = MachineSafety.releaseRoute(machine.plugin) ?: return false
        call("/work/machines/${q(machine.id)}/$route", "POST", "{}")
        return true
    }

    /** The contract of the settings of the server with the values in force (`GET /settings/contract`): administrators only, any other role is refused with 403. Answers the text of the reply. */
    suspend fun settingsContract(): String = call("/settings/contract")

    /**
     * The values in force of the settings, light and read only (`GET /settings/values`): any login. [etag] is the tag of the last answer; the server answers `304` when nothing changed. The text of a new
     * answer is read by the model (`parseLight`), which refuses what it does not trust.
     */
    suspend fun settingsValues(etag: String?): LightReply {
        renewIfDue(LIGHT_PATH)
        var retry = 0
        while (true) {
            try { return onceLight(etag) } catch (error: ApiException) {
                if (retry >= settings.retries || !RetryRules.mayRepeat("GET", error.status, error.neverSent)) throw error
                pause(RetryRules.delayMs(retry))
                retry += 1
            }
        }
    }

    /** Writes one setting by the method and the route its entry of the contract names (path without `/api/v1`). Answers the text of the reply: the new policy and, in `changed`, what the server did. */
    suspend fun writeSetting(method: String, path: String, body: String): String = call(path, method, body)

    /** The switches of security of the server that the app explains (administrators only; any other role is refused with 403). */
    suspend fun policySwitches(): List<PolicySwitch> = parsePolicySwitches(call("/auth/policy"))

    suspend fun session(): Login {
        val j = JSONObject(call("/session"))
        return Login(token, j.optString("name"), j.optString("role", "viewer"))
    }

    suspend fun logout() { runCatching { call("/auth/logout", "POST", "{}") } }

    suspend fun fleet(): Fleet = parseFleet(call("/fleet"))
    suspend fun jobs(machine: String? = null): List<JobSummary> = parseJobs(call("/jobs" + (machine?.let { "?machine=${q(it)}" } ?: "")))
    suspend fun job(id: String): Job = parseJob(call("/jobs/${q(id)}"))
    suspend fun start(id: String) { call("/jobs/${q(id)}/start", "POST", "{}") }
    suspend fun confirm(id: String) { call("/jobs/${q(id)}/confirm", "POST", "{}") }
    suspend fun cancel(id: String) { call("/jobs/${q(id)}/cancel", "POST", "{}") }
    suspend fun delete(id: String) { call("/jobs/${q(id)}", "DELETE") }
    /** Holds a running G-code job, and lets a held one (or one that goes on after a stop) go on. */
    suspend fun pause(id: String) { call("/jobs/${q(id)}/pause", "POST", "{}") }
    suspend fun resume(id: String) { call("/jobs/${q(id)}/resume", "POST", "{}") }

    /** Makes a new job, ready and not started, from the request the server kept for a cancelled or failed one, with the work already done left out: the file is not asked for again. */
    suspend fun resumeFrom(id: String): Job = parseJob(call("/jobs/${q(id)}/resume-from", "POST", "{}"))

    /** The switch of the machine for the `$` lines of GRBL (read only here: an administrator changes it in Studio). */
    suspend fun allowDollar(machine: String): Boolean = parseAllowDollar(call("/machines/${q(machine)}"))

    /** Plans a job and answers its view, creating nothing: the tools of the file, the holes, the lines of a G-code program. */
    suspend fun preview(machine: String, body: String): JobView = parsePreview(call("/machines/${q(machine)}/preview", "POST", body))

    /** Plans a job and keeps it ready (it never starts by itself); the answer is the job. */
    suspend fun createJob(machine: String, body: String): Job = parseJob(call("/machines/${q(machine)}/jobs", "POST", body))

    /** The spindle of the machine, its limits and the switch that says what happens to a job that asks for more. */
    suspend fun spindle(machine: String): SpindleInfo = parseSpindle(call("/machines/${q(machine)}/spindle"))

    /** Sets the spindle (`original`, `vfd` or empty for none) and/or the switch (`reject` or `warn`); what is null stays as it was. Administrator only. */
    suspend fun setSpindle(machine: String, spindle: String?, guard: String?): SpindleInfo {
        val body = JSONObject()
        if (spindle != null) body.put("spindle", spindle)
        if (guard != null) body.put("guard", guard)
        return parseSpindle(call("/machines/${q(machine)}/spindle", "PUT", body.toString()))
    }

    suspend fun head(machine: String): HeadStatus = parseHead(call("/machines/${q(machine)}/head/status"))
    suspend fun measure(machine: String, samples: Int = 7): Measure = parseMeasure(call("/machines/${q(machine)}/head/measure?samples=$samples", "POST", "{}"))

    /** The ring of LEDs around the camera: off (level 0) or on in the white of the camera at a level; the node keeps its own ceiling. */
    suspend fun setLight(machine: String, level: Int): RingLight {
        val path = "/machines/${q(machine)}/head/light"
        return parseRingLight(call(if (level <= 0) "$path?on=0" else "$path?on=1&level=${level.coerceAtMost(100)}&tone=white&sectors=all", "POST", "{}"))
    }

    /** A quality check: the ring is lit at [light] (0: left as it is), the picture is taken, the analyser of the shop judges it and the ring shows how it went. [holeMm] is the width of the hole of the job in the centre (0: none). */
    suspend fun inspect(machine: String, light: Int, holeMm: Double): Quality {
        val holes = if (holeMm > 0.0) """[{"x_mm":0,"y_mm":0,"dia_mm":${"%.3f".format(java.util.Locale.ROOT, holeMm)}}]""" else "[]"
        val lit = if (light > 0) ""","light":${light.coerceAtMost(100)},"restore_light":true""" else ""
        return parseQuality(call("/machines/${q(machine)}/head/inspect", "POST", """{"holes":$holes$lit}"""))
    }

    /** The last check of the machine (made from here or from another program), or null when there has been none since the server started. */
    suspend fun lastInspection(machine: String): Quality? =
        try { parseQuality(call("/machines/${q(machine)}/head/inspect")) } catch (error: ApiException) { if (error.code == "no_inspection") null else throw error }

    /** Makes the profile of the analyser one of the presets: 0 standard, 1 fine pitch, 2 prototype. */
    suspend fun inspectPreset(machine: String, index: Int) {
        val name = listOf("standard", "fine_pitch", "prototype")[index.coerceIn(0, 2)]
        call("/machines/${q(machine)}/head/inspect/profile", "PUT", """{"preset":"$name"}""")
    }

    /** Sets the scale of the picture from the hole that was just measured. */
    suspend fun inspectCalibrate(machine: String) { call("/machines/${q(machine)}/head/inspect/calibrate", "POST", "{}") }

    /** The address of the camera of the head, as the server hands it on; the token is in the address because a picture cannot carry a header. */
    /** The address of the camera without any credential: [withTicket] adds a one-use ticket just before it is opened. */
    fun streamUrl(machine: String): String = origin.trimEnd('/') + "/api/v1/machines/${q(machine)}/head/stream"

    /** [url] with a new ticket in it (good for one use within half a minute): the token of the session never goes in an address. */
    suspend fun withTicket(url: String): String = ticketUrl(url, JSONObject(call("/tickets", "POST", "{}")).optString("ticket"))

    /** The stop button of a machine: whatever job is active on it is ended at once (the server reaches the machine without waiting for the line it is sending). */
    suspend fun stopMachine(machine: String) { call("/machines/${q(machine)}/stop", "POST", "{}") }
}
