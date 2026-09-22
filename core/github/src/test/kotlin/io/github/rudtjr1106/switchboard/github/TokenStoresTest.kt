package io.github.rudtjr1106.switchboard.github

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenStoresTest {

    private lateinit var directory: Path

    @BeforeTest
    fun setUp() {
        directory = Files.createTempDirectory("switchboard-token-store")
    }

    @AfterTest
    fun tearDown() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `file store round trips a token and restricts permissions`() {
        val path = directory.resolve("nested/github-token.json")
        val store = FileTokenStore(path)
        assertNull(store.load())

        val token = GitHubToken("gho_secret", TokenSource.GH_CLI)
        store.save(token)

        assertEquals(token, store.load())
        if (path.fileSystem.supportedFileAttributeViews().contains("posix")) {
            assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(path))
        }

        store.save(GitHubToken("gho_other", TokenSource.DEVICE_FLOW))
        assertEquals(GitHubToken("gho_other", TokenSource.DEVICE_FLOW), store.load())

        store.clear()
        assertNull(store.load())
        assertFalse(Files.exists(path))
        store.clear()
    }

    @Test
    fun `file store returns null for a corrupt file`() {
        val path = directory.resolve("github-token.json")
        Files.writeString(path, "not json at all")
        assertNull(FileTokenStore(path).load())

        Files.writeString(path, """{"source":"FROM_THE_FUTURE","value":"x"}""")
        assertNull(FileTokenStore(path).load())

        Files.writeString(path, """{"source":"MANUAL","value":""}""")
        assertNull(FileTokenStore(path).load())
    }

    @Test
    fun `keyring value format round trips`() {
        val token = GitHubToken("gho_abc", TokenSource.DEVICE_FLOW)
        assertEquals("DEVICE_FLOW:gho_abc", TokenStores.encode(token))
        assertEquals(token, TokenStores.decode("DEVICE_FLOW:gho_abc"))
        assertEquals(GitHubToken("ghp_raw", TokenSource.MANUAL), TokenStores.decode("ghp_raw"))
        assertNull(TokenStores.decode("UNKNOWN:gho_abc"))
        assertNull(TokenStores.decode("GH_CLI:"))
        assertNull(TokenStores.decode("   "))
    }

    @Test
    fun `default store never throws on load even without a keyring backend`() {
        // 실제 키체인은 CI 마다 달라 검사하지 않는다. 어떤 백엔드가 걸리든 load 는 조용히 null 또는 값을 준다
        val store = TokenStores.default(directory)
        store.load()
        assertTrue(Files.isDirectory(directory))
    }
}
