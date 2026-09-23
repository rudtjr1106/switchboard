package io.github.rudtjr1106.switchboard.app.ui.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rudtjr1106.switchboard.app.di.AppContainer
import io.github.rudtjr1106.switchboard.app.platform.AppPaths
import io.github.rudtjr1106.switchboard.app.platform.DesktopActions
import io.github.rudtjr1106.switchboard.app.session.SessionState
import io.github.rudtjr1106.switchboard.app.ui.components.BackButton
import io.github.rudtjr1106.switchboard.app.ui.components.Caption
import io.github.rudtjr1106.switchboard.app.ui.components.Hint
import io.github.rudtjr1106.switchboard.app.ui.components.NoteBanner
import io.github.rudtjr1106.switchboard.app.ui.components.NoteKind
import io.github.rudtjr1106.switchboard.app.ui.icons.AppIcons
import io.github.rudtjr1106.switchboard.app.ui.settings.LogoutDialog
import io.github.rudtjr1106.switchboard.app.ui.theme.Dimens
import io.github.rudtjr1106.switchboard.app.update.AvailableUpdate
import io.github.rudtjr1106.switchboard.app.update.UpdateChecker
import io.github.rudtjr1106.switchboard.app.workspace.WorkspaceState
import io.github.rudtjr1106.switchboard.github.GitHubRepo
import io.github.rudtjr1106.switchboard.github.RepoRef

@Composable
fun RepoPickerScreen(container: AppContainer, session: SessionState.SignedIn, state: WorkspaceState.Browsing, onOpenSettings: () -> Unit) {
    var manual by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val returnTo = state.returnTo

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = if (returnTo != null) 8.dp else 24.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (returnTo != null) {
                BackButton(onBack = container.workspace::back, label = "${returnTo.editor.ref.name} 로 돌아가기")
            }
            Column(Modifier.weight(1f)) {
                Text("설정 저장소 고르기", style = MaterialTheme.typography.titleLarge)
                if (returnTo != null) Caption("지금 연 저장소: ${returnTo.editor.ref.fullName}")
            }
            Text("@${session.user.login}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Hint("설정") { IconButton(onClick = onOpenSettings) { Icon(AppIcons.Settings, "설정") } }
            Hint("로그아웃") { IconButton(onClick = { confirmLogout = true }) { Icon(AppIcons.Logout, "로그아웃") } }
        }
        HorizontalDivider()
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                session.missingScopes.takeIf { it.isNotEmpty() }?.let {
                    NoteBanner("토큰에 ${it.joinToString(", ")} 스코프가 없어요. 저장소 생성이나 워크플로 파일 커밋이 막힐 수 있어요.", NoteKind.WARNING)
                }
                state.openError?.let { NoteBanner(it, NoteKind.ERROR) }
                if (returnTo != null && returnTo.editor.state.value.hasChanges) {
                    NoteBanner("${returnTo.editor.ref.name} 에 적용하지 않은 변경 사항이 있어요. 다른 저장소를 열면 사라지고, 뒤로가기로 돌아가면 그대로 남아 있어요.", NoteKind.WARNING)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("내 설정 저장소", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Caption("topic: switchboard-config")
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { container.workspace.browse(session) }) { Icon(AppIcons.Refresh, "새로고침") }
                    Button(onClick = { showCreate = true }, enabled = !state.loading) {
                        Icon(AppIcons.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("새 저장소 만들기")
                    }
                }

                when {
                    state.loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Caption("저장소를 찾는 중…")
                    }
                    state.error != null -> NoteBanner(state.error, NoteKind.ERROR)
                    state.repos.isEmpty() -> Surface(
                        shape = RoundedCornerShape(Dimens.radiusMedium),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("아직 설정 저장소가 없어요", style = MaterialTheme.typography.titleSmall)
                            Caption("새 저장소 만들기를 누르면 app-config.json · schema.json · 검사 워크플로 · GitHub Pages 까지 한 번에 준비돼요. 기존 저장소가 있으면 아래에 주소를 넣으세요.")
                        }
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.repos.forEach { repo -> RepoRow(repo) { container.workspace.opened(session, repo) } }
                    }
                }

                HorizontalDivider()
                Text("주소로 열기", style = MaterialTheme.typography.titleMedium)
                Caption("이 앱으로 만들지 않은 저장소도 app-config.json 과 schema.json 만 있으면 열 수 있어요.")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manual,
                        onValueChange = { manual = it },
                        placeholder = { Text("owner/repo 또는 https://github.com/owner/repo") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    val ref = RepoRef.parse(manual)
                    OutlinedButton(onClick = { ref?.let { container.workspace.open(session, it) } }, enabled = ref != null) { Text("열기") }
                }
            }
        }
    }

    if (showCreate) {
        CreateRepoDialog(container, session, state.owners, onClose = { showCreate = false })
    }
    if (confirmLogout) {
        LogoutDialog(container, session, onDone = { confirmLogout = false }, onDismiss = { confirmLogout = false })
    }
}

@Composable
private fun RepoRow(repo: GitHubRepo, onOpen: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(Dimens.radiusMedium),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(AppIcons.Folder, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(repo.ref.fullName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                repo.description?.takeIf { it.isNotBlank() }?.let { Caption(it) }
            }
            if (!repo.permissions.push) Caption("읽기 전용", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onOpen) { Text("열기") }
        }
    }
}

@Composable
fun UpdateBanner(update: AvailableUpdate, updater: UpdateChecker) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${AppPaths.DISPLAY_NAME} ${update.version} 버전이 나왔어요.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { updater.skip(update) }) { Text("이 버전 건너뛰기") }
            TextButton(onClick = { DesktopActions.openUrl(update.release.htmlUrl) }) { Text("릴리즈 노트") }
            Button(onClick = { DesktopActions.openUrl(update.downloadUrl); updater.dismiss() }) { Text("내려받기") }
        }
    }
}
