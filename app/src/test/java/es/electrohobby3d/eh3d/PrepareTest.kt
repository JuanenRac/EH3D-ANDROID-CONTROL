package es.electrohobby3d.eh3d

import es.electrohobby3d.eh3d.model.Prepare
import es.electrohobby3d.eh3d.model.Side
import es.electrohobby3d.eh3d.model.SideState
import es.electrohobby3d.eh3d.model.Work
import es.electrohobby3d.eh3d.model.parseJob
import es.electrohobby3d.eh3d.model.parseJobs
import es.electrohobby3d.eh3d.model.parsePreview
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Preparing a job from the phone: the tools of a file, the ones switched off, the start at a tool and the requests the server takes. */
class PrepareTest {
    private val previewText = """
        {"kind":"drill","name":"board","tools":[{"number":2,"spindle_rpm":24000,"count":2,"feed_mm_min":1000},{"number":1,"spindle_rpm":24000,"count":1,"feed_mm_min":1000},{"number":3,"spindle_rpm":24000,"count":1,"feed_mm_min":1000}],
         "holes":[{"tool":2,"x_mm":1,"y_mm":1},{"tool":2,"x_mm":2,"y_mm":1},{"tool":1,"x_mm":3,"y_mm":1},{"tool":3,"x_mm":4,"y_mm":1}],
         "paths":[],"bounds_mm":[1,1,4,1],"items":8,"hole_ends":[2,4,6,8],"estimate_s":12.5,"travel_mm":3.0,"skipped_tools":[],"skipped":0}
    """.trimIndent()

    @Test fun theToolsComeInTheOrderTheJobDoesThem() {
        assertEquals(listOf(2, 1, 3), Prepare.toolOrder(parsePreview(previewText)))
    }

    @Test fun aPreviewMayBeTheViewOrAJobThatCarriesIt() {
        assertEquals(4, parsePreview(previewText).holes.size)
        val job = """{"id":"j1","machine":"m","name":"n","kind":"drill","state":"ready","view":$previewText}"""
        assertEquals(4, parsePreview(job).holes.size)
    }

    @Test fun switchedOffToolsAreLeftOut() {
        val order = listOf(2, 1, 3)
        assertEquals(listOf(1), Prepare.skipTools(order, setOf(1), null))
        assertEquals(emptyList<Int>(), Prepare.skipTools(order, emptySet(), null))
    }

    @Test fun startingAtAToolLeavesOutTheOnesBeforeItInTheOrderOfTheJob() {
        val order = listOf(2, 1, 3)
        assertEquals(listOf(2, 1), Prepare.skipTools(order, emptySet(), 3))
        assertEquals(listOf(2), Prepare.skipTools(order, emptySet(), 1))
        assertEquals(emptyList<Int>(), Prepare.skipTools(order, emptySet(), 2))
        // the ones switched off and the ones before the start are both left out, none twice
        assertEquals(listOf(2, 3), Prepare.skipTools(order, setOf(3, 2), 1))
        assertEquals(listOf(2, 3), Prepare.skipTools(order, setOf(3), 1))
    }

    @Test fun aStartThatIsNotInTheFileLeavesNothingOut() {
        assertEquals(emptyList<Int>(), Prepare.skipTools(listOf(1, 2), emptySet(), 9))
    }

    @Test fun nothingIsLeftWhenEveryToolIsOut() {
        assertTrue(Prepare.nothingLeft(listOf(1, 2), listOf(1, 2)))
        assertFalse(Prepare.nothingLeft(listOf(1, 2), listOf(1)))
        assertFalse(Prepare.nothingLeft(emptyList(), emptyList()))
    }

    @Test fun choosingTheStartAgainTakesItBackAndAToolThatIsOffCannotBeTheStart() {
        assertEquals(3, Prepare.toggleStartAt(null, 3, emptySet()))
        assertNull(Prepare.toggleStartAt(3, 3, emptySet()))
        assertEquals(2, Prepare.toggleStartAt(3, 2, emptySet()))
        assertEquals(3, Prepare.toggleStartAt(3, 1, setOf(1)))
        assertEquals(setOf(1), Prepare.toggleOff(emptySet(), 1))
        assertEquals(emptySet<Int>(), Prepare.toggleOff(setOf(1), 1))
    }

    @Test fun theDiametersAnExcellonFileDeclaresAreRead() {
        val ncd = "M48\nINCH\nT1C0.8\nT02C1.0\nT3C.9\n%\nT1\nX1000Y1000\n"
        assertEquals(mapOf(1 to 0.8, 2 to 1.0, 3 to 0.9), Prepare.declaredDiameters(ncd))
        assertTrue(Prepare.declaredDiameters("%\nT01\nX1000Y1000\n").isEmpty())
    }

