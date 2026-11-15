// EH3D-ANDROID-CONTROL - the client of the API against a server that is a socket of this computer: what it sends (path, method, headers, body) and how it reads the answers and the refusals.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import com.sun.net.httpserver.HttpServer
import es.electrohobby3d.eh3d.network.ApiException
import es.electrohobby3d.eh3d.network.Eh3dApi
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

class ApiClientTest {
    private class Seen(val method: String, val path: String, val query: String?, val authorization: String?, val contentType: String?, val agent: String?, val body: String)

    /** Runs [block] against a server that answers every request with [reply] and writes down what it was asked. */
    private fun withServer(reply: (Seen) -> Pair<Int, String>, block: (String, List<Seen>) -> Unit) {
        val seen = CopyOnWriteArrayList<Seen>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val request = Seen(
                exchange.requestMethod, exchange.requestURI.rawPath, exchange.requestURI.rawQuery, exchange.requestHeaders.getFirst("Authorization"),
                exchange.requestHeaders.getFirst("Content-Type"), exchange.requestHeaders.getFirst("User-Agent"), exchange.requestBody.readBytes().toString(Charsets.UTF_8),
            )
            seen.add(request)
            val (status, text) = reply(request)
            val bytes = text.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) } else exchange.close()
        }
        server.start()
        try { block("http://127.0.0.1:${server.address.port}", seen) } finally { server.stop(0) }
    }

    private fun ok(text: String = "{}") = { _: Seen -> 200 to text }

    @Test fun `the health call reads the version of the server`() = withServer(ok("""{"version":"1.2.3"}""")) { origin, seen ->
        assertEquals("1.2.3", runBlocking { Eh3dApi(origin).health() })
        assertEquals("GET", seen[0].method)
        assertEquals("/api/v1/health", seen[0].path)
    }

    @Test fun `a server that says nothing of its version gives an empty one`() = withServer(ok("{}")) { origin, _ ->
        assertEquals("", runBlocking { Eh3dApi(origin).health() })
    }

    @Test fun `the setup call says whether the first administrator is still to be made`() {
        withServer(ok("""{"needed":true}""")) { origin, _ -> assertTrue(runBlocking { Eh3dApi(origin).setupNeeded() }) }
        withServer(ok("""{"needed":false}""")) { origin, _ -> assertFalse(runBlocking { Eh3dApi(origin).setupNeeded() }) }
        withServer(ok("{}")) { origin, _ -> assertFalse(runBlocking { Eh3dApi(origin).setupNeeded() }) }
    }

    @Test fun `the first administrator is made with the code the server printed`() = withServer(ok("""{"ok":true}""")) { origin, seen ->
        runBlocking { Eh3dApi(origin).setup("123456", "owner", "a long pass phrase") }
        assertEquals("POST", seen[0].method)
        assertEquals("/api/v1/setup", seen[0].path)
        val body = JSONObject(seen[0].body)
        assertEquals("123456", body.getString("code"))
        assertEquals("owner", body.getString("name"))
        assertEquals("a long pass phrase", body.getString("password"))
        assertEquals("application/json", seen[0].contentType)
    }

    @Test fun `a login sends the name and the password and reads the token and the role`() = withServer(ok("""{"token":"t_abc","name":"Ana","role":"operator"}""")) { origin, seen ->
        val login = runBlocking { Eh3dApi(origin).login("ana", "secret phrase") }
        assertEquals("t_abc", login.token)
        assertEquals("Ana", login.name)
        assertEquals("operator", login.role)
        assertEquals("/api/v1/login", seen[0].path)
        assertEquals("ana", JSONObject(seen[0].body).getString("name"))
        assertNull(seen[0].authorization)
    }

    @Test fun `a login that names no role is a viewer and keeps the typed name`() = withServer(ok("""{"token":"t_x"}""")) { origin, _ ->
        val login = runBlocking { Eh3dApi(origin).login("ana", "secret phrase") }
        assertEquals("viewer", login.role)
        assertEquals("ana", login.name)
    }

    @Test fun `the session call keeps the token it was asked with`() = withServer(ok("""{"name":"Ana","role":"admin"}""")) { origin, seen ->
        val login = runBlocking { Eh3dApi(origin, "t_kept").session() }
        assertEquals("t_kept", login.token)
        assertEquals("admin", login.role)
        assertEquals("Bearer t_kept", seen[0].authorization)
    }

    @Test fun `no token means no Authorization header`() = withServer(ok("""{"machines":[]}""")) { origin, seen ->
        runBlocking { Eh3dApi(origin).fleet() }
        assertNull(seen[0].authorization)
    }

    @Test fun `every request says which app it comes from`() = withServer(ok("""{"machines":[]}""")) { origin, seen ->
        runBlocking { Eh3dApi(origin, "t").fleet() }
        assertNotNull(seen[0].agent)
        assertTrue(seen[0].agent!!.startsWith("EH3D-ANDROID-CONTROL/"))
        assertEquals("Bearer t", seen[0].authorization)
    }

    @Test fun `a trailing slash of the address does not double in the path`() = withServer(ok("""{"machines":[]}""")) { origin, seen ->
        runBlocking { Eh3dApi("$origin/").fleet() }
        assertEquals("/api/v1/fleet", seen[0].path)
    }

    @Test fun `a refusal gives its status its code and its detail`() = withServer({ 403 to """{"error":"forbidden","detail":"viewers cannot do this"}""" }) { origin, _ ->
        val error = assertThrows(ApiException::class.java) { runBlocking { Eh3dApi(origin, "t").start("job1") } }
        assertEquals(403, error.status)
        assertEquals("forbidden", error.code)
        assertEquals("viewers cannot do this", error.message)
        assertFalse(error.unauthorized)
    }

    @Test fun `a refused token is told as unauthorized`() = withServer({ 401 to """{"error":"unauthorized"}""" }) { origin, _ ->
        val error = assertThrows(ApiException::class.java) { runBlocking { Eh3dApi(origin, "t_old").fleet() } }
        assertTrue(error.unauthorized)
        assertEquals("unauthorized", error.code)
        assertEquals("unauthorized", error.message)
    }

    @Test fun `an error that is not JSON is named after its status`() = withServer({ 502 to "<html>bad gateway</html>" }) { origin, _ ->
        val error = assertThrows(ApiException::class.java) { runBlocking { Eh3dApi(origin).fleet() } }
        assertEquals(502, error.status)
        assertEquals("http_502", error.code)
    }

    @Test fun `an error with an empty body is named after its status too`() = withServer({ 404 to "" }) { origin, _ ->
        val error = assertThrows(ApiException::class.java) { runBlocking { Eh3dApi(origin).job("gone") } }
        assertEquals(404, error.status)
        assertEquals("http_404", error.code)
    }

    @Test fun `a 200 that is not JSON is another service and not a crash`() = withServer({ 200 to "<html>router login</html>" }) { origin, _ ->
        val error = assertThrows(ApiException::class.java) { runBlocking { Eh3dApi(origin).fleet() } }
        assertEquals(200, error.status)
        assertEquals("bad_json", error.code)
    }

    @Test fun `nobody listening is an unreachable server with no status`() {
        val port = ServerSocket(0).use { it.localPort }
        val error = assertThrows(ApiException::class.java) { runBlocking { Eh3dApi("http://127.0.0.1:$port").fleet() } }
        assertEquals(0, error.status)
        assertEquals("unreachable", error.code)
    }

    @Test fun `the jobs of one machine are asked for with its name encoded`() = withServer(ok("[]")) { origin, seen ->
        runBlocking { Eh3dApi(origin).jobs("Mill & Drill") }
        assertEquals("/api/v1/jobs", seen[0].path)
        assertEquals("machine=Mill+%26+Drill", seen[0].query)
    }

    @Test fun `the jobs of all the machines are asked for without a query`() = withServer(ok("[]")) { origin, seen ->
        assertEquals(0, runBlocking { Eh3dApi(origin).jobs() }.size)
        assertNull(seen[0].query)
    }

    @Test fun `the id of a job is encoded in the path so it cannot climb out of it`() = withServer(ok("""{"id":"x"}""")) { origin, seen ->
        runBlocking { Eh3dApi(origin).job("../health") }
        assertEquals("/api/v1/jobs/..%2Fhealth", seen[0].path)
    }

    @Test fun `the actions on a job use the method and the path of the API`() = withServer(ok("""{"ok":true}""")) { origin, seen ->
        val api = Eh3dApi(origin, "t")
        runBlocking {
            api.start("j"); api.confirm("j"); api.cancel("j"); api.pause("j"); api.resume("j"); api.delete("j"); api.stopMachine("m1")
        }
        assertEquals(
            listOf("POST /api/v1/jobs/j/start", "POST /api/v1/jobs/j/confirm", "POST /api/v1/jobs/j/cancel", "POST /api/v1/jobs/j/pause", "POST /api/v1/jobs/j/resume", "DELETE /api/v1/jobs/j", "POST /api/v1/machines/m1/stop"),
            seen.map { "${it.method} ${it.path}" },
        )
        assertEquals("{}", seen[0].body)
    }

    @Test fun `going on from a stopped job reads the new job it makes`() = withServer(ok("""{"id":"j2","resumed_from":"j1","state":"ready"}""")) { origin, seen ->
        val job = runBlocking { Eh3dApi(origin).resumeFrom("j1") }
        assertEquals("j2", job.summary.id)
        assertEquals("j1", job.summary.resumedFrom)
        assertEquals("/api/v1/jobs/j1/resume-from", seen[0].path)
    }

    @Test fun `the switch of the dollar lines is read from the machine`() = withServer(ok("""{"allow_dollar":true}""")) { origin, seen ->
        assertTrue(runBlocking { Eh3dApi(origin).allowDollar("m 1") })
        assertEquals("/api/v1/machines/m+1", seen[0].path)
    }

    @Test fun `a preview and a new job post the request as it is`() = withServer(ok("""{"kind":"drill","name":"p","holes":[]}""")) { origin, seen ->
        val api = Eh3dApi(origin)
        runBlocking { api.preview("m1", """{"kind":"drill"}"""); api.createJob("m1", """{"kind":"route"}""") }
        assertEquals("/api/v1/machines/m1/preview", seen[0].path)
        assertEquals("""{"kind":"drill"}""", seen[0].body)
        assertEquals("/api/v1/machines/m1/jobs", seen[1].path)
        assertEquals("""{"kind":"route"}""", seen[1].body)
    }

    @Test fun `setting the spindle sends only what is given`() = withServer(ok("""{"spindle":"vfd","guard":"warn"}""")) { origin, seen ->
        val api = Eh3dApi(origin, "t")
        val info = runBlocking { api.setSpindle("m1", null, "warn") }
        assertEquals("PUT", seen[0].method)
        assertEquals("""{"guard":"warn"}""", seen[0].body)
        assertEquals("vfd", info.spindle)
        runBlocking { api.setSpindle("m1", "original", null) }
        assertEquals("""{"spindle":"original"}""", seen[1].body)
        runBlocking { api.setSpindle("m1", "", "reject") }
        assertEquals("", JSONObject(seen[2].body).getString("spindle"))
    }

    @Test fun `the ring is switched off at level zero and never asked for more than a hundred`() = withServer(ok("""{"on":true,"level":100,"max_level":80}""")) { origin, seen ->
        val api = Eh3dApi(origin)
        runBlocking { api.setLight("m1", 0); api.setLight("m1", -5); api.setLight("m1", 40); api.setLight("m1", 250) }
        assertEquals("on=0", seen[0].query)
        assertEquals("on=0", seen[1].query)
        assertEquals("on=1&level=40&tone=white&sectors=all", seen[2].query)
        assertEquals("on=1&level=100&tone=white&sectors=all", seen[3].query)
    }

    @Test fun `a measure asks for the samples it is given`() = withServer(ok("""{"ok":true,"distance_mm":12.5,"sigma_mm":0.01,"used":5,"asked":7}""")) { origin, seen ->
        val measure = runBlocking { Eh3dApi(origin).measure("m1", 9) }
        assertEquals("samples=9", seen[0].query)
        assertEquals(12.5, measure.distanceMm, 1e-9)
        runBlocking { Eh3dApi(origin).measure("m1") }
        assertEquals("samples=7", seen[1].query)
    }

    @Test fun `a quality check carries the hole and the light only when there are any`() = withServer(ok("""{"verdict":"ok","holes":[],"findings":[]}""")) { origin, seen ->
        val api = Eh3dApi(origin)
        runBlocking { api.inspect("m1", 0, 0.0); api.inspect("m1", 50, 0.8); api.inspect("m1", 300, 0.0) }
        assertEquals("""{"holes":[]}""", seen[0].body)
        val lit = JSONObject(seen[1].body)
        assertEquals(50, lit.getInt("light"))
        assertTrue(lit.getBoolean("restore_light"))
        assertEquals(0.8, lit.getJSONArray("holes").getJSONObject(0).getDouble("dia_mm"), 1e-9)
        assertEquals(100, JSONObject(seen[2].body).getInt("light"))
    }

    @Test fun `there being no check yet is null and any other refusal is passed on`() {
        withServer({ 404 to """{"error":"no_inspection"}""" }) { origin, _ -> assertNull(runBlocking { Eh3dApi(origin).lastInspection("m1") }) }
        withServer({ 403 to """{"error":"forbidden"}""" }) { origin, _ ->
            assertEquals("forbidden", assertThrows(ApiException::class.java) { runBlocking { Eh3dApi(origin).lastInspection("m1") } }.code)
        }
        withServer(ok("""{"verdict":"not_ok","holes":[{"ok":false}],"findings":[]}""")) { origin, _ ->
            assertEquals("not_ok", runBlocking { Eh3dApi(origin).lastInspection("m1") }!!.verdict)
        }
    }

    @Test fun `the preset of the analyser is chosen by number and a wrong number is clamped`() = withServer(ok("""{"ok":true}""")) { origin, seen ->
        val api = Eh3dApi(origin)
        runBlocking { api.inspectPreset("m1", 0); api.inspectPreset("m1", 1); api.inspectPreset("m1", 2); api.inspectPreset("m1", 9); api.inspectPreset("m1", -3) }
        assertEquals(
            listOf("standard", "fine_pitch", "prototype", "prototype", "standard"),
            seen.map { JSONObject(it.body).getString("preset") },
        )
        assertEquals("PUT", seen[0].method)
    }

    @Test fun `the camera address has no credential and the ticket is added to it just before it is opened`() = withServer(ok("""{"ticket":"a b&c"}""")) { origin, seen ->
        val api = Eh3dApi(origin, "t_secret")
        val bare = api.streamUrl("m 1")
        assertEquals("$origin/api/v1/machines/m+1/head/stream", bare)
        assertFalse(bare.contains("t_secret"))
        val withTicket = runBlocking { api.withTicket(bare) }
        assertEquals("$bare?ticket=a+b%26c", withTicket)
        assertFalse(withTicket.contains("t_secret"))
        assertEquals("/api/v1/tickets", seen[0].path)
    }

    @Test fun `calibrating the picture and reading the head and the spindle use their paths`() = withServer(ok("""{"node":"n1"}""")) { origin, seen ->
        val api = Eh3dApi(origin)
        runBlocking { api.inspectCalibrate("m1"); api.head("m1"); api.spindle("m1") }
        assertEquals(
            listOf("POST /api/v1/machines/m1/head/inspect/calibrate", "GET /api/v1/machines/m1/head/status", "GET /api/v1/machines/m1/spindle"),
            seen.map { "${it.method} ${it.path}" },
        )
    }
}
