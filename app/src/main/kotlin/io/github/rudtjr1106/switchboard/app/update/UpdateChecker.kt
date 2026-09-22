package io.github.rudtjr1106.switchboard.app.update

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.app.BuildInfo
import io.github.rudtjr1106.switchboard.app.platform.AppPaths
import io.github.rudtjr1106.switchboard.app.platform.OperatingSystem
import io.github.rudtjr1106.switchboard.app.settings.SettingsStore
import io.github.rudtjr1106.switchboard.config.VersionNumber
import io.github.rudtjr1106.switchboard.github.GitHubApi
import io.github.rudtjr1106.switchboard.github.Release
import io.github.rudtjr1106.switchboard.github.RepoRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

data class AvailableUpdate(val release: Release, val version: String, val downloadUrl: String)

/**
 * GitHub 릴리즈에서 새 버전을 확인한다
 *
 * iOS 편집 앱처럼 앱 번들을 직접 바꿔 끼우지는 않는다. macOS 와 Windows 설치 방식이 달라서 설치 파일(DMG/MSI)을 받아 여는 데까지만 한다.
 */
class UpdateChecker(
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
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
                _available.value = AvailableUpdate(release, latest.toString(), asset?.downloadUrl ?: release.htmlUrl)
                _lastResult.value = null
            } catch (e: Exception) {
                logger.warn(e) { "업데이트 확인 실패" }
                _lastResult.value = if (userInitiated) "업데이트를 확인하지 못했어요: ${e.message}" else null
            }
        }
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
        OperatingSystem.WINDOWS -> ".msi"
        OperatingSystem.LINUX -> ".deb"
    }
}
