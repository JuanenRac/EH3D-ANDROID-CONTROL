// EH3D-ANDROID-CONTROL - the safety of the machines of the plugins as the phone decides it: the simulator badge, the interlocks, who may stop, release and start, the queue of a machine, the limits of a
// printer and the switches of the server. The same cases are in EH3D-STUDIO (src/services/safety.test.ts); the SERVER decides in the end.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import es.electrohobby3d.eh3d.model.Heater
import es.electrohobby3d.eh3d.model.Interlock
import es.electrohobby3d.eh3d.model.JobAuth
import es.electrohobby3d.eh3d.model.MachineSafety
import es.electrohobby3d.eh3d.model.TempLimits
import es.electrohobby3d.eh3d.model.WorkJob
import es.electrohobby3d.eh3d.model.WorkMachine
import es.electrohobby3d.eh3d.model.parsePolicySwitches
import es.electrohobby3d.eh3d.model.parseWorkJobs
import es.electrohobby3d.eh3d.model.parseWorkMachines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkSafetyTest {
    private fun machine(plugin: String, detail: String = "{}", job: String = "", capabilities: String = "{}"): WorkMachine {
        val text = """{"machines":[{"id":"m","name":"M","plugin":"$plugin","state":"idle","job":${if (job.isEmpty()) "null" else "\"$job\""},"capabilities":$capabilities,"detail":$detail}]}"""
        return parseWorkMachines(text).single()
    }

    private fun job(id: String, machine: String, state: String, position: Int = 0) = WorkJob(id, machine, "printer3d", id, "ana", state, 0, position)

    @Test fun `a machine that says it only ran against a simulator is marked, whichever way the server says it`() {
        assertTrue(machine("laser", """{"simulator_only":true}""").simulatorOnly)
        assertTrue(machine("laser", """{"evidence":"simulator"}""").simulatorOnly)
        assertTrue(machine("pnp", "{}", capabilities = """{"evidence":"simulator"}""").simulatorOnly)
        assertFalse(machine("laser", """{"evidence":"hardware"}""").simulatorOnly)
        assertFalse(machine("laser").simulatorOnly)
    }

    @Test fun `the nine plugins that only run against a simulator are listed`() {
        assertEquals(listOf("laser", "pnp", "pcb", "cnc", "gerber", "cam", "printer3d", "vision", "cad"), MachineSafety.SIMULATOR_PLUGINS)
    }

    @Test fun `the interlocks come in the order the screen shows them`() {
        assertEquals(emptyList<Interlock>(), machine("laser").interlocks)
        assertEquals(listOf(Interlock.Latched), machine("printer3d", """{"latched":true}""").interlocks)
        assertEquals(listOf(Interlock.EstopLatched), machine("laser", """{"estop_latched":true}""").interlocks)
        assertEquals(listOf(Interlock.Latched, Interlock.EstopLatched, Interlock.Alarm), machine("cnc", """{"alarm":true,"estop_latched":true,"latched":true}""").interlocks)
    }

    @Test fun `an alarm is a flag, a word that is not a calm one, or the state alarm`() {
        assertEquals(listOf(Interlock.Alarm), machine("cnc", """{"alarm":"hard_limit"}""").interlocks)
        assertEquals(listOf(Interlock.Alarm), machine("cnc", """{"state_word":"Alarm"}""").interlocks)
        for (calm in listOf("", "none", "ok", "false", "0", "idle", "run", " OK ")) assertEquals(calm, emptyList<Interlock>(), machine("cnc", """{"alarm":"$calm"}""").interlocks)
        assertEquals(emptyList<Interlock>(), machine("cnc", """{"alarm":false,"state_word":"idle"}""").interlocks)
    }

    @Test fun `a machine with an interlock is latched and one without is not`() {
        assertTrue(machine("laser", """{"estop_latched":true}""").latched)
        assertFalse(machine("laser", """{"estop_latched":false}""").latched)
    }

    @Test fun `a queue that starts jobs by itself is a warning`() {
        assertTrue(machine("printer3d", """{"auto_start_queue":true}""").autoStart)
        assertTrue(machine("printer3d", """{"auto_start":true}""").autoStart)
        assertFalse(machine("printer3d", """{"auto_start_queue":false}""").autoStart)
        assertFalse(machine("printer3d").autoStart)
    }

    @Test fun `the plugins that stop by a route of their own and the ones that stop by cancelling the job`() {
        assertEquals("emergency-stop", MachineSafety.stopRoute("cnc"))
        assertEquals("emergency-stop", MachineSafety.stopRoute("printer3d"))
        for (plugin in listOf("laser", "pnp", "pcb", "gerber", "cam", "vision", "cad", "demo")) assertNull(plugin, MachineSafety.stopRoute(plugin))
    }

    @Test fun `the plugins that latch have a route to release the latch`() {
        assertEquals("release", MachineSafety.releaseRoute("cnc"))
        assertEquals("release", MachineSafety.releaseRoute("printer3d"))
        assertEquals("laser/release", MachineSafety.releaseRoute("laser"))
        for (plugin in listOf("pnp", "pcb", "gerber", "cam", "vision", "cad")) assertNull(plugin, MachineSafety.releaseRoute(plugin))
    }

    @Test fun `the emergency stop is for an operator, with a route or a job, and not for a machine that is already held`() {
        assertTrue(MachineSafety.canStop(true, machine("printer3d")))
        assertTrue(MachineSafety.canStop(true, machine("laser", job = "w-1")))
        assertFalse("a laser with no job has nothing to cancel", MachineSafety.canStop(true, machine("laser")))
        assertFalse("a viewer only watches", MachineSafety.canStop(false, machine("printer3d")))
        assertFalse("a held machine is released, not stopped", MachineSafety.canStop(true, machine("printer3d", """{"latched":true}""")))
    }

    @Test fun `the release is for an administrator and only for a held machine whose plugin latches`() {
        val held = machine("laser", """{"estop_latched":true}""")
        assertTrue(MachineSafety.canRelease(true, held))
        assertFalse("an operator cannot release", MachineSafety.canRelease(false, held))
        assertFalse("nothing to release", MachineSafety.canRelease(true, machine("laser")))
        assertFalse("a pick-and-place machine has no route to release", MachineSafety.canRelease(true, machine("pnp", """{"alarm":true}""")))
    }

    @Test fun `a print waits for a person`() {
        assertTrue(MachineSafety.needsManualStart("printer3d"))
        assertFalse(MachineSafety.needsManualStart("laser"))
    }

    @Test fun `the limits of temperature and the heaters of a printer are read and a missing heater shows a dash`() {
        val printer = machine("printer3d", """{"limits_c":{"hotend":260,"bed":100,"chamber":0},"temperatures":[{"name":"hotend","current_c":24.6,"target_c":200},{"name":"","current_c":1,"target_c":2},{"name":"bed","current_c":23,"target_c":60.4}]}""")
        assertEquals(TempLimits(260.0, 100.0, 0.0), printer.limits)
        assertEquals(listOf(Heater("hotend", 24.6, 200.0), Heater("bed", 23.0, 60.4)), printer.heaters)
        assertEquals(listOf("hotend 25/200", "bed 23/60"), printer.heaters.map { MachineSafety.heaterText(it) })
        assertEquals("260", MachineSafety.limitText(260.0))
        assertEquals("-", MachineSafety.limitText(0.0))
        assertEquals("-", MachineSafety.limitText(Double.NaN))
        assertNull(machine("laser").limits)
        assertEquals(emptyList<Heater>(), machine("laser").heaters)
    }

    @Test fun `the facts leave out what the screen shows in its own way and keep to the limit`() {
        val printer = machine("printer3d", """{"evidence":"simulator","simulator_only":true,"latched":false,"limits_c":{"hotend":260},"temperatures":[],"auto_start_queue":false,"model":"generic","online":true}""")
        assertEquals(listOf("model" to "generic", "online" to "true"), MachineSafety.facts(printer))
        val many = (1..10).joinToString(",") { "\"k$it\":$it" }
        assertEquals(3, MachineSafety.facts(machine("cnc", "{$many}"), 3).size)
    }

    @Test fun `the active jobs of a machine are the running first, then the held, then the queued in their place`() {
        val jobs = listOf(job("a", "p1", "queued", 2), job("b", "p1", "done"), job("c", "p1", "queued", 1), job("d", "p2", "queued"), job("e", "p1", "paused"), job("f", "p1", "running"), job("g", "p1", "cancelled"))
        assertEquals(listOf("f", "e", "c", "a"), MachineSafety.activeJobs(jobs, "p1").map { it.id })
        assertEquals(listOf("d"), MachineSafety.activeJobs(jobs, "p2").map { it.id })
        assertEquals(emptyList<WorkJob>(), MachineSafety.activeJobs(jobs, "none"))
    }

    @Test fun `a job is started only by an operator and only while it waits, and cancelled while it waits or is held`() {
        assertTrue(MachineSafety.canStartJob(true, job("a", "m", "queued")))
        assertFalse(MachineSafety.canStartJob(false, job("a", "m", "queued")))
        for (state in listOf("running", "paused", "done", "failed", "cancelled")) assertFalse(state, MachineSafety.canStartJob(true, job("a", "m", state)))
        assertTrue(MachineSafety.canCancelJob(true, job("a", "m", "queued")))
        assertTrue(MachineSafety.canCancelJob(true, job("a", "m", "paused")))
        assertFalse("a running job is stopped with the emergency stop", MachineSafety.canCancelJob(true, job("a", "m", "running")))
        assertFalse(MachineSafety.canCancelJob(false, job("a", "m", "queued")))
    }

    @Test fun `the jobs of the queue are read and an item without an id is left out`() {
        val text = """{"total":3,"items":[{"id":"w-1","machine":"l1","plugin":"laser","name":"Badge","owner":"ana","state":"running","percent":40,"position":0},{"machine":"x"},{"id":"w-2","machine":"p1"}]}"""
        val jobs = parseWorkJobs(text)
        assertEquals(listOf("w-1", "w-2"), jobs.map { it.id })
        assertEquals(WorkJob("w-1", "l1", "laser", "Badge", "ana", "running", 40, 0), jobs[0])
        assertEquals("queued", jobs[1].state)
        assertTrue(parseWorkJobs("{}").isEmpty())
    }

    @Test fun `the switches the app explains are the four that are off by default, in that order, and the rest are left out`() {
        assertEquals(listOf("mdns_announce", "compat_api", "compat_write", "slice_enabled"), MachineSafety.SWITCH_KEYS)
        val text = """{"switches":[{"key":"url_token","value":false,"safe":true},{"key":"slice_enabled","value":true,"safe":false},{"key":"mdns_announce","value":false,"safe":true},{"key":"compat_api","value":false,"safe":true}]}"""
        val list = parsePolicySwitches(text)
        assertEquals(listOf("mdns_announce", "compat_api", "slice_enabled"), list.map { it.key })
        assertTrue(list.last().on)
        assertFalse(list.last().safe)
        assertTrue(parsePolicySwitches("{}").isEmpty())
    }

    @Test fun `a machine of the fleet is not one of the plugins and a detail that is not an object is no detail`() {
        val text = """{"machines":[{"id":"d","plugin":"bungard","state":"idle","detail":{"latched":true}},{"id":"x","plugin":"cad","state":"idle","detail":null}]}"""
        val list = parseWorkMachines(text)
        assertEquals(listOf("x"), list.map { it.id })
        assertFalse(list.single().latched)
        assertFalse(list.single().simulatorOnly)
        assertEquals("", list.single().job)
    }

    private fun hashed(hash: String, authorized: String = "", state: String = "queued") = WorkJob("w-9", "l1", "laser", "Badge", "ana", state, 0, 0, hash, authorized, if (authorized.isEmpty()) "" else "ana")

    @Test fun `the hash and the authorization of a job are read from the queue`() {
        val text = """{"items":[{"id":"w-1","machine":"l1","state":"queued","toolpath_sha256":"HASH","authorization":{"by":"ana","at":5,"toolpath_sha256":"HASH"}},{"id":"w-2","machine":"l1","state":"queued","toolpath_sha256":"HASH","authorization":null},{"id":"w-3","machine":"l1","state":"queued"}]}""".replace("HASH", "H0")
        val jobs = parseWorkJobs(text)
        assertEquals(listOf("H0", "H0", ""), jobs.map { it.toolpathSha256 })
        assertEquals(listOf("H0", "", ""), jobs.map { it.authorizedHash })
        assertEquals("ana", jobs[0].authorizedBy)
        assertEquals(listOf(JobAuth.Authorized, JobAuth.None, JobAuth.None), jobs.map { MachineSafety.authOf(it) })
    }

    @Test fun `an authorization of another hash is expired and the badge says so`() {
        assertEquals(JobAuth.None, MachineSafety.authOf(hashed("aa")))
        assertEquals(JobAuth.Authorized, MachineSafety.authOf(hashed("aa", "aa")))
        assertEquals(JobAuth.Stale, MachineSafety.authOf(hashed("bb", "aa")))
        assertEquals("a job with no hash cannot be told authorized", JobAuth.Stale, MachineSafety.authOf(hashed("", "aa")))
    }

    @Test fun `the button to authorize is offered to an operator for a queued job whose present hash is not authorized`() {
        assertTrue(MachineSafety.canAuthorize(true, hashed("aa")))
        assertTrue("an expired authorization can be given again", MachineSafety.canAuthorize(true, hashed("bb", "aa")))
        assertFalse("already authorized", MachineSafety.canAuthorize(true, hashed("aa", "aa")))
        assertFalse("a viewer", MachineSafety.canAuthorize(false, hashed("aa")))
        assertFalse("no hash to authorize", MachineSafety.canAuthorize(true, hashed("")))
        for (state in listOf("running", "paused", "done", "failed", "cancelled")) assertFalse(state, MachineSafety.canAuthorize(true, hashed("aa", state = state)))
    }

    @Test fun `the short hash is the first twelve characters and the refusals that ask for a new look are the two of the authorization`() {
        assertEquals("a3f9c2d1e4b7", MachineSafety.shortHash("a3f9c2d1e4b7a3f9c2d1"))
        assertEquals("abc", MachineSafety.shortHash("abc"))
        assertEquals("-", MachineSafety.shortHash(""))
        assertTrue(MachineSafety.needsReview("stale_authorization"))
        assertTrue(MachineSafety.needsReview("authorization_required"))
        assertFalse(MachineSafety.needsReview("machine_busy"))
        assertFalse(MachineSafety.needsReview("forbidden_scope"))
    }
}
