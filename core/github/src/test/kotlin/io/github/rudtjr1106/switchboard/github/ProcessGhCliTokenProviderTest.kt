package io.github.rudtjr1106.switchboard.github

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 가짜 gh 스크립트를 PATH 에 두고 실행한다. 진짜 gh 는 건드리지 않는다 */
class ProcessGhCliTokenProviderTest {

    private val posix = !System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)
    private lateinit var directory: Path

    @BeforeTest
    fun setUp() {
        directory = Files.createTempDirectory("switchboard-gh")
    }

    @AfterTest
    fun tearDown() {
        directory.toFile().deleteRecursively()
    }

    private fun fakeGh(script: String) {
        val gh = directory.resolve("gh")
        Files.writeString(gh, "#!/bin/sh\n$script\n")
        Files.setPosixFilePermissions(gh, PosixFilePermissions.fromString("rwx------"))
    }

    private fun provider() = ProcessGhCliTokenProvider(
        environment = mapOf("PATH" to directory.toString()),
        homeDirectory = directory.resolve("home"),
        isWindows = false,
        fallbackDirectories = emptyList(),
    )

    @Test
    fun `reads the trimmed token from gh auth token`() = runTest {
        if (!posix) return@runTest
        fakeGh("""if [ "$1 $2" = "auth token" ]; then echo "gho_from_cli  "; else echo "unexpected args" >&2; exit 2; fi""")

        assertTrue(provider().isInstalled())
        assertEquals(GitHubToken("gho_from_cli", TokenSource.GH_CLI), provider().readToken())
    }

    @Test
    fun `returns null when gh is not logged in`() = runTest {
        if (!posix) return@runTest
        fakeGh("""echo "not logged in" >&2; exit 1""")

        assertTrue(provider().isInstalled())
        assertNull(provider().readToken())
    }

    @Test
    fun `returns null when gh prints nothing`() = runTest {
        if (!posix) return@runTest
        fakeGh("exit 0")
        assertNull(provider().readToken())
    }

    @Test
    fun `returns null without throwing when gh is not installed`() = runTest {
        assertFalse(provider().isInstalled())
        assertNull(provider().readToken())
    }
}
