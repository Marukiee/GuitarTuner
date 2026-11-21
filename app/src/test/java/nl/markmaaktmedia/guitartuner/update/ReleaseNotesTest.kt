package nl.markmaaktmedia.guitartuner.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {

    @Test
    fun `bullets win over the subject, at most two`() {
        val body = """
            feat: a new card

            The long story, which is not for a card.

            - First thing
            * second thing
            - Third thing
        """.trimIndent()

        assertEquals(listOf("First thing", "Second thing"), ReleaseNotes.highlights(body))
    }

    @Test
    fun `without bullets only the subject is shown, without its type prefix`() {
        val body = "fix(tuner): the needle no longer jumps\n\nA paragraph that explains it at length.\n"

        assertEquals(listOf("The needle no longer jumps"), ReleaseNotes.highlights(body))
    }

    @Test
    fun `a heading is read as the subject`() {
        assertEquals(listOf("A line tuner"), ReleaseNotes.highlights("## feat: a line tuner\n\nSome text\n---\n\nInstall guitartuner.apk"))
    }

    @Test
    fun `everything after a rule is boilerplate, and trailers are not notes`() {
        val body = "- Real note\n- Another\n- Co-Authored-By: Someone <a@b.c>\n---\n- Install it"

        assertEquals(listOf("Real note", "Another"), ReleaseNotes.highlights(body))
        assertEquals(emptyList<String>(), ReleaseNotes.highlights("Co-Authored-By: Someone <a@b.c>"))
    }

    @Test
    fun `nothing from an empty body`() {
        assertEquals(emptyList<String>(), ReleaseNotes.highlights(null))
        assertEquals(emptyList<String>(), ReleaseNotes.highlights("  \n\n"))
    }
}
