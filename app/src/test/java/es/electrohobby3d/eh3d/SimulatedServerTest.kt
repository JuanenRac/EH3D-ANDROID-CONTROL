// EH3D-ANDROID-CONTROL - the app against a simulated EH3D-SERVER that has a memory: it hands out sessions, shows a fleet with a job and can be made to fail the ways a workshop server fails
// (a link that drops, another service on the address, a 503, a restart that forgets the sessions, a stop that takes a moment to reach the machine). A phone that looks at the fleet the way the app
// does, with the real client and the real rules of the link and of the stops, shows what the screen would say at each step. The clock is a number, so nothing waits and every run is the same.
// Nothing here has run against a real server or a phone.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import es.electrohobby3d.eh3d.model.Fleet
import es.electrohobby3d.eh3d.model.LinkFailure
import es.electrohobby3d.eh3d.model.LinkPolicy
import es.electrohobby3d.eh3d.model.LinkState
import es.electrohobby3d.eh3d.model.Notice
import es.electrohobby3d.eh3d.model.NoticeKind
import es.electrohobby3d.eh3d.model.ServerLink
import es.electrohobby3d.eh3d.model.StopNote
import es.electrohobby3d.eh3d.model.StopOutcome
import es.electrohobby3d.eh3d.model.classifyFailure
import es.electrohobby3d.eh3d.model.fleetNotices
import es.electrohobby3d.eh3d.model.fleetStates
import es.electrohobby3d.eh3d.model.followStops
import es.electrohobby3d.eh3d.network.ApiException
import es.electrohobby3d.eh3d.network.Eh3dApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

class SimulatedServerTest {
    /** The ways the simulated server answers. */
    private enum class Mode { Up, Dead, Html, Unavailable }

    /** A server of this computer with one machine, `m1`, and one job on it, `j1`; it forgets its sessions when it is restarted. */
    private class SimServer {
        @Volatile var mode = Mode.Up
        @Volatile var jobState: String? = "running"
        @Volatile private var stopReads = 0
        private val tokens = CopyOnWriteArraySet<String>()
        private val issued = AtomicInteger(0)
        val logins = AtomicInteger(0)
        private val http: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val origin: String get() = "http://127.0.0.1:${http.address.port}"

        init {
            http.createContext("/") { exchange -> handle(exchange) }
            http.start()
        }

        /** The server starts again: nobody is signed in. */
        fun restart() { tokens.clear() }

        fun close() { http.stop(0) }

        private fun reply(exchange: HttpExchange, status: Int, text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        private fun fleetJson(): String {
            val state = jobState
            val job = if (state == null) "null" else """{"id":"j1","name":"board","kind":"drill","state":"$state","by":"ana","items_done":3,"items_total":10,"holes_done":3,"holes_total":10,"percent":30,"estimate_s":60.0}"""
            val machine = when {
                state == null -> "idle"
                state == "waiting_operator" -> "waiting"
                else -> "busy"
            }
            // after a stop the machine goes on showing its job for two more looks
            if (stopReads > 0) {
                stopReads -= 1
                if (stopReads == 0) jobState = null
            }
            return """{"machines":[{"id":"m1","name":"Drill 1","model":"ccd-mtc","station":"bench","connection":"node","state":"$machine","ready":0,"job":$job}]}"""
        }

        private fun handle(exchange: HttpExchange) {
            exchange.requestBody.readBytes()
            val path = exchange.requestURI.rawPath
            when (mode) {
                Mode.Dead -> { exchange.close(); return }
                Mode.Html -> { reply(exchange, 200, "<html>router login</html>"); return }
                Mode.Unavailable -> { reply(exchange, 503, """{"error":"unavailable"}"""); return }
                Mode.Up -> {}
            }
            if (path == "/api/v1/login") {
                val token = "t" + issued.incrementAndGet()
                tokens.add(token)
                logins.incrementAndGet()
                reply(exchange, 200, """{"token":"$token","name":"ana","role":"operator"}""")
                return
            }
            val sent = exchange.requestHeaders.getFirst("Authorization").orEmpty().removePrefix("Bearer ")
            if (sent !in tokens) { reply(exchange, 401, """{"error":"unauthorized"}"""); return }
            when {
                path == "/api/v1/fleet" -> reply(exchange, 200, fleetJson())
                path == "/api/v1/jobs/j1/cancel" -> { if (jobState != null) stopReads = 2; reply(exchange, 200, """{"ok":true}""") }
                else -> reply(exchange, 404, """{"error":"not_found"}""")
            }
        }
    }

    /** A phone: it looks at the fleet every 1.5 s of its own clock, signs in again once when the server says the session is gone, and keeps the link as the app does. */
    private class Phone(origin: String) {
        val api = Eh3dApi(origin)
        var link = ServerLink()
        var fleet: Fleet = Fleet.EMPTY
        var now = 0L
        var nextDelayMs = 0L

        fun look() {
            now += 1_500
            try {
                fleet = try {
                    runBlocking { api.fleet() }
                } catch (error: ApiException) {
                    if (!error.unauthorized) throw error
                    api.token = runBlocking { api.login("ana", "a long pass phrase") }.token
                    runBlocking { api.fleet() }
                }
                link = link.succeeded(now)
            } catch (error: ApiException) {
                val why = classifyFailure(error.status, error.code)
                if (why != null) link = link.failed(why, now)
            }
            nextDelayMs = if (link.failures == 0) 1_500L else LinkPolicy().retryDelayMs(link.failures)
        }
    }

    private fun withSim(block: (SimServer, Phone) -> Unit) {
        val server = SimServer()
        try { block(server, Phone(server.origin)) } finally { server.close() }
    }

