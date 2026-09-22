package io.github.rudtjr1106.switchboard.github

import com.github.javakeyring.BackendNotSupportedException
import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

private val logger = KotlinLogging.logger {}

/** 토큰을 저장하지 못했을 때. 화면에 그대로 보여줄 수 있는 문장이다 */
class TokenStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** OS 자격 증명 저장소(macOS 키체인, Windows 자격 증명 관리자, Linux 시크릿 서비스)에 보관한다 */
class KeyringTokenStore(
    private val keyring: Keyring,
    private val service: String = TokenStores.SERVICE,
    private val account: String = TokenStores.ACCOUNT,
) : TokenStore {

    override fun load(): GitHubToken? = try {
        keyring.getPassword(service, account)?.let(TokenStores::decode)
    } catch (e: PasswordAccessException) {
        // 저장한 적이 없을 때도 이 예외로 온다. 정상 경로라 경고까지는 아니다
        logger.debug { "키체인에 토큰이 없어요: ${e.message}" }
        null
    } catch (e: Exception) {
        logger.warn(e) { "키체인에서 토큰을 읽지 못했어요" }
        null
    }

    override fun save(token: GitHubToken) {
        try {
            keyring.setPassword(service, account, TokenStores.encode(token))
        } catch (e: PasswordAccessException) {
            throw TokenStoreException("OS 자격 증명 저장소에 토큰을 저장하지 못했어요.", e)
        }
    }

    override fun clear() {
        try {
            keyring.deletePassword(service, account)
        } catch (e: PasswordAccessException) {
            logger.debug { "지울 토큰이 없어요: ${e.message}" }
        }
    }

    companion object {
        /** @throws BackendNotSupportedException 이 OS 에서 쓸 수 있는 저장소가 없을 때 */
        fun create(): KeyringTokenStore = KeyringTokenStore(Keyring.create())
    }
}

/**
 * 키체인을 못 쓸 때의 폴백. JSON 파일 하나에 저장한다
 *
 * 평문이라 POSIX 에서는 소유자만 읽고 쓸 수 있게(rw-------) 권한을 줄인다.
 */
class FileTokenStore(private val path: Path) : TokenStore {

    @Serializable
    private data class StoredToken(val source: String, val value: String)

    override fun load(): GitHubToken? {
        if (!Files.exists(path)) return null
        return try {
            val stored = TokenStores.json.decodeFromString(StoredToken.serializer(), Files.readString(path))
            val source = TokenSource.entries.firstOrNull { it.name == stored.source } ?: return null
            stored.value.takeIf { it.isNotBlank() }?.let { GitHubToken(it, source) }
        } catch (e: IOException) {
            logger.warn(e) { "토큰 파일을 읽지 못했어요: $path" }
            null
        } catch (e: SerializationException) {
            logger.warn { "토큰 파일이 깨져 있어요: $path" }
            null
        } catch (e: IllegalArgumentException) {
            logger.warn { "토큰 파일이 깨져 있어요: $path" }
            null
        }
    }

    override fun save(token: GitHubToken) {
        try {
            path.parent?.let { Files.createDirectories(it) }
            if (!Files.exists(path) && supportsPosix()) {
                // 만들 때부터 권한을 주어야 umask 로 잠깐이라도 남에게 읽히지 않는다
                Files.createFile(path, PosixFilePermissions.asFileAttribute(OWNER_ONLY))
            }
            Files.writeString(path, TokenStores.json.encodeToString(StoredToken.serializer(), StoredToken(token.source.name, token.value)))
            if (supportsPosix()) Files.setPosixFilePermissions(path, OWNER_ONLY)
        } catch (e: IOException) {
            throw TokenStoreException("토큰 파일을 저장하지 못했어요: $path", e)
        }
    }

    override fun clear() {
        try {
            Files.deleteIfExists(path)
        } catch (e: IOException) {
            logger.warn(e) { "토큰 파일을 지우지 못했어요: $path" }
        }
    }

    private fun supportsPosix(): Boolean = path.fileSystem.supportedFileAttributeViews().contains("posix")

    companion object {
        private val OWNER_ONLY = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
    }
}

/** 키체인을 우선 쓰고 안 되면 파일로 넘어간다. 읽을 때는 둘 다 본다 */
private class FallbackTokenStore(private val primary: TokenStore, private val fallback: TokenStore) : TokenStore {

    override fun load(): GitHubToken? = primary.load() ?: fallback.load()

    override fun save(token: GitHubToken) {
        try {
            primary.save(token)
            fallback.clear()
        } catch (e: TokenStoreException) {
            logger.warn(e) { "키체인 저장에 실패해 파일에 저장해요" }
            fallback.save(token)
        }
    }

    override fun clear() {
        primary.clear()
        fallback.clear()
    }
}

object TokenStores {

    const val SERVICE = "io.github.rudtjr1106.switchboard"
    const val ACCOUNT = "github"
    const val FILE_NAME = "github-token.json"

    internal val json = GitHubHttp.json

    /**
     * 이 OS 에서 쓸 수 있는 저장소. 키체인이 없거나 열다가 실패하면 [fallbackDir] 아래 파일로 대신한다
     *
     * 키체인은 열렸지만 저장이 실패하는 경우(예: 잠긴 Linux 시크릿 서비스)에도 파일로 넘어간다.
     */
    fun default(fallbackDir: Path): TokenStore {
        val file = FileTokenStore(fallbackDir.resolve(FILE_NAME))
        val keyring = try {
            KeyringTokenStore.create()
        } catch (e: BackendNotSupportedException) {
            logger.warn { "이 OS 의 자격 증명 저장소를 쓸 수 없어 파일에 저장해요: ${e.message}" }
            return file
        } catch (e: LinkageError) {
            logger.warn(e) { "자격 증명 저장소 라이브러리를 불러오지 못해 파일에 저장해요" }
            return file
        } catch (e: Exception) {
            logger.warn(e) { "자격 증명 저장소를 열지 못해 파일에 저장해요" }
            return file
        }
        return FallbackTokenStore(keyring, file)
    }

    /** 키체인에 넣는 형식 `<source>:<token>`. 토큰에는 `:` 가 없어서 첫 `:` 로 자를 수 있다 */
    internal fun encode(token: GitHubToken): String = "${token.source.name}:${token.value}"

    internal fun decode(raw: String): GitHubToken? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val separator = text.indexOf(':')
        // 접두어 없이 저장된 값은 사람이 직접 넣은 토큰으로 본다
        if (separator < 0) return GitHubToken(text, TokenSource.MANUAL)
        val source = TokenSource.entries.firstOrNull { it.name == text.substring(0, separator) } ?: return null
        val value = text.substring(separator + 1)
        return value.takeIf { it.isNotEmpty() }?.let { GitHubToken(it, source) }
    }
}
