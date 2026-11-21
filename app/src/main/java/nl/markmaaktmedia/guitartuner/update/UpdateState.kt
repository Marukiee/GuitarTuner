package nl.markmaaktmedia.guitartuner.update

import android.content.pm.PackageInstaller
import androidx.annotation.StringRes
import nl.markmaaktmedia.guitartuner.R
import java.util.Locale

/** Why an update did not happen, in words the card can say. */
enum class UpdateProblem(@StringRes val text: Int, val takesDetail: Boolean = false) {
    Network(R.string.update_problem_network),
    Server(R.string.update_problem_server, takesDetail = true),
    Incomplete(R.string.update_problem_incomplete),
    Storage(R.string.update_problem_storage),
    Permission(R.string.update_allow_installs),
    Conflict(R.string.update_problem_conflict),
    Incompatible(R.string.update_problem_incompatible),
    Invalid(R.string.update_problem_invalid),
    Blocked(R.string.update_problem_blocked),
    Cancelled(R.string.update_problem_cancelled),
    Other(R.string.update_problem_other, takesDetail = true),
    ;

    companion object {
        /** What the system installer's status code says went wrong. */
        fun fromInstallStatus(status: Int): UpdateProblem = when (status) {
            PackageInstaller.STATUS_FAILURE_STORAGE -> Storage
            PackageInstaller.STATUS_FAILURE_CONFLICT -> Conflict
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> Incompatible
            PackageInstaller.STATUS_FAILURE_INVALID -> Invalid
            PackageInstaller.STATUS_FAILURE_BLOCKED -> Blocked
            PackageInstaller.STATUS_FAILURE_ABORTED -> Cancelled
            else -> Other
        }
    }
}

/** A problem with the reason attached, thrown by the parts of an update that can fail. */
class UpdateException(val problem: UpdateProblem, val detail: String? = null) : Exception(detail ?: problem.name)

/**
 * Where an update stands. The card, the Updates page and the flow all read this one value.
 *
 * [Downloading] carries 0..1, or a negative number when the size is not known, and the bytes
 * for the "x of y MB" line. [Installing] is the stretch after the download, while the system
 * installer has the file: it ends with the app being replaced, or with [Failed].
 */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: Float, val doneBytes: Long = 0L, val totalBytes: Long = 0L) : DownloadState
    data object Installing : DownloadState
    data class Failed(val problem: UpdateProblem, val detail: String? = null) : DownloadState
}

/** Sizes as the card writes them: "12,4" of "22" MB, in the language of the phone. */
internal object SizeText {
    private const val MEGABYTE = 1_000_000.0

    fun megabytes(bytes: Long, locale: Locale, whole: Boolean = false): String =
        String.format(locale, if (whole) "%.0f" else "%.1f", bytes / MEGABYTE)

    /** The total is shown without a decimal once it is big enough for the decimal to be noise. */
    fun total(bytes: Long, locale: Locale): String = megabytes(bytes, locale, whole = bytes >= 10 * MEGABYTE)
}

/** Checks the downloaded file is the build that was announced, before the system sees it. */
internal object ArchiveCheck {
    fun problem(archivePackage: String?, archiveCode: Long?, ownPackage: String, announcedCode: Long): UpdateProblem? = when {
        archivePackage == null || archiveCode == null -> UpdateProblem.Incomplete
        archivePackage != ownPackage -> UpdateProblem.Invalid
        archiveCode != announcedCode -> UpdateProblem.Invalid
        else -> null
    }
}
