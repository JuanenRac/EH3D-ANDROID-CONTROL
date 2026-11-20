// EH3D-ANDROID-CONTROL - the app against a simulated EH3D-SERVER that has a memory: it hands out sessions, shows a fleet with a job and can be made to fail the ways a workshop server fails
// (a link that drops, another service on the address, a 503, a restart that forgets the sessions, a stop that takes a moment to reach the machine). A phone that looks at the fleet the way the app
// does, with the real client and the real rules of the link and of the stops, shows what the screen would say at each step. The clock is a number, so nothing waits and every run is the same.
// Nothing here has run against a real server or a phone.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import es.electrohobby3d.eh3d.model.ConnectionSettings
import es.electrohobby3d.eh3d.model.Fleet
import es.electrohobby3d.eh3d.model.Interlock
import es.electrohobby3d.eh3d.model.MachineSafety
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

class SimulatedServerTest {
    /** The ways the simulated server answers. */
    private enum class Mode { Up, Dead, Html, Unavailable }

    private companion object {
        /** The queue of the simulated server: a laser job that runs, a print that waits for a person and a print that is over. */
        const val WORK_JOBS = """{"total":3,"offset":0,"limit":100,"items":[
            {"id":"w-1","machine":"l1","plugin":"laser","name":"Badge","owner":"ana","state":"running","percent":40,"position":0},
            {"id":"w-2","machine":"p1","plugin":"printer3d","name":"Benchy","owner":"ana","state":"queued","percent":0,"position":1},
            {"id":"w-3","machine":"p1","plugin":"printer3d","name":"Old","owner":"ana","state":"done","percent":100,"position":0}]}"""

        /** The switches of security as the server answers an administrator: the four of the app are off, and one more that the app leaves out. */
        const val POLICY = """{"switches":[{"key":"url_token","value":false,"safe":true},{"key":"slice_enabled","value":false,"safe":true},{"key":"mdns_announce","value":false,"safe":true},{"key":"compat_api","value":false,"safe":true},{"key":"compat_write","value":false,"safe":true}]}"""
    }

    /** A server of this computer with one machine, `m1`, and one job on it, `j1`; it forgets its sessions when it is restarted. */
    private class SimServer {
        @Volatile var mode = Mode.Up
        @Volatile var jobState: String? = "running"
        @Volatile private var stopReads = 0
        private val tokens = CopyOnWriteArraySet<String>()
        private val issued = AtomicInteger(0)
        val logins = AtomicInteger(0)
        val refreshes = AtomicInteger(0)
        val hits = AtomicInteger(0)
        /** The next requests are answered 503 (a server that is busy for a moment). */
        @Volatile var failNext = 0
        /** The ids of the logins that were closed from the list. */
        val closedSessions = CopyOnWriteArraySet<String>()
        /** The machines of the plugins: whether the simulated laser and printer are latched, the calls the phone made to `/work/`, and whether the login may release a latch and read the policy. */
        @Volatile var laserLatched = false
        @Volatile var printerLatched = false
        @Volatile var admin = false
        val workCalls = CopyOnWriteArrayList<String>()
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

