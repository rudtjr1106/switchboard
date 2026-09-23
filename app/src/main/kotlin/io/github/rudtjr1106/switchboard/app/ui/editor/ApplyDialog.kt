package io.github.rudtjr1106.switchboard.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.rudtjr1106.switchboard.app.editor.ApplyState
import io.github.rudtjr1106.switchboard.app.editor.EditorModel
import io.github.rudtjr1106.switchboard.app.editor.EditorState
import io.github.rudtjr1106.switchboard.app.platform.DesktopActions
import io.github.rudtjr1106.switchboard.app.ui.components.Caption
import io.github.rudtjr1106.switchboard.app.ui.components.DialogHeader
import io.github.rudtjr1106.switchboard.app.ui.components.NoteBanner
import io.github.rudtjr1106.switchboard.app.ui.components.NoteKind
import io.github.rudtjr1106.switchboard.app.ui.components.StepRow
import io.github.rudtjr1106.switchboard.app.ui.icons.AppIcons
import io.github.rudtjr1106.switchboard.app.ui.theme.Dimens
import io.github.rudtjr1106.switchboard.app.ui.theme.status
import io.github.rudtjr1106.switchboard.config.Change
import io.github.rudtjr1106.switchboard.config.ChangeKind
import io.github.rudtjr1106.switchboard.github.ApplyProgress
import io.github.rudtjr1106.switchboard.github.ApplyStep

@Composable
fun ApplyDialog(editor: EditorModel, state: EditorState) {
    val apply = state.apply ?: return
    val running = apply is ApplyState.Running
    Dialog(onDismissRequest = { if (!running) editor.closeApply() }, properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = !running)) {
        Surface(shape = RoundedCornerShape(Dimens.radiusLarge), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column(Modifier.width(580.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (apply) {
                    is ApplyState.Confirming -> Confirming(editor, apply)
                    is ApplyState.Running -> Progress("적용하는 중…", apply.progress, error = null, editor = editor)
                    is ApplyState.Failed -> Progress("적용하지 못했어요", apply.progress, error = apply.message, editor = editor)
                    is ApplyState.Finished -> Finished(editor, apply)
                }
            }
        }
    }
}

@Composable
private fun Confirming(editor: EditorModel, apply: ApplyState.Confirming) {
    DialogHeader("변경 사항 적용", onClose = editor::closeApply)
    if (apply.isDangerous) {
        NoteBanner("모든 화면을 막는 차단 안내가 켜져요. 적용 후 최대 10분 안에 모든 사용자가 앱을 쓸 수 없게 돼요.", NoteKind.ERROR)
    }
    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        apply.changes.forEach { ChangeRow(it) }
    }
    OutlinedTextField(
        value = apply.commitMessage,
        onValueChange = editor::setCommitMessage,
        label = { Text("커밋 메시지 (PR 제목)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = apply.memo,
        onValueChange = editor::setMemo,
        label = { Text("메모 (선택, PR 본문에 들어가요)") },
        placeholder = { Text("예: 9/20 새벽 점검 안내. 점검 끝나면 끄기") },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    apply.blockedReason?.let { NoteBanner(it, NoteKind.ERROR) }
    Caption("브랜치를 만들고 PR 을 열어 validate 검사를 통과하면 스쿼시 머지한 뒤 GitHub Pages 배포까지 기다려요.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = editor::closeApply) { Text("취소") }
        Spacer(Modifier.width(8.dp))
        val enabled = apply.blockedReason == null && apply.commitMessage.isNotBlank()
        if (apply.isDangerous) {
            Button(onClick = editor::confirmApply, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("앱 막기 적용") }
        } else {
            Button(onClick = editor::confirmApply, enabled = enabled) { Text("적용") }
        }
    }
}

@Composable
private fun ChangeRow(change: Change) {
    val status = MaterialTheme.status
    val (icon, tint) = when (change.kind) {
        ChangeKind.ADDED -> AppIcons.AddCircleOutline to status.live
        ChangeKind.REMOVED -> AppIcons.RemoveCircleOutline to MaterialTheme.colorScheme.error
        ChangeKind.ENABLED -> AppIcons.ToggleOn to status.live
        ChangeKind.DISABLED -> AppIcons.ToggleOff to status.off
        ChangeKind.MODIFIED -> AppIcons.Edit to MaterialTheme.colorScheme.primary
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
        Text(change.text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Progress(title: String, progress: ApplyProgress, error: String?, editor: EditorModel) {
    DialogHeader(title, onClose = editor::closeApply, closeEnabled = error != null)
    Column {
        ApplyStep.entries.forEachIndexed { index, step ->
            StepRow(step.title, progress.state(step), isLast = index == ApplyStep.entries.lastIndex)
        }
    }
    if (error != null) {
        NoteBanner(error, NoteKind.ERROR)
        Caption("머지 전에 실패했으면 앱이 연 PR 을 닫고 브랜치를 지웠어요. 고치던 값은 그대로 남아 있어요.")
    } else {
        Caption("진행되는 동안에는 창을 닫거나 앱을 종료할 수 없어요. 중간에 끊기면 브랜치나 PR 만 남기 때문이에요.")
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        progress.pullRequestUrl?.let { url ->
            OutlinedButton(onClick = { DesktopActions.openUrl(url) }) { Text("PR 보기") }
            Spacer(Modifier.width(8.dp))
        }
        if (error != null) TextButton(onClick = editor::closeApply) { Text("닫기") }
    }
}

@Composable
private fun Finished(editor: EditorModel, apply: ApplyState.Finished) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(AppIcons.CheckCircle, null, Modifier.size(56.dp), tint = MaterialTheme.status.live)
        Text(if (apply.result.deployed) "배포됐어요" else "머지됐어요", style = MaterialTheme.typography.titleLarge)
        Caption(
            if (apply.result.deployed) "앱에는 최대 10분 뒤에 반영돼요. 캐시가 10분이라 그래요."
            else "GitHub Pages 배포는 확인하지 못했어요. 저장소의 Pages 상태를 확인하세요.",
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        OutlinedButton(onClick = { DesktopActions.openUrl(apply.result.pullRequestUrl) }) { Text("PR 보기") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = editor::closeApply) { Text("닫기") }
    }
}
