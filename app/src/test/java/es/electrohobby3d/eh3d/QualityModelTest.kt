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
