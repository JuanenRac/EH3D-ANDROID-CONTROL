package es.electrohobby3d.eh3d

import es.electrohobby3d.eh3d.model.parseFleet
import es.electrohobby3d.eh3d.model.parseJob
import es.electrohobby3d.eh3d.model.parseJobs
import es.electrohobby3d.eh3d.model.parsePreview
import es.electrohobby3d.eh3d.model.parseSpindle
import es.electrohobby3d.eh3d.network.describeError
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the server answers, as the tests of the server make it (the `.json` files of the folder `api-examples`, copied from `tests/examples` of EH3D-SERVER): every example is read by the same functions
 * that read the answers of a real server, and what comes out is compared with the example itself, so an answer that changes and that the app does not follow fails here.
 */
class ApiExamplesTest {
    private fun example(name: String): String =
        (javaClass.classLoader!!.getResource("api-examples/$name.json") ?: error("api-examples/$name.json is missing: tools/sync_api_examples.py of EH3D-SERVER copies it")).readText()

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    @Test fun theFleetIsReadWithEveryMachineOfTheExample() {
        val text = example("fleet")
        val raw = JSONObject(text)
        val fleet = parseFleet(text)
        val machines = raw.getJSONArray("machines").objects()
        assertTrue(machines.size > 1)
        assertEquals(machines.size, fleet.machines.size)
        for (m in machines) {
            val read = fleet.machine(m.getString("id"))
            assertNotNull(m.getString("id"), read)
            assertEquals(m.getString("name"), read!!.name)
            assertEquals(m.getString("model"), read.model)
            assertEquals(m.getString("station"), read.station)
            assertEquals(m.getString("connection"), read.connection)
            assertEquals(m.getString("state"), read.state)
            assertEquals(m.getInt("ready"), read.ready)
            assertEquals(m.isNull("job"), read.job == null)
        }
        val summary = raw.getJSONObject("summary")
        assertEquals(summary.getInt("idle"), fleet.idle)
        assertEquals(summary.getInt("busy"), fleet.busy)
        assertEquals(summary.getInt("waiting"), fleet.waiting)
    }

    @Test fun theListOfJobsAndOneJobAreReadWithWhatTheExampleSays() {
        val list = JSONArray(example("jobs")).objects()
        val read = parseJobs(example("jobs"))
        assertTrue(list.isNotEmpty())
        assertEquals(list.size, read.size)
        for ((raw, job) in list.zip(read)) {
            assertEquals(raw.getString("id"), job.id)
            assertEquals(raw.getString("machine"), job.machine)
            assertEquals(raw.getString("name"), job.name)
            assertEquals(raw.getString("kind"), job.kind)
            assertEquals(raw.getString("state"), job.state)
            assertEquals(raw.getString("by"), job.by)
            assertEquals(raw.getInt("items_total"), job.itemsTotal)
            assertEquals(raw.getInt("holes_total"), job.holesTotal)
            assertEquals(raw.getLong("created"), job.created)
            assertEquals(raw.isNull("finished"), job.finished == null)
            assertEquals(raw.getDouble("estimate_s"), job.estimateS, 1e-9)
        }
        val text = example("job")
        val raw = JSONObject(text)
        val job = parseJob(text)
        assertEquals(raw.getString("id"), job.summary.id)
        assertEquals(list[0].getString("id"), job.summary.id)
        val view = raw.getJSONObject("view")
        assertEquals(view.getJSONArray("holes").length(), job.view.holes.size)
        assertEquals(view.getJSONArray("tools").length(), job.view.tools.size)
        assertEquals(view.getInt("items"), job.view.items)
        assertEquals(view.getJSONArray("hole_ends").length(), job.view.holeEnds.size)
        val bounds = view.getJSONArray("bounds_mm")
        assertEquals(bounds.getDouble(2), job.view.bounds!![2], 1e-9)
        assertEquals(view.getDouble("estimate_s"), job.view.estimateS, 1e-9)
    }

    @Test fun thePreviewIsTheViewThatTheJobCarries() {
        val preview = parsePreview(example("preview"))
        val inJob = parseJob(example("job")).view
        assertTrue(preview.holes.isNotEmpty())
        assertEquals(inJob.holes, preview.holes)
        assertEquals(inJob.items, preview.items)
        assertEquals(inJob.tools.map { it.number }, preview.tools.map { it.number })
        assertTrue(preview.spindleWarnings.isEmpty())
    }

    @Test fun theSpindleIsReadWithItsLimitsAndTheOneInForce() {
        val text = example("spindle")
        val raw = JSONObject(text)
        val spindle = parseSpindle(text)
        assertEquals(raw.getString("spindle"), spindle.spindle)
        assertEquals(raw.getString("guard"), spindle.guard)
        val vfd = raw.getJSONObject("limits").getJSONObject("vfd")
        assertEquals(vfd.getInt("max_rpm"), spindle.vfd!!.maxRpm)
        assertEquals(vfd.getInt("max_z_mm_min"), spindle.vfd!!.maxZMmMin)
        assertEquals(raw.getJSONObject("limits").getJSONObject("original").getInt("min_rpm"), spindle.original!!.minRpm)
        assertEquals(raw.isNull("active"), spindle.active == null)
        if (!raw.isNull("active")) assertEquals(raw.getJSONObject("active").getInt("max_xy_mm_min"), spindle.active!!.maxXyMmMin)
    }

    @Test fun aRefusalGivesItsCodeAndItsDetail() {
        val text = example("error")
        val raw = JSONObject(text)
        val (code, detail) = describeError(422, text)
        assertEquals(raw.getString("error"), code)
        assertEquals(raw.getString("detail"), detail)
    }
}
