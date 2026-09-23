package io.github.rudtjr1106.switchboard.app.update

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.app.BuildInfo
import io.github.rudtjr1106.switchboard.app.platform.AppPaths
import io.github.rudtjr1106.switchboard.app.platform.OperatingSystem
import io.github.rudtjr1106.switchboard.app.settings.SettingsStore
import io.github.rudtjr1106.switchboard.config.VersionNumber
import io.github.rudtjr1106.switchboard.github.GitHubApi
import io.github.rudtjr1106.switchboard.github.Release
import io.github.rudtjr1106.switchboard.github.ReleaseAsset
import io.github.rudtjr1106.switchboard.github.RepoRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * @property asset 이 OS 에 맞는 설치 파일. 없으면 릴리즈 페이지만 열어 준다
 * @property checksums Windows zip 의 무결성을 확인할 `SHA256SUMS.txt`
 */
data class AvailableUpdate(
    val release: Release,
    val version: String,
    val downloadUrl: String,
    val asset: ReleaseAsset? = null,
    val checksums: ReleaseAsset? = null,
)

/** 업데이트 설치 상태. 화면은 이것만 보고 진행을 그린다 */
sealed interface UpdateInstallState {
    data object Idle : UpdateInstallState
    data class Working(val step: InstallStep) : UpdateInstallState
    data class Failed(val message: String) : UpdateInstallState
}

/**
 * GitHub 릴리즈에서 새 버전을 확인한다
 *
 * iOS 편집 앱처럼 앱 번들을 직접 바꿔 끼우지는 않는다. macOS 와 Windows 설치 방식이 달라서 설치 파일(DMG/MSI)을 받아 여는 데까지만 한다.
 */
class UpdateChecker(
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val installer: UpdateInstaller? = null,
) {

    /** 앱 안에서 바로 설치할 수 있는지 (개발 중 실행이면 false) */
    val canInstall: Boolean get() = installer?.supported == true

    private val _install = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    val install: StateFlow<UpdateInstallState> = _install.asStateFlow()
    private val _available = MutableStateFlow<AvailableUpdate?>(null)
    val available: StateFlow<AvailableUpdate?> = _available.asStateFlow()

    private val _lastResult = MutableStateFlow<String?>(null)
    val lastResult: StateFlow<String?> = _lastResult.asStateFlow()

    fun check(api: GitHubApi, userInitiated: Boolean = false) {
        scope.launch {
            try {
                val release = api.latestRelease(RepoRef(BuildInfo.REPOSITORY_OWNER, BuildInfo.REPOSITORY_NAME))
                val latest = release?.let { VersionNumber.parse(it.tagName) }
                val current = VersionNumber.parse(BuildInfo.VERSION)
                if (release == null || latest == null || current == null || latest <= current) {
                    _lastResult.value = "최신 버전을 쓰고 있어요 (${AppPaths.DISPLAY_NAME} ${BuildInfo.VERSION})"
                    return@launch
                }
                if (!userInitiated && settings.current.skippedUpdateTag == release.tagName) return@launch
                val asset = release.assets.firstOrNull { it.name.endsWith(preferredExtension()) }
                _available.value = AvailableUpdate(
                    release = release,
                    version = latest.toString(),
                    downloadUrl = asset?.downloadUrl ?: release.htmlUrl,
                    asset = asset,
                    checksums = release.assets.firstOrNull { it.name == CHECKSUMS },
                )
                _lastResult.value = null
            } catch (e: Exception) {
                logger.warn(e) { "업데이트 확인 실패" }
                _lastResult.value = if (userInitiated) "업데이트를 확인하지 못했어요: ${e.message}" else null
            }
        }
    }

    /** 새 버전을 받아 제자리에서 바꿔 끼운다. 성공하면 앱이 새로 뜨면서 이 프로세스는 끝난다 */
    fun install(update: AvailableUpdate) {
        val installer = installer ?: return
        if (_install.value is UpdateInstallState.Working) return
        scope.launch {
            _install.value = UpdateInstallState.Working(InstallStep.Downloading(0, update.asset?.sizeBytes ?: -1))
            try {
                installer.install(update) { step -> _install.value = UpdateInstallState.Working(step) }
            } catch (e: Exception) {
                logger.warn(e) { "업데이트 설치 실패" }
                _install.value = UpdateInstallState.Failed(e.message ?: "업데이트를 설치하지 못했어요")
            }
        }
    }

    fun clearInstallResult() {
        _install.value = UpdateInstallState.Idle
    }

    fun skip(update: AvailableUpdate) {
        settings.update { it.copy(skippedUpdateTag = update.release.tagName) }
        _available.value = null
    }

    fun dismiss() {
        _available.value = null
    }

    fun clearResult() {
        _lastResult.value = null
    }

    private fun preferredExtension(): String = when (AppPaths.os) {
        OperatingSystem.MAC -> ".dmg"
        // Windows 는 설치 없이 풀어서 쓰는 zip 으로 배포한다 (MSI 는 설치가 느리고 제자리 업데이트도 번거롭다)
        OperatingSystem.WINDOWS -> ".zip"
        OperatingSystem.LINUX -> ".deb"
    }

    private companion object {
        const val CHECKSUMS = "SHA256SUMS.txt"
    }
}
