package es.electrohobby3d.eh3d

import es.electrohobby3d.eh3d.model.Prepare
import es.electrohobby3d.eh3d.model.Side
import es.electrohobby3d.eh3d.model.Work
import es.electrohobby3d.eh3d.update.SemanticVersion
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bug hunt: the address that is typed, the tools of a file with a damaged header, names that are cut, and the versions of a release. */
class HuntRound4Test {
    private fun origin(host: String, port: String) = ServerEndpoint.fromHostAndPort(host, port)?.origin

    // ---- the address of the server ----

    @Test fun theSchemeIsReadWhateverItsCase() {
        assertEquals("http://192.168.0.80:8080", origin("HTTP://192.168.0.80", "8080"))
        assertEquals("http://10.0.0.5:80", origin("Http://10.0.0.5", "80"))
        assertEquals("https://eh3d.example:8443", origin("HTTPS://eh3d.example", "8443"))
        assertEquals("https://eh3d.example:8443", origin("hTtPs://eh3d.example", "8443"))
    }

    @Test fun aSchemeInCapitalsNeverMakesThePortOrThePrefixBecomeTheHost() {
        // the old reading kept `HTTPS:` as a text and made the host "HTTPS"
        assertNull(origin("HTTP://example.com", "8080"))
        assertNotNull(origin("HTTPS://example.com", "8080"))
        assertFalse(origin("HTTPS://example.com", "8080")!!.contains("HTTPS"))
    }

    @Test fun aPortIsPlainDigitsFromOneToSixtyFiveThousandFiveHundredAndThirtyFive() {
        for (port in listOf("", " ", "+80", "-1", "0", "65536", "８０８０", "80 80", "8e3", "0x50", "99999999999999999999", "٨٠", "80.0", "08x")) {
            assertNull(port, origin("192.168.0.80", port))
        }
        assertEquals("http://192.168.0.80:1", origin("192.168.0.80", "1"))
        assertEquals("http://192.168.0.80:65535", origin("192.168.0.80", "65535"))
        assertEquals("http://192.168.0.80:8080", origin("192.168.0.80", " 8080 "))
        assertEquals("http://192.168.0.80:80", origin("192.168.0.80", "0080"))
    }

    @Test fun aNameOrAPasswordBeforeTheHostIsRefusedWhicheverWayItIsTyped() {
        assertNull(origin("192.168.0.80@evil.example", "8080"))
        assertNull(origin("user:pw@192.168.0.80", "8080"))
        assertNull(ServerEndpoint.parse("http://user@192.168.0.5"))
        assertNull(ServerEndpoint.parse("https://a:b@example.com"))
        assertNull(ServerEndpoint.parse("http://192.168.0.5@evil.example"))
    }

    @Test fun aPathAfterTheHostIsNotPartOfAnOriginAndTheSlashIs() {
        assertNull(ServerEndpoint.parse("http://192.168.0.5:8080/api"))
        assertNull(ServerEndpoint.parse("https://eh3d.example/a/b"))
        assertNull(ServerEndpoint.parse("http://192.168.0.5:8080//"))
        assertEquals("http://192.168.0.5:8080", ServerEndpoint.parse("http://192.168.0.5:8080/")?.origin)
        assertEquals("http://192.168.0.5:8080", ServerEndpoint.parse("http://192.168.0.5:8080")?.origin)
    }

    @Test fun thePortOfAnAddressIsRealOrNone() {
        assertNull(ServerEndpoint.parse("http://192.168.0.5:0"))
        assertNull(ServerEndpoint.parse("http://192.168.0.5:65536"))
        assertNull(ServerEndpoint.parse("http://192.168.0.5:-1"))
        assertEquals("http://192.168.0.5:65535", ServerEndpoint.parse("http://192.168.0.5:65535")?.origin)
        assertEquals("http://192.168.0.5:1", ServerEndpoint.parse("http://192.168.0.5:1")?.origin)
    }

    @Test fun spacesAndLineBreaksAroundAnAddressAreLeftOut() {
        assertEquals("http://192.168.0.5:8080", ServerEndpoint.parse("\thttp://192.168.0.5:8080\n")?.origin)
        assertEquals("http://192.168.0.80:8080", origin("192.168.0.80\n", "8080"))
        assertEquals("https://eh3d.example", ServerEndpoint.parse("  https://eh3d.example \r\n")?.origin)
    }

