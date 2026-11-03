// EH3D-ANDROID-CONTROL - more unit tests of the rules of the app: the preparation of a job, the link, the stops, the drawing and the summary, from their edges.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import es.electrohobby3d.eh3d.model.ActiveJob
import es.electrohobby3d.eh3d.model.DryRun
import es.electrohobby3d.eh3d.model.Fleet
import es.electrohobby3d.eh3d.model.FleetMachine
import es.electrohobby3d.eh3d.model.Hole
import es.electrohobby3d.eh3d.model.Iso
import es.electrohobby3d.eh3d.model.JobView
import es.electrohobby3d.eh3d.model.LinkFailure
import es.electrohobby3d.eh3d.model.LinkPolicy
import es.electrohobby3d.eh3d.model.LinkState
import es.electrohobby3d.eh3d.model.PathView
import es.electrohobby3d.eh3d.model.Plan
import es.electrohobby3d.eh3d.model.Prepare
import es.electrohobby3d.eh3d.model.ServerLink
import es.electrohobby3d.eh3d.model.Side
import es.electrohobby3d.eh3d.model.StopNote
import es.electrohobby3d.eh3d.model.StopOutcome
import es.electrohobby3d.eh3d.model.ToolView
import es.electrohobby3d.eh3d.model.Work
import es.electrohobby3d.eh3d.model.WorkWarning
import es.electrohobby3d.eh3d.model.classifyFailure
import es.electrohobby3d.eh3d.model.drawingBox
import es.electrohobby3d.eh3d.model.followStops
import es.electrohobby3d.eh3d.model.formatDuration
import es.electrohobby3d.eh3d.model.headSpot
import es.electrohobby3d.eh3d.model.holesDone
import es.electrohobby3d.eh3d.model.plain
import es.electrohobby3d.eh3d.model.pointsReached
import es.electrohobby3d.eh3d.model.reachLine
import es.electrohobby3d.eh3d.model.round1
import es.electrohobby3d.eh3d.model.startSummary
import es.electrohobby3d.eh3d.model.stopOutcome
import es.electrohobby3d.eh3d.model.warningArgs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreModelTest {
    private fun view(
        kind: String = "drill", holes: List<Hole> = emptyList(), paths: List<PathView> = emptyList(), tools: List<ToolView> = emptyList(),
        bounds: DoubleArray? = null, items: Int = 0, holeEnds: List<Int> = emptyList(), estimateS: Double = 0.0, dryRun: DryRun? = null, warnings: List<WorkWarning> = emptyList(),
    ) = JobView(kind, "job", tools, holes, paths, bounds, items, holeEnds, estimateS, 0.0, dryRun = dryRun, warnings = warnings)

    private fun tool(n: Int) = ToolView(n, 24000, 1, 1000, null, null)

    // ---- the tools of a job

    @Test fun `the order of the tools is the order the work uses them, holes first and then the pens`() {
        assertEquals(listOf(3, 1, 2), Prepare.toolOrder(view(holes = listOf(Hole(3, 0.0, 0.0), Hole(1, 1.0, 1.0), Hole(3, 2.0, 2.0), Hole(2, 3.0, 3.0)))))
        assertEquals(listOf(2, 1), Prepare.toolOrder(view(kind = "route", paths = listOf(PathView(2, emptyList()), PathView(1, emptyList()), PathView(2, emptyList())))))
        assertEquals(listOf(5, 1, 2), Prepare.toolOrder(view(holes = listOf(Hole(5, 0.0, 0.0)), paths = listOf(PathView(1, emptyList()), PathView(5, emptyList()), PathView(2, emptyList())))))
        assertEquals(listOf(4, 2), Prepare.toolOrder(view(tools = listOf(tool(4), tool(2), tool(4)))))
        assertEquals(emptyList<Int>(), Prepare.toolOrder(view()))
    }

    @Test fun `the tools left out are the switched off ones, the ones before the start, and the unknown ones last`() {
        val order = listOf(3, 1, 2)
        assertEquals(listOf(3, 2), Prepare.skipTools(order, setOf(2), 1))
        assertEquals(listOf(3, 1), Prepare.skipTools(order, emptySet(), 2))
        assertEquals(emptyList<Int>(), Prepare.skipTools(order, emptySet(), 3))
        assertEquals(emptyList<Int>(), Prepare.skipTools(order, emptySet(), 99))
        assertEquals(listOf(7, 9), Prepare.skipTools(listOf(1), setOf(9, 7), null))
        assertEquals(listOf(1, 7), Prepare.skipTools(listOf(1, 2), setOf(7, 1), null))
        assertEquals(emptyList<Int>(), Prepare.skipTools(emptyList(), emptySet(), 1))
    }

    @Test fun `nothing is left to do only when the file has tools and every one is out`() {
        assertFalse(Prepare.nothingLeft(emptyList(), emptyList()))
        assertFalse(Prepare.nothingLeft(listOf(1, 2), listOf(1)))
        assertTrue(Prepare.nothingLeft(listOf(1, 2), listOf(2, 1)))
        assertTrue(Prepare.nothingLeft(listOf(1), listOf(1, 5)))
    }

    @Test fun `choosing the start and switching a tool off follow the rules of the screen`() {
        assertEquals(3, Prepare.toggleStartAt(null, 3, emptySet()))
        assertNull(Prepare.toggleStartAt(3, 3, emptySet()))
        assertEquals(2, Prepare.toggleStartAt(3, 2, emptySet()))
        assertEquals(3, Prepare.toggleStartAt(3, 2, setOf(2)))
        assertNull(Prepare.toggleStartAt(null, 2, setOf(2)))
        assertEquals(setOf(1, 2), Prepare.toggleOff(setOf(1), 2))
        assertEquals(setOf(1), Prepare.toggleOff(setOf(1, 2), 2))
        assertEquals(emptySet<Int>(), Prepare.toggleOff(setOf(2), 2))
    }

    @Test fun `the diameters a drilling file declares are read from its header`() {
        assertEquals(mapOf(1 to 0.8, 2 to 1.0, 12 to 3.175), Prepare.declaredDiameters("M48\nT01C0.80\nT02C1.0\r\n  T12 C3.175\n%\nT01C9.9\n"))
        assertEquals(mapOf(1 to 0.8), Prepare.declaredDiameters("T1C.8F200S50"))
        assertEquals(emptyMap<Int, Double>(), Prepare.declaredDiameters("M48\nMETRIC\n%\nT01\nX1Y1\n"))
        assertEquals(emptyMap<Int, Double>(), Prepare.declaredDiameters(""))
        // only the beginning of the file is read
        assertEquals(emptyMap<Int, Double>(), Prepare.declaredDiameters("\n".repeat(20_000) + "T01C0.80\n"))
        assertEquals(mapOf(1 to 0.8), Prepare.declaredDiameters("\n".repeat(19_000) + "T01C0.80\n"))
        // a tool that is not at the start of its line is not a declaration
        assertEquals(emptyMap<Int, Double>(), Prepare.declaredDiameters("G05 T01C0.80\n"))
    }

    // ---- the requests

    @Test fun `the request of a file has its slots, and the name and the skips only when there is something to say`() {
        val drill = JSONObject(Prepare.fileBody(Side.Drill, "Placa ñandú 日本", "M48\n", "tools", listOf(2, 5)))
        assertEquals("drill", drill.getString("kind"))
        assertEquals("M48\n", drill.getJSONObject("files").getString("ncd"))
        assertEquals("tools", drill.getJSONObject("files").getString("dtd"))
        assertEquals("Placa ñandú 日本", drill.getString("name"))
        assertEquals(listOf(2, 5), (0 until drill.getJSONArray("skip_tools").length()).map { drill.getJSONArray("skip_tools").getInt(it) })
        val route = JSONObject(Prepare.fileBody(Side.Route, "  ", "IN;", "  \n", emptyList()))
        assertEquals("route", route.getString("kind"))
        assertEquals("IN;", route.getJSONObject("files").getString("plt"))
        assertFalse(route.getJSONObject("files").has("rtd") || route.has("name") || route.has("skip_tools"))
        assertFalse(route.getJSONObject("files").has("ncd"))
    }

    @Test fun `the request of a program never has a negative skip and the outputs are restored only when asked`() {
        val a = JSONObject(Prepare.gcodeBody("p", "G0 X1", -5, false))
        assertEquals(0, a.getInt("skip"))
        assertFalse(a.getBoolean("restore_outputs"))
        assertEquals("p", a.getString("name"))
        val b = JSONObject(Prepare.gcodeBody("", "G0 X1\nM2", 7, true))
        assertEquals(7, b.getInt("skip"))
        assertTrue(b.getBoolean("restore_outputs"))
        assertFalse(b.has("name"))
        assertEquals("gcode", b.getString("kind"))
    }

    @Test fun `a program counts its lines as the skip does, and the way to go on never goes past the end`() {
        assertEquals(0, Prepare.lineCount(""))
        assertEquals(1, Prepare.lineCount("a"))
        assertEquals(1, Prepare.lineCount("a\n"))
        assertEquals(1, Prepare.lineCount("\n"))
        assertEquals(2, Prepare.lineCount("a\n\n"))
        assertEquals(3, Prepare.lineCount("a\n\nb"))
        assertEquals(2, Prepare.lineCount("a\r\nb\r\n"))
        assertEquals(0, Prepare.resumeSkip(-3, 10))
        assertEquals(10, Prepare.resumeSkip(99, 10))
        assertEquals(4, Prepare.resumeSkip(4, 10))
        assertEquals(0, Prepare.resumeSkip(4, -1))
        assertEquals(0, Prepare.resumeSkip(0, 0))
    }

    @Test fun `the name of a job is its file without the extension and no longer than sixty`() {
        assertEquals("board.v2", Prepare.jobName("board.v2.ncd"))
        assertEquals("noext", Prepare.jobName("noext"))
        assertEquals(60, Prepare.jobName("x".repeat(100) + ".nc").length)
        assertEquals("pieza ñandú", Prepare.jobName("pieza ñandú.plt"))
    }

    @Test fun `each connection takes the works it can do`() {
        assertEquals(listOf(Work.Gcode), Prepare.works("grbl"))
        assertEquals(listOf(Work.Drill, Work.Route, Work.Both), Prepare.works("node"))
        assertEquals(Work.entries.toList(), Prepare.works("demo"))
        assertEquals(Work.entries.toList(), Prepare.works("something-new"))
        assertFalse(Prepare.machineTakes("node", Work.Gcode) || Prepare.machineTakes("grbl", Work.Drill))
    }

    // ---- the link

    @Test fun `the wait before a retry grows with the failures and stops at the last one`() {
        val p = LinkPolicy()
        assertEquals(listOf(1_500L, 1_500L, 3_000L, 6_000L, 10_000L, 10_000L), listOf(-5, 0, 2, 3, 4, 400).map { p.retryDelayMs(it) })
        assertEquals(1_500L, p.retryDelayMs(1))
        assertEquals(3_000L, LinkPolicy(2, 5, listOf(1_000L, 3_000L)).retryDelayMs(2))
        assertEquals(3_000L, LinkPolicy(2, 5, listOf(1_000L, 3_000L)).retryDelayMs(50))
    }

    @Test fun `one failure is not yet no connection, old data is stale, two failures are offline and another service is the wrong server`() {
        val ok = ServerLink().succeeded(10_000)
        assertEquals(LinkState.Connected, ok.state())
        assertEquals(0L, ok.ageS())
        val one = ok.failed(LinkFailure.Unreachable, 12_000)
        assertEquals(LinkState.Connected, one.state())
        assertEquals(2L, one.ageS())
        assertEquals(LinkState.Stale, ok.failed(LinkFailure.Unreachable, 15_000).state())
        assertEquals(LinkState.Connected, ok.failed(LinkFailure.Unreachable, 14_999).state())
        assertEquals(LinkState.Offline, one.failed(LinkFailure.Unreachable, 13_000).state())
        assertEquals(LinkState.WrongServer, ok.failed(LinkFailure.WrongServer, 11_000).state())
        assertEquals(LinkState.WrongServer, ok.failed(LinkFailure.WrongServer, 11_000).failed(LinkFailure.WrongServer, 12_000).state())
        assertEquals(LinkState.Offline, ok.failed(LinkFailure.WrongServer, 11_000).failed(LinkFailure.Unreachable, 12_000).state())
        assertEquals(LinkState.Connected, one.succeeded(20_000).state())
        assertEquals(0, one.succeeded(20_000).failures)
    }

    @Test fun `without a good answer the age is unknown and a clock that went back never gives a negative age`() {
        assertNull(ServerLink().ageS())
        assertEquals(LinkState.Offline, ServerLink().failed(LinkFailure.Unreachable, 1).failed(LinkFailure.Unreachable, 2).state())
        assertEquals(LinkState.Connected, ServerLink().failed(LinkFailure.Unreachable, 1).state())
        assertEquals(0L, ServerLink(1, 50_000, 10_000, LinkFailure.Unreachable).ageS())
    }

    @Test fun `a failure is told apart by its status and its code`() {
        assertEquals(LinkFailure.Unreachable, classifyFailure(0, ""))
        assertEquals(LinkFailure.Unreachable, classifyFailure(0, "bad_json"))
        assertEquals(LinkFailure.WrongServer, classifyFailure(200, "bad_json"))
        assertEquals(LinkFailure.WrongServer, classifyFailure(500, "bad_json"))
        assertEquals(LinkFailure.Unreachable, classifyFailure(500, ""))
        assertEquals(LinkFailure.Unreachable, classifyFailure(503, "unavailable"))
        assertEquals(LinkFailure.WrongServer, classifyFailure(404, "http_404"))
        assertEquals(LinkFailure.WrongServer, classifyFailure(400, "http_400"))
        for (status in listOf(401, 403, 429)) assertNull(classifyFailure(status, "http_$status"))
        assertNull(classifyFailure(404, "no_such_job"))
        assertNull(classifyFailure(200, ""))
        assertNull(classifyFailure(302, "http_302"))
        assertNull(classifyFailure(399, "http_399"))
    }

    // ---- the stops

    private fun machine(state: String, job: Boolean) = FleetMachine(
        "m1", "Bungard", "ccd-mtc", "taller", "node", state,
        if (job) ActiveJob("j", "n", "drill", "running", "ana", 1, 2, 0, 1, 50, null, 10.0) else null, 0,
    )

    private fun fleetOf(m: FleetMachine?) = Fleet(listOfNotNull(m), 0, 0, 0)

    @Test fun `a stop is only sent until the fleet is looked at, and then it is confirmed or the machine still works`() {
        assertEquals(StopOutcome.Sent, stopOutcome(null, false))
        assertEquals(StopOutcome.Sent, stopOutcome(machine("busy", true), false))
        assertEquals(StopOutcome.Confirmed, stopOutcome(null, true))
        assertEquals(StopOutcome.Confirmed, stopOutcome(machine("idle", false), true))
        assertEquals(StopOutcome.StillWorking, stopOutcome(machine("idle", true), true))
        assertEquals(StopOutcome.StillWorking, stopOutcome(machine("busy", false), true))
        assertEquals(StopOutcome.StillWorking, stopOutcome(machine("waiting", true), true))
    }

    @Test fun `a look that was asked for before the stop says nothing about it`() {
        val note = StopNote("m1", "Bungard", atMs = 5_000)
        val out = followStops(listOf(note), fleetOf(machine("idle", false)), askedMs = 4_999, nowMs = 5_500)
        assertEquals(listOf(note), out)
        val same = followStops(listOf(note), fleetOf(machine("idle", false)), askedMs = 5_000, nowMs = 5_500)
        assertEquals(StopOutcome.Confirmed, same.single().outcome)
    }

    @Test fun `the machine that still works is given three seconds before it is said so`() {
        val note = StopNote("m1", "Bungard", atMs = 1_000)
        val busy = fleetOf(machine("busy", true))
        assertEquals(StopOutcome.Sent, followStops(listOf(note), busy, 1_500, 3_999).single().outcome)
        assertEquals(StopOutcome.StillWorking, followStops(listOf(note), busy, 1_500, 4_000).single().outcome)
        // and it stays on the screen as long as the machine works
        val later = followStops(listOf(note), busy, 1_500, 600_000)
        assertEquals(StopOutcome.StillWorking, later.single().outcome)
        assertNull(later.single().resolvedMs)
    }

    @Test fun `a confirmed stop stays eight seconds from its confirmation and then goes`() {
        val note = StopNote("m1", "Bungard", atMs = 1_000)
        val idle = fleetOf(machine("idle", false))
        val first = followStops(listOf(note), idle, 2_000, 10_000)
        assertEquals(10_000L, first.single().resolvedMs)
        // the confirmation time is the first one, not the last look
        val again = followStops(first, idle, 14_000, 17_999)
        assertEquals(10_000L, again.single().resolvedMs)
        assertTrue(followStops(first, idle, 18_000, 18_000).isNotEmpty())
        assertTrue(followStops(first, idle, 18_001, 18_001).isEmpty())
        // a machine that left the fleet is a machine that is not working
        assertEquals(StopOutcome.Confirmed, followStops(listOf(note), fleetOf(null), 2_000, 10_000).single().outcome)
        assertTrue(followStops(emptyList(), idle, 0, 0).isEmpty())
    }

    // ---- the drawing

    private val holes = listOf(Hole(1, 1.0, 1.0), Hole(1, 2.0, 1.0), Hole(2, 3.0, 1.0))

    @Test fun `the holes done are the holes whose end has been reached`() {
        val v = view(holes = holes, holeEnds = listOf(2, 4, 6))
        assertEquals(listOf(0, 0, 0, 1, 1, 2, 3, 3), listOf(-1, 0, 1, 2, 3, 4, 6, 100).map { holesDone(v, it) })
        assertEquals(0, holesDone(view(), 10))
    }

    @Test fun `a drilling job is at the next hole and at the last one when all are done`() {
        val v = view(holes = holes, holeEnds = listOf(2, 4, 6))
        assertEquals(1.0, headSpot(v, 0, 6)!!.x, 0.0)
        assertEquals(2.0, headSpot(v, 2, 6)!!.x, 0.0)
        assertEquals(3.0, headSpot(v, 4, 6)!!.x, 0.0)
        assertEquals(3.0, headSpot(v, 6, 6)!!.x, 0.0)
        assertEquals(3.0, headSpot(v, 600, 6)!!.x, 0.0)
        assertNull(headSpot(view(), 0, 0))
        // holes without paths are drilled even when the kind says route
        assertEquals(1.0, headSpot(view(kind = "route", holes = holes, holeEnds = listOf(2, 4, 6)), 0, 6)!!.x, 0.0)
    }

    @Test fun `a routing job moves along its paths in proportion to the items done`() {
        val v = view(kind = "route", paths = listOf(PathView(1, listOf(Pair(0.0, 0.0), Pair(10.0, 0.0))), PathView(1, listOf(Pair(10.0, 10.0)))), items = 4)
        fun at(done: Int, total: Int = 4) = headSpot(v, done, total)!!
        assertEquals(0.0, at(0).x, 1e-9)
        assertEquals(5.0, at(1).x, 1e-9)
        assertEquals(10.0, at(2).x, 1e-9)
        assertEquals(5.0, at(3).y, 1e-9)
        assertEquals(10.0, at(4).y, 1e-9)
        assertEquals(10.0, at(99).y, 1e-9)
        assertEquals(0.0, at(-3).x, 1e-9)
        // the total of the view is used when the server did not say one
        assertEquals(5.0, headSpot(v, 1, 0)!!.x, 1e-9)
        val empty = view(kind = "route", paths = listOf(PathView(1, listOf(Pair(7.0, 8.0)))), items = 0)
        assertEquals(7.0, headSpot(empty, 5, 0)!!.x, 1e-9)
        assertNull(headSpot(view(kind = "route", paths = listOf(PathView(1, emptyList()))), 1, 1))
    }

    @Test fun `the points reached follow the items and never go past the paths`() {
        val v = view(kind = "route", paths = listOf(PathView(1, List(4) { Pair(it.toDouble(), 0.0) }), PathView(2, List(2) { Pair(0.0, it.toDouble()) })), items = 12)
        assertEquals(listOf(0, 3, 6, 6, 0), listOf(0, 6, 12, 99, -4).map { pointsReached(v, it, 12) })
        assertEquals(3, pointsReached(v, 6, 0))
        assertEquals(0, pointsReached(view(kind = "route", items = 5), 3, 5))
        assertEquals(0, pointsReached(v.copy(items = 0), 3, 0))
    }

    @Test fun `the box of the drawing has a margin, a floor of a millimetre and a table when there is no work`() {
        assertEquals(listOf(-4.0, -4.0, 104.0, 84.0), drawingBox(view()).toList())
        assertEquals(listOf(1.0, 1.0, 10.0, 10.0), drawingBox(view(bounds = doubleArrayOf(5.0, 5.0, 5.0, 5.0))).toList())
        assertEquals(listOf(0.0, 0.0, 50.0, 20.0), drawingBox(view(bounds = doubleArrayOf(0.0, 0.0, 50.0, 20.0)), 0.0).toList())
        assertEquals(listOf(-3.0, -3.0, 13.0, 13.0), drawingBox(view(bounds = doubleArrayOf(0.0, 0.0, 10.0, 10.0)), 3.0).toList())
    }

    @Test fun `a plan fits the box in the canvas, centred, with Y going up`() {
        val plan = Plan(doubleArrayOf(0.0, 0.0, 100.0, 50.0), 200f, 200f)
        assertEquals(2f, plan.scale, 1e-6f)
        assertEquals(0f, plan.x(0.0), 1e-4f)
        assertEquals(200f, plan.x(100.0), 1e-4f)
        assertEquals(150f, plan.y(0.0), 1e-4f)
        assertEquals(50f, plan.y(50.0), 1e-4f)
        val tall = Plan(doubleArrayOf(-10.0, -10.0, 10.0, 30.0), 100f, 400f)
        assertEquals(5f, tall.scale, 1e-6f)
        assertEquals(0f, tall.x(-10.0), 1e-4f)
        assertEquals(200f, tall.y(10.0), 1e-4f)
    }

    @Test fun `the isometric view from above is the plan, and the scale that fits grows with the canvas`() {
        val top = Iso(50.0, 25.0, 0.0, Math.PI / 2)
        val p = top.project(60.0, 35.0)
        assertEquals(10f, p.sx, 1e-4f)
        assertEquals(-10f, p.sy, 1e-4f)
        assertEquals(2f, top.fit(100.0, 50.0, 0.0, 200f, 200f, 0f), 1e-3f)
        assertTrue(top.fit(100.0, 50.0, 0.0, 400f, 400f) > top.fit(100.0, 50.0, 0.0, 200f, 200f))
        assertTrue(top.fit(0.0, 0.0, 0.0, 200f, 200f) > 0f)
        val side = Iso(0.0, 0.0, 0.0, 0.0)
        val high = side.project(0.0, 0.0, 10.0)
        assertEquals(-10f, high.sy, 1e-4f)
        // the table turned a whole circle is the table
        val turned = Iso(50.0, 25.0, 2 * Math.PI, 0.5).project(70.0, 20.0, 3.0)
        val still = Iso(50.0, 25.0, 0.0, 0.5).project(70.0, 20.0, 3.0)
        assertEquals(still.sx, turned.sx, 1e-3f)
        assertEquals(still.sy, turned.sy, 1e-3f)
    }

    @Test fun `a time is written in hours and minutes, minutes and seconds, or seconds`() {
        assertEquals(listOf("0 s", "0 s", "59 s", "1 min 00 s", "1 min 01 s", "59 min 59 s", "1 h 00 min", "1 h 01 min", "27 h 46 min"), listOf(0.0, -5.0, 59.9, 60.0, 61.0, 3599.0, 3600.0, 3661.0, 100_000.0).map { formatDuration(it) })
        assertEquals("0 s", formatDuration(Double.NaN))
    }

    // ---- the summary

    @Test fun `numbers are rounded half up to one decimal and written as short as they can be`() {
        assertEquals(0.3, round1(0.25), 1e-12)
        assertEquals(-0.2, round1(-0.25), 1e-12)
        assertEquals(10.0, round1(9.96), 1e-12)
        assertEquals(0.0, round1(0.04), 1e-12)
        assertEquals(listOf("10", "1.5", "0", "-2.5", "0.1", "1000000"), listOf(10.0, 1.5, 0.0, -2.5, 0.1, 1_000_000.0).map { plain(it) })
        assertEquals(listOf("0", "0", "0"), listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).map { plain(it) })
        assertEquals("0", plain(-0.0))
    }

    @Test fun `the reach of the axes is on one line with a dot for the decimals in any language`() {
        assertEquals("X 0.0..30.0   Y 0.0..20.0   Z -1.5..25.8", reachLine(DryRun(listOf(0.0, 0.0, -1.5), listOf(30.0, 20.0, 25.8), emptyList(), 10, 1.0, true)))
        assertEquals("X 1.0..2.0   Y 0.0..0.0   Z 0.0..0.0", reachLine(DryRun(listOf(1.0), listOf(2.0), emptyList(), 10, 1.0, true)))
    }

    @Test fun `the summary counts what the kind of job counts and takes the time from the dry run before the estimate`() {
        val reach = DryRun(listOf(0.0, 0.0, 0.0), listOf(10.0, 10.0, 1.0), emptyList(), 100, 42.0, false)
        val drill = startSummary(view(kind = "drill", holes = holes, tools = listOf(tool(1), tool(2)), bounds = doubleArrayOf(1.0, 1.0, 3.04, 1.0), estimateS = 9.0, dryRun = reach))
        assertEquals(3, drill.count)
        assertEquals(2, drill.tools)
        assertEquals(Pair(2.0, 0.0), drill.sizeMm)
        assertEquals(42.0, drill.seconds!!, 0.0)
        assertTrue(drill.outOfCourse)
        val route = startSummary(view(kind = "route", paths = listOf(PathView(1, emptyList()), PathView(1, emptyList())), estimateS = 9.0))
        assertEquals(2, route.count)
        assertEquals(9.0, route.seconds!!, 0.0)
        assertFalse(route.outOfCourse)
        assertNull(route.sizeMm)
        val gcode = startSummary(view(kind = "gcode", items = 77))
        assertEquals(77, gcode.count)
        assertNull(gcode.seconds)
        assertFalse(startSummary(view(dryRun = reach.copy(withinCourse = true))).outOfCourse)
    }

    @Test fun `every warning gives the numbers of its phrase in order and an unknown one gives its code`() {
        assertEquals(listOf("0.5", "0.3"), warningArgs(WorkWarning("tiny", "", listOf(0.46, 0.25))))
        assertEquals(listOf("400", "300", "300", "200"), warningArgs(WorkWarning("too_big", "", listOf(400.0, 300.04, 299.96, 200.0))))
        assertEquals(listOf("123.5"), warningArgs(WorkWarning("far", "", listOf(123.45))))
        assertEquals(listOf("1.02", "1"), warningArgs(WorkWarning("scale", "", listOf(1.02, 1.0))))
        assertEquals(listOf("X", "5.3"), warningArgs(WorkWarning("out_of_course", "X", listOf(5.25))))
        assertEquals(listOf("something_new"), warningArgs(WorkWarning("something_new", "", listOf(1.0))))
        assertEquals(listOf("0", "0"), warningArgs(WorkWarning("tiny", "", emptyList())))
        assertEquals(listOf("0", "0"), warningArgs(WorkWarning("scale", "", emptyList())))
        assertEquals(listOf("Y", "0"), warningArgs(WorkWarning("out_of_course", "Y", emptyList())))
        assertNotNull(warningArgs(WorkWarning("far", "", emptyList())))
    }

    // ---- the addresses

    @Test fun `an address with a port that does not exist is not an address`() {
        for (url in listOf("http://192.168.0.5:0", "http://192.168.0.5:65536", "http://192.168.0.5:99999", "https://eh3d.example:70000")) assertNull(url, ServerEndpoint.parse(url))
        assertEquals("http://192.168.0.5:65535", ServerEndpoint.parse("http://192.168.0.5:65535")?.origin)
        assertEquals("http://192.168.0.5:1", ServerEndpoint.parse("http://192.168.0.5:1")?.origin)
        assertEquals("https://eh3d.example", ServerEndpoint.parse("https://eh3d.example")?.origin)
    }

    @Test fun `the typed address needs a secure connection only outside the house, and a bare public address is flagged`() {
        assertFalse(ServerEndpoint.needsSecureConnection(""))
        assertFalse(ServerEndpoint.needsSecureConnection("192.168.0.5"))
        assertFalse(ServerEndpoint.needsSecureConnection("http://10.0.0.1:18200/api"))
        assertFalse(ServerEndpoint.needsSecureConnection("https://eh3d.example"))
        assertFalse(ServerEndpoint.needsSecureConnection("HTTPS://eh3d.example"))
        assertTrue(ServerEndpoint.needsSecureConnection("eh3d.example"))
        assertTrue(ServerEndpoint.needsSecureConnection("8.8.8.8:18200"))
        assertTrue(ServerEndpoint.needsSecureConnection("http://eh3d.example"))
        assertTrue(ServerEndpoint.usesPublicIpAddress("8.8.8.8"))
        assertTrue(ServerEndpoint.usesPublicIpAddress("https://8.8.8.8:443"))
        assertFalse(ServerEndpoint.usesPublicIpAddress("192.168.0.5"))
        assertFalse(ServerEndpoint.usesPublicIpAddress("eh3d.example"))
        assertFalse(ServerEndpoint.usesPublicIpAddress("010.1.1.1.5"))
        assertFalse(ServerEndpoint.usesPublicIpAddress("  "))
    }

    @Test fun `a host and a port that are typed are put together safely`() {
        assertEquals("http://192.168.0.5:18200", ServerEndpoint.fromHostAndPort("  192.168.0.5  ", " 18200 ")?.origin)
        assertEquals("http://localhost:1", ServerEndpoint.fromHostAndPort("localhost", "1")?.origin)
        assertEquals("http://192.168.0.5:65535", ServerEndpoint.fromHostAndPort("http://192.168.0.5/some/path", "65535")?.origin)
        for (port in listOf("", "0", "-1", "65536", "abc", "18 200", "1e3")) assertNull("port '$port'", ServerEndpoint.fromHostAndPort("192.168.0.5", port))
        for (host in listOf("", " ", "user@192.168.0.5", "192.168.0.5?x=1", "192.168.0.5#f")) assertNull("host '$host'", ServerEndpoint.fromHostAndPort(host, "18200"))
    }
}
