package io.github.rudtjr1106.switchboard.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileModelStoreTest {

    private val dir: Path = createTempDirectory("switchboard-models")

    /** 1.5 MB: 512 KB 마다 진행을 알리니 서너 번 나온다 */
    private val data = ByteArray(1_500_000) { (it * 31 % 251).toByte() }
    private val spec = ModelCatalog.GEMMA_3_1B.copy(
        fileName = "tiny.gguf",
        downloadUrl = "https://huggingface.test/resolve/main/tiny.gguf",
        sizeBytes = data.size.toLong(),
    )
    private val target = dir.resolve(spec.fileName)
    private val part = dir.resolve(spec.fileName + FileModelStore.PART_SUFFIX)

    @AfterTest
    fun cleanup() {
        dir.toFile().deleteRecursively()
    }

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine { request -> handler(request) })

    private fun MockRequestHandleScope.full() =
        respond(data, headers = headersOf(HttpHeaders.ContentLength, data.size.toString()))

    @Test
    fun `fresh download reports progress then done`() = runTest {
        val store = FileModelStore(dir, client { full() })
        assertNull(store.installedPath(spec))

        val events = store.download(spec).toList()

        val progress = events.filterIsInstance<ModelDownloadEvent.Progress>()
        assertTrue(progress.size in 2..6, "진행 이벤트 ${progress.size}개")
        assertEquals(data.size.toLong(), progress.last().downloadedBytes)
        assertEquals(data.size.toLong(), progress.last().totalBytes)
        assertEquals(1f, progress.last().fraction)
        assertEquals(ModelDownloadEvent.Done(target), events.last())
        assertContentEquals(data, target.readBytes())
        assertFalse(part.exists())
        assertEquals(target, store.installedPath(spec))
        assertTrue(store.isInstalled(spec))
    }

    @Test
    fun `resumes a part file with a range request and appends`() = runTest {
        val head = 700_000
        dir.createDirectories()
        part.writeBytes(data.copyOfRange(0, head))
        var range: String? = null
        val store = FileModelStore(dir, client { request ->
            range = request.headers[HttpHeaders.Range]
            val from = range!!.removePrefix("bytes=").removeSuffix("-").toInt()
            respond(
                data.copyOfRange(from, data.size),
                HttpStatusCode.PartialContent,
                headersOf(
                    HttpHeaders.ContentRange to listOf("bytes $from-${data.size - 1}/${data.size}"),
                    HttpHeaders.ContentLength to listOf((data.size - from).toString()),
                ),
            )
        })

        val events = store.download(spec).toList()

        assertEquals("bytes=$head-", range)
        assertEquals(head.toLong(), assertIs<ModelDownloadEvent.Progress>(events.first()).downloadedBytes)
        assertEquals(ModelDownloadEvent.Done(target), events.last())
        assertContentEquals(data, target.readBytes())
        assertFalse(part.exists())
    }

    @Test
    fun `starts over when the server ignores the range`() = runTest {
        dir.createDirectories()
        part.writeBytes(ByteArray(100) { 7 })
        var range: String? = null
        val store = FileModelStore(dir, client { request ->
            range = request.headers[HttpHeaders.Range]
            full()
        })

        val events = store.download(spec).toList()

        assertEquals("bytes=100-", range)
        assertEquals(ModelDownloadEvent.Done(target), events.last())
        assertContentEquals(data, target.readBytes())
    }

    @Test
    fun `follows a redirect like hugging face`() = runTest {
        val store = FileModelStore(dir, client { request ->
            if (request.url.host == "huggingface.test") {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://cdn.test/blob/tiny.gguf"))
            } else {
                full()
            }
        })
        assertEquals(ModelDownloadEvent.Done(target), store.download(spec).toList().last())
        assertContentEquals(data, target.readBytes())
    }

    @Test
    fun `size mismatch fails and drops the part file`() = runTest {
        val wrong = spec.copy(sizeBytes = data.size + 1L)
        val store = FileModelStore(dir, client { full() })

        val last = store.download(wrong).toList().last()

        val failed = assertIs<ModelDownloadEvent.Failed>(last)
        assertTrue(failed.message.contains("크기"), failed.message)
        assertFalse(target.exists())
        assertFalse(part.exists())
    }

    @Test
    fun `http error fails without throwing`() = runTest {
        val store = FileModelStore(dir, client { respond("nope", HttpStatusCode.NotFound) })
        val failed = assertIs<ModelDownloadEvent.Failed>(store.download(spec).toList().single())
        assertTrue(failed.message.contains("404"), failed.message)
    }

    @Test
    fun `cancelling keeps the part file for a later resume`() = runTest {
        // 600 KB 만 넣고 닫지 않는 채널: 진행 이벤트를 받자마자 수집을 끊는다
        val channel = ByteChannel()
        channel.writeFully(data, 0, 600_000)
        channel.flush()
        val store = FileModelStore(dir, client { respond(channel, headers = headersOf(HttpHeaders.ContentLength, data.size.toString())) })

        val progress = store.download(spec).first { it is ModelDownloadEvent.Progress && it.downloadedBytes >= 512 * 1024 }

        assertIs<ModelDownloadEvent.Progress>(progress)
        assertTrue(part.exists())
        assertTrue(part.fileSize() >= 512 * 1024)
        assertFalse(target.exists())
        channel.cancel(null)
    }

    @Test
    fun `already installed emits done without a request`() = runTest {
        dir.createDirectories()
        target.writeBytes(data)
        var requests = 0
        val store = FileModelStore(dir, client { requests++; full() })
        assertEquals(listOf(ModelDownloadEvent.Done(target)), store.download(spec).toList())
        assertEquals(0, requests)
    }

    @Test
    fun `installedPath needs the exact size and delete removes both files`() {
        dir.createDirectories()
        target.writeBytes(data.copyOfRange(0, 10))
        part.writeBytes(data.copyOfRange(0, 10))
        val store = FileModelStore(dir, client { full() })
        assertNull(store.installedPath(spec))
        assertEquals(target, store.installedPath(spec.copy(sizeBytes = 0)))

        store.delete(spec)

        assertFalse(target.exists())
        assertFalse(part.exists())
    }
}