    @Test fun anAddressOfTheVersionSixIsKeptBetweenBracketsOrRefusedWhenItIsPlain() {
        assertEquals("https://[fe80::1]:8443", ServerEndpoint.parse("https://[fe80::1]:8443")?.origin)
        assertNull(ServerEndpoint.parse("http://[::1]:8080"))
    }

    @Test fun anAddressWithADotAtTheEndIsNotPrivate() {
        assertNull(ServerEndpoint.parse("http://192.168.0.5.:8080"))
        assertFalse(ServerEndpoint.isPrivateLanHost("192.168.0.5."))
        assertFalse(ServerEndpoint.isPrivateLanHost("localhost."))
    }

    @Test fun everyOriginIsItsOwnFixedPoint() {
        for (value in listOf("http://192.168.0.5:8080", "https://eh3d.example:8443", "https://eh3d.example", "http://localhost:18080", "https://[fe80::1]:8443")) {
            val once = ServerEndpoint.parse(value)
            assertNotNull(value, once)
            assertEquals(value, once!!.origin, ServerEndpoint.parse(once.origin)?.origin)
        }
    }

    @Test fun theHousePrivateRangesAreExactAtTheirEdges() {
        for (host in listOf("172.16.0.0", "172.31.255.255", "10.0.0.0", "192.168.255.255", "127.255.255.255")) assertTrue(host, ServerEndpoint.isPrivateLanHost(host))
        for (host in listOf("172.15.255.255", "172.32.0.0", "192.167.255.255", "192.169.0.0", "11.0.0.0", "9.255.255.255", "0.0.0.0", "255.255.255.255", " 10.0.0.1", "10.0.0.1\n", "１０.0.0.1")) assertFalse(host, ServerEndpoint.isPrivateLanHost(host))
    }

    @Test fun theSecureConnectionIsAskedForAnythingThatIsNotTheHouseWhateverTheScheme() {
        assertFalse(ServerEndpoint.needsSecureConnection("HTTP://192.168.0.5"))
        assertFalse(ServerEndpoint.needsSecureConnection("HTTPS://example.com"))
        assertTrue(ServerEndpoint.needsSecureConnection("HTTP://example.com"))
        assertTrue(ServerEndpoint.needsSecureConnection("192.168.0.5@evil.example"))
        assertTrue(ServerEndpoint.needsSecureConnection("10.attacker.example"))
        assertFalse(ServerEndpoint.needsSecureConnection("localhost"))
        assertFalse(ServerEndpoint.needsSecureConnection(""))
        assertFalse(ServerEndpoint.needsSecureConnection("   "))
        assertFalse(ServerEndpoint.needsSecureConnection("\n192.168.0.5\n"))
    }

    @Test fun aPublicIpAddressIsToldFromANameWhateverTheScheme() {
        assertTrue(ServerEndpoint.usesPublicIpAddress("HTTP://203.0.113.9"))
        assertTrue(ServerEndpoint.usesPublicIpAddress("HTTPS://203.0.113.9:8443/"))
        assertFalse(ServerEndpoint.usesPublicIpAddress("203.0.113.9@evil.example"))
        assertFalse(ServerEndpoint.usesPublicIpAddress("203.000.113.9"))
        assertFalse(ServerEndpoint.usesPublicIpAddress("256.1.1.1"))
        assertFalse(ServerEndpoint.usesPublicIpAddress("10.0.0.1"))
        assertFalse(ServerEndpoint.usesPublicIpAddress(""))
    }

    // ---- preparing a job ----

    @Test fun theDiametersOfTheHeaderAreReadAndTheFirstOneOfATooolWins() {
        val d = Prepare.declaredDiameters("M48\nT01C0.80\nT02C1.00\nT01C9.9\n  T3 C.5\nT4C\n%\n")
        assertEquals(linkedMapOf(1 to 0.8, 2 to 1.0, 3 to 0.5), d)
        assertEquals(1, Prepare.declaredDiameters("T0001C0.8").keys.single())
    }

    @Test fun aToolNumberTooBigForAnIntegerIsSkippedAndNeverEndsInAnException() {
        val d = Prepare.declaredDiameters("T99999999999C0.8\nT2147483648C0.3\nT1C0.5\nT2147483647C0.7\n")
        assertEquals(linkedMapOf(1 to 0.5, 2147483647 to 0.7), d)
        assertTrue(Prepare.declaredDiameters("T" + "9".repeat(5000) + "C1").isEmpty())
    }

