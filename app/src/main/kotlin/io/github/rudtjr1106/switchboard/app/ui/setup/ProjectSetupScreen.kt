package io.github.rudtjr1106.switchboard.app.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.rudtjr1106.switchboard.app.di.AppContainer
import io.github.rudtjr1106.switchboard.app.editor.EditorModel
import io.github.rudtjr1106.switchboard.app.platform.DesktopActions
import io.github.rudtjr1106.switchboard.app.platform.DirectoryPicker
import io.github.rudtjr1106.switchboard.app.session.SessionState
import io.github.rudtjr1106.switchboard.app.setup.PlanningProgress
import io.github.rudtjr1106.switchboard.app.setup.ProjectSetupModel
import io.github.rudtjr1106.switchboard.app.setup.SetupStep
import io.github.rudtjr1106.switchboard.app.ui.components.Caption
import io.github.rudtjr1106.switchboard.app.ui.components.CodeBlock
import io.github.rudtjr1106.switchboard.app.ui.components.ConfirmDialog
import io.github.rudtjr1106.switchboard.app.ui.components.KeyValueRow
import io.github.rudtjr1106.switchboard.app.ui.components.NoteBanner
import io.github.rudtjr1106.switchboard.app.ui.components.NoteKind
import io.github.rudtjr1106.switchboard.app.ui.components.PageScaffold
import io.github.rudtjr1106.switchboard.app.ui.components.SectionCard
import io.github.rudtjr1106.switchboard.app.ui.components.StepRow
import io.github.rudtjr1106.switchboard.app.ui.icons.AppIcons
import io.github.rudtjr1106.switchboard.app.ui.theme.Dimens
import io.github.rudtjr1106.switchboard.app.ui.theme.status
import io.github.rudtjr1106.switchboard.config.ScreenCatalog
import io.github.rudtjr1106.switchboard.github.ApplyStep
import io.github.rudtjr1106.switchboard.scanner.FileAction
import java.awt.Frame

@Composable
fun ProjectSetupScreen(
    container: AppContainer,
    session: SessionState.SignedIn,
    editor: EditorModel,
    // 테스트에서 헤드리스로 그릴 때는 null (폴더 선택 창을 띄우지 않는다)
    window: Frame?,
    onBack: () -> Unit,
    // 화면 스크린샷 테스트가 단계별 상태를 만들어 넣을 수 있게 밖에서도 받는다
    model: ProjectSetupModel = remember(editor) {
        ProjectSetupModel(session, editor, container.scanner, container.generator, container.ai, container.settings, container.scope)
    },
) {
    val step by model.state.collectAsState()
    val busy = step is SetupStep.Scanning || step is SetupStep.Running || (step as? SetupStep.Labeling)?.planning != null
    // 스캔 결과나 라벨을 고친 뒤 나가면 처음부터 다시 해야 하므로 한 번 묻는다
    val hasProgress = step is SetupStep.Review || step is SetupStep.Labeling || step is SetupStep.Plan
    var confirmLeave by remember { mutableStateOf(false) }
    val leave = { if (hasProgress) confirmLeave = true else onBack() }

    PageScaffold(
        title = "Android 프로젝트 세팅",
        subtitle = stepLabel(step),
        onBack = leave,
        backEnabled = !busy,
        backDisabledReason = "진행 중이에요. 끝날 때까지 기다려 주세요",
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
            when (val s = step) {
                is SetupStep.PickFolder -> PickFolder(model, s, window)
                is SetupStep.Scanning -> Centered("${s.path.fileName} 을 읽는 중…")
                is SetupStep.Review -> Review(model, s)
                is SetupStep.Labeling -> Labeling(container, model, s)
                is SetupStep.Plan -> Plan(model, s)
                is SetupStep.Running -> Running(s)
                is SetupStep.Done -> Done(model, s, onBack)
            }
        }
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "세팅을 그만둘까요?",
            confirmLabel = "그만두기",
            onConfirm = { confirmLeave = false; onBack() },
            onDismiss = { confirmLeave = false },
        ) {
            Text("스캔 결과와 고친 화면 이름이 사라져요. 프로젝트와 저장소에는 아직 아무것도 쓰지 않았어요.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun stepLabel(step: SetupStep): String = when (step) {
    is SetupStep.PickFolder, is SetupStep.Scanning -> "1/4 프로젝트 고르기"
    is SetupStep.Review -> "2/4 화면 고르기"
    is SetupStep.Labeling -> "3/4 화면 이름 붙이기"
    is SetupStep.Plan -> "4/4 생성 계획 확인"
    is SetupStep.Running -> "실행 중"
    is SetupStep.Done -> "완료"
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun PickFolder(model: ProjectSetupModel, step: SetupStep.PickFolder, window: Frame?) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Android 프로젝트 폴더(settings.gradle.kts 가 있는 곳)를 고르면 내비게이션 목적지·DI·HTTP 라이브러리를 읽어 연동 코드를 만들어 드려요.", style = MaterialTheme.typography.bodyLarge)
        SectionCard("만들어지는 것") {
            KeyValueRow("프로젝트", "RemoteConfigApi · Repository · UseCase · Hilt/Koin 모듈 · RemoteNoticeHost(Compose) 파일. 기존 파일은 건드리지 않아요")
            KeyValueRow("설정 저장소", "schema.json 의 screen 목록을 스캔한 화면으로 갱신하는 PR (선택)")
        }
        step.error?.let { NoteBanner(it, NoteKind.ERROR) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                DirectoryPicker.pick(window, "Android 프로젝트 폴더 선택", model.lastProjectPath)?.let(model::pick)
            }) {
                Icon(AppIcons.FolderOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("폴더 고르기")
            }
            model.lastProjectPath?.let { last ->
                OutlinedButton(onClick = { model.pick(last) }) { Text("최근: ${last.fileName}") }
            }
        }
    }
}

