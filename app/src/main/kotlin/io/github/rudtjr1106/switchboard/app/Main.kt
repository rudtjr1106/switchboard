package io.github.rudtjr1106.switchboard.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.rudtjr1106.switchboard.app.di.AppContainer
import io.github.rudtjr1106.switchboard.app.di.createAppContainer
import io.github.rudtjr1106.switchboard.app.platform.AppPaths
import io.github.rudtjr1106.switchboard.app.platform.DesktopActions
import io.github.rudtjr1106.switchboard.app.platform.OperatingSystem
import io.github.rudtjr1106.switchboard.app.session.SessionState
import io.github.rudtjr1106.switchboard.app.ui.AppRoot
import io.github.rudtjr1106.switchboard.app.ui.theme.SwitchboardTheme
import io.github.rudtjr1106.switchboard.app.workspace.WorkspaceState
import java.awt.Dimension
import java.awt.Toolkit

fun main() {
    AppPaths.ensure()
    // macOS 메뉴 막대·Dock 이름. Info.plist 가 있는 배포본에서는 무시된다
    System.setProperty("apple.awt.application.name", AppPaths.APP_NAME)
    val container = createAppContainer()

    application {
        val windowState = rememberWindowState(size = DpSize(1240.dp, 800.dp), position = WindowPosition(Alignment.Center))
        var showSettings by remember { mutableStateOf(false) }
        var showSetup by remember { mutableStateOf(false) }
        val workspace by container.workspace.state.collectAsState()
        val editor = (workspace as? WorkspaceState.Open)?.editor
        val editorState = editor?.state?.collectAsState()?.value

        Window(
            onCloseRequest = {
                // 적용 도중 끝나면 브랜치·PR·머지 사이에서 멈춰 저장소에 반쯤 된 상태가 남는다
                if (editorState?.isApplying == true) Toolkit.getDefaultToolkit().beep() else exitApplication()
            },
            state = windowState,
            title = AppPaths.APP_NAME,
            icon = painterResource("icon.png"),
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(980, 660) }

            val meta = AppPaths.os == OperatingSystem.MAC
            MenuBar {
                Menu(AppPaths.APP_NAME, mnemonic = 'S') {
                    Item("설정…", shortcut = KeyShortcut(Key.Comma, meta = meta, ctrl = !meta)) { showSettings = true }
                    Item("업데이트 확인…") {
                        container.session.signedIn?.let { container.updater.check(it.api, userInitiated = true) }
                    }
                    Separator()
                    Item("종료", shortcut = KeyShortcut(Key.Q, meta = meta, ctrl = !meta)) {
                        if (editorState?.isApplying != true) exitApplication()
                    }
                }
                Menu("저장소", mnemonic = 'R') {
                    Item("새로고침", shortcut = KeyShortcut(Key.R, meta = meta, ctrl = !meta), enabled = editor != null) { editor?.load() }
                    Item("적용…", shortcut = KeyShortcut(Key.S, meta = meta, ctrl = !meta), enabled = editorState?.applyBlockedReason == null) { editor?.beginApply() }
                    Item("되돌리기", enabled = editorState?.hasChanges == true) { editor?.revert() }
                    Separator()
                    Item("GitHub 에서 열기", enabled = editor != null) { editor?.let { DesktopActions.openUrl(it.ref.htmlUrl) } }
                    Item("다른 저장소 열기…", enabled = editor != null) {
                        container.session.signedIn?.let { container.workspace.close(it) }
                    }
                }
                Menu("도구", mnemonic = 'T') {
                    Item("Android 프로젝트 세팅…", enabled = editor != null) { showSetup = true }
                }
            }

            SwitchboardTheme {
                AppRoot(
                    container = container,
                    showSettings = showSettings,
                    onShowSettings = { showSettings = it },
                    showSetup = showSetup,
                    onShowSetup = { showSetup = it },
                    window = window,
                )
            }
        }
    }
}
