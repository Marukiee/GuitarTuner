package nl.markmaaktmedia.guitartuner.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Parsing is tested against a **real** payload captured from the repository's own
 * `releases/latest` endpoint, not a hand-written fixture, because a hand-written fixture only
 * proves the parser agrees with my idea of the response.
 *
 * These run against the genuine `org.json` from Maven rather than the stubbed framework classes,
 * which by default return zeroes and nulls and would make any parser look correct.
 */
class UpdateCheckerParseTest {

    private fun payload(): String =
        checkNotNull(javaClass.getResourceAsStream("/release_latest.json")) {
            "release_latest.json missing from test resources"
        }.bufferedReader().use { it.readText() }

    @Test
    fun `parses a real releases-latest payload`() {
        val release = ReleaseParser.parse(payload())

        assertNotNull("A published release with an APK asset must parse", release)
        assertEquals(10L, release!!.versionCode)
        assertEquals(
            "https://github.com/Marukiee/GuitarTuner/releases/download/v10/guitartuner.apk",
            release.apkUrl,
        )
        assertEquals("feat: rounded font, green in-tune feedback, instrument picker", release.notes)
    }

    /**
     * Mutations go through JSONObject rather than string replacement. The first version of these
     * tests edited the raw text and silently matched nothing, because the API returns compact
     * JSON with no space after the colon, so they asserted against an unmodified payload and
     * "passed" for the wrong reason.
     */
    private fun mutated(block: JSONObject.() -> Unit): String =
        JSONObject(payload()).apply(block).toString()

    @Test
    fun `ignores a prerelease`() {
        assertNull(ReleaseParser.parse(mutated { put("prerelease", true) }))
    }

    @Test
    fun `ignores a draft`() {
        assertNull(ReleaseParser.parse(mutated { put("draft", true) }))
    }

    @Test
    fun `ignores a release with no apk attached`() {
        val json = payload().replace("guitartuner.apk", "guitartuner.txt")
        assertNull(ReleaseParser.parse(json))
    }

    @Test
    fun `ignores a tag that is not a build number`() {
        assertNull(ReleaseParser.parse(mutated { put("tag_name", "nightly") }))
    }
}