@Composable
private fun Review(model: ProjectSetupModel, step: SetupStep.Review) {
    val project = step.project
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard("스캔 결과") {
                KeyValueRow("프로젝트", project.name)
                KeyValueRow("모듈", project.modules.size.toString() + "개" + (project.appModule?.let { " · 앱 ${it.path}" } ?: ""))
                KeyValueRow("DI", project.di.name)
                KeyValueRow("HTTP", project.http.name)
                KeyValueRow("내비게이션", project.navigation.name)
                if (project.hasRemoteConfigIntegration) Caption("이미 원격 설정 연동 코드가 있어요. 생성 파일이 기존 파일을 덮어쓰지 않도록 계획 단계에서 확인하세요.", color = MaterialTheme.status.expired)
            }
            if (project.notes.isNotEmpty()) SectionCard("메모") { project.notes.forEach { Caption("• $it") } }
        }
        Column(Modifier.weight(1f)) {
            // 스키마에 이미 있던 화면도 selected 에 들어 있다. 숫자는 이번에 스캔한 것만 센다
            val scanned = project.destinations.map { it.name }.toSet()
            val kept = step.selected.size - step.selected.count { it in scanned }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "schema.json 에 넣을 화면 (${step.selected.count { it in scanned }}/${project.destinations.size})",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { model.selectAll(true) }) { Text("모두") }
                TextButton(onClick = { model.selectAll(false) }) { Text("없음") }
            }
            if (kept > 0) Caption("여기 없는 기존 화면 ${kept}개는 그대로 둬요. 체크는 이번에 찾은 화면에만 해당해요.")
            if (project.destinations.isEmpty()) {
                NoteBanner("내비게이션 목적지를 찾지 못했어요. 라벨 단계에서 화면 이름을 직접 추가할 수는 없으니 스키마의 기존 화면만 유지돼요.", NoteKind.WARNING)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(project.destinations, key = { it.name }) { destination ->
                    Row(Modifier.fillMaxWidth().clickable { model.toggle(destination.name) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = destination.name in step.selected, onCheckedChange = { model.toggle(destination.name) })
                        Column {
                            Text(destination.name + if (destination.hasArguments) "  (인자 있음)" else "", style = MaterialTheme.typography.bodyMedium)
                            destination.comment?.let { Caption(it) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = model::restart) { Text("다른 폴더") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = model::toLabeling) { Text("다음") }
            }
        }
    }
}

