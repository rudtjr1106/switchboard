package io.github.rudtjr1106.switchboard.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rudtjr1106.switchboard.app.di.AppContainer
import io.github.rudtjr1106.switchboard.app.session.SessionState
import io.github.rudtjr1106.switchboard.app.ui.components.CenteredMessage
import io.github.rudtjr1106.switchboard.app.ui.editor.EditorScreen
import io.github.rudtjr1106.switchboard.app.ui.login.LoginScreen
import io.github.rudtjr1106.switchboard.app.ui.settings.SettingsScreen
import io.github.rudtjr1106.switchboard.app.ui.setup.ProjectSetupScreen
import io.github.rudtjr1106.switchboard.app.ui.workspace.RepoPickerScreen
import io.github.rudtjr1106.switchboard.app.ui.workspace.UpdateBanner
import io.github.rudtjr1106.switchboard.app.workspace.WorkspaceState
import java.awt.Frame

/** 메인 화면 위에 여는 페이지. null 이면 로그인·저장소 고르기·편집기 중 지금 상태의 화면이 보인다 */
enum class AppPage { SETTINGS, PROJECT_SETUP }

@Composable
fun AppRoot(
    container: AppContainer,
    page: AppPage?,
    onPage: (AppPage?) -> Unit,
    window: Frame,
) {
    val session by container.session.state.collectAsState()
    val workspace by container.workspace.state.collectAsState()
    val update by container.updater.available.collectAsState()

    LaunchedEffect(Unit) { container.session.restore() }
    LaunchedEffect(session) {
        when (val s = session) {
            is SessionState.SignedIn -> {
                container.workspace.start(s)
                container.updater.check(s.api)
            }
            is SessionState.SignedOut -> container.workspace.reset()
            else -> Unit
        }
    }

    val signedIn = session as? SessionState.SignedIn
    val open = workspace as? WorkspaceState.Open
    // 프로젝트 세팅은 열린 저장소가 있어야 한다. 로그아웃 등으로 사라지면 페이지를 닫는다
    LaunchedEffect(page, open, signedIn) {
        if (page == AppPage.PROJECT_SETUP && (open == null || signedIn == null)) onPage(null)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            update?.let { UpdateBanner(it, container.updater) }
            when {
                page == AppPage.SETTINGS -> SettingsScreen(container, onBack = { onPage(null) })
                page == AppPage.PROJECT_SETUP && open != null && signedIn != null ->
                    ProjectSetupScreen(container, signedIn, open.editor, window, onBack = { onPage(null) })
                else -> MainContent(container, session, workspace, onPage)
            }
        }
    }
}

@Composable
private fun MainContent(container: AppContainer, session: SessionState, workspace: WorkspaceState, onPage: (AppPage?) -> Unit) {
    when (val s = session) {
        SessionState.Restoring -> CenteredMessage("불러오는 중…", showProgress = true)
        is SessionState.SignedOut, is SessionState.SigningIn -> LoginScreen(container.session, s, onOpenSettings = { onPage(AppPage.SETTINGS) })
        is SessionState.SignedIn -> when (val w = workspace) {
            WorkspaceState.Idle -> CenteredMessage("불러오는 중…", showProgress = true)
            is WorkspaceState.Browsing -> RepoPickerScreen(container, s, w, onOpenSettings = { onPage(AppPage.SETTINGS) })
            is WorkspaceState.Opening -> OpeningScreen(w, onCancel = { container.workspace.cancelOpening(s) })
            is WorkspaceState.Open -> EditorScreen(
                container = container,
                session = s,
                editor = w.editor,
                repo = w.repo,
                onOpenSettings = { onPage(AppPage.SETTINGS) },
                onOpenSetup = { onPage(AppPage.PROJECT_SETUP) },
            )
        }
    }
}

@Composable
private fun OpeningScreen(state: WorkspaceState.Opening, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            Text("${state.ref.fullName} 여는 중…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onCancel) { Text("취소") }
        }
    }
}
