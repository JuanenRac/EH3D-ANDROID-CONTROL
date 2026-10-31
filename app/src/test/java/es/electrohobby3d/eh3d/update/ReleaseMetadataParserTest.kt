// EH3D-ANDROID-CONTROL - GitHub Release metadata parser unit tests.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
//
// No Robolectric here (unlike HYDRA-UMC-ANDROID-CONTROL's own version of
// this test): this project's build.gradle.kts already pulls in the real
// org.json:json library for local tests (see its own comment there), so
// the JVM's default org.json stub - the reason Robolectric is normally
// needed for org.json code - is never the one used to run this test.
package es.electrohobby3d.eh3d.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseMetadataParserTest {
    private fun release(tag: String, url: String = "https://example.invalid/app.apk", extras: String = "") = """
        {"tag_name":"$tag","name":"Release $tag","body":" notes ","assets":[
          {"name":"${ReleaseMetadataParser.REQUIRED_ASSET_NAME}","browser_download_url":"$url"}
        ]$extras}
    """.trimIndent()

    @Test fun `accepts only a newer stable release with the exact HTTPS APK asset`() {
        val result = ReleaseMetadataParser.parseLatestStable(release("v0.3.6"), "0.3.5")
        assertTrue(result is UpdateCheckResult.Available)
        assertEquals(SemanticVersion(0, 3, 6), (result as UpdateCheckResult.Available).update.version)
    }

    @Test fun `rejects non stable release tag missing asset and non HTTPS URL`() {
        assertTrue(ReleaseMetadataParser.parseLatestStable(release("v0.3.6-beta"), "0.3.5") is UpdateCheckResult.Failed)
        assertTrue(ReleaseMetadataParser.parseLatestStable("{\"tag_name\":\"v0.3.6\",\"assets\":[]}", "0.3.5") is UpdateCheckResult.Failed)
        assertTrue(ReleaseMetadataParser.parseLatestStable(release("v0.3.6", "http://example.invalid/app.apk"), "0.3.5") is UpdateCheckResult.Failed)
    }

    @Test fun `does not offer draft prerelease or non newer releases`() {
        assertEquals(UpdateCheckResult.UpToDate, ReleaseMetadataParser.parseLatestStable(release("v0.3.6", extras = ",\"draft\":true"), "0.3.5"))
        assertEquals(UpdateCheckResult.UpToDate, ReleaseMetadataParser.parseLatestStable(release("v0.3.5"), "0.3.5"))
    }
}