        private fun workMachinesJson(): String {
            val laserJob = if (laserLatched) "null" else "\"w-1\""
            val laserState = if (laserLatched) "idle" else "busy"
            return """{"machines":[
                {"id":"l1","name":"Laser","plugin":"laser","state":"$laserState","job":$laserJob,"capabilities":{"evidence":"simulator"},"detail":{"evidence":"simulator","simulator_only":true,"estop_latched":$laserLatched}},
                {"id":"p1","name":"Printer","plugin":"printer3d","state":"idle","job":null,"detail":{"evidence":"simulator","simulator_only":true,"latched":$printerLatched,"auto_start_queue":false,"limits_c":{"hotend":260,"bed":100,"chamber":0},"temperatures":[{"name":"hotend","current_c":24.6,"target_c":0},{"name":"bed","current_c":23,"target_c":0}]}}]}"""
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
            hits.incrementAndGet()
            if (failNext > 0) { failNext -= 1; reply(exchange, 503, """{"error":"unavailable"}"""); return }
            if (path == "/api/v1/auth/login") {
                val token = "t" + issued.incrementAndGet()
                tokens.add(token)
                logins.incrementAndGet()
                reply(exchange, 200, """{"token":"$token","name":"ana","role":"operator","app":"android","session_id":"s$token","expires_s":3600}""")
                return
            }
            val sent = exchange.requestHeaders.getFirst("Authorization").orEmpty().removePrefix("Bearer ")
            if (sent !in tokens) { reply(exchange, 401, """{"error":"unauthorized"}"""); return }
            when {
                path == "/api/v1/auth/refresh" -> { refreshes.incrementAndGet(); reply(exchange, 200, """{"expires_s":3600}""") }
                path == "/api/v1/auth/sessions" && exchange.requestMethod == "GET" ->
                    reply(exchange, 200, """{"sessions":[{"id":"s1","name":"ana","role":"operator","app":"android","created":1,"expires":2,"ip":"10.0.0.2","current":true},{"id":"s2","name":"ana","role":"operator","app":"studio","created":1,"expires":2,"ip":"10.0.0.9","current":false}]}""")
                path.startsWith("/api/v1/auth/sessions/") && exchange.requestMethod == "DELETE" -> { closedSessions.add(path.substringAfterLast('/')); reply(exchange, 200, """{"ok":true}""") }
                path == "/api/v1/auth/logout-all" -> { tokens.clear(); reply(exchange, 200, """{"closed":1}""") }
                path == "/api/v1/auth/logout" -> { tokens.remove(sent); reply(exchange, 200, """{"ok":true}""") }
                path == "/api/v1/work/machines" -> reply(exchange, 200, workMachinesJson())
                path == "/api/v1/work/jobs" -> reply(exchange, 200, WORK_JOBS)
                path.startsWith("/api/v1/work/") && exchange.requestMethod == "POST" -> {
                    workCalls.add("POST $path")
                    when (path) {
                        "/api/v1/work/jobs/w-1/cancel" -> { laserLatched = true; reply(exchange, 200, """{"ok":true}""") }
                        "/api/v1/work/machines/p1/emergency-stop" -> { printerLatched = true; reply(exchange, 200, """{"ok":true}""") }
                        "/api/v1/work/machines/l1/laser/release" -> if (admin) { laserLatched = false; reply(exchange, 200, """{"released":true}""") } else reply(exchange, 403, """{"error":"forbidden"}""")
                        "/api/v1/work/machines/p1/release" -> if (admin) { printerLatched = false; reply(exchange, 200, """{"released":true}""") } else reply(exchange, 403, """{"error":"forbidden"}""")
                        "/api/v1/work/jobs/w-2/start" -> reply(exchange, 200, """{"id":"w-2","state":"running"}""")
                        else -> reply(exchange, 404, """{"error":"not_found"}""")
                    }
                }
                path == "/api/v1/auth/policy" -> if (admin) reply(exchange, 200, POLICY) else reply(exchange, 403, """{"error":"forbidden"}""")
                path == "/api/v1/fleet" -> reply(exchange, 200, fleetJson())
                path == "/api/v1/jobs/j1/cancel" -> { if (jobState != null) stopReads = 2; reply(exchange, 200, """{"ok":true}""") }
                else -> reply(exchange, 404, """{"error":"not_found"}""")
            }
        }
    }

    /** A phone: it looks at the fleet every 1.5 s of its own clock, signs in again once when the server says the session is gone, and keeps the link as the app does. */
    private class Phone(origin: String) {
        val api = Eh3dApi(origin, settings = ConnectionSettings(retries = 0))   // the looks of the phone are one try each: the retries have their own tests
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

    @Test fun `a machine of a plugin says it is a simulator, and its emergency stop is one request that latches it`() = withSim { server, phone ->
        phone.look()
        val laser = runBlocking { phone.api.workMachines() }.first { it.id == "l1" }
        assertTrue(laser.simulatorOnly)
        assertEquals("w-1", laser.job)
        assertTrue(MachineSafety.canStop(true, laser))
        assertFalse(MachineSafety.canStop(false, laser))
        assertTrue(runBlocking { phone.api.emergencyStopWork(laser) })
        assertEquals(listOf("POST /api/v1/work/jobs/w-1/cancel"), server.workCalls.toList())
        val held = runBlocking { phone.api.workMachines() }.first { it.id == "l1" }
        assertEquals(listOf(Interlock.EstopLatched), held.interlocks)
        assertTrue(held.latched)
        assertFalse("a held machine is released, not stopped again", MachineSafety.canStop(true, held))
        assertTrue(MachineSafety.canRelease(true, held))
        assertFalse(MachineSafety.canRelease(false, held))
    }

