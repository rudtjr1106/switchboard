package io.github.rudtjr1106.switchboard.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile

private val logger = KotlinLogging.logger {}

/**
 * 모델 파일을 [directory]/<fileName> 에 둔다
 *
 * 내려받는 동안은 `<fileName>.part` 에 쓰고 끝나면 이름을 바꾼다. 중간에 취소되면 .part 가 남고
 * 다음 [download] 가 `Range` 헤더로 이어받는다. Hugging Face 는 CDN 으로 302 를 보내므로 [client] 는
 * 리다이렉트를 따라가야 한다(Ktor 기본값). 서버가 Range 를 무시하고 200 을 주면 처음부터 다시 받는다.
 */
class FileModelStore(
    override val directory: Path,
    private val client: HttpClient,
    /** 끊긴 뒤 다시 이을 때 기다리는 시간의 기본 단위. 테스트에서 0 으로 줄인다 */
    private val retryDelayMs: Long = RETRY_DELAY_MS,
) : ModelStore {

    override fun installedPath(spec: ModelSpec): Path? {
        val path = directory.resolve(spec.fileName)
        if (!path.isRegularFile()) return null
        // 크기가 다르면 다른 파일이거나 덜 받은 것이다. .part 없이 남은 반쪽 파일을 걸러 낸다
        if (spec.sizeBytes > 0 && path.fileSize() != spec.sizeBytes) return null
        return path
    }

    override fun download(spec: ModelSpec): Flow<ModelDownloadEvent> = flow {
        val target = directory.resolve(spec.fileName)
        val part = partPath(spec)
        try {
            installedPath(spec)?.let {
                emit(ModelDownloadEvent.Done(it))
                return@flow
            }
            directory.createDirectories()
            fetchWithRetry(spec, part)
            if (spec.sizeBytes > 0 && part.fileSize() != spec.sizeBytes) {
                logger.warn { "${spec.id}: 받은 크기 ${part.fileSize()} ≠ 기대 ${spec.sizeBytes}, .part 삭제" }
                // 이어받아도 같은 결과일 테니 버린다
                part.deleteIfExists()
                throw DownloadFailed("내려받은 파일 크기가 맞지 않아요. 다시 시도해 주세요.")
            }
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            emit(ModelDownloadEvent.Done(target))
        } catch (e: CancellationException) {
            // .part 는 남긴다. 다음에 이어받는다
            throw e
        } catch (e: DownloadFailed) {
            emit(ModelDownloadEvent.Failed(e.message, e.cause))
        } catch (e: Exception) {
            logger.warn(e) { "${spec.id} 내려받기 실패" }
            emit(ModelDownloadEvent.Failed(describe(e), e))
        }
    }.flowOn(Dispatchers.IO)

    override fun delete(spec: ModelSpec) {
        directory.resolve(spec.fileName).deleteIfExists()
        partPath(spec).deleteIfExists()
    }

    private fun partPath(spec: ModelSpec): Path = directory.resolve(spec.fileName + PART_SUFFIX)

    /**
     * 연결이 끊기거나 멈추면 받은 데까지 두고 이어받기로 다시 시도한다
     *
     * 수 GB 파일은 와이파이가 잠깐 흔들려도 끊긴다. 사용자가 다시 누르지 않아도 [MAX_ATTEMPTS] 번까지 스스로 잇는다.
     * HTTP 오류(404 등)는 다시 해도 같아서 바로 실패로 낸다.
     */
    private suspend fun FlowCollector<ModelDownloadEvent>.fetchWithRetry(spec: ModelSpec, part: Path) {
        var attempt = 1
        while (true) {
            try {
                fetch(spec, part)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: DownloadFailed) {
                throw e
            } catch (e: IOException) {
                if (attempt >= MAX_ATTEMPTS) throw e
                logger.warn { "${spec.id}: 연결이 끊겨 이어받기 다시 시도 ($attempt/$MAX_ATTEMPTS): ${e.message}" }
                attempt++
                delay(retryDelayMs * attempt)
            }
        }
    }

    /** .part 가 있으면 그 뒤부터 요청한다. 416 이면 .part 가 서버 파일보다 크다는 뜻이라 지우고 한 번 더 */
    private suspend fun FlowCollector<ModelDownloadEvent>.fetch(spec: ModelSpec, part: Path) {
        val resumeFrom = if (part.isRegularFile()) part.fileSize() else 0L
        val statement = client.prepareGet(spec.downloadUrl) {
            if (resumeFrom > 0) header(HttpHeaders.Range, "bytes=$resumeFrom-")
            // 공용 클라이언트는 요청 전체를 60초로 끊는다. 수 GB 는 그보다 오래 걸리므로 전체 제한은 풀고,
            // 데이터가 멈춘 경우만 소켓 제한으로 잡는다
            if (client.pluginOrNull(HttpTimeout) != null) {
                timeout {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = STALL_TIMEOUT_MS
                }
            }
        }
        val restart = statement.execute { response ->
            when (response.status) {
                HttpStatusCode.PartialContent -> {
                    logger.info { "${spec.id}: $resumeFrom 바이트부터 이어받기" }
                    copyBody(response, part, offset = resumeFrom, total = totalOf(spec, response, resumeFrom))
                    false
                }
                HttpStatusCode.OK -> {
                    if (resumeFrom > 0) logger.info { "${spec.id}: 서버가 Range 를 무시해 처음부터 받기" }
                    copyBody(response, part, offset = 0, total = totalOf(spec, response, 0))
                    false
                }
                HttpStatusCode.RequestedRangeNotSatisfiable -> resumeFrom > 0
                else -> throw DownloadFailed("모델 서버가 응답하지 않아요 (HTTP ${response.status.value}).")
            }
        }
        if (restart) {
            part.deleteIfExists()
            fetch(spec, part)
        }
    }

    /** 스트리밍으로 파일에 쓰며 [PROGRESS_STEP] 마다 진행을 알린다. [offset] 이 0 이면 새로, 아니면 이어서 쓴다 */
    private suspend fun FlowCollector<ModelDownloadEvent>.copyBody(response: HttpResponse, part: Path, offset: Long, total: Long) {
        val channel = response.bodyAsChannel()
        val open = if (offset > 0) {
            arrayOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)
        } else {
            arrayOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        }
        var downloaded = offset
        var reported = offset
        emit(ModelDownloadEvent.Progress(downloaded, total))
        Files.newOutputStream(part, *open).use { out ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = channel.readAvailable(buffer)
                if (read < 0) break
                if (read == 0) continue
                out.write(buffer, 0, read)
                downloaded += read
                if (downloaded - reported >= PROGRESS_STEP) {
                    emit(ModelDownloadEvent.Progress(downloaded, total))
                    reported = downloaded
                }
            }
            out.flush()
        }
        if (downloaded != reported) emit(ModelDownloadEvent.Progress(downloaded, total))
    }

    /** 전체 크기. 카탈로그 값 → Content-Range 의 전체 → 남은 길이 + 이미 받은 만큼 순으로 찾고, 모르면 -1 */
    private fun totalOf(spec: ModelSpec, response: HttpResponse, offset: Long): Long {
        if (spec.sizeBytes > 0) return spec.sizeBytes
        response.headers[HttpHeaders.ContentRange]?.substringAfter('/', "")?.trim()?.toLongOrNull()?.let { return it }
        return response.contentLength()?.let { offset + it } ?: -1L
    }

    private fun describe(e: Exception): String = when (e) {
        is UnknownHostException, is ConnectException, is SocketTimeoutException,
        is UnresolvedAddressException, is HttpRequestTimeoutException,
        -> "모델을 내려받지 못했어요. 네트워크 연결을 확인해 주세요."
        is IOException -> "모델을 내려받는 중 문제가 생겼어요: ${e.message ?: e::class.simpleName}"
        else -> "모델을 내려받지 못했어요: ${e.message ?: e::class.simpleName}"
    }

    private class DownloadFailed(override val message: String, cause: Throwable? = null) : Exception(message, cause)

    companion object {
        const val PART_SUFFIX = ".part"
        const val MAX_ATTEMPTS = 4
        const val RETRY_DELAY_MS = 2_000L

        /** 이만큼 데이터가 한 바이트도 오지 않으면 멈춘 것으로 보고 이어받기로 넘어간다 */
        const val STALL_TIMEOUT_MS = 60_000L
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_STEP = 512L * 1024
    }
}
