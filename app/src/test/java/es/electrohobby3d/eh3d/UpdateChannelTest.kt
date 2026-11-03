package es.electrohobby3d.eh3d

import es.electrohobby3d.eh3d.update.ReleaseMetadataParser
import es.electrohobby3d.eh3d.update.SemanticVersion
import es.electrohobby3d.eh3d.update.UpdateCheckResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The trust decisions of the update channel: which tags are versions, which release is newer, and which APK link is accepted. */
class UpdateChannelTest {
    private val asset = ReleaseMetadataParser.REQUIRED_ASSET_NAME

    private fun release(
        tag: String = "v0.0.9",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: String = """[{"name":"$asset","browser_download_url":"https://github.com/JuanenRac/EH3D-ANDROID-CONTROL/releases/download/$tag/$asset"}]""",
        name: String = "EH3D Control $tag",
        body: String = " notes ",
    ) = """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"name":"$name","body":"$body","assets":$assets}"""

    @Test fun aStableTagIsReadWithOrWithoutTheV() {
        assertEquals(SemanticVersion(0, 0, 9), SemanticVersion.parseStable("v0.0.9"))
        assertEquals(SemanticVersion(1, 20, 300), SemanticVersion.parseStable("1.20.300"))
        assertEquals(SemanticVersion(0, 8, 0, 1), SemanticVersion.parseStable("0.8.0.1"))
        assertEquals(SemanticVersion(0, 8, 0, 0), SemanticVersion.parseStable(" v0.8.0.0 "))
    }

    @Test fun aTagThatIsNotStableSemverIsNull() {
        for (bad in listOf("", "v", "1", "1.2", "1.2.3-rc1", "1.2.3+build", "01.2.3", "1.02.3", "1.2.03", "1.2.3.", ".1.2.3", "1..2", "a.b.c", "1.2.3.4.5", "V1.2.3", "1.2.3 4", "-1.2.3", "nightly")) {
            assertNull("'$bad' must not be a version", SemanticVersion.parseStable(bad))
        }
    }

    @Test fun aNumberTooBigForAnIntIsNotAVersionAndDoesNotThrow() {
        assertNull(SemanticVersion.parseStable("99999999999.0.0"))
        assertNull(SemanticVersion.parseStable("0.99999999999.0"))
        assertNull(SemanticVersion.parseStable("0.0.99999999999"))
        assertNull(SemanticVersion.parseStable("0.0.0.99999999999"))
        assertEquals(SemanticVersion(Int.MAX_VALUE, 0, 0), SemanticVersion.parseStable("${Int.MAX_VALUE}.0.0"))
    }

    @Test fun versionsAreComparedByTheirNumbersAndAMissingBuildCountsAsZero() {
        fun v(s: String) = SemanticVersion.parseStable(s)!!
        assertTrue(v("0.0.10") > v("0.0.9"))
        assertTrue(v("0.5.0") > v("0.4.9"))
        assertTrue(v("1.0.0") > v("0.99.99"))
        assertTrue(v("0.8.0.1") > v("0.8.0"))
        assertTrue(v("0.8.1.0") > v("0.8.0.9"))
        assertEquals(0, v("1.0.0").compareTo(v("1.0.0.0")))
        assertEquals(0, v("v2.3.4").compareTo(v("2.3.4")))
    }

    @Test fun aVersionIsWrittenWithoutTheVAndWithItsBuildOnlyWhenItHasOne() {
        assertEquals("0.0.9", SemanticVersion(0, 0, 9).toString())
        assertEquals("0.8.0.1", SemanticVersion(0, 8, 0, 1).toString())
        assertEquals("0.8.0.0", SemanticVersion(0, 8, 0, 0).toString())
        assertEquals(SemanticVersion(1, 2, 3), SemanticVersion.parseStable(SemanticVersion(1, 2, 3).toString()))
    }

    @Test fun aNewerStableReleaseWithItsApkIsAvailable() {
        val r = ReleaseMetadataParser.parseLatestStable(release(tag = "v0.1.0"), "0.0.9")
        assertTrue(r is UpdateCheckResult.Available)
        val u = (r as UpdateCheckResult.Available).update
        assertEquals(SemanticVersion(0, 1, 0), u.version)
        assertEquals("EH3D Control v0.1.0", u.releaseName)
        assertEquals("notes", u.notes)
        assertTrue(u.assetUrl.startsWith("https://"))
        assertTrue(u.assetUrl.endsWith(asset))
    }