@Composable
private fun Labeling(container: AppContainer, model: ProjectSetupModel, step: SetupStep.Labeling) {
    val aiState by container.ai.state.collectAsState()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("편집기 사이드바와 README 에 보일 이름과 구분이에요. 소스 주석·구역 제목으로 초안을 채워 뒀어요. AI 로 다듬거나 직접 고치세요.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            val reason = container.ai.unavailableReason()
            OutlinedButton(onClick = model::runAiLabels, enabled = reason == null && !step.aiRunning && !aiState.isBusy) {
                Icon(AppIcons.AutoAwesome, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (step.aiRunning) "다듬는 중…" else "AI 로 다듬기")
            }
        }
        container.ai.unavailableReason()?.let { Caption(it) }
        step.aiError?.let { NoteBanner(it, NoteKind.ERROR) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text("경로 이름", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(240.dp))
            Text("이름", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("구분", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(180.dp))
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(step.screens, key = { it.id }) { screen ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(screen.id, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.width(232.dp))
                    OutlinedTextField(
                        value = if (screen.label == ScreenCatalog.defaultLabel(screen.id) && screen.id != ScreenCatalog.ALL) "" else screen.label,
                        onValueChange = { model.setScreen(screen.id, it, screen.group) },
                        placeholder = { Text(screen.id) },
                        singleLine = true,
                        enabled = screen.id != ScreenCatalog.ALL,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = screen.group.orEmpty(),
                        onValueChange = { model.setScreen(screen.id, screen.label, it) },
                        placeholder = { Text("예: 홈") },
                        singleLine = true,
                        modifier = Modifier.width(180.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = model::backToReview) { Text("이전") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = model::buildPlan, enabled = !step.aiRunning && step.planning == null) { Text("계획 만들기") }
        }
    }
    step.planning?.let { PlanningDialog(it, onCancel = model::cancelPlanning) }
}

/** 계획을 만드는 동안 띄우는 로딩 창. AI 가 파일을 고칠 때는 몇 번째 파일인지 보여준다 */
@Composable
private fun PlanningDialog(progress: PlanningProgress, onCancel: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false)) {
        Surface(shape = RoundedCornerShape(Dimens.radiusLarge), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column(Modifier.width(420.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("계획 만드는 중", style = MaterialTheme.typography.titleLarge)
                Text(progress.message, style = MaterialTheme.typography.bodyMedium)
                if (progress.total > 0) {
                    LinearProgressIndicator(progress = { progress.done.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth())
                    Caption("${progress.done}/${progress.total} · 파일 하나에 30초쯤 걸려요. 모델은 이 컴퓨터에서만 돌아요")
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("취소") }
                }
            }
        }
    }
}

@Composable
private fun Plan(model: ProjectSetupModel, step: SetupStep.Plan) {
    val plan = step.plan
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(360.dp).fillMaxHeight()) {
                Text("생성 파일 ${plan.files.size}개", style = MaterialTheme.typography.titleSmall)
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(plan.files) { index, file ->
                        val selected = index == step.selectedFile
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { model.updatePlan { it.copy(selectedFile = index) } }
                                .padding(vertical = 6.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (file.adaptedByAi) {
                                Text("AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                            }
                            Text(
                                if (file.action == FileAction.MODIFY) "수정" else "새로",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (file.action == FileAction.MODIFY) MaterialTheme.status.expired else MaterialTheme.status.live,
                                modifier = Modifier.width(32.dp),
                            )
                            Text(file.path.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                if (plan.notes.isNotEmpty()) {
                    HorizontalDivider()
                    Column(Modifier.verticalScroll(rememberScrollState()).height(120.dp).padding(top = 6.dp)) { plan.notes.forEach { Caption("• $it") } }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                val file = plan.files.getOrNull(step.selectedFile)
                if (file != null) {
                    Text(file.path.fileName.toString(), style = MaterialTheme.typography.titleSmall)
                    Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) { CodeBlock(file.content) }
                } else {
                    Caption("생성할 파일이 없어요")
                }
            }
        }
        step.repoBlockedReason?.let { NoteBanner(it, NoteKind.WARNING) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = step.writeFiles, onCheckedChange = { v -> model.updatePlan { it.copy(writeFiles = v) } })
            Text("프로젝트에 파일 쓰기", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(16.dp))
            Checkbox(checked = step.updateRepo, onCheckedChange = { v -> model.updatePlan { it.copy(updateRepo = v) } }, enabled = step.repoBlockedReason == null)
            Text("저장소 schema.json 화면 목록 갱신 (PR → 머지)", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(16.dp))
            Checkbox(checked = step.updateReadme, onCheckedChange = { v -> model.updatePlan { it.copy(updateReadme = v) } }, enabled = step.updateRepo && step.repoBlockedReason == null)
            Text("README 도 다시 쓰기", style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = model::backToLabeling) { Text("이전") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = model::run, enabled = step.writeFiles || (step.updateRepo && step.repoBlockedReason == null)) { Text("실행") }
        }
    }
}

@Composable
private fun Running(step: SetupStep.Running) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Centered(step.phase)
        step.progress?.let { progress ->
            Column(Modifier.width(420.dp).align(Alignment.CenterHorizontally)) {
                ApplyStep.entries.forEachIndexed { index, s -> StepRow(s.title, progress.state(s), isLast = index == ApplyStep.entries.lastIndex) }
            }
        }
    }
}

@Composable
private fun Done(model: ProjectSetupModel, step: SetupStep.Done, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(AppIcons.CheckCircle, null, Modifier.size(28.dp), tint = MaterialTheme.status.live)
            Text("세팅이 끝났어요", style = MaterialTheme.typography.titleLarge)
        }
        step.note?.let { NoteBanner(it, if (it.contains("실패")) NoteKind.ERROR else NoteKind.INFO) }
        if (step.written.isNotEmpty()) SectionCard("프로젝트에 쓴 파일") { step.written.forEach { Caption(it.toString()) } }
        step.pullRequestUrl?.let { url ->
            SectionCard("저장소") {
                Caption("화면 목록 PR 을 머지했어요. 편집기가 새 스키마를 다시 불러와요.")
                TextButton(onClick = { DesktopActions.openUrl(url) }) { Text("PR 보기") }
            }
        }
        if (step.manualSteps.isNotEmpty()) {
            SectionCard("직접 해야 하는 일") {
                step.manualSteps.forEachIndexed { index, s -> Text("${index + 1}. $s", style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = model::restart) { Text("다른 프로젝트") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onClose) { Text("닫기") }
        }
    }
}
