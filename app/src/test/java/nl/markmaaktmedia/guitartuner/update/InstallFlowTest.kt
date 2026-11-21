package nl.markmaaktmedia.guitartuner.update

import android.content.pm.PackageInstaller
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The order things happen in, with a fetcher and an installer that do nothing but record. */
@OptIn(ExperimentalCoroutinesApi::class)
class InstallFlowTest {

    @get:Rule val folder = TemporaryFolder()

    private val release = AppRelease(24L, "Build 24", null, "https://example.test/guitartuner.apk", sizeBytes = 1000L)

    private inner class FakeFetcher : ApkFetcher {
        var calls = 0
        var failure: UpdateException? = null
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun fetch(release: AppRelease, onProgress: (done: Long, total: Long) -> Unit): File {
            calls++
            onProgress(250L, 1000L)
            gate?.await()
            failure?.let { throw it }
            onProgress(1000L, 1000L)
            return folder.newFile("guitartuner$calls.apk")
        }
    }

    private class FakeInstaller : ApkInstaller {
        var allowed = true
        var installs = 0
        var asked = 0
        var failure: UpdateException? = null
        override fun canInstall() = allowed
        override fun askPermission() { asked++ }
        override suspend fun install(file: File, release: AppRelease) {
            installs++
            failure?.let { throw it }
        }
    }

    @Test
    fun `download goes straight on to the install with no second press`() = runTest {
        val flow = InstallFlow(FakeFetcher(), FakeInstaller().also { }, this)
        assertEquals(DownloadState.Idle, flow.state)

        flow.start(release)
        advanceUntilIdle()

        assertEquals(DownloadState.Installing, flow.state)
    }

    @Test
    fun `progress carries the bytes for the size line`() = runTest {
        val fetcher = FakeFetcher().apply { gate = CompletableDeferred() }
        val flow = InstallFlow(fetcher, FakeInstaller(), this)

        flow.start(release)
        advanceUntilIdle()

        assertEquals(DownloadState.Downloading(0.25f, 250L, 1000L), flow.state)
        fetcher.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(DownloadState.Installing, flow.state)
    }

    @Test
    fun `a failed download says why, and trying again fetches it again`() = runTest {
        val fetcher = FakeFetcher().apply { failure = UpdateException(UpdateProblem.Network) }
        val installer = FakeInstaller()
        val flow = InstallFlow(fetcher, installer, this)

        flow.start(release)
        advanceUntilIdle()
        assertEquals(DownloadState.Failed(UpdateProblem.Network, UpdateProblem.Network.name), flow.state.withDetail())

        fetcher.failure = null
        flow.start(release)
        advanceUntilIdle()

        assertEquals(2, fetcher.calls)
        assertEquals(DownloadState.Installing, flow.state)
        assertEquals(1, installer.installs)
    }

    @Test
    fun `without the install permission it asks once, keeps the file, and carries on when it is given`() = runTest {
        val fetcher = FakeFetcher()
        val installer = FakeInstaller().apply { allowed = false }
        val flow = InstallFlow(fetcher, installer, this)

        flow.start(release)
        advanceUntilIdle()
        assertEquals(DownloadState.Failed(UpdateProblem.Permission), flow.state)
        assertEquals(1, installer.asked)

        // Back in the app without having allowed it: nothing happens yet.
        flow.resume()
        advanceUntilIdle()
        assertEquals(DownloadState.Failed(UpdateProblem.Permission), flow.state)

        installer.allowed = true
        flow.resume()
        advanceUntilIdle()

        assertEquals(DownloadState.Installing, flow.state)
        assertEquals("the file is not fetched twice", 1, fetcher.calls)
        assertEquals(1, installer.installs)
    }

    @Test
    fun `a file the installer refuses is a failure with its reason`() = runTest {
        val installer = FakeInstaller().apply { failure = UpdateException(UpdateProblem.Invalid) }
        val flow = InstallFlow(FakeFetcher(), installer, this)

        flow.start(release)
        advanceUntilIdle()

        assertTrue(flow.state is DownloadState.Failed)
        assertEquals(UpdateProblem.Invalid, (flow.state as DownloadState.Failed).problem)
    }

    @Test
    fun `cancelling the system question ends the installing, and only then`() = runTest {
        val flow = InstallFlow(FakeFetcher(), FakeInstaller(), this)

        // An answer before there is a question is somebody else's.
        flow.onInstallResult(InstallOutcome(PackageInstaller.STATUS_FAILURE_ABORTED, null))
        assertEquals(DownloadState.Idle, flow.state)

        flow.start(release)
        advanceUntilIdle()
        flow.onInstallResult(InstallOutcome(PackageInstaller.STATUS_FAILURE_ABORTED, "User cancelled"))

        assertEquals(DownloadState.Failed(UpdateProblem.Cancelled, "User cancelled"), flow.state)
    }

    @Test
    fun `pressing again while it works does nothing`() = runTest {
        val fetcher = FakeFetcher().apply { gate = CompletableDeferred() }
        val flow = InstallFlow(fetcher, FakeInstaller(), this)

        flow.start(release)
        advanceUntilIdle()
        flow.start(release)
        advanceUntilIdle()

        assertEquals(1, fetcher.calls)
        fetcher.gate!!.complete(Unit)
        advanceUntilIdle()
    }

    /** The exception's message is its detail, which these tests do not care about. */
    private fun DownloadState.withDetail(): DownloadState =
        if (this is DownloadState.Failed) copy(detail = problem.name) else this
}
