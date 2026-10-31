// EH3D-ANDROID-CONTROL - the client of the API of EH3D-SERVER (docs/API.md of that project): plain java.net, JSON by hand, one request at a time on the IO dispatcher.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.network

import es.electrohobby3d.eh3d.BuildConfig
import es.electrohobby3d.eh3d.model.Fleet
import es.electrohobby3d.eh3d.model.HeadStatus
import es.electrohobby3d.eh3d.model.Job
import es.electrohobby3d.eh3d.model.JobSummary
import es.electrohobby3d.eh3d.model.Measure
import es.electrohobby3d.eh3d.model.parseFleet
import es.electrohobby3d.eh3d.model.parseHead
import es.electrohobby3d.eh3d.model.parseJob
import es.electrohobby3d.eh3d.model.parseJobs
import es.electrohobby3d.eh3d.model.parseMeasure
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

    suspend fun head(machine: String): HeadStatus = parseHead(call("/machines/${q(machine)}/head/status"))
    suspend fun measure(machine: String, samples: Int = 7): Measure = parseMeasure(call("/machines/${q(machine)}/head/measure?samples=$samples", "POST", "{}"))

    /** The address of the camera of the head, as the server hands it on; the token is in the address because a picture cannot carry a header. */
    fun streamUrl(machine: String): String = origin.trimEnd('/') + "/api/v1/machines/${q(machine)}/head/stream?token=${q(token)}"
}