    @Test fun theSameOrAnOlderReleaseIsUpToDate() {
        for (tag in listOf("v0.0.9", "v0.0.8", "0.0.9")) {
            assertEquals(UpdateCheckResult.UpToDate, ReleaseMetadataParser.parseLatestStable(release(tag = tag), "0.0.9"))
        }
        assertEquals(UpdateCheckResult.UpToDate, ReleaseMetadataParser.parseLatestStable(release(tag = "v0.0.9.0"), "0.0.9"))
    }

    @Test fun aDraftOrAPrereleaseIsNeverAnUpdateEvenWithAHigherTag() {
        assertEquals(UpdateCheckResult.UpToDate, ReleaseMetadataParser.parseLatestStable(release(tag = "v9.0.0", draft = true), "0.0.9"))
        assertEquals(UpdateCheckResult.UpToDate, ReleaseMetadataParser.parseLatestStable(release(tag = "v9.0.0", prerelease = true), "0.0.9"))
    }

    @Test fun aTagThatIsNotAVersionOrAnInstalledVersionThatIsNotOneFailsWithAMessage() {
        val badTag = ReleaseMetadataParser.parseLatestStable(release(tag = "nightly"), "0.0.9")
        assertTrue(badTag is UpdateCheckResult.Failed && badTag.message.isNotBlank())
        val badLocal = ReleaseMetadataParser.parseLatestStable(release(tag = "v0.1.0"), "0.0.9-debug")
        assertTrue(badLocal is UpdateCheckResult.Failed && badLocal.message.isNotBlank())
    }

    @Test fun aReleaseWithoutTheNamedApkFailsAndSaysWhichVersionIsMissingIt() {
        for (assets in listOf("[]", """[{"name":"other.apk","browser_download_url":"https://x/y"}]""", """[null,{"browser_download_url":"https://x/y"}]""")) {
            val r = ReleaseMetadataParser.parseLatestStable(release(tag = "v0.1.0", assets = assets), "0.0.9")
            assertTrue("$assets -> $r", r is UpdateCheckResult.Failed)
            assertTrue((r as UpdateCheckResult.Failed).message.contains("0.1.0"))
        }
        val noList = ReleaseMetadataParser.parseLatestStable("""{"tag_name":"v0.1.0"}""", "0.0.9")
        assertTrue(noList is UpdateCheckResult.Failed)
    }

    @Test fun anApkLinkThatIsNotHttpsIsRefused() {
        for (url in listOf("http://github.com/x.apk", "ftp://x/y", "file:///sdcard/x.apk", "", "//github.com/x.apk")) {
            val assets = """[{"name":"$asset","browser_download_url":"$url"}]"""
            val r = ReleaseMetadataParser.parseLatestStable(release(tag = "v0.1.0", assets = assets), "0.0.9")
            assertTrue("$url -> $r", r is UpdateCheckResult.Failed)
        }
    }

    @Test fun aReleaseWithoutANameOrNotesGetsADefaultNameAndEmptyNotes() {
        val payload = """{"tag_name":"v0.2.0","assets":[{"name":"$asset","browser_download_url":"https://github.com/x/$asset"}]}"""
        val r = ReleaseMetadataParser.parseLatestStable(payload, "0.1.0") as UpdateCheckResult.Available
        assertEquals("EH3D Control v0.2.0", r.update.releaseName)
        assertEquals("", r.update.notes)
        // a null name or body is the same as a missing one
        val nulls = """{"tag_name":"v0.2.0","name":null,"body":null,"assets":[{"name":"$asset","browser_download_url":"https://github.com/x/$asset"}]}"""
        val n = ReleaseMetadataParser.parseLatestStable(nulls, "0.1.0") as UpdateCheckResult.Available
        assertEquals("EH3D Control v0.2.0", n.update.releaseName)
        assertEquals("", n.update.notes)
    }

    @Test fun theFirstApkWithTheRightNameIsTheOneThatCounts() {
        val assets = """[{"name":"$asset","browser_download_url":"https://github.com/first/$asset"},{"name":"$asset","browser_download_url":"https://github.com/second/$asset"}]"""
        val r = ReleaseMetadataParser.parseLatestStable(release(tag = "v0.3.0", assets = assets), "0.0.9") as UpdateCheckResult.Available
        assertTrue(r.update.assetUrl.contains("/first/"))
    }
}
