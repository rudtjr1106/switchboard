package io.github.rudtjr1106.switchboard.app.di

import io.github.rudtjr1106.switchboard.app.ai.AiManager
import io.github.rudtjr1106.switchboard.app.session.SessionManager
import io.github.rudtjr1106.switchboard.app.settings.SettingsStore
import io.github.rudtjr1106.switchboard.app.update.UpdateChecker
import io.github.rudtjr1106.switchboard.app.workspace.WorkspaceManager
import io.github.rudtjr1106.switchboard.github.GitHubClientFactory
import io.github.rudtjr1106.switchboard.scanner.AndroidProjectScanner
import io.github.rudtjr1106.switchboard.scanner.IntegrationGenerator
import kotlinx.coroutines.CoroutineScope

/** 화면이 쓰는 앱 전역 객체 묶음. Koin 모듈(AppModule)에서 조립한다 */
class AppContainer(
    val scope: CoroutineScope,
    val settings: SettingsStore,
    val session: SessionManager,
    val workspace: WorkspaceManager,
    val clientFactory: GitHubClientFactory,
    val ai: AiManager,
    val scanner: AndroidProjectScanner,
    val generator: IntegrationGenerator,
    val updater: UpdateChecker,
) {
    fun aiModelsDir(): java.nio.file.Path = io.github.rudtjr1106.switchboard.app.platform.AppPaths.modelsDir
}