    @Test fun `the printer stops by its own route and only an administrator gets the latch released`() = withSim { server, phone ->
        phone.look()
        val printer = runBlocking { phone.api.workMachines() }.first { it.id == "p1" }
        assertTrue(printer.simulatorOnly)
        assertEquals("", printer.job)
        assertTrue(MachineSafety.canStop(true, printer))
        assertEquals(listOf("hotend 25/0", "bed 23/0"), printer.heaters.map { MachineSafety.heaterText(it) })
        assertEquals("260", MachineSafety.limitText(printer.limits?.hotendC ?: 0.0))
        assertTrue(runBlocking { phone.api.emergencyStopWork(printer) })
        val held = runBlocking { phone.api.workMachines() }.first { it.id == "p1" }
        assertEquals(listOf(Interlock.Latched), held.interlocks)
        val refused = assertThrows(ApiException::class.java) { runBlocking { phone.api.releaseWork(held) } }
        assertTrue(refused.forbidden)
        assertTrue("the refused release leaves it held", runBlocking { phone.api.workMachines() }.first { it.id == "p1" }.latched)
        server.admin = true
        assertTrue(runBlocking { phone.api.releaseWork(held) })
        assertFalse(runBlocking { phone.api.workMachines() }.first { it.id == "p1" }.latched)
        assertEquals(
            listOf("POST /api/v1/work/machines/p1/emergency-stop", "POST /api/v1/work/machines/p1/release", "POST /api/v1/work/machines/p1/release"),
            server.workCalls.toList(),
        )
    }

    @Test fun `looking at the queue starts nothing, and a job starts only when it is asked for by its id`() = withSim { server, phone ->
        phone.look()
        val jobs = runBlocking { phone.api.workJobs() }
        runBlocking { phone.api.workMachines() }
        assertEquals(listOf("w-2"), MachineSafety.activeJobs(jobs, "p1").map { it.id })
        assertEquals(listOf("w-1"), MachineSafety.activeJobs(jobs, "l1").map { it.id })
        val waiting = jobs.first { it.id == "w-2" }
        assertTrue(MachineSafety.canStartJob(true, waiting))
        assertFalse(MachineSafety.canStartJob(false, waiting))
        assertFalse(MachineSafety.canStartJob(true, jobs.first { it.id == "w-1" }))
        assertTrue("reading the queue and the machines sent nothing", server.workCalls.isEmpty())
        runBlocking { phone.api.startWorkJob(waiting.id) }
        assertEquals(listOf("POST /api/v1/work/jobs/w-2/start"), server.workCalls.toList())
    }

