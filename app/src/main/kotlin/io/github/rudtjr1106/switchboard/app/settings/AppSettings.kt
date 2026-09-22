package io.github.rudtjr1106.switchboard.app.settings

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.github.RepoRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

@Serializable
data class AppSettings(
    val lastRepository: RepoRef? = null,
    val modelId: String? = null,
    val aiEnabled: Boolean = true,
    val lastProjectPath: String? = null,
    val skippedUpdateTag: String? = null,
)

/** settings.json 하나에 다 넣는다. 값이 작아서 바뀔 때마다 통째로 다시 쓴다 */
class SettingsStore(private val file: Path) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        save(_settings.value)
    }

    /** 연결 해제할 때 설정 파일까지 지운다 */
    fun reset() {
        _settings.value = AppSettings()
        runCatching { Files.deleteIfExists(file) }.onFailure { logger.warn(it) { "설정 파일을 지우지 못했어요" } }
    }

    private fun load(): AppSettings = runCatching {
        if (Files.exists(file)) json.decodeFromString<AppSettings>(Files.readString(file)) else AppSettings()
    }.getOrElse {
        logger.warn(it) { "설정 파일을 읽지 못해 기본값으로 시작해요" }
        AppSettings()
    }

    private fun save(settings: AppSettings) {
        runCatching {
            Files.createDirectories(file.parent)
            Files.writeString(file, json.encodeToString(AppSettings.serializer(), settings))
        }.onFailure { logger.warn(it) { "설정 파일을 쓰지 못했어요" } }
    }
}
