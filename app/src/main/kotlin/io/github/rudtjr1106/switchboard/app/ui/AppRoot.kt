package io.github.rudtjr1106.switchboard.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.rudtjr1106.switchboard.app.di.AppContainer
import io.github.rudtjr1106.switchboard.app.session.SessionState
import io.github.rudtjr1106.switchboard.app.ui.components.CenteredMessage
import io.github.rudtjr1106.switchboard.app.ui.editor.EditorScreen
import io.github.rudtjr1106.switchboard.app.ui.login.LoginScreen
import io.github.rudtjr1106.switchboard.app.ui.settings.SettingsDialog
import io.github.rudtjr1106.switchboard.app.ui.setup.ProjectSetupDialog
import io.github.rudtjr1106.switchboard.app.ui.workspace.RepoPickerScreen
import io.github.rudtjr1106.switchboard.app.ui.workspace.UpdateBanner
import io.github.rudtjr1106.switchboard.app.workspace.WorkspaceState
import java.awt.Frame

@Composable
fun AppRoot(
    container: AppContainer,
    showSettings: Boolean,
    onShowSettings: (Boolean) -> Unit,
    showSetup: Boolean,
    onShowSetup: (Boolean) -> Unit,
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            update?.let { UpdateBanner(it, container.updater) }
            when (val s = session) {
                SessionState.Restoring -> CenteredMessage("불러오는 중…", showProgress = true)
                is SessionState.SignedOut, is SessionState.SigningIn -> LoginScreen(container.session, s, onOpenSettings = { onShowSettings(true) })
                is SessionState.SignedIn -> when (val w = workspace) {
                    WorkspaceState.Idle -> CenteredMessage("불러오는 중…", showProgress = true)
                    is WorkspaceState.Browsing -> RepoPickerScreen(container, s, w, onOpenSettings = { onShowSettings(true) })
                    is WorkspaceState.Opening -> CenteredMessage("${w.ref.fullName} 여는 중…", showProgress = true)
                    is WorkspaceState.Open -> EditorScreen(
                        container = container,
                        session = s,
                        editor = w.editor,
                        repo = w.repo,
                        onOpenSettings = { onShowSettings(true) },
                        onOpenSetup = { onShowSetup(true) },
                    )
                }
            }
        }
        if (showSettings) SettingsDialog(container, onClose = { onShowSettings(false) })
        val open = workspace as? WorkspaceState.Open
        val signedIn = session as? SessionState.SignedIn
        if (showSetup && open != null && signedIn != null) {
            ProjectSetupDialog(container, signedIn, open.editor, window, onClose = { onShowSetup(false) })
        }
    }
}
