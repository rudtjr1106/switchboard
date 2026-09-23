package io.github.rudtjr1106.switchboard.app.update

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.app.platform.AppPaths
import io.github.rudtjr1106.switchboard.app.platform.SafeFiles
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

/** 업데이트가 어디까지 왔는지 (화면에 진행을 보여 주려고) */
sealed interface InstallStep {
    data class Downloading(val received: Long, val total: Long) : InstallStep
    data object Verifying : InstallStep
    data object Applying : InstallStep
    data object Restarting : InstallStep
}

class UpdateFailed(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 새 버전을 받아 제자리에서 바꿔 끼운다
 *
 * 브라우저로 내보내지 않고 앱이 직접 설치한다. 대신 **받은 파일을 그대로** 넣는다. macOS 는 Apple 이 공증한
 * 앱 번들을 통째로 바꾸므로 서명·공증이 유지되고, Windows 는 배포한 zip 의 SHA-256 을 릴리즈의 체크섬 파일과
 * 맞춰 본다. 앱 안의 jar 만 골라 바꾸는 방식(델타)은 서명 봉인을 깨뜨려서 쓰지 않는다.
 *
 * 설치가 안 되는 상황(개발 중 실행, 권한 없음)에서는 [UpdateFailed] 를 던져 화면이 브라우저 내려받기로 안내한다.
 */
class UpdateInstaller(
    private val client: HttpClient,
    private val location: InstallLocation? = InstallLocation.detect(),
    private val workDir: Path = AppPaths.dataDir.resolve("updates"),
) {

    /** 제자리 업데이트를 할 수 있는 설치본인지 */
    val supported: Boolean get() = location != null

    suspend fun install(update: AvailableUpdate, onStep: (InstallStep) -> Unit) {
        val where = location ?: throw UpdateFailed("설치된 앱이 아니라서 앱 안에서 업데이트할 수 없어요")
        val asset = update.asset ?: throw UpdateFailed("이 릴리즈에는 ${AppPaths.os} 용 설치 파일이 없어요")
        withContext(Dispatchers.IO) {
            Files.createDirectories(workDir)
            val file = workDir.resolve(asset.name)
            file.deleteIfExists()
            download(asset.downloadUrl, file, asset.sizeBytes, onStep)
            onStep(InstallStep.Verifying)
            when (where) {
                is InstallLocation.MacBundle -> applyMac(file, where.bundle, onStep)
                is InstallLocation.WindowsFolder -> {
                    // 체크섬은 받아 두고 넘긴다 (설치 쪽에서 다시 suspend 하지 않게)
                    val sums = update.checksums?.let { fetchText(it.downloadUrl) }
                        ?: throw UpdateFailed("릴리즈에 체크섬 파일이 없어 설치를 멈췄어요")
                    applyWindows(file, where.folder, asset.name, sums, onStep)
                }
            }
        }
    }

    // ---- 내려받기 ----

    private suspend fun download(url: String, target: Path, expectedSize: Long, onStep: (InstallStep) -> Unit) {
        val statement = client.prepareGet(url) {
            // 공용 클라이언트는 요청 전체를 60초로 끊는다. 100MB 는 그보다 오래 걸린다
            if (client.pluginOrNull(HttpTimeout) != null) {
                timeout {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = STALL_TIMEOUT_MS
                }
            }
        }
        statement.execute { response ->
            if (response.status != HttpStatusCode.OK) {
                throw UpdateFailed("내려받지 못했어요 (HTTP ${response.status.value})")
            }
            val total = response.contentLength() ?: expectedSize
            val channel = response.bodyAsChannel()
            var received = 0L
            var reported = 0L
            onStep(InstallStep.Downloading(0, total))
            Files.newOutputStream(target).use { out ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = channel.readAvailable(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    out.write(buffer, 0, read)
                    received += read
                    if (received - reported >= PROGRESS_STEP) {
                        onStep(InstallStep.Downloading(received, total))
                        reported = received
                    }
                }
            }
            onStep(InstallStep.Downloading(received, total))
        }
    }

    private suspend fun fetchText(url: String): String =
        client.prepareGet(url).execute { response ->
            if (response.status != HttpStatusCode.OK) throw UpdateFailed("체크섬 파일을 받지 못했어요 (HTTP ${response.status.value})")
            response.bodyAsText()
        }

    // ---- macOS ----

    /** DMG 를 열어 안의 앱을 검사한 뒤, 지금 앱 번들과 통째로 바꾼다 */
    private fun applyMac(dmg: Path, bundle: Path, onStep: (InstallStep) -> Unit) {
        val mount = attach(dmg)
        try {
            val source = Files.list(mount).use { paths ->
                paths.filter { it.name.endsWith(".app") }.findFirst().orElse(null)
            } ?: throw UpdateFailed("내려받은 파일 안에 앱이 없어요")
            verifyMac(source, bundle)

            onStep(InstallStep.Applying)
            val staging = bundle.resolveSibling(".${bundle.name}.new")
            val backup = bundle.resolveSibling(".${bundle.name}.old")
            SafeFiles.deleteTree(staging)
            SafeFiles.deleteTree(backup)
            // ditto 는 확장 속성과 서명을 그대로 옮긴다. cp -r 는 서명이 깨질 수 있다
            exec("ditto", source.toString(), staging.toString())
            Files.move(bundle, backup, StandardCopyOption.ATOMIC_MOVE)
            try {
                Files.move(staging, bundle, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: Exception) {
                Files.move(backup, bundle, StandardCopyOption.ATOMIC_MOVE)
                throw UpdateFailed("새 앱을 제자리에 넣지 못해 되돌렸어요", e)
            }
            SafeFiles.deleteTree(backup)
        } finally {
            runCatching { exec("hdiutil", "detach", mount.toString(), "-quiet") }
                .onFailure { logger.warn(it) { "DMG 를 내리지 못했어요: $mount" } }
        }
        onStep(InstallStep.Restarting)
        ProcessBuilder("open", "-n", bundle.toString()).start()
        exitProcess(0)
    }

    /**
     * 받은 앱이 Apple 이 공증한, 지금 앱과 같은 개발자의 것인지 본다
     *
     * 이 검사를 통과한 것만 설치하므로, 중간에서 파일이 바뀌었다면 설치되지 않는다.
     */
    private fun verifyMac(downloaded: Path, installed: Path) {
        runCatching { exec("codesign", "--verify", "--strict", "--deep", downloaded.toString()) }
            .onFailure { throw UpdateFailed("내려받은 앱의 서명이 올바르지 않아요", it) }
        val assessment = runCatching { exec("spctl", "--assess", "--type", "exec", "--verbose=2", downloaded.toString()) }
            .getOrElse { throw UpdateFailed("Apple 공증을 확인하지 못했어요", it) }
        if ("accepted" !in assessment) throw UpdateFailed("Apple 공증을 통과하지 못한 앱이에요")
        val expected = teamIdOf(installed)
        val actual = teamIdOf(downloaded)
        if (expected != null && expected != actual) {
            throw UpdateFailed("지금 쓰는 앱과 다른 개발자($actual)가 서명한 파일이라 설치하지 않았어요")
        }
    }

    private fun teamIdOf(app: Path): String? = runCatching {
        exec("codesign", "-dv", "--verbose=2", app.toString())
            .lineSequence().firstOrNull { it.startsWith("TeamIdentifier=") }
            ?.removePrefix("TeamIdentifier=")?.trim()?.takeIf { it.isNotBlank() && it != "not set" }
    }.getOrNull()

    /** `hdiutil attach` 출력에서 마운트 지점을 찾는다 (`/dev/disk4s1 \t Apple_HFS \t /Volumes/스위치보드`) */
    private fun attach(dmg: Path): Path {
        val output = exec("hdiutil", "attach", dmg.toString(), "-nobrowse", "-readonly")
        val mount = output.lineSequence()
            .mapNotNull { line -> line.indexOf("/Volumes/").takeIf { it >= 0 }?.let { line.substring(it).trim() } }
            .lastOrNull()
        return mount?.let(Path::of) ?: throw UpdateFailed("내려받은 DMG 를 열지 못했어요")
    }

    // ---- Windows ----

    /**
     * zip 을 옆 폴더에 풀고, 앱이 꺼진 뒤 폴더를 바꿔 끼우는 스크립트를 띄운다
     *
     * 도는 중인 `Switchboard.exe` 는 자기 자신을 못 지우므로 바꿔 끼우기는 앱이 꺼진 뒤에 일어나야 한다.
     */
    private fun applyWindows(zip: Path, folder: Path, assetName: String, sums: String, onStep: (InstallStep) -> Unit) {
        val expected = expectedSha256(sums, assetName)
            ?: throw UpdateFailed("체크섬 목록에서 $assetName 을 찾지 못했어요")
        if (!sha256(zip).equals(expected, ignoreCase = true)) throw UpdateFailed("내려받은 파일이 손상됐어요")

        onStep(InstallStep.Applying)
        val staging = folder.resolveSibling("${folder.name}.new")
        SafeFiles.deleteTree(staging)
        unzip(zip, staging)
        val root = if (staging.resolve(EXE).exists()) {
            staging
        } else {
            Files.list(staging).use { it.filter { p -> p.isDirectory() && p.resolve(EXE).exists() }.findFirst().orElse(null) }
                ?: throw UpdateFailed("내려받은 zip 안에 $EXE 가 없어요")
        }
        check(folder.resolve(EXE).exists()) { "설치 폴더가 아니에요: $folder" }

        val script = workDir.resolve("apply-update.cmd")
        Files.writeString(script, windowsSwapScript(ProcessHandle.current().pid(), folder, root))
        ProcessBuilder("cmd", "/c", "start", "", "/min", script.toString()).start()
        onStep(InstallStep.Restarting)
        exitProcess(0)
    }

    /** zip 을 [target] 아래에만 푼다 (`../` 가 들어간 항목은 건너뛴다) */
    private fun unzip(zip: Path, target: Path) {
        Files.createDirectories(target)
        ZipInputStream(Files.newInputStream(zip).buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val path = target.resolve(entry.name).normalize()
                if (!path.startsWith(target)) {
                    logger.warn { "zip 항목이 폴더 밖을 가리켜 건너뜀: ${entry.name}" }
                    continue
                }
                if (entry.isDirectory) {
                    Files.createDirectories(path)
                } else {
                    Files.createDirectories(path.parent)
                    Files.newOutputStream(path).use { input.copyTo(it) }
                }
            }
        }
    }

    private fun exec(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        if (code != 0) throw UpdateFailed("${command.first()} 이(가) 실패했어요 (코드 $code)\n${output.take(400)}")
        return output
    }

    private fun sha256(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).buffered().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val EXE = "${InstallLocation.EXECUTABLE}.exe"
        private const val BUFFER_SIZE = 1 shl 16
        private const val PROGRESS_STEP = 1L shl 20
        private const val STALL_TIMEOUT_MS = 60_000L

        /** `sha256sum` 형식(`<해시>  <파일 이름>`)에서 [name] 의 해시를 찾는다 */
        fun expectedSha256(sums: String, name: String): String? = sums.lineSequence()
            .map { it.trim().split(Regex("\\s+")) }
            .firstOrNull { it.size >= 2 && it[1].removePrefix("*") == name }
            ?.first()

        /**
         * 앱이 꺼지기를 기다렸다가 폴더를 바꾸고 다시 띄우는 배치 스크립트
         *
         * 마지막 줄의 `del "%~f0"` 은 스크립트가 자기 자신을 지우는 것으로, Windows 에서는 도는 중에도 된다.
         */
        fun windowsSwapScript(pid: Long, target: Path, staging: Path): String {
            // cmd 는 / 를 경로 구분자로 잘 받지 못한다. 어디서 만들든 \\ 로 맞춘다
            val target = target.toString().replace('/', '\\')
            val staging = staging.toString().replace('/', '\\')
            return """
            @echo off
            chcp 65001 >nul
            :wait
            tasklist /FI "PID eq $pid" 2>nul | find "$pid" >nul
            if not errorlevel 1 (
                timeout /t 1 /nobreak >nul
                goto wait
            )
            rmdir /s /q "$target"
            move "$staging" "$target" >nul
            start "" "$target\$EXE"
            del "%~f0"
            """.trimIndent().replace("\n", "\r\n")
        }
    }
}