    @Test fun onlyTheFirstTwentyThousandCharactersOfTheFileAreLookedAt() {
        val padding = "; ".repeat(10_000)
        assertTrue(Prepare.declaredDiameters(padding + "\nT1C0.8\n").isEmpty())
        assertEquals(1, Prepare.declaredDiameters("T1C0.8\n$padding").size)
        assertTrue(Prepare.declaredDiameters("").isEmpty())
    }

    @Test fun theNameOfAJobIsCutBetweenCharactersAndNeverInsideAPicture() {
        val name = Prepare.jobName("😀".repeat(61) + ".ncd")
        assertEquals(60, name.codePointCount(0, name.length))
        assertEquals(120, name.length)
        assertFalse(name.any { Character.isSurrogate(it) && !name.contains("😀") })
        assertEquals("a".repeat(60), Prepare.jobName("a".repeat(80) + ".ncd"))
        assertEquals("a".repeat(60), Prepare.jobName("a".repeat(60) + ".ncd"))
    }

    @Test fun theNameOfAJobIsTheFileWithoutItsLastExtension() {
        assertEquals("a.b", Prepare.jobName("a.b.c"))
        assertEquals("noext", Prepare.jobName("noext"))
        assertEquals("a", Prepare.jobName("a."))
        assertEquals("", Prepare.jobName(""))
    }

    @Test fun theLinesOfAProgramAreCountedTheWayTheServerNumbersThem() {
        for ((text, lines) in listOf("" to 0, "a" to 1, "a\n" to 1, "a\n\n" to 2, "a\r\nb\r\n" to 2, "\n" to 1, "\r\n" to 1, "a\nb" to 2)) {
            assertEquals(text, lines, Prepare.lineCount(text))
        }
    }

    @Test fun theLineToGoOnFromStaysInsideTheProgram() {
        assertEquals(0, Prepare.resumeSkip(-5, 10))
        assertEquals(10, Prepare.resumeSkip(99, 10))
        assertEquals(4, Prepare.resumeSkip(4, 10))
        assertEquals(0, Prepare.resumeSkip(5, -3))
        assertEquals(0, Prepare.resumeSkip(Int.MAX_VALUE, 0))
        assertEquals(7, Prepare.resumeSkip(Int.MAX_VALUE, 7))
        assertEquals(0, Prepare.resumeSkip(Int.MIN_VALUE, 7))
    }

    @Test fun theToolsToLeaveOutAreTheSwitchedOffOnesAndTheOnesBeforeTheStart() {
        assertEquals(listOf(1, 2), Prepare.skipTools(listOf(1, 2, 3), setOf(2), 3))
        assertEquals(listOf(2), Prepare.skipTools(listOf(1, 2, 3), setOf(2), 9))
        assertEquals(listOf(5, 9), Prepare.skipTools(listOf(1), setOf(9, 5), null))
        assertEquals(emptyList<Int>(), Prepare.skipTools(emptyList(), emptySet(), 4))
        assertEquals(listOf(3), Prepare.skipTools(listOf(3, 1), emptySet(), 1))
    }

    @Test fun nothingIsLeftOnlyWhenAFileHasToolsAndEveryOneIsOut() {
        assertFalse(Prepare.nothingLeft(emptyList(), emptyList()))
        assertFalse(Prepare.nothingLeft(emptyList(), listOf(1)))
        assertTrue(Prepare.nothingLeft(listOf(1, 2), listOf(1, 2)))
        assertTrue(Prepare.nothingLeft(listOf(1, 2), listOf(2, 1, 7)))
        assertFalse(Prepare.nothingLeft(listOf(1, 2), listOf(1)))
    }

    @Test fun theToggleOfTheStartAndOfASwitchedOffToolFollowTheirRules() {
        assertEquals(3, Prepare.toggleStartAt(null, 3, emptySet()))
        assertNull(Prepare.toggleStartAt(3, 3, emptySet()))
        assertEquals(2, Prepare.toggleStartAt(2, 3, setOf(3)))
        assertNull(Prepare.toggleStartAt(null, 3, setOf(3)))
        assertEquals(emptySet<Int>(), Prepare.toggleOff(setOf(1), 1))
        assertEquals(setOf(1, 2), Prepare.toggleOff(setOf(1), 2))
    }

