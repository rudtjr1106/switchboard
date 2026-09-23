package io.github.rudtjr1106.switchboard.app.ui.editor

import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rudtjr1106.switchboard.app.di.AppContainer
import io.github.rudtjr1106.switchboard.app.editor.EditorModel
import io.github.rudtjr1106.switchboard.app.editor.LoadState
import io.github.rudtjr1106.switchboard.app.editor.Selection
import io.github.rudtjr1106.switchboard.app.platform.DesktopActions
import io.github.rudtjr1106.switchboard.app.session.SessionState
import io.github.rudtjr1106.switchboard.app.ui.components.CenteredMessage
import io.github.rudtjr1106.switchboard.app.ui.components.NoteBanner
import io.github.rudtjr1106.switchboard.app.ui.components.NoteKind
import io.github.rudtjr1106.switchboard.app.ui.icons.AppIcons
import io.github.rudtjr1106.switchboard.app.ui.theme.Dimens
import io.github.rudtjr1106.switchboard.github.GitHubRepo
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

@Composable
fun EditorScreen(
    container: AppContainer,
    session: SessionState.SignedIn,
    editor: EditorModel,
    repo: GitHubRepo,
    onOpenSettings: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val state by editor.state.collectAsState()
    var confirmReload by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Toolbar(
            editorState = state,
            repo = repo,
            onReload = { if (state.hasChanges) confirmReload = true else editor.load() },
            onRevert = editor::revert,
            onApply = editor::beginApply,
            onOpenGitHub = { DesktopActions.openUrl(repo.htmlUrl) },
            onOpenSettings = onOpenSettings,
            onOpenSetup = onOpenSetup,
            onSwitchRepo = { container.workspace.switchRepository(session) },
        )
        HorizontalDivider()
        when (val load = state.loadState) {
            LoadState.Loading -> CenteredMessage("불러오는 중…", showProgress = true)
            is LoadState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.width(480.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    NoteBanner(load.message, NoteKind.ERROR)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { container.workspace.switchRepository(session) }) { Text("다른 저장소 열기") }
                        Button(onClick = editor::load) { Text("다시 시도") }
                    }
                }
            }
            LoadState.Loaded -> Row(Modifier.fillMaxSize()) {
                SidebarPane(editor, state, Modifier.width(Dimens.sidebarWidth).fillMaxHeight())
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (val selection = state.selection) {
                        Selection.MinimumVersion -> MinimumVersionForm(editor, state)
                        is Selection.NoticeItem -> state.draft.notice(selection.id)?.let { notice ->
                            NoticeForm(container, editor, state, notice)
                        } ?: CenteredMessage("왼쪽에서 항목을 고르세요")
                        null -> CenteredMessage(if (state.draft.notices.isEmpty()) "안내가 없어요. 사이드바의 + 로 추가하세요." else "왼쪽에서 항목을 고르세요")
                    }
                }
                val selected = state.selectedNotice
                if (selected != null) {
                    VerticalDivider()
                    AndroidPreview(selected, Modifier.width(Dimens.previewWidth).fillMaxHeight())
                }
            }
        }
    }

    if (state.apply != null) ApplyDialog(editor, state)

    if (confirmReload) {
        AlertDialog(
            onDismissRequest = { confirmReload = false },
            title = { Text("변경 사항을 버릴까요?") },
            text = { Text("아직 적용하지 않은 변경 사항이 사라져요.") },
            confirmButton = { TextButton(onClick = { confirmReload = false; editor.load() }) { Text("버리고 새로고침", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmReload = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun Toolbar(
    editorState: io.github.rudtjr1106.switchboard.app.editor.EditorState,
    repo: GitHubRepo,
    onReload: () -> Unit,
    onRevert: () -> Unit,
    onApply: () -> Unit,
    onOpenGitHub: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSetup: () -> Unit,
    onSwitchRepo: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(repo.ref.fullName, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onSwitchRepo, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) { Text("바꾸기", style = MaterialTheme.typography.labelMedium) }
            }
            Text(subtitle(editorState), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ToolbarIcon(AppIcons.Refresh, "새로고침", onReload)
        ToolbarIcon(AppIcons.OpenInNew, "GitHub 에서 열기", onOpenGitHub)
        ToolbarIcon(AppIcons.Build, "Android 프로젝트 세팅", onOpenSetup)
        ToolbarIcon(AppIcons.Settings, "설정", onOpenSettings)
        if (editorState.hasChanges) {
            OutlinedButton(onClick = onRevert) {
                Icon(AppIcons.Undo, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("되돌리기")
            }
        }
        val blocked = editorState.applyBlockedReason
        TooltipArea(tooltip = {
            if (blocked != null) {
                Surface(shape = RoundedCornerShape(Dimens.radiusSmall), color = MaterialTheme.colorScheme.inverseSurface, tonalElevation = 4.dp) {
                    Text(blocked, Modifier.padding(8.dp), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodySmall)
                }
            }
        }) {
            Button(onClick = onApply, enabled = blocked == null) {
                Icon(AppIcons.CloudUpload, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("적용")
            }
        }
    }
}

@Composable
private fun ToolbarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    TooltipArea(tooltip = {
        Surface(shape = RoundedCornerShape(Dimens.radiusSmall), color = MaterialTheme.colorScheme.inverseSurface, tonalElevation = 4.dp) {
            Text(description, Modifier.padding(6.dp), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelSmall)
        }
    }) {
        IconButton(onClick = onClick) { Icon(icon, description) }
    }
}

@Composable
private fun subtitle(state: io.github.rudtjr1106.switchboard.app.editor.EditorState): String {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Instant.now()
        }
    }
    val loadedAt = state.loadedAt ?: return "${state.branch} · 불러오는 중"
    val minutes = Duration.between(loadedAt, now).toMinutes()
    val ago = when {
        minutes < 1 -> "방금 불러옴"
        minutes < 60 -> "${minutes}분 전 불러옴"
        else -> "${minutes / 60}시간 전 불러옴"
    }
    return "${state.branch} · $ago"
}
