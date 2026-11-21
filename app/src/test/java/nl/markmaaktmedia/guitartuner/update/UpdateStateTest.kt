package nl.markmaaktmedia.guitartuner.update

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class UpdateStateTest {

    @Test
    fun `every installer failure is named, and the cancel is the person's own`() {
        assertEquals(UpdateProblem.Cancelled, UpdateProblem.fromInstallStatus(PackageInstaller.STATUS_FAILURE_ABORTED))
        assertEquals(UpdateProblem.Conflict, UpdateProblem.fromInstallStatus(PackageInstaller.STATUS_FAILURE_CONFLICT))
        assertEquals(UpdateProblem.Storage, UpdateProblem.fromInstallStatus(PackageInstaller.STATUS_FAILURE_STORAGE))
        assertEquals(UpdateProblem.Incompatible, UpdateProblem.fromInstallStatus(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE))
        assertEquals(UpdateProblem.Invalid, UpdateProblem.fromInstallStatus(PackageInstaller.STATUS_FAILURE_INVALID))
        assertEquals(UpdateProblem.Blocked, UpdateProblem.fromInstallStatus(PackageInstaller.STATUS_FAILURE_BLOCKED))
        assertEquals(UpdateProblem.Other, UpdateProblem.fromInstallStatus(PackageInstaller.STATUS_FAILURE))
        assertEquals(UpdateProblem.Other, UpdateProblem.fromInstallStatus(1234))
    }

    @Test
    fun `sizes are written in the language of the phone`() {
        assertEquals("12.4", SizeText.megabytes(12_400_000L, Locale.US))
        assertEquals("12,4", SizeText.megabytes(12_400_000L, Locale("nl", "NL")))
        assertEquals("22", SizeText.total(22_400_000L, Locale("nl", "NL")))
        assertEquals("1,4", SizeText.total(1_389_506L, Locale("nl", "NL")))
        assertEquals("0,0", SizeText.megabytes(0L, Locale("nl", "NL")))
    }

    @Test
    fun `a file is only handed over when it is the announced build of this app`() {
        val own = "nl.markmaaktmedia.guitartuner"
        assertNull(ArchiveCheck.problem(own, 24L, own, 24L))
        assertEquals(UpdateProblem.Incomplete, ArchiveCheck.problem(null, null, own, 24L))
        assertEquals(UpdateProblem.Invalid, ArchiveCheck.problem("com.other.app", 24L, own, 24L))
        assertEquals(UpdateProblem.Invalid, ArchiveCheck.problem(own, 23L, own, 24L))
    }
}
