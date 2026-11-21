package nl.markmaaktmedia.guitartuner.update

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetAddress
import java.net.InetSocketAddress

/** The download against a real server on this machine, so the bytes, the progress and the failures are real. */
class HttpApkFetcherTest {

    @get:Rule val folder = TemporaryFolder()
    private lateinit var server: HttpServer
    private val payload = ByteArray(1_000_000) { (it % 251).toByte() }
    private var served = 0

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/ok.apk") { exchange ->
                served++
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
            }
            createContext("/short.apk") { exchange ->
                // Says a million bytes and sends half, then hangs up.
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.write(payload, 0, 500_000)
                exchange.responseBody.flush()
                exchange.close()
            }
            createContext("/gone.apk") { exchange ->
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
            start()
        }
    }

    @After
    fun stop() = server.stop(0)

    private fun release(path: String) =
        AppRelease(24L, "Build 24", null, "http://127.0.0.1:${server.address.port}$path", sizeBytes = payload.size.toLong())

    @Test
    fun `downloads the file and reports progress up to the full size`() = runBlocking {
        val seen = mutableListOf<Pair<Long, Long>>()
        val file = HttpApkFetcher(folder.root).fetch(release("/ok.apk")) { done, total -> seen += done to total }

        assertEquals("guitartuner-24.apk", file.name)
        assertTrue(file.readBytes().contentEquals(payload))
        assertEquals(0L to payload.size.toLong(), seen.first())
        assertEquals(payload.size.toLong() to payload.size.toLong(), seen.last())
        assertTrue("progress only ever goes forward", seen.map { it.first }.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("about one report per percent, not one per buffer", seen.size in 10..130)
    }

    @Test
    fun `a complete file for the same build is kept, not downloaded again`() = runBlocking {
        val fetcher = HttpApkFetcher(folder.root)
        fetcher.fetch(release("/ok.apk")) { _, _ -> }
        val again = fetcher.fetch(release("/ok.apk")) { _, _ -> }

        assertEquals(1, served)
        assertTrue(again.readBytes().contentEquals(payload))
    }

    @Test
    fun `a file of another build is replaced`() = runBlocking {
        val fetcher = HttpApkFetcher(folder.root)
        fetcher.fetch(release("/ok.apk")) { _, _ -> }
        val newer = release("/ok.apk").copy(versionCode = 25L)
        val file = fetcher.fetch(newer) { _, _ -> }

        assertEquals(2, served)
        assertEquals("guitartuner-25.apk", file.name)
        assertEquals(listOf("guitartuner-25.apk"), folder.root.walk().filter { it.isFile }.map { it.name }.toList())
    }

    @Test
    fun `a missing release is a server problem with the code in it`() = runBlocking<Unit> {
        try {
            HttpApkFetcher(folder.root).fetch(release("/gone.apk")) { _, _ -> }
            fail("expected a failure")
        } catch (e: UpdateException) {
            assertEquals(UpdateProblem.Server, e.problem)
            assertEquals("404", e.detail)
        }
    }

    @Test
    fun `a download that stops halfway is not handed on`() = runBlocking<Unit> {
        try {
            HttpApkFetcher(folder.root).fetch(release("/short.apk")) { _, _ -> }
            fail("expected a failure")
        } catch (e: UpdateException) {
            assertTrue(e.problem == UpdateProblem.Incomplete || e.problem == UpdateProblem.Network)
        }
        assertTrue("no half file is left behind to be installed", folder.root.walk().none { it.name.endsWith(".apk") })
    }

    @Test
    fun `an address nobody answers on is a network problem`() = runBlocking<Unit> {
        val dead = AppRelease(24L, "Build 24", null, "http://127.0.0.1:1/x.apk")
        try {
            HttpApkFetcher(folder.root).fetch(dead) { _, _ -> }
            fail("expected a failure")
        } catch (e: UpdateException) {
            assertEquals(UpdateProblem.Network, e.problem)
        }
    }
}