    @Test fun `the switches of the server are read by an administrator only and the four are off`() = withSim { server, phone ->
        phone.look()
        val refused = assertThrows(ApiException::class.java) { runBlocking { phone.api.policySwitches() } }
        assertTrue(refused.forbidden)
        assertNotEquals("a refusal for the role keeps the login", "", phone.api.token)
        server.admin = true
        val list = runBlocking { phone.api.policySwitches() }
        assertEquals(MachineSafety.SWITCH_KEYS, list.map { it.key })
        assertTrue(list.none { it.on })
        assertTrue(list.all { it.safe })
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

    /** An API with a clock the test moves and a pause that is only written down, so nothing waits. */
    private class Timed(origin: String, retries: Int = 2) {
        var nowMs = 1_000_000L
        val pauses = mutableListOf<Long>()
        val api = Eh3dApi(origin, settings = ConnectionSettings(retries = retries), pause = { pauses += it }, clockMs = { nowMs })
    }

    @Test fun `the login says how long it lasts and a request in the last quarter renews it by itself, once`() = withSim { server, _ ->
        val timed = Timed(server.origin)
        val login = runBlocking { timed.api.login("ana", "a long pass phrase") }
        assertEquals(3600L, login.expiresS)
        assertTrue(login.sessionId.startsWith("s"))
        runBlocking { timed.api.fleet() }
        assertEquals(0, server.refreshes.get())
        timed.nowMs += 2_600_000   // 1000 s left of 3600: more than a quarter
        runBlocking { timed.api.fleet() }
        assertEquals(0, server.refreshes.get())
        timed.nowMs += 200_000   // 800 s left: in the last quarter
        runBlocking { timed.api.fleet() }
        assertEquals(1, server.refreshes.get())
        runBlocking { timed.api.fleet() }   // the life is whole again
        assertEquals(1, server.refreshes.get())
    }

    @Test fun `a client that is told not to renew never asks for it`() = withSim { server, _ ->
        val timed = Timed(server.origin)
        runBlocking { timed.api.login("ana", "a long pass phrase") }
        timed.api.autoRefresh = false
        timed.nowMs += 3_000_000
        runBlocking { timed.api.fleet() }
        assertEquals(0, server.refreshes.get())
    }

    @Test fun `a refused token is forgotten and a login that is over is not renewed`() = withSim { server, _ ->
        val timed = Timed(server.origin)
        runBlocking { timed.api.login("ana", "a long pass phrase") }
        server.restart()
        val error = assertThrows(ApiException::class.java) { runBlocking { timed.api.fleet() } }
        assertTrue(error.unauthorized)
        assertEquals("", timed.api.token)
        assertEquals(0, server.refreshes.get())
    }

    @Test fun `the open logins are listed and one is closed by its id`() = withSim { server, _ ->
        val timed = Timed(server.origin)
        runBlocking { timed.api.login("ana", "a long pass phrase") }
        val list = runBlocking { timed.api.sessions() }
        assertEquals(listOf("s1", "s2"), list.map { it.id })
        assertTrue(list[0].current)
        assertFalse(list[1].current)
        assertEquals("studio", list[1].app)
        runBlocking { timed.api.closeSession("s2") }
        assertEquals(setOf("s2"), server.closedSessions.toSet())
    }

    @Test fun `ending every login of the person ends this one too`() = withSim { server, _ ->
        val timed = Timed(server.origin)
        runBlocking { timed.api.login("ana", "a long pass phrase") }
        runBlocking { timed.api.logoutAll() }
        val error = assertThrows(ApiException::class.java) { runBlocking { timed.api.fleet() } }
        assertTrue(error.unauthorized)
    }

    @Test fun `signing out ends the login with the route of the single login`() = withSim { server, _ ->
        val timed = Timed(server.origin)
        runBlocking { timed.api.login("ana", "a long pass phrase") }
        runBlocking { timed.api.logout() }
        val error = assertThrows(ApiException::class.java) { runBlocking { timed.api.fleet() } }
        assertTrue(error.unauthorized)
    }

    @Test fun `a read that gets a 503 is tried again with growing pauses and then succeeds`() = withSim { server, _ ->
        val timed = Timed(server.origin)
        runBlocking { timed.api.login("ana", "a long pass phrase") }
        server.failNext = 2
        val fleet = runBlocking { timed.api.fleet() }
        assertEquals("m1", fleet.machines.single().id)
        assertEquals(listOf(200L, 400L), timed.pauses)
    }

    @Test fun `the retries are the setting and no more`() = withSim { server, _ ->
        val timed = Timed(server.origin, retries = 1)
        runBlocking { timed.api.login("ana", "a long pass phrase") }
        val before = server.hits.get()
        server.failNext = 5
        val error = assertThrows(ApiException::class.java) { runBlocking { timed.api.fleet() } }
        assertEquals(503, error.status)
        assertEquals(2, server.hits.get() - before)   // the try and one retry
        assertEquals(listOf(200L), timed.pauses)
    }

    @Test fun `a post that got a 503 is never sent twice`() = withSim { server, _ ->
        val timed = Timed(server.origin)
        runBlocking { timed.api.login("ana", "a long pass phrase") }
        val before = server.hits.get()
        server.failNext = 1
        assertThrows(ApiException::class.java) { runBlocking { timed.api.cancel("j1") } }
        assertEquals(1, server.hits.get() - before)
        assertTrue(timed.pauses.isEmpty())
    }

    @Test fun `a connection that cannot be made is tried again whatever the method`() {
        val port = java.net.ServerSocket(0).use { it.localPort }   // nobody listens there
        val timed = Timed("http://127.0.0.1:$port")
        val error = assertThrows(ApiException::class.java) { runBlocking { timed.api.cancel("j1") } }
        assertEquals("unreachable", error.code)
        assertTrue(error.neverSent)
        assertEquals(listOf(200L, 400L), timed.pauses)
    }

    @Test fun `a 503 on a read is tried again and on a post it is not`() = withSim { server, _ ->
        val timed = Timed(server.origin, retries = 1)
        runBlocking { timed.api.login("ana", "a long pass phrase") }
        server.mode = Mode.Unavailable
        assertThrows(ApiException::class.java) { runBlocking { timed.api.fleet() } }
        assertEquals(listOf(200L), timed.pauses)
        timed.pauses.clear()
        assertThrows(ApiException::class.java) { runBlocking { timed.api.cancel("j1") } }
        assertTrue(timed.pauses.isEmpty())
    }
}