    @Test fun `one failed look is a blink, two are no connection, the waits grow, and the first good answer ends it`() = withSim { server, phone ->
        phone.look()
        assertEquals(LinkState.Connected, phone.link.state())
        assertEquals(1, server.logins.get())
        server.mode = Mode.Dead
        phone.look()
        assertEquals(LinkState.Connected, phone.link.state())
        assertEquals(1_500L, phone.nextDelayMs)
        val waits = mutableListOf<Long>()
        repeat(4) {
            phone.look()
            waits += phone.nextDelayMs
        }
        assertEquals(LinkState.Offline, phone.link.state())
        assertEquals(listOf(3_000L, 6_000L, 10_000L, 10_000L), waits)
        server.mode = Mode.Up
        phone.look()
        assertEquals(LinkState.Connected, phone.link.state())
        assertEquals(0, phone.link.failures)
        assertEquals(1_500L, phone.nextDelayMs)
        // the fleet that was last seen is still there after the outage
        assertEquals("m1", phone.fleet.machines.single().id)
    }

    @Test fun `an answer that is old is called old after one failure`() = withSim { server, phone ->
        phone.look()
        server.mode = Mode.Dead
        phone.now += 5_000
        phone.look()
        assertEquals(1, phone.link.failures)
        assertEquals(LinkState.Stale, phone.link.state())
    }

    @Test fun `another service on the address is told at the first look and the app recovers when the right server is back`() = withSim { server, phone ->
        phone.look()
        server.mode = Mode.Html
        phone.look()
        assertEquals(LinkFailure.WrongServer, phone.link.failure)
        assertEquals(LinkState.WrongServer, phone.link.state())
        // a body that is not JSON is another service whatever status the client could read from it
        assertEquals(LinkFailure.WrongServer, classifyFailure(0, "bad_json"))
        assertEquals(LinkFailure.Unreachable, classifyFailure(0, "unreachable"))
        server.mode = Mode.Up
        phone.look()
        assertEquals(LinkState.Connected, phone.link.state())
        assertNull(phone.link.failure)
    }

    @Test fun `a 503 is an unreachable server but a 404 is a server that answered as itself`() = withSim { server, phone ->
        phone.look()
        server.mode = Mode.Unavailable
        phone.look()
        assertEquals(LinkFailure.Unreachable, phone.link.failure)
        server.mode = Mode.Up
        val missing = assertThrows(ApiException::class.java) { runBlocking { phone.api.job("zzz") } }
        assertEquals(404, missing.status)
        assertNull(classifyFailure(missing.status, missing.code))
    }

    @Test fun `a server that restarted forgets the session and the app signs in again once without showing a failure`() = withSim { server, phone ->
        phone.look()
        val first = phone.api.token
        assertEquals(1, server.logins.get())
        server.restart()
        phone.look()
        assertEquals(2, server.logins.get())
        assertNotEquals(first, phone.api.token)
        assertEquals(LinkState.Connected, phone.link.state())
        assertEquals(0, phone.link.failures)
        // a look with the new session needs no new sign-in
        phone.look()
        assertEquals(2, server.logins.get())
    }

    @Test fun `a stop is sent, then still working past the grace, then confirmed when the machine is free, and the note goes after a while`() = withSim { _, phone ->
        phone.look()
        assertEquals("busy", phone.fleet.machine("m1")!!.state)
        runBlocking { phone.api.cancel("j1") }
        var notes = listOf(StopNote("m1", "Drill 1", phone.now))
        val seen = mutableListOf<StopOutcome>()
        repeat(3) {
            phone.look()
            notes = followStops(notes, phone.fleet, phone.now, phone.now + 50)
            seen += notes.single().outcome
        }
        assertEquals(listOf(StopOutcome.Sent, StopOutcome.StillWorking, StopOutcome.Confirmed), seen)
        assertTrue(phone.fleet.machine("m1")!!.job == null)
        // eight seconds after the confirmation the note is gone
        notes = followStops(notes, phone.fleet, phone.now + 8_000, phone.now + 8_100)
        assertTrue(notes.isEmpty())
    }

    @Test fun `a machine that does not stop is told as still working until it does`() = withSim { server, phone ->
        phone.look()
        var notes = listOf(StopNote("m1", "Drill 1", phone.now))
        val seen = mutableListOf<StopOutcome>()
        // the stop never reaches the machine: the server is not told to cancel
        repeat(4) {
            phone.look()
            notes = followStops(notes, phone.fleet, phone.now, phone.now + 50)
            seen += notes.single().outcome
        }
        assertEquals(listOf(StopOutcome.Sent, StopOutcome.StillWorking, StopOutcome.StillWorking, StopOutcome.StillWorking), seen)
        server.jobState = null
        phone.look()
        notes = followStops(notes, phone.fleet, phone.now, phone.now + 50)
        assertEquals(StopOutcome.Confirmed, notes.single().outcome)
    }

    @Test fun `an outage makes no notice and the changes that happened meanwhile are told when the server is back`() = withSim { server, phone ->
        phone.look()
        val before = fleetStates(phone.fleet)
        server.jobState = "waiting_operator"
        server.mode = Mode.Dead
        phone.look()
        assertTrue(fleetNotices(before, phone.fleet).isEmpty())
        server.mode = Mode.Up
        phone.look()
        assertEquals(listOf(Notice("m1", "Drill 1", NoticeKind.Waiting)), fleetNotices(before, phone.fleet))
        val waiting = fleetStates(phone.fleet)
        server.jobState = null
        phone.look()
        assertEquals(listOf(Notice("m1", "Drill 1", NoticeKind.Ended)), fleetNotices(waiting, phone.fleet))
    }
}
