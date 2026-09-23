package io.github.rudtjr1106.switchboard.app.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.rudtjr1106.switchboard.app.BuildInfo
import io.github.rudtjr1106.switchboard.app.di.AppContainer
import io.github.rudtjr1106.switchboard.app.platform.DesktopActions
import io.github.rudtjr1106.switchboard.app.session.SessionState
import io.github.rudtjr1106.switchboard.app.ui.components.Caption
import io.github.rudtjr1106.switchboard.app.ui.components.DialogHeader
import io.github.rudtjr1106.switchboard.app.ui.components.NoteBanner
import io.github.rudtjr1106.switchboard.app.ui.components.NoteKind
import io.github.rudtjr1106.switchboard.app.ui.components.StepRow
import io.github.rudtjr1106.switchboard.app.ui.theme.Dimens
import io.github.rudtjr1106.switchboard.app.workspace.CreateRepoFlow
import io.github.rudtjr1106.switchboard.app.workspace.CreateRepoState
import io.github.rudtjr1106.switchboard.github.BootstrapStep
import io.github.rudtjr1106.switchboard.github.GitHubOwner
import io.github.rudtjr1106.switchboard.github.OwnerType

@Composable
fun CreateRepoDialog(container: AppContainer, session: SessionState.SignedIn, owners: List<GitHubOwner>, onClose: () -> Unit) {
    val flow = remember { CreateRepoFlow(session, container.clientFactory, owners.ifEmpty { listOf(GitHubOwner(session.user.login, OwnerType.USER)) }, container.scope) }
    val state by flow.state.collectAsState()
    val running = state is CreateRepoState.Running

    Dialog(onDismissRequest = { if (!running) onClose() }, properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = !running)) {
        Surface(shape = RoundedCornerShape(Dimens.radiusLarge), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column(Modifier.width(560.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (val s = state) {
                    is CreateRepoState.Form -> FormContent(flow, s, owners, onClose)
                    is CreateRepoState.Running -> ProgressContent("저장소를 만드는 중…", s.progress.steps.let { steps -> { step: BootstrapStep -> s.progress.state(step) } }, null, null, onClose)
                    is CreateRepoState.Done -> ProgressContent("준비됐어요", { s.progress.state(it) }, "이제 ${s.repo.ref.fullName} 을 열어 안내를 편집할 수 있어요. 앱은 ${s.repo.ref.pagesUrl("app-config.json")} 을 읽어요.", null, onClose) {
                        Button(onClick = { onClose(); container.workspace.opened(session, s.repo) }) { Text("열기") }
                    }
                    is CreateRepoState.Failed -> ProgressContent("만들지 못했어요", { s.progress.state(it) }, null, s.message, onClose) {
                        TextButton(onClick = flow::backToForm) { Text("다시 입력") }
                    }
                }
            }
        }
    }
}

/** 이 OAuth 앱이 어떤 조직에 접근할 수 있는지 사용자가 직접 켜는 GitHub 설정 */
private val APP_ACCESS_URL = "https://github.com/settings/connections/applications/${BuildInfo.GITHUB_CLIENT_ID}"

@Composable
private fun FormContent(flow: CreateRepoFlow, form: CreateRepoState.Form, owners: List<GitHubOwner>, onClose: () -> Unit) {
    var ownerMenu by remember { mutableStateOf(false) }
    DialogHeader("새 설정 저장소", onClose = onClose)
    Caption("공개 저장소로 만들어요. GitHub Pages 가 공개 저장소에서만 무료라서 그래요. 비밀값은 절대 넣지 마세요.")
    form.error?.let { NoteBanner(it, NoteKind.ERROR) }

    ExposedDropdownMenuBox(expanded = ownerMenu, onExpandedChange = { ownerMenu = it }) {
        OutlinedTextField(
            value = form.owner.login + if (form.owner.type == OwnerType.ORGANIZATION) " (조직)" else " (내 계정)",
            onValueChange = {},
            readOnly = true,
            label = { Text("소유자") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ownerMenu) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(expanded = ownerMenu, onDismissRequest = { ownerMenu = false }) {
            owners.forEach { owner ->
                DropdownMenuItem(
                    text = { Text(owner.login + if (owner.type == OwnerType.ORGANIZATION) "  (조직)" else "") },
                    onClick = { flow.update { it.copy(owner = owner) }; ownerMenu = false },
                )
            }
        }
    }
    // 조직에 만들려면 그 조직이 이 앱의 접근을 승인해야 목록에 뜬다. 안 보이는 이유를 여기서 바로 알려 준다
    Row(verticalAlignment = Alignment.CenterVertically) {
        Caption("조직이 안 보이나요? 조직이 이 앱의 접근을 승인해야 보여요.")
        TextButton(onClick = { DesktopActions.openUrl(APP_ACCESS_URL) }) { Text("승인 설정 열기") }
    }
    OutlinedTextField(
        value = form.name,
        onValueChange = { name -> flow.update { it.copy(name = name) } },
        label = { Text("저장소 이름") },
        placeholder = { Text("my-app-android-config") },
        singleLine = true,
        isError = form.nameError != null,
        supportingText = { Text(form.nameError ?: "앱이 읽는 주소: https://${form.owner.login.lowercase()}.github.io/${form.name.ifBlank { "<이름>" }}/app-config.json") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.description,
        onValueChange = { d -> flow.update { it.copy(description = d) } },
        label = { Text("설명") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = form.includeMinimumVersion, onCheckedChange = { v -> flow.update { it.copy(includeMinimumVersion = v) } })
        Column {
            Text("강제 업데이트(minimumVersion) 필드 포함", style = MaterialTheme.typography.bodyMedium)
            Caption("특정 버전보다 낮은 앱을 업데이트 화면으로 막는 값이에요. 나중에 스키마를 고쳐서 넣을 수도 있어요.")
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClose) { Text("취소") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = flow::submit, enabled = form.canSubmit) { Text("만들기") }
    }
}

@Composable
private fun ProgressContent(
    title: String,
    stateOf: (BootstrapStep) -> io.github.rudtjr1106.switchboard.github.StepState,
    successMessage: String?,
    errorMessage: String?,
    onClose: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    DialogHeader(title, onClose = onClose, closeEnabled = successMessage != null || errorMessage != null)
    Column {
        BootstrapStep.entries.forEachIndexed { index, step ->
            StepRow(step.title, stateOf(step), isLast = index == BootstrapStep.entries.lastIndex)
        }
    }
    successMessage?.let { NoteBanner(it, NoteKind.SUCCESS) }
    errorMessage?.let { NoteBanner(it, NoteKind.ERROR) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        if (successMessage != null || errorMessage != null) {
            TextButton(onClick = onClose) { Text("닫기") }
            Spacer(Modifier.width(8.dp))
        }
        actions()
    }
}
