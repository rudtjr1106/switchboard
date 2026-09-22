package io.github.rudtjr1106.switchboard.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.rudtjr1106.switchboard.ai.ModelSpec
import io.github.rudtjr1106.switchboard.app.BuildInfo
import io.github.rudtjr1106.switchboard.app.ai.ModelStatus
import io.github.rudtjr1106.switchboard.app.di.AppContainer
import io.github.rudtjr1106.switchboard.app.platform.AppPaths
import io.github.rudtjr1106.switchboard.app.platform.DesktopActions
import io.github.rudtjr1106.switchboard.app.session.SessionState
import io.github.rudtjr1106.switchboard.app.ui.components.Caption
import io.github.rudtjr1106.switchboard.app.ui.components.KeyValueRow
import io.github.rudtjr1106.switchboard.app.ui.components.NoteBanner
import io.github.rudtjr1106.switchboard.app.ui.components.NoteKind
import io.github.rudtjr1106.switchboard.app.ui.components.SectionCard
import io.github.rudtjr1106.switchboard.app.ui.theme.Dimens
import io.github.rudtjr1106.switchboard.github.TokenSource

@Composable
fun SettingsDialog(container: AppContainer, onClose: () -> Unit) {
    val session by container.session.state.collectAsState()
    val settings by container.settings.settings.collectAsState()
    val ai by container.ai.state.collectAsState()
    val updateResult by container.updater.lastResult.collectAsState()
    var clientId by remember { mutableStateOf(settings.githubClientId.orEmpty()) }

    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(Dimens.radiusLarge), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column(Modifier.width(640.dp).heightIn(max = 720.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("설정", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SectionCard("계정") {
                        when (val s = session) {
                            is SessionState.SignedIn -> {
                                KeyValueRow("GitHub", "@${s.user.login}" + (s.user.name?.let { " · $it" } ?: ""))
                                KeyValueRow(
                                    "토큰",
                                    when (s.token.source) {
                                        TokenSource.DEVICE_FLOW -> "브라우저 로그인 (자격 증명 저장소에 보관)"
                                        TokenSource.GH_CLI -> "gh CLI 에서 빌려 씀"
                                        TokenSource.MANUAL -> "직접 입력한 토큰"
                                    },
                                )
                                if (s.scopes.isNotEmpty()) KeyValueRow("스코프", s.scopes.joinToString(", "))
                                Row { OutlinedButton(onClick = { container.session.signOut(); onClose() }) { Text("로그아웃") } }
                            }
                            else -> Caption("로그인하지 않았어요")
                        }
                    }

                    SectionCard("GitHub OAuth App") {
                        Caption("브라우저 로그인(Device Flow)에 쓰는 Client ID 예요. 비밀값이 아니라 앱에 넣어도 돼요. 등록: GitHub › Settings › Developer settings › OAuth Apps › New OAuth App → 'Enable Device Flow' 체크 → Client ID 복사.")
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = clientId,
                                onValueChange = { clientId = it },
                                label = { Text("Client ID") },
                                placeholder = { Text(BuildInfo.GITHUB_CLIENT_ID.ifBlank { "Ov23li…" }) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = { container.settings.update { it.copy(githubClientId = clientId.trim().ifBlank { null }) } }) { Text("저장") }
                        }
                        TextButton(onClick = { DesktopActions.openUrl("https://github.com/settings/developers") }) { Text("GitHub 에서 OAuth App 등록 페이지 열기") }
                    }

                    SectionCard("온디바이스 AI") {
                        if (!ai.supported) {
                            NoteBanner("이 기기(${ai.platform})에는 미리 빌드된 llama.cpp 가 없어 AI 기능을 쓸 수 없어요.", NoteKind.WARNING)
                        } else {
                            Caption("문구 다듬기·화면 라벨·코드 적응에 쓰는 모델이에요. 파일은 ${container.aiModelsDir()} 에 저장돼요. 켜면 메모리를 2~3GB 써요.")
                            io.github.rudtjr1106.switchboard.ai.ModelCatalog.all.forEach { spec ->
                                ModelRow(container, spec, selected = ai.selected.id == spec.id, status = ai.statuses[spec.id] ?: ModelStatus.NotInstalled)
                            }
                        }
                    }

                    SectionCard("정보") {
                        KeyValueRow("버전", "Switchboard ${BuildInfo.VERSION}")
                        updateResult?.let { Caption(it) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { (session as? SessionState.SignedIn)?.let { container.updater.check(it.api, userInitiated = true) } }, enabled = session is SessionState.SignedIn) { Text("업데이트 확인") }
                            OutlinedButton(onClick = { DesktopActions.reveal(AppPaths.settingsFile) }) { Text("데이터 폴더 열기") }
                            OutlinedButton(onClick = { DesktopActions.openUrl("https://github.com/${BuildInfo.REPOSITORY_OWNER}/${BuildInfo.REPOSITORY_NAME}") }) { Text("GitHub") }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onClose) { Text("닫기") }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(container: AppContainer, spec: ModelSpec, selected: Boolean, status: ModelStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = { container.ai.select(spec) })
            Column(Modifier.weight(1f)) {
                Text("${spec.displayName} · ${spec.sizeLabel}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Caption(spec.description)
            }
            Spacer(Modifier.width(8.dp))
            when (status) {
                ModelStatus.NotInstalled -> OutlinedButton(onClick = { container.ai.download(spec) }) { Text("내려받기") }
                is ModelStatus.Downloading -> TextButton(onClick = { container.ai.cancelDownload(spec) }) { Text("취소") }
                ModelStatus.Installed -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { container.ai.delete(spec) }) { Text("삭제") }
                    Button(onClick = { container.ai.select(spec); container.ai.load(spec) }) { Text("켜기") }
                }
                ModelStatus.Loading -> Caption("올리는 중…")
                ModelStatus.Ready -> OutlinedButton(onClick = { container.ai.unload() }) { Text("끄기") }
                is ModelStatus.Failed -> OutlinedButton(onClick = { container.ai.download(spec) }) { Text("다시 시도") }
            }
        }
        when (status) {
            is ModelStatus.Downloading -> {
                LinearProgressIndicator(progress = { status.fraction }, modifier = Modifier.fillMaxWidth().padding(start = 48.dp))
                Caption("%.0f%% · %.1f / %.1f GB".format(status.fraction * 100, status.downloadedBytes / 1e9, status.totalBytes / 1e9), modifier = Modifier.padding(start = 48.dp))
            }
            ModelStatus.Ready -> Caption("켜져 있어요. 문구 폼의 'AI 로 다듬기' 를 쓸 수 있어요.", modifier = Modifier.padding(start = 48.dp))
            is ModelStatus.Failed -> Caption(status.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 48.dp))
            else -> Spacer(Modifier.height(0.dp))
        }
    }
}
