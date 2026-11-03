// EH3D-ANDROID-CONTROL - more unit tests of the update channel: which tags are versions, how versions order, and which releases are offered.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreUpdateTest {
    @Test fun `reads three or four numbers with an optional v and spaces around`() {
        assertEquals(SemanticVersion(1, 2, 3), SemanticVersion.parseStable("1.2.3"))
        assertEquals(SemanticVersion(0, 8, 0, 1), SemanticVersion.parseStable("v0.8.0.1"))
        assertEquals(SemanticVersion(0, 0, 0), SemanticVersion.parseStable("  0.0.0\n"))
        assertEquals(SemanticVersion(10, 20, 30, 40), SemanticVersion.parseStable("10.20.30.40"))
    }

    @Test fun `refuses everything that is not exactly a stable number`() {
        for (tag in listOf("", "v", "1", "1.2", "1.2.3.4.5", "01.2.3", "1.02.3", "1.2.03", "1.2.3.04", "1.2.3-rc1", "1.2.3+build5", "V1.2.3", "vv1.2.3", "1.2.3.", ".1.2.3", "1..3", "1.2.x", "1,2,3", "1 .2.3", "v 1.2.3", "latest", "1.2.3 beta", "١.٢.٣", "-1.2.3")) {
            assertNull("'$tag' is not a stable version", SemanticVersion.parseStable(tag))
        }
    }

    @Test fun `a number too big for an int is not a version and does not crash`() {
        assertEquals(SemanticVersion(Int.MAX_VALUE, 0, 0), SemanticVersion.parseStable("2147483647.0.0"))
        for (tag in listOf("2147483648.0.0", "99999999999.0.0", "1.99999999999.0", "1.2.99999999999", "1.2.3.99999999999", "9".repeat(60) + ".0.0")) {
            assertNull("'$tag' does not fit", SemanticVersion.parseStable(tag))
        }
    }

    @Test fun `versions order by number and a missing fourth number is a zero`() {
        fun v(s: String) = SemanticVersion.parseStable(s)!!
        assertTrue(v("0.10.0") > v("0.9.0"))
        assertTrue(v("0.8.0.1") > v("0.8.0"))
        assertTrue(v("1.0.0") > v("0.99.99.99"))
        assertEquals(0, v("1.2.3").compareTo(v("1.2.3.0")))
        assertTrue(v("1.2.3") < v("1.2.3.1"))
        val shuffled = listOf("0.9.0", "0.10.0", "0.8.0.1", "0.8.0", "1.0.0", "0.8.1").map(::v)
        assertEquals(listOf("0.8.0", "0.8.0.1", "0.8.1", "0.9.0", "0.10.0", "1.0.0"), shuffled.sorted().map { it.toString() })
    }

    @Test fun `a version is written as it was read`() {
        for (s in listOf("0.0.0", "1.2.3", "1.2.3.4", "0.8.0.0", "10.20.30")) assertEquals(s, SemanticVersion.parseStable(s)!!.toString())
        assertEquals("1.2.3", SemanticVersion.parseStable("v1.2.3")!!.toString())
    }

    private fun release(tag: String, url: String = "https://github.com/x/y/releases/download/$tag/${ReleaseMetadataParser.REQUIRED_ASSET_NAME}", name: String = "\"Release $tag\"", body: String = "\" notes \""): String =
        """{"tag_name":"$tag","name":$name,"body":$body,"assets":[{"name":"other.zip","browser_download_url":"https://x/other.zip"},{"name":"${ReleaseMetadataParser.REQUIRED_ASSET_NAME}","browser_download_url":"$url"}]}"""

    @Test fun `offers a newer build of the same version and writes down what the release says`() {
        val r = ReleaseMetadataParser.parseLatestStable(release("v0.8.0.1"), "0.8.0")
        assertTrue(r is UpdateCheckResult.Available)
        val u = (r as UpdateCheckResult.Available).update
        assertEquals(SemanticVersion(0, 8, 0, 1), u.version)
        assertEquals("Release v0.8.0.1", u.releaseName)
        assertEquals("notes", u.notes)
        assertEquals("https://github.com/x/y/releases/download/v0.8.0.1/${ReleaseMetadataParser.REQUIRED_ASSET_NAME}", u.assetUrl)
    }

    @Test fun `a release with no name or no notes gets a name of its own and empty notes`() {
        val none = ReleaseMetadataParser.parseLatestStable(release("v1.0.0", name = "null", body = "null"), "0.9.0") as UpdateCheckResult.Available
        assertEquals("EH3D Control v1.0.0", none.update.releaseName)
        assertEquals("", none.update.notes)
        val numbers = ReleaseMetadataParser.parseLatestStable(release("v1.0.0", name = "5", body = "7"), "0.9.0") as UpdateCheckResult.Available
        assertEquals("5", numbers.update.releaseName)
    }

    @Test fun `does not offer what is the same or older or not ready`() {
        assertEquals(UpdateCheckResult.UpToDate, ReleaseMetadataParser.parseLatestStable(release("v0.3.5"), "0.3.5"))
        assertEquals(UpdateCheckResult.UpToDate, ReleaseMetadataParser.parseLatestStable(release("v0.3.5"), "0.3.5.0"))
        assertEquals(UpdateCheckResult.UpToDate, ReleaseMetadataParser.parseLatestStable(release("v0.3.4"), "0.3.5"))
        val prerelease = release("v9.0.0").replaceFirst("{", "{\"prerelease\":true,")
        val draft = release("v9.0.0").replaceFirst("{", "{\"draft\":true,")
        assertEquals(UpdateCheckResult.UpToDate, ReleaseMetadataParser.parseLatestStable(prerelease, "0.3.5"))
        assertEquals(UpdateCheckResult.UpToDate, ReleaseMetadataParser.parseLatestStable(draft, "0.3.5"))
        // a draft flag that is false does not hide the release
        val notDraft = release("v9.0.0").replaceFirst("{", "{\"draft\":false,\"prerelease\":false,")
        assertTrue(ReleaseMetadataParser.parseLatestStable(notDraft, "0.3.5") is UpdateCheckResult.Available)
    }

    @Test fun `fails, and says so, when the tag or the installed version cannot be read`() {
        for (tag in listOf("banana", "v1.0.0-rc1", "1.0", "99999999999.0.0")) {
            assertTrue(tag, ReleaseMetadataParser.parseLatestStable(release(tag), "0.3.5") is UpdateCheckResult.Failed)
        }
        for (installed in listOf("", "debug", "0.3.5-debug", "99999999999.0.0")) {
            assertTrue(installed, ReleaseMetadataParser.parseLatestStable(release("v1.0.0"), installed) is UpdateCheckResult.Failed)
        }
        assertTrue(ReleaseMetadataParser.parseLatestStable("""{"name":"no tag"}""", "0.3.5") is UpdateCheckResult.Failed)
        assertTrue(ReleaseMetadataParser.parseLatestStable("""{"tag_name":null}""", "0.3.5") is UpdateCheckResult.Failed)
    }

    @Test fun `needs the exact asset with an https address`() {
        val withoutAssets = """{"tag_name":"v1.0.0"}"""
        assertTrue(ReleaseMetadataParser.parseLatestStable(withoutAssets, "0.9.0") is UpdateCheckResult.Failed)
        val similar = """{"tag_name":"v1.0.0","assets":[{"name":"EH3D-ANDROID-CONTROL-debug.apk","browser_download_url":"https://x/a.apk"},{"name":"eh3d-android-control-release.apk","browser_download_url":"https://x/b.apk"}]}"""
        assertTrue(ReleaseMetadataParser.parseLatestStable(similar, "0.9.0") is UpdateCheckResult.Failed)
        for (url in listOf("http://x/a.apk", "ftp://x/a.apk", "", "HTTPS://x/a.apk", "//x/a.apk", "file:///sdcard/a.apk")) {
            assertTrue("'$url'", ReleaseMetadataParser.parseLatestStable(release("v1.0.0", url = url), "0.9.0") is UpdateCheckResult.Failed)
        }
        val noUrl = """{"tag_name":"v1.0.0","assets":[{"name":"${ReleaseMetadataParser.REQUIRED_ASSET_NAME}"}]}"""
        assertTrue(ReleaseMetadataParser.parseLatestStable(noUrl, "0.9.0") is UpdateCheckResult.Failed)
        val holes = """{"tag_name":"v1.0.0","assets":[null,5,{"name":null},{"name":"${ReleaseMetadataParser.REQUIRED_ASSET_NAME}","browser_download_url":"https://x/a.apk"}]}"""
        assertTrue(ReleaseMetadataParser.parseLatestStable(holes, "0.9.0") is UpdateCheckResult.Available)
    }

    @Test fun `the first asset with the exact name is the one that is used`() {
        val two = """{"tag_name":"v1.0.0","assets":[{"name":"${ReleaseMetadataParser.REQUIRED_ASSET_NAME}","browser_download_url":"https://x/first.apk"},{"name":"${ReleaseMetadataParser.REQUIRED_ASSET_NAME}","browser_download_url":"https://x/second.apk"}]}"""
        assertEquals("https://x/first.apk", ((ReleaseMetadataParser.parseLatestStable(two, "0.9.0")) as UpdateCheckResult.Available).update.assetUrl)
    }
}
