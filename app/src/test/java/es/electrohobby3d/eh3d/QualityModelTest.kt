package es.electrohobby3d.eh3d

import es.electrohobby3d.eh3d.model.parseHead
import es.electrohobby3d.eh3d.model.parseQuality
import es.electrohobby3d.eh3d.model.parseRingLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ring of LEDs and the quality check, as the server hands them on (docs/API.md of EH3D-SERVER), read into what the screens show. */
class QualityModelTest {
    private val good = """
        {"verdict":"ok","profile":"standard","width_px":640,"height_px":480,
         "holes":[{"index":0,"ok":true,"issues":[]},{"index":1,"ok":true,"issues":[]}],
         "milling":{"width_mm":0.21,"samples":120,"ok":true,"issues":[]},"findings":[]}
    """

    private val bad = """
        {"verdict":"not_ok","holes":[{"index":0,"ok":true},{"index":1,"ok":false},{"index":2,"ok":false}],"milling":null,
         "findings":[{"code":"hole_missing","hole":1,"fails":true},{"code":"hole_out_of_view","hole":2,"fails":false},{"code":"mill_pinched","hole":null,"fails":true}]}
    """

    @Test fun aGoodCheckIsOkWithItsHolesAndTheWidthOfTheChannels() {
        val q = parseQuality(good)
        assertEquals("ok", q.verdict)
        assertEquals(2, q.holesTotal)
        assertEquals(2, q.holesOk)
        assertEquals(0.21, q.channelMm!!, 1e-9)
        assertTrue(q.findings.isEmpty())
    }

    @Test fun aBadCheckSaysWhichHoleAndWhatFailsAndWhatOnlyPreventedJudging() {
        val q = parseQuality(bad)
        assertEquals("not_ok", q.verdict)
        assertEquals(3, q.holesTotal)
        assertEquals(1, q.holesOk)
        assertNull(q.channelMm)
        assertEquals(3, q.findings.size)
        assertEquals("hole_missing", q.findings[0].code)
        assertEquals(1, q.findings[0].hole)
        assertTrue(q.findings[0].fails)
        assertFalse(q.findings[1].fails)
        assertNull(q.findings[2].hole)
    }

    @Test fun theWidthOfTheChannelsCountsOnlyWhenChannelsWereSeen() {
        val none = good.replace("\"samples\":120", "\"samples\":0")
        assertNull(parseQuality(none).channelMm)
    }

    @Test fun aVerdictThatIsNotOneIsUnknownNotAPass() {
        assertEquals("unknown", parseQuality("""{"holes":[]}""").verdict)
    }

    @Test fun theRingIsReadAndAnOlderNodeWithoutOneIsNotBroken() {
        val ring = parseRingLight("""{"light":true,"on":true,"level":60,"max_level":80,"tone":"white"}""")
        assertTrue(ring.on)
        assertEquals(60, ring.level)
        assertEquals(80, ring.maxLevel)
        val head = parseHead("""{"node":"head-1","camera":true,"camera_model":"ov5640","laser":false,"rpm_sensor":false,"rpm":0,"temp_c":null,"heat":"ok","protect_open":false,"uptime_s":5}""")
        assertFalse(head.light)
        assertFalse(head.lightOn)
        assertEquals(0, head.lightLevel)
        val withRing = parseHead("""{"node":"head-1","camera":true,"light":true,"light_on":true,"light_level":55,"protect_open":false}""")
        assertTrue(withRing.light && withRing.lightOn)
        assertEquals(55, withRing.lightLevel)
    }
}

/** The spindle of a machine and what a job says about it and about the tools switched off, as the server hands them on (docs/API.md of EH3D-SERVER). */
class SpindleModelTest {
    @Test fun theSpindleIsReadWithItsLimitsTheSwitchAndTheTopSpeedOfTheNode() {
        val info = es.electrohobby3d.eh3d.model.parseSpindle(
            """{"spindle":"vfd","guard":"warn","limits":{"original":{"min_rpm":5000,"max_rpm":63000,"power_w":300,"max_xy_mm_min":3000,"max_z_mm_min":4000,"max_bit_mm":3.2},
               "vfd":{"min_rpm":6000,"max_rpm":24000,"power_w":800,"max_xy_mm_min":1500,"max_z_mm_min":1200,"max_bit_mm":6.0}},
               "active":{"min_rpm":6000,"max_rpm":24000,"power_w":800,"max_xy_mm_min":1500,"max_z_mm_min":1200,"max_bit_mm":6.0},"node_max_rpm":24000}"""
        )
        assertEquals("vfd", info.spindle)
        assertEquals("warn", info.guard)
        assertEquals(63000, info.original!!.maxRpm)
        assertEquals(24000, info.active!!.maxRpm)
        assertEquals(6.0, info.vfd!!.maxBitMm, 1e-9)
        assertEquals(24000, info.nodeMaxRpm)
    }

    @Test fun aMachineWithNoSpindleHasNoActiveLimitsAndNoTopSpeed() {
        val info = es.electrohobby3d.eh3d.model.parseSpindle("""{"spindle":"","guard":"reject","limits":{},"active":null,"node_max_rpm":null}""")
        assertEquals("", info.spindle)
        assertNull(info.active)
        assertNull(info.nodeMaxRpm)
        assertEquals("reject", info.guard)
    }

    @Test fun aJobViewCarriesTheToolsSwitchedOffTheSkippedWorkAndTheWarnings() {
        val job = es.electrohobby3d.eh3d.model.parseJob(
            """{"id":"j1","machine":"m1","name":"board","kind":"drill","state":"ready","view":{"kind":"drill","name":"board","tools":[],"holes":[],"paths":[],"bounds_mm":null,"items":0,"hole_ends":[],
               "skipped_tools":[2,3],"skipped":5,"spindle_warnings":[{"code":"rpm_above_max","tool":1,"value":30000,"limit":24000}]}}"""
        )
        assertEquals(listOf(2, 3), job.view.skippedTools)
        assertEquals(5, job.view.skipped)
        assertEquals(1, job.view.spindleWarnings.size)
        assertEquals("rpm_above_max", job.view.spindleWarnings[0].code)
        assertEquals(24000, job.view.spindleWarnings[0].limit)
    }

    @Test fun aServerThatSaysNothingOfThemLeavesThemEmpty() {
        val job = es.electrohobby3d.eh3d.model.parseJob("""{"id":"j1","machine":"m1","state":"ready","view":{"kind":"route","tools":[],"holes":[],"paths":[],"items":0,"hole_ends":[]}}""")
        assertTrue(job.view.skippedTools.isEmpty())
        assertEquals(0, job.view.skipped)
        assertTrue(job.view.spindleWarnings.isEmpty())
    }
}