    @Test fun theRequestOfAFileCarriesTheTextTheToolsOffAndNoHolesAtTheSameTime() {
        val body = JSONObject(Prepare.fileBody(Side.Drill, "board 1", "T1\nX1Y1", "table", listOf(1, 2)))
        assertEquals("drill", body.getString("kind"))
        assertEquals("T1\nX1Y1", body.getJSONObject("files").getString("ncd"))
        assertEquals("table", body.getJSONObject("files").getString("dtd"))
        assertEquals(2, body.getJSONArray("skip_tools").length())
        assertFalse(body.has("holes"))
        val route = JSONObject(Prepare.fileBody(Side.Route, "", "IN;", "", emptyList()))
        assertEquals("route", route.getString("kind"))
        assertEquals("IN;", route.getJSONObject("files").getString("plt"))
        assertFalse(route.getJSONObject("files").has("rtd"))
        assertFalse(route.has("skip_tools"))
        assertFalse(route.has("name"))
    }

    @Test fun theRequestOfAProgramKeepsTheOutputsOffUnlessAsked() {
        val body = JSONObject(Prepare.gcodeBody("frame", "G21\nM30\n", 0, false))
        assertEquals("gcode", body.getString("kind"))
        assertEquals(0, body.getInt("skip"))
        assertFalse(body.getBoolean("restore_outputs"))
        val on = JSONObject(Prepare.gcodeBody("frame", "G21\nM30\n", 5, true))
        assertEquals(5, on.getInt("skip"))
        assertTrue(on.getBoolean("restore_outputs"))
        assertEquals(0, JSONObject(Prepare.gcodeBody("", "x", -3, false)).getInt("skip"))
    }

    @Test fun whatAMachineTakesFollowsItsConnection() {
        assertEquals(listOf(Work.Drill, Work.Route, Work.Both), Prepare.works("node"))
        assertEquals(listOf(Work.Gcode), Prepare.works("grbl"))
        assertEquals(Work.entries.toList(), Prepare.works("demo"))
    }

    @Test fun aProgramGoesOnFromTheLineTheJobSaysWithoutGoingBeyondTheProgram() {
        assertEquals(3, Prepare.lineCount("a\nb\nc\n"))
        assertEquals(3, Prepare.lineCount("a\nb\nc"))
        assertEquals(0, Prepare.lineCount(""))
        assertEquals(12, Prepare.resumeSkip(12, 40))
        assertEquals(40, Prepare.resumeSkip(99, 40))
        assertEquals(0, Prepare.resumeSkip(-1, 40))
        assertEquals("board", Prepare.jobName("board.NCD"))
    }

    @Test fun aGcodeJobCarriesItsLineAndTheHoldPause() {
        val job = parseJob("""{"id":"g1","machine":"m","name":"frame","kind":"gcode","state":"waiting_operator","line":17,"pause":{"kind":"hold"},"items_total":40,"items_done":12,
            "view":{"kind":"gcode","items":40,"bounds_mm":[0,0,40,10],"estimate_s":5.0,"spindle_warnings":[{"code":"rpm_above_max","tool":0,"value":30000,"limit":24000}]}}""")
        assertEquals(17, job.summary.line)
        assertEquals("hold", job.summary.pause?.kind)
        assertEquals(40, job.view.items)
        assertEquals(1, job.view.spindleWarnings.size)
        assertEquals(0, parseJobs("""[{"id":"a","machine":"m","kind":"drill","state":"done"}]""").first().line)
    }

    private fun side(items: Boolean, off: Set<Int> = emptySet(), startAt: Int? = null) =
        SideState(fileName = "a.ncd", text = "x", view = if (items) parsePreview(previewText) else null, off = off, startAt = startAt)

    @Test fun theStartIsOnlyOfferedWhenEveryFileIsPlannedAndSomethingIsLeft() {
        assertFalse(prepareReady(PrepareUi(work = Work.Drill)))
        assertTrue(prepareReady(PrepareUi(work = Work.Drill, drill = side(true))))
        assertFalse(prepareReady(PrepareUi(work = Work.Drill, drill = side(true, off = setOf(1, 2, 3)))))
        assertFalse(prepareReady(PrepareUi(work = Work.Both, drill = side(true), route = SideState())))
        assertFalse(prepareReady(PrepareUi(work = Work.Drill, drill = side(true), busy = true)))
        assertFalse(prepareReady(PrepareUi(work = Work.Gcode)))
        assertTrue(prepareReady(PrepareUi(work = Work.Gcode, gcode = "G21\nM30\n", gcodeView = parsePreview("""{"kind":"gcode","items":2}"""))))
        assertFalse(PrepareUi().restoreOutputs)
    }
}
