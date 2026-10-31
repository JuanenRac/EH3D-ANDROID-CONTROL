package es.electrohobby3d.eh3d

import es.electrohobby3d.eh3d.model.Fleet
import es.electrohobby3d.eh3d.model.JobStates
import es.electrohobby3d.eh3d.model.parseFleet
import es.electrohobby3d.eh3d.model.parseHead
import es.electrohobby3d.eh3d.model.parseJob
import es.electrohobby3d.eh3d.model.parseJobs
import es.electrohobby3d.eh3d.model.parseMeasure
import es.electrohobby3d.eh3d.network.Roles
import es.electrohobby3d.eh3d.network.describeError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the server says (docs/API.md of EH3D-SERVER), read into the data the screens show. */
class ModelsTest {
    private val fleetText = """
        {"stations":[{"id":"bench","machines":["bungard","spare"]}],
         "machines":[
          {"id":"bungard","name":"Bungard CCD","model":"ccd-mtc","station":"bench","connection":"node","state":"waiting","ready":2,
           "job":{"id":"j7","name":"board 1","kind":"drill","state":"waiting_operator","by":"ana","items_done":17,"items_total":34,"holes_done":8,"holes_total":16,"percent":50,
                  "pause":{"kind":"tool_change","tool":2},"estimate_s":120.0}},
          {"id":"spare","name":"Spare","model":"","station":"bench","connection":"demo","state":"idle","ready":0,"job":null}],
         "summary":{"machines":2,"idle":1,"busy":0,"waiting":1,"stations":1}}
    """.trimIndent()

    @Test fun theFleetIsReadWithItsJobsAndThePauseOfAJob() {
        val fleet = parseFleet(fleetText)
        assertEquals(2, fleet.machines.size)
        assertEquals(Triple(1, 0, 1), Triple(fleet.idle, fleet.busy, fleet.waiting))
        val m = fleet.machine("bungard")!!
        assertEquals("waiting", m.state)
        assertEquals(2, m.ready)
        val job = m.job!!
        assertEquals(50, job.percent)
        assertEquals("tool_change", job.pause?.kind)
        assertEquals(2, job.pause?.tool)
        // half the estimate is left
        assertEquals(60.0, job.remainingS, 1e-9)
        assertNull(fleet.machine("spare")!!.job)
        assertNull(fleet.machine("nobody"))
    }

    @Test fun anEmptyOrOddFleetDoesNotBreakTheScreens() {
        assertEquals(Fleet.EMPTY.machines, parseFleet("""{"machines":[]}""").machines)
        val odd = parseFleet("""{"machines":[{"id":"a"}]}""")
        assertEquals("idle", odd.machines.single().state)
        assertEquals(1, odd.idle)
    }

    @Test fun theJobWithItsViewIsRead() {
        val job = parseJob("""
            {"id":"j7","machine":"bungard","name":"board 1","kind":"drill","state":"running","created":10,"started":11,"finished":null,"by":"ana",
             "items_total":34,"items_done":14,"holes_total":3,"holes_done":1,"message":"","pause":null,"estimate_s":28.8,
             "view":{"kind":"drill","name":"board 1","tools":[{"number":1,"spindle_rpm":24000,"count":3,"feed_mm_min":1000,"depth_mm":null,"iterations":null}],
                     "holes":[{"tool":1,"x_mm":10.0,"y_mm":10.0},{"tool":1,"x_mm":20.0,"y_mm":10.0},{"tool":1,"x_mm":30.0,"y_mm":20.0}],"paths":[],
                     "bounds_mm":[10.0,10.0,30.0,20.0],"items":34,"hole_ends":[14,17,24],"estimate_s":28.8,"travel_mm":10.0}}
        """.trimIndent())
        assertEquals("running", job.summary.state)
        assertEquals(41, job.summary.percent)
        assertEquals(3, job.view.holes.size)
        assertEquals(24000, job.view.tools.single().spindleRpm)
        assertNull(job.view.tools.single().depthMm)
        assertEquals(listOf(14, 17, 24), job.view.holeEnds)
        assertEquals(30.0, job.view.bounds!![2], 0.0)
        assertTrue(JobStates.active(job.summary.state))
        assertFalse(JobStates.finished(job.summary.state))
    }

    @Test fun aRoutingJobKeepsItsPathsInMillimetres() {
        val job = parseJob("""{"id":"r","machine":"m","name":"","kind":"route","state":"ready","by":"x","items_total":4,"items_done":0,"holes_total":0,"holes_done":0,"message":"","estimate_s":3,
            "view":{"kind":"route","name":"","tools":[],"holes":[],"paths":[{"pen":1,"points_mm":[[10,10],[40,10],[40,30]]}],"bounds_mm":[10,10,40,30],"items":4,"hole_ends":[],"estimate_s":3,"travel_mm":50}}""")
        assertEquals(listOf(Pair(10.0, 10.0), Pair(40.0, 10.0), Pair(40.0, 30.0)), job.view.paths.single().points)
        assertEquals("ready", job.summary.state)
        assertNotNull(job.view.bounds)
    }

    @Test fun theJobsOfTheListHaveNoView() {
        val list = parseJobs("""[{"id":"a","machine":"m","name":"n","kind":"drill","state":"done","by":"x","created":1,"finished":5,"items_total":10,"items_done":10,"holes_total":5,"holes_done":5,"message":"","estimate_s":1.0}]""")
        assertEquals(100, list.single().percent)
        assertEquals(5L, list.single().finished)
        assertTrue(JobStates.finished(list.single().state))
    }

    @Test fun theHeadAndAMeasureAreRead() {
        val head = parseHead("""{"node":"aux","camera":true,"camera_model":"ov5640","laser":true,"rpm_sensor":true,"rpm":23890,"temp_c":41.5,"heat":"warn","protect_open":false,"uptime_s":99}""")
        assertTrue(head.camera)
        assertEquals("ov5640", head.cameraModel)
        assertEquals(41.5, head.tempC!!, 0.0)
        assertEquals("warn", head.heat)
        assertNull(parseHead("""{"temp_c":null}""").tempC)
        val m = parseMeasure("""{"ok":true,"why":"ok","distance_mm":12.345,"sigma_mm":0.02,"asked":7,"used":6,"rejected":1}""")
        assertTrue(m.ok)
        assertEquals(6, m.used)
    }

    @Test fun onlyAnOperatorOrAnAdministratorMayOperate() {
        assertTrue(Roles.canOperate("operator"))
        assertTrue(Roles.canOperate("admin"))
        assertFalse(Roles.canOperate("viewer"))
        assertFalse(Roles.canOperate(""))
    }

    @Test fun theErrorOfTheServerIsItsCodeAndItsDetail() {
        assertEquals("machine_busy" to "one job at a time", describeError(409, """{"error":"machine_busy","detail":"one job at a time"}"""))
        assertEquals("http_502" to "", describeError(502, "<html>bad gateway</html>"))
    }
}
