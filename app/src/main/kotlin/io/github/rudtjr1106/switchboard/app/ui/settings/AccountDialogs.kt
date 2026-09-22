package io.github.rudtjr1106.switchboard.app.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import io.github.rudtjr1106.switchboard.app.di.AppContainer
import io.github.rudtjr1106.switchboard.app.platform.AppPaths
import io.github.rudtjr1106.switchboard.app.platform.DesktopActions
import io.github.rudtjr1106.switchboard.app.session.SessionState
import io.github.rudtjr1106.switchboard.app.ui.components.BulletLine
import io.github.rudtjr1106.switchboard.app.ui.components.Caption
import io.github.rudtjr1106.switchboard.app.ui.components.ConfirmDialog
import io.github.rudtjr1106.switchboard.app.ui.components.NoteBanner
import io.github.rudtjr1106.switchboard.app.ui.components.NoteKind
import io.github.rudtjr1106.switchboard.github.TokenSource

enum class AccountDialog { LOGOUT, DISCONNECT }

/** 편집 중인 저장소의 상태. 로그아웃하면 편집 중인 값이 사라지고, 적용 중이면 막아야 한다 */
private data class EditingState(val hasChanges: Boolean, val isApplying: Boolean)

@Composable
private fun editingState(container: AppContainer): EditingState {
    val editor = container.workspace.editorInUse ?: return EditingState(hasChanges = false, isApplying = false)
    val state = editor.state.value
    return EditingState(state.hasChanges, state.isApplying)
}

@Composable
fun LogoutDialog(container: AppContainer, session: SessionState.SignedIn, onDone: () -> Unit, onDismiss: () -> Unit) {
    val editing = editingState(container)
    ConfirmDialog(
        title = "로그아웃할까요?",
        confirmLabel = "로그아웃",
        confirmEnabled = !editing.isApplying,
        onConfirm = {
            container.session.signOut()
            onDone()
        },
        onDismiss = onDismiss,
    ) {
        Text("@${session.user.login} 계정에서 로그아웃해요.", style = MaterialTheme.typography.bodyMedium)
        when (session.token.source) {
            TokenSource.DEVICE_FLOW -> BulletLine("이 컴퓨터에 저장된 GitHub 로그인 정보를 지워요. 다시 쓰려면 GitHub 으로 로그인하면 돼요.")
            TokenSource.GH_CLI -> BulletLine("${AppPaths.DISPLAY_NAME}에서만 로그아웃해요. 터미널의 gh CLI 로그인은 그대로 남아요.")
            TokenSource.MANUAL -> BulletLine("저장한 토큰을 이 컴퓨터에서 지워요. 토큰 자체는 GitHub 에서 계속 유효하니, 더 쓰지 않으면 GitHub 설정에서 삭제하세요.")
        }
        BulletLine("설정 저장소와 GitHub 계정에는 아무 변화가 없어요.")
        if (editing.isApplying) {
            NoteBanner("지금 변경 사항을 적용하고 있어요. 적용이 끝난 뒤 로그아웃할 수 있어요.", NoteKind.WARNING)
        } else if (editing.hasChanges) {
            NoteBanner("편집기에 적용하지 않은 변경 사항이 있어요. 로그아웃하면 사라져요.", NoteKind.WARNING)
        }
    }
}

/**
 * 연결 해제(탈퇴) 확인 창
 *
 * 스위치보드에는 회원 가입이 따로 없어서, 탈퇴는 GitHub 연결을 끊고 이 컴퓨터의 데이터를 지우는 것으로 대신한다.
 */
@Composable
fun DisconnectDialog(container: AppContainer, session: SessionState.SignedIn, onDone: () -> Unit, onDismiss: () -> Unit) {
    val editing = editingState(container)
    val modelBytes = remember { container.ai.installedSizeBytes() }
    var deleteModels by remember { mutableStateOf(true) }
    val revokeUrl = container.session.revokeUrl(session.token.source)

    ConfirmDialog(
        title = "${AppPaths.DISPLAY_NAME} 연결을 해제할까요?",
        confirmLabel = "연결 해제",
        destructive = true,
        confirmEnabled = !editing.isApplying,
        onConfirm = {
            if (deleteModels) container.ai.deleteAll()
            container.session.disconnect()?.let(DesktopActions::openUrl)
            onDone()
        },
        onDismiss = onDismiss,
    ) {
        Text(
            "${AppPaths.DISPLAY_NAME}는 따로 회원 가입이 없어요. 탈퇴는 GitHub 연결을 끊고 이 컴퓨터의 데이터를 지우는 것으로 대신해요.",
            style = MaterialTheme.typography.bodyMedium,
        )
        BulletLine("이 컴퓨터에 저장된 로그인 정보를 지워요.")
        BulletLine("최근 연 저장소, 모델 선택 같은 ${AppPaths.DISPLAY_NAME} 설정을 지워요.")
        when {
            revokeUrl != null && session.token.source == TokenSource.DEVICE_FLOW ->
                BulletLine("GitHub 의 '승인된 OAuth 앱' 페이지를 열어 드려요. 거기서 Revoke 를 눌러야 GitHub 쪽 권한까지 없어져요.")
            revokeUrl != null ->
                BulletLine("GitHub 토큰 설정 페이지를 열어 드려요. 쓰던 토큰을 거기서 삭제하세요.")
            else ->
                BulletLine("gh CLI 로그인은 그대로 남아요. 필요하면 터미널에서 gh auth logout 을 실행하세요.")
        }
        BulletLine("설정 저장소와 그 안의 내용은 지워지지 않아요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (modelBytes > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = deleteModels, onCheckedChange = { deleteModels = it })
                Text("내려받은 AI 모델도 지우기 (%.1f GB)".format(modelBytes / 1024.0 / 1024.0 / 1024.0), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (editing.isApplying) {
            NoteBanner("지금 변경 사항을 적용하고 있어요. 적용이 끝난 뒤 연결을 해제할 수 있어요.", NoteKind.WARNING)
        } else if (editing.hasChanges) {
            NoteBanner("편집기에 적용하지 않은 변경 사항이 있어요. 연결을 해제하면 사라져요.", NoteKind.WARNING)
        }
        Caption("이 동작은 되돌릴 수 없어요.", color = MaterialTheme.colorScheme.error)
    }
}
