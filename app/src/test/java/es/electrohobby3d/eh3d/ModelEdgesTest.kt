// EH3D-ANDROID-CONTROL - the readers of the answers of the server from their edges: what is missing, null, of the wrong kind or from an older server. The iPhone app has the same cases.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import es.electrohobby3d.eh3d.model.ActiveJob
import es.electrohobby3d.eh3d.model.JobStates
import es.electrohobby3d.eh3d.model.JobSummary
import es.electrohobby3d.eh3d.model.NoticeKind
import es.electrohobby3d.eh3d.model.dbl
import es.electrohobby3d.eh3d.model.fleetNotices
import es.electrohobby3d.eh3d.model.fleetStates
import es.electrohobby3d.eh3d.model.laserStatusPhrase
import es.electrohobby3d.eh3d.model.parseAllowDollar
import es.electrohobby3d.eh3d.model.parseFleet
import es.electrohobby3d.eh3d.model.parseHead
import es.electrohobby3d.eh3d.model.parseJob
import es.electrohobby3d.eh3d.model.parseJobs
import es.electrohobby3d.eh3d.model.parseMeasure
import es.electrohobby3d.eh3d.model.parsePreview
import es.electrohobby3d.eh3d.model.parseQuality
import es.electrohobby3d.eh3d.model.parseRingLight
import es.electrohobby3d.eh3d.model.parseSpindle
import es.electrohobby3d.eh3d.model.parseView
import es.electrohobby3d.eh3d.model.rpmQualityPhrase
import es.electrohobby3d.eh3d.model.str
import es.electrohobby3d.eh3d.network.Roles
import es.electrohobby3d.eh3d.network.describeError
import es.electrohobby3d.eh3d.network.isJson
import es.electrohobby3d.eh3d.network.ticketUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelEdgesTest {
    private fun job(percent: Int, estimate: Double) = ActiveJob("j", "n", "drill", "running", "ana", 0, 0, 0, 0, percent, null, estimate)
    private fun summary(done: Int, total: Int) = JobSummary("j", "m", "n", "drill", "running", "ana", total, done, 0, 0, "", null, 0.0, 0L, null)

    @Test fun `the states a job can go on from are the ones that stopped before the end`() {
        assertTrue(JobStates.canGoOn("cancelled"))
        assertTrue(JobStates.canGoOn("failed"))
        for (state in listOf("ready", "connecting", "running", "waiting_operator", "done", "", "other")) assertFalse(state, JobStates.canGoOn(state))
    }

    @Test fun `a job is active while it connects runs or waits`() {
        for (state in listOf("connecting", "running", "waiting_operator")) assertTrue(state, JobStates.active(state))
        for (state in listOf("ready", "done", "cancelled", "failed", "")) assertFalse(state, JobStates.active(state))
    }

    @Test fun `a job is finished when it is done cancelled or failed and never both active and finished`() {
        for (state in listOf("done", "cancelled", "failed")) assertTrue(state, JobStates.finished(state))
        for (state in listOf("ready", "connecting", "running", "waiting_operator")) assertFalse(state, JobStates.finished(state))
        for (state in listOf("ready", "connecting", "running", "waiting_operator", "done", "cancelled", "failed")) assertFalse(state, JobStates.active(state) && JobStates.finished(state))
    }

    @Test fun `what is left of the estimate follows the percent and never goes below zero or above the estimate`() {
        assertEquals(50.0, job(50, 100.0).remainingS, 1e-9)
        assertEquals(100.0, job(0, 100.0).remainingS, 1e-9)
        assertEquals(0.0, job(100, 100.0).remainingS, 1e-9)
        assertEquals(100.0, job(-20, 100.0).remainingS, 1e-9)
        assertEquals(0.0, job(250, 100.0).remainingS, 1e-9)
    }

    @Test fun `with no estimate nothing is left to wait for`() {
        assertEquals(0.0, job(10, 0.0).remainingS, 1e-9)
        assertEquals(0.0, job(10, -5.0).remainingS, 1e-9)
    }

    @Test fun `the percent of a listed job is its items done over its items total`() {
        assertEquals(0, summary(5, 0).percent)
        assertEquals(0, summary(0, 10).percent)
        assertEquals(50, summary(5, 10).percent)
        assertEquals(33, summary(1, 3).percent)
        assertEquals(100, summary(10, 10).percent)
        assertEquals(100, summary(12, 10).percent)
        assertEquals(0, summary(-3, 10).percent)
    }

    @Test fun `a string member that is missing null or not a string falls back to the default`() {
        val j = JSONObject("""{"a":"x","n":null,"num":5}""")
        assertEquals("x", j.str("a"))
        assertEquals("", j.str("missing"))
        assertEquals("fallback", j.str("missing", "fallback"))
        assertEquals("fallback", j.str("n", "fallback"))
        assertEquals("5", j.str("num"))
    }

    @Test fun `a number member is null when it is missing null or not a number`() {
        val j = JSONObject("""{"a":1.5,"n":null,"s":"text","i":3}""")
        assertEquals(1.5, j.dbl("a")!!, 1e-9)
        assertEquals(3.0, j.dbl("i")!!, 1e-9)
        assertNull(j.dbl("missing"))
        assertNull(j.dbl("n"))
        assertNull(j.dbl("s"))
    }

    @Test fun `a fleet with no summary counts its machines by state`() {
        val fleet = parseFleet("""{"machines":[{"id":"a","state":"idle"},{"id":"b","state":"busy"},{"id":"c","state":"busy"},{"id":"d","state":"waiting"}]}""")
        assertEquals(1, fleet.idle)
        assertEquals(2, fleet.busy)
        assertEquals(1, fleet.waiting)
    }

    @Test fun `the summary of the server wins over the count of the machines`() {
        val fleet = parseFleet("""{"machines":[{"id":"a","state":"idle"}],"summary":{"idle":7,"busy":8,"waiting":9}}""")
        assertEquals(listOf(7, 8, 9), listOf(fleet.idle, fleet.busy, fleet.waiting))
    }

    @Test fun `a machine with no state is idle and one with no job has none`() {
        val machine = parseFleet("""{"machines":[{"id":"a","name":"Mill"}]}""").machines[0]
        assertEquals("idle", machine.state)
        assertNull(machine.job)
        assertEquals("Mill", machine.name)
        assertEquals("", machine.model)
        assertEquals(0, machine.ready)
    }

    @Test fun `entries of the machines list that are not objects are left out`() {
        val fleet = parseFleet("""{"machines":[1,"x",null,{"id":"a"}]}""")
        assertEquals(listOf("a"), fleet.machines.map { it.id })
    }

    @Test fun `the fleet can find a machine by its id`() {
        val fleet = parseFleet("""{"machines":[{"id":"a"},{"id":"b"}]}""")
        assertEquals("b", fleet.machine("b")!!.id)
        assertNull(fleet.machine("zzz"))
    }

    @Test fun `a fleet that is not JSON is an exception for the caller to catch`() {
        assertThrows(org.json.JSONException::class.java) { parseFleet("<html>") }
    }

    @Test fun `the state of a machine is the state of its job, else its own`() {
        val fleet = parseFleet("""{"machines":[{"id":"a","state":"busy","job":{"id":"j","state":"running"}},{"id":"b","state":"idle"}]}""")
        assertEquals(mapOf("a" to "running", "b" to "idle"), fleetStates(fleet))
    }

    @Test fun `there are no notices when there was nothing to compare with`() {
        val fleet = parseFleet("""{"machines":[{"id":"a","state":"busy","job":{"id":"j","state":"waiting_operator"}}]}""")
        assertTrue(fleetNotices(emptyMap(), fleet).isEmpty())
    }

    @Test fun `a machine that was not there before gives no notice`() {
        val fleet = parseFleet("""{"machines":[{"id":"new","state":"busy","job":{"id":"j","state":"waiting_operator"}}]}""")
        assertTrue(fleetNotices(mapOf("old" to "running"), fleet).isEmpty())
    }

    @Test fun `waiting for the operator and ending are told once and a steady state is not told`() {
        val waiting = parseFleet("""{"machines":[{"id":"a","name":"Mill","state":"waiting","job":{"id":"j","state":"waiting_operator"}}]}""")
        assertEquals(NoticeKind.Waiting, fleetNotices(mapOf("a" to "running"), waiting).single().kind)
        assertTrue(fleetNotices(mapOf("a" to "waiting_operator"), waiting).isEmpty())
        val ended = parseFleet("""{"machines":[{"id":"a","name":"Mill","state":"idle"}]}""")
        assertEquals(NoticeKind.Ended, fleetNotices(mapOf("a" to "running"), ended).single().kind)
        assertTrue(fleetNotices(mapOf("a" to "ready"), ended).isEmpty())
    }

    @Test fun `a finished job in the list has a finish time and an unfinished one has none`() {
        val jobs = parseJobs("""[{"id":"a","created":100,"finished":160},{"id":"b","created":200,"finished":null},{"id":"c","created":300}]""")
        assertEquals(160L, jobs[0].finished)
        assertNull(jobs[1].finished)
        assertNull(jobs[2].finished)
        assertEquals(listOf(100L, 200L, 300L), jobs.map { it.created })
    }

    @Test fun `the list of jobs keeps only the objects and an empty list is empty`() {
        assertEquals(0, parseJobs("[]").size)
        assertEquals(listOf("a"), parseJobs("""[1,null,{"id":"a"}]""").map { it.id })
    }

    @Test fun `a job from an older server has no line no resumed from and nobody who started it`() {
        val j = parseJobs("""[{"id":"a","state":"cancelled"}]""")[0]
        assertEquals(0, j.line)
        assertEquals("", j.resumedFrom)
        assertEquals("", j.startedBy)
        assertEquals("", j.stoppedBy)
        assertNull(j.pause)
    }

    @Test fun `a job without a view gets the empty view`() {
        val job = parseJob("""{"id":"a"}""")
        assertEquals(0, job.view.holes.size)
        assertNull(job.view.bounds)
        assertEquals("", job.view.kind)
    }

    @Test fun `a pause without a tool has no tool number`() {
        val pause = parseJobs("""[{"id":"a","pause":{"kind":"spindle_start"}}]""")[0].pause!!
        assertEquals("spindle_start", pause.kind)
        assertNull(pause.tool)
        assertNull(pause.question)
        assertEquals(false, pause.powerOn)
        assertEquals(true, parseJobs("""[{"id":"a","pause":{"kind":"resume","power_on":true}}]""")[0].pause!!.powerOn)
        assertEquals(false, pause.plunge)
        assertEquals(true, parseJobs("""[{"id":"a","pause":{"kind":"resume","plunge":true}}]""")[0].pause!!.plunge)
        assertEquals("q0a1b2c3", parseJobs("""[{"id":"a","pause":{"kind":"hold","question":"q0a1b2c3"}}]""")[0].pause!!.question)
        assertEquals(3, parseJobs("""[{"id":"a","pause":{"kind":"tool_change","tool":3}}]""")[0].pause!!.tool)
    }

    @Test fun `the bounds of a view need four numbers`() {
        assertNull(parseView(JSONObject("""{"bounds_mm":[0,0,10]}""")).bounds)
        assertNull(parseView(JSONObject("""{}""")).bounds)
        val bounds = parseView(JSONObject("""{"bounds_mm":[1,2,11,22]}""")).bounds!!
        assertEquals(listOf(1.0, 2.0, 11.0, 22.0), bounds.toList())
    }

    @Test fun `a point of a path with a single number is dropped`() {
        val view = parseView(JSONObject("""{"paths":[{"pen":2,"points_mm":[[1,2],[3],[4,5]]}]}"""))
        assertEquals(listOf(Pair(1.0, 2.0), Pair(4.0, 5.0)), view.paths[0].points)
        assertEquals(2, view.paths[0].pen)
    }

    @Test fun `a view without lists is empty and does not throw`() {
        val view = parseView(JSONObject("""{"kind":"route","name":"p","items":4}"""))
        assertEquals(4, view.items)
        assertTrue(view.tools.isEmpty() && view.holes.isEmpty() && view.paths.isEmpty() && view.holeEnds.isEmpty())
        assertTrue(view.skippedTools.isEmpty() && view.spindleWarnings.isEmpty() && view.warnings.isEmpty())
        assertNull(view.dryRun)
    }

    @Test fun `the dry run keeps its three axes and counts a missing course flag as within the course`() {
        val dry = parseView(JSONObject("""{"dry_run":{"min_mm":[0,0,0],"max_mm":[10,20,5],"end_mm":[1,1,1],"steps":1234,"seconds":12.5}}""")).dryRun!!
        assertEquals(listOf(10.0, 20.0, 5.0), dry.maxMm)
        assertEquals(1234L, dry.steps)
        assertEquals(12.5, dry.seconds, 1e-9)
        assertTrue(dry.withinCourse)
        val out = parseView(JSONObject("""{"dry_run":{"within_course":false}}""")).dryRun!!
        assertFalse(out.withinCourse)
        assertTrue(out.minMm.isEmpty())
    }

    @Test fun `a warning of the work keeps its values in order and its axis`() {
        val warning = parseView(JSONObject("""{"warnings":[{"code":"out_of_course","axis":"Z","values":[26.5,25]}]}""")).warnings.single()
        assertEquals("out_of_course", warning.code)
        assertEquals("Z", warning.axis)
        assertEquals(listOf(26.5, 25.0), warning.values)
    }

    @Test fun `a tool without depth or iterations has none`() {
        val tool = parseView(JSONObject("""{"tools":[{"number":1,"spindle_rpm":24000,"count":5,"feed_mm_min":300}]}""")).tools.single()
        assertNull(tool.depthMm)
        assertNull(tool.iterations)
        assertEquals(24000, tool.spindleRpm)
    }

    @Test fun `a preview that carries a view is that view and a bare one is itself`() {
        assertEquals("drill", parsePreview("""{"view":{"kind":"drill"}}""").kind)
        assertEquals("route", parsePreview("""{"kind":"route"}""").kind)
    }

    @Test fun `the switch for the dollar lines is off unless the server says on`() {
        assertTrue(parseAllowDollar("""{"allow_dollar":true}"""))
        assertFalse(parseAllowDollar("""{"allow_dollar":false}"""))
        assertFalse(parseAllowDollar("{}"))
    }

    @Test fun `the head of an older node has the defaults and no temperature`() {
        val head = parseHead("""{"node":"n1"}""")
        assertEquals("n1", head.node)
        assertFalse(head.camera)
        assertNull(head.tempC)
        assertEquals("ok", head.heat)
        assertEquals(-1L, head.laserAgeMs)
        assertEquals(0, head.tempSensors)
        assertFalse(head.light)
    }

    @Test fun `the temperature sensors are counted from the list of their addresses and blanks do not count`() {
        assertEquals(2, parseHead("""{"temp_roms":"28aa,28bb"}""").tempSensors)
        assertEquals(1, parseHead("""{"temp_roms":"28aa, ,"}""").tempSensors)
        assertEquals(0, parseHead("""{"temp_roms":""}""").tempSensors)
    }

    @Test fun `the phrase for the laser follows the status and an unknown status is not valid`() {
        assertEquals("", laserStatusPhrase(""))
        assertEquals("Láser: lectura válida", laserStatusPhrase("valid"))
        assertEquals("Láser: señal débil", laserStatusPhrase("signal_low"))
        assertEquals("Láser: demasiada dispersión", laserStatusPhrase("sigma_high"))
        assertEquals("Láser: fuera de rango", laserStatusPhrase("out_of_range"))
        assertEquals("Láser: fallo del sensor", laserStatusPhrase("sensor_fault"))
        assertEquals("Láser: sin lecturas todavía", laserStatusPhrase("none"))
        assertEquals("Láser: lectura no válida", laserStatusPhrase("something new"))
    }

    @Test fun `the phrase for the speed signal is empty for an unknown quality`() {
        assertEquals("Señal de rpm buena", rpmQualityPhrase("good"))
        assertEquals("Señal de rpm con ruido", rpmQualityPhrase("noisy"))
        assertEquals("Sin señal de rpm", rpmQualityPhrase("none"))
        assertEquals("", rpmQualityPhrase(""))
        assertEquals("", rpmQualityPhrase("something new"))
    }

    @Test fun `a check without findings or holes is unknown and an empty one has no channel`() {
        val q = parseQuality("{}")
        assertEquals("unknown", q.verdict)
        assertEquals(0, q.holesTotal)
        assertNull(q.channelMm)
        assertTrue(q.findings.isEmpty())
    }

    @Test fun `a finding without the fails member fails and one that names no hole has none`() {
        val q = parseQuality("""{"verdict":"not_ok","findings":[{"code":"mill_pinched"},{"code":"hole_missing","hole":2,"fails":false}]}""")
        assertTrue(q.findings[0].fails)
        assertNull(q.findings[0].hole)
        assertFalse(q.findings[1].fails)
        assertEquals(2, q.findings[1].hole)
    }

    @Test fun `the ring is off at level zero and its ceiling is eighty unless the node says`() {
        val ring = parseRingLight("{}")
        assertFalse(ring.on)
        assertEquals(0, ring.level)
        assertEquals(80, ring.maxLevel)
        assertEquals(60, parseRingLight("""{"on":true,"level":40,"max_level":60}""").maxLevel)
    }

    @Test fun `a measure that failed keeps the reason`() {
        val m = parseMeasure("""{"ok":false,"why":"signal_low","used":2,"asked":7}""")
        assertFalse(m.ok)
        assertEquals("signal_low", m.why)
        assertEquals(0.0, m.distanceMm, 1e-9)
        assertEquals(2, m.used)
    }

    @Test fun `a spindle with no limits has none of them and the guard defaults to reject`() {
        val info = parseSpindle("""{"spindle":""}""")
        assertEquals("", info.spindle)
        assertEquals("reject", info.guard)
        assertNull(info.original)
        assertNull(info.vfd)
        assertNull(info.active)
        assertNull(info.nodeMaxRpm)
    }

    @Test fun `the error of the server falls back to the status when it names no code`() {
        assertEquals("http_500" to "", describeError(500, "not json"))
        assertEquals("http_404" to "", describeError(404, """{"detail":"x"}""".replace("detail", "other")))
        assertEquals("bad_job" to "the job is gone", describeError(404, """{"error":"bad_job","detail":"the job is gone"}"""))
        assertEquals("http_400" to "", describeError(400, """{"error":"  "}"""))
    }

    @Test fun `only objects and arrays are the JSON of the API`() {
        assertTrue(isJson("{}"))
        assertTrue(isJson("[]"))
        assertTrue(isJson("""{"a":[1,2,{"b":null}]}"""))
        for (text in listOf("", "text", "5", "true", "null", "<html></html>", "{", "[1,")) assertFalse(text, isJson(text))
    }

    @Test fun `a ticket is added with a question mark or with an ampersand and is encoded`() {
        assertEquals("http://h/s?ticket=abc", ticketUrl("http://h/s", "abc"))
        assertEquals("http://h/s?a=1&ticket=abc", ticketUrl("http://h/s?a=1", "abc"))
        assertEquals("http://h/s?ticket=a+b%2Fc%3D", ticketUrl("http://h/s", "a b/c="))
    }

    @Test fun `viewers watch operators operate and only administrators set the spindle`() {
        assertFalse(Roles.canOperate("viewer"))
        assertFalse(Roles.canOperate(""))
        assertTrue(Roles.canOperate("operator"))
        assertTrue(Roles.canOperate("admin"))
        assertFalse(Roles.canAdmin("operator"))
        assertTrue(Roles.canAdmin("admin"))
        assertNotNull(Roles)
    }
}