    @Test fun aMachineTakesTheWorkItsConnectionCanDo() {
        assertEquals(listOf(Work.Gcode), Prepare.works("grbl"))
        assertEquals(listOf(Work.Drill, Work.Route, Work.Both), Prepare.works("node"))
        for (connection in listOf("demo", "", "unknown", "NODE")) assertEquals(connection, Work.entries.toList(), Prepare.works(connection))
    }

    @Test fun theRequestOfAFileHoldsTheTextExactlyWhateverIsInIt() {
        val text = "M48\r\n\"quoted\" \\ back\u0000slash é日😀\n"
        val body = JSONObject(Prepare.fileBody(Side.Drill, "board", text, "   ", emptyList()))
        assertEquals("drill", body.getString("kind"))
        assertEquals(text, body.getJSONObject("files").getString("ncd"))
        assertFalse(body.getJSONObject("files").has("dtd"))
        assertFalse(body.has("skip_tools"))
        assertEquals("board", body.getString("name"))
    }

    @Test fun theRequestOfARoutingFileUsesItsOwnNamesAndLeavesOutABlankName() {
        val body = JSONObject(Prepare.fileBody(Side.Route, " \n ", "IN;", "table", listOf(2, 5)))
        assertEquals("route", body.getString("kind"))
        assertEquals("IN;", body.getJSONObject("files").getString("plt"))
        assertEquals("table", body.getJSONObject("files").getString("rtd"))
        assertFalse(body.has("name"))
        assertEquals(2, body.getJSONArray("skip_tools").length())
    }

    @Test fun theRequestOfAProgramNeverHasANegativeSkip() {
        val body = JSONObject(Prepare.gcodeBody("", "G0 X1\n", -7, true))
        assertEquals(0, body.getInt("skip"))
        assertTrue(body.getBoolean("restore_outputs"))
        assertFalse(body.has("name"))
        assertEquals("G0 X1\n", body.getString("gcode"))
        assertEquals(5, JSONObject(Prepare.gcodeBody("n", "", 5, false)).getInt("skip"))
        assertFalse(JSONObject(Prepare.gcodeBody("n", "", 5, false)).getBoolean("restore_outputs"))
    }

    // ---- the versions of a release ----

    @Test fun aStableVersionIsReadWithItsOptionalLetterAndFourthNumber() {
        assertEquals(SemanticVersion(1, 2, 3), SemanticVersion.parseStable("v1.2.3"))
        assertEquals(SemanticVersion(1, 2, 3, 4), SemanticVersion.parseStable("1.2.3.4"))
        assertEquals(SemanticVersion(0, 0, 0), SemanticVersion.parseStable(" 0.0.0 "))
        assertEquals(SemanticVersion(2147483647, 0, 0), SemanticVersion.parseStable("2147483647.0.0"))
    }

    @Test fun aTagThatIsNotAStableVersionIsNullAndNeverAnException() {
        for (tag in listOf("1.2", "01.2.3", "1.2.3-rc1", "1.2.3+build", "1.2.3.", "1..3", "", "v", "V1.2.3", "١.٢.٣", "2147483648.0.0", "1.2.3.99999999999", "1.2.3.4.5", "1.2.-3", "latest", "1.2.3 4", "vv1.2.3")) {
            assertNull(tag, SemanticVersion.parseStable(tag))
        }
    }

    @Test fun aMissingFourthNumberComparesAsZeroAndTheOrderIsNumeric() {
        assertEquals(0, SemanticVersion(0, 8, 0).compareTo(SemanticVersion(0, 8, 0, 0)))
        assertTrue(SemanticVersion(0, 8, 0, 1) > SemanticVersion(0, 8, 0))
        assertTrue(SemanticVersion(0, 7, 9) < SemanticVersion(0, 8, 0))
        assertTrue(SemanticVersion(0, 0, 9) < SemanticVersion(0, 0, 10))
        assertTrue(SemanticVersion(1, 0, 0) > SemanticVersion(0, 99, 99, 99))
        assertEquals("0.8.0", SemanticVersion(0, 8, 0).toString())
        assertEquals("0.8.0.2", SemanticVersion(0, 8, 0, 2).toString())
    }
}
