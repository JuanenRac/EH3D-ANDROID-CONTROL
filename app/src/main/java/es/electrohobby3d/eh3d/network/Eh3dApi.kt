// EH3D-ANDROID-CONTROL - the client of the API of EH3D-SERVER (docs/API.md of that project): plain java.net, JSON by hand, one request at a time on the IO dispatcher.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.network

import es.electrohobby3d.eh3d.BuildConfig
import es.electrohobby3d.eh3d.model.Fleet
import es.electrohobby3d.eh3d.model.HeadStatus
import es.electrohobby3d.eh3d.model.Quality
import es.electrohobby3d.eh3d.model.RingLight
import es.electrohobby3d.eh3d.model.SpindleInfo
import es.electrohobby3d.eh3d.model.parseSpindle
import es.electrohobby3d.eh3d.model.parseQuality
import es.electrohobby3d.eh3d.model.parseRingLight
import es.electrohobby3d.eh3d.model.Job
import es.electrohobby3d.eh3d.model.JobSummary
import es.electrohobby3d.eh3d.model.Measure
import es.electrohobby3d.eh3d.model.parseFleet
import es.electrohobby3d.eh3d.model.parseHead
import es.electrohobby3d.eh3d.model.parseJob
import es.electrohobby3d.eh3d.model.parseJobs
import es.electrohobby3d.eh3d.model.parseMeasure
import es.electrohobby3d.eh3d.model.JobView
import es.electrohobby3d.eh3d.model.parsePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The server refused, or could not be reached: [status] is the HTTP status (0 when there was no answer) and [code] the `error` code of the server, if it sent one. */
class ApiException(val status: Int, val code: String, message: String) : IOException(message) {
    val unauthorized get() = status == 401
}

/** Who is signed in: the token (a login's or a program's), the name and the role (`viewer`, `operator` or `admin`). */
data class Login(val token: String, val name: String, val role: String)

/** What a role may do: an operator also plans, starts, confirms and cancels jobs; a viewer only watches. */
object Roles {
    fun canOperate(role: String) = role == "operator" || role == "admin"
    /** Only an administrator sets the spindle of a machine. */
    fun canAdmin(role: String) = role == "admin"
}

/** The pure part of the error of the server: its code and its detail, or what the status says. */
fun describeError(status: Int, body: String): Pair<String, String> {
    val json = runCatching { JSONObject(body) }.getOrNull()
    val code = json?.optString("error").orEmpty().ifBlank { "http_$status" }
    val detail = json?.optString("detail").orEmpty()
    return code to detail
}

open class Eh3dApi(private val origin: String, var token: String = "") {
    private fun open(path: String, method: String, body: String?): HttpURLConnection {
        val connection = URL(origin.trimEnd('/') + "/api/v1" + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 6_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "EH3D-ANDROID-CONTROL/${BuildConfig.VERSION_NAME}")
        if (token.isNotEmpty()) connection.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        return connection
    }

    protected suspend fun call(path: String, method: String = "GET", body: String? = null): String = withContext(Dispatchers.IO) {
        val connection = try { open(path, method, body) } catch (error: IOException) { throw ApiException(0, "unreachable", error.message ?: "no answer") }
        try {
            val status = try { connection.responseCode } catch (error: IOException) { throw ApiException(0, "unreachable", error.message ?: "no answer") }
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val (code, detail) = describeError(status, text)
                throw ApiException(status, code, detail.ifBlank { code })
            }
            text
        } finally { connection.disconnect() }
    }

    private fun q(value: String) = URLEncoder.encode(value, "UTF-8")

    suspend fun health(): String = JSONObject(call("/health")).optString("version")
    suspend fun setupNeeded(): Boolean = JSONObject(call("/setup")).optBoolean("needed")

    suspend fun setup(code: String, name: String, password: String): Unit {
        call("/setup", "POST", JSONObject().put("code", code).put("name", name).put("password", password).toString())
    }

    suspend fun login(name: String, password: String): Login {
        val j = JSONObject(call("/login", "POST", JSONObject().put("name", name).put("password", password).toString()))
        return Login(j.optString("token"), j.optString("name", name), j.optString("role", "viewer"))
    }

    suspend fun session(): Login {
        val j = JSONObject(call("/session"))
        return Login(token, j.optString("name"), j.optString("role", "viewer"))
    }

    suspend fun logout() { runCatching { call("/logout", "POST", "{}") } }

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
    fun streamUrl(machine: String): String = origin.trimEnd('/') + "/api/v1/machines/${q(machine)}/head/stream?token=${q(token)}"
}
