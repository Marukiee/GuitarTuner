package nl.markmaaktmedia.guitartuner.update

import java.util.Locale

/**
 * The two lines of release notes the update card shows.
 *
 * The notes are whatever the release body holds, which on this repository is the commit message
 * of the push that made the release. A commit message is prose, so the card does not try to
 * show it all: it takes the first two bullet lines when the message has bullets, and otherwise
 * only the subject, tidied up. Nothing from a release with no body, and nothing from the
 * redirect route, which carries no body at all.
 */
internal object ReleaseNotes {

    private val bullet = Regex("""^\s*[-*•]\s+(.+)$""")
    private val heading = Regex("""^\s*#+\s*(.+)$""")
    private val typePrefix = Regex("""^[a-z]+(\([^)]*\))?!?:\s+""")
    private val trailer = Regex("""^(co-authored-by|signed-off-by):""", RegexOption.IGNORE_CASE)

    fun highlights(body: String?, max: Int = 2): List<String> {
        val lines = body.orEmpty().lineSequence()
            .map { it.trim() }
            // The release workflow may close the notes with a rule and a line of boilerplate.
            .takeWhile { it != "---" }
            .filterNot { trailer.containsMatchIn(it) }
            .toList()

        val bullets = lines.mapNotNull { bullet.matchEntire(it)?.groupValues?.get(1) }
            .map(::tidy)
            .filter { it.isNotBlank() }
        if (bullets.isNotEmpty()) return bullets.take(max)

        val subject = lines.firstOrNull { it.isNotBlank() }
            ?.let { heading.matchEntire(it)?.groupValues?.get(1) ?: it }
            ?.let(::tidy)
        return listOfNotNull(subject?.takeIf { it.isNotBlank() })
    }

    /** Drops a `feat:` style prefix and starts with a capital, so the line reads as a sentence. */
    private fun tidy(line: String): String =
        line.trim().replace(typePrefix, "").trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
}
