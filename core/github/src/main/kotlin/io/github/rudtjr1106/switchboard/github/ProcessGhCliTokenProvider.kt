package io.github.rudtjr1106.switchboard.github

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/**
 * `gh auth token` 을 실행해 GitHub CLI 의 토큰을 빌려 쓴다
 *
 * Finder·시작 메뉴로 띄운 앱은 PATH 가 짧아서 흔한 설치 위치를 뒤에 덧붙인다.
 * 설치돼 있지 않거나 로그인 전이면 예외 대신 null 을 돌려준다. 로그인 유도는 UI 몫이다.
 */
class ProcessGhCliTokenProvider(
    private val environment: Map<String, String> = System.getenv(),
    private val homeDirectory: Path = Path.of(System.getProperty("user.home", ".")),
    private val isWindows: Boolean = System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true),
    private val timeout: Duration = 10.seconds,
    /** PATH 에 없을 때 뒤져볼 위치. 테스트는 비워서 진짜 gh 를 건드리지 않는다 */
    private val fallbackDirectories: List<Path> = defaultFallbackDirectories(isWindows, environment, homeDirectory),
) : GhCliTokenProvider {

    override suspend fun isInstalled(): Boolean = withContext(Dispatchers.IO) { locate() != null }

    override suspend fun readToken(): GitHubToken? {
        val executable = withContext(Dispatchers.IO) { locate() } ?: return null
        return try {
            // 취소되면 스레드를 인터럽트해 waitFor 에서 빠져나오고 finally 가 프로세스를 죽인다
            runInterruptible(Dispatchers.IO) { run(executable) }
        } catch (e: IOException) {
            logger.debug(e) { "gh auth token 실행 실패" }
            null
        }
    }

    /** 실행 가능한 gh 의 경로. PATH 를 먼저 보고 없으면 OS 별 흔한 설치 위치를 본다 */
    fun locate(): Path? {
        val pathDirectories = environment["PATH"].orEmpty()
            .split(File.pathSeparatorChar)
            .filter { it.isNotBlank() }
            .map { Path.of(it) }
        val candidates = (pathDirectories + fallbackDirectories).map { it.resolve(executableName) }
        return candidates.firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
    }

    private val executableName: String get() = if (isWindows) "gh.exe" else "gh"

    private fun run(executable: Path): GitHubToken? {
        val process = ProcessBuilder(executable.toString(), "auth", "token")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        try {
            // 토큰 한 줄이라 파이프 버퍼가 찰 일이 없어 먼저 기다려도 된다
            if (!process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
                logger.warn { "gh auth token 이 ${timeout.inWholeSeconds}초 안에 끝나지 않았어요" }
                return null
            }
            if (process.exitValue() != 0) return null
            val token = process.inputStream.bufferedReader().readText().trim()
            return token.takeIf { it.isNotEmpty() }?.let { GitHubToken(it, TokenSource.GH_CLI) }
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    companion object {
        /** Homebrew·MacPorts·mise, Windows 설치 프로그램의 기본 위치 */
        fun defaultFallbackDirectories(isWindows: Boolean, environment: Map<String, String>, homeDirectory: Path): List<Path> =
            if (isWindows) {
                listOfNotNull(
                    Path.of(environment["ProgramFiles"] ?: "C:\\Program Files", "GitHub CLI"),
                    environment["LOCALAPPDATA"]?.let { Path.of(it, "Programs", "GitHub CLI") },
                )
            } else {
                listOf(
                    Path.of("/opt/homebrew/bin"),
                    Path.of("/usr/local/bin"),
                    Path.of("/opt/local/bin"),
                    homeDirectory.resolve(".local/share/mise/shims"),
                )
            }
    }
}
