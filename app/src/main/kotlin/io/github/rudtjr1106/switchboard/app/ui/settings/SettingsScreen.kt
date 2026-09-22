package io.github.rudtjr1106.switchboard.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import io.github.rudtjr1106.switchboard.ai.ModelCatalog
import io.github.rudtjr1106.switchboard.ai.ModelSpec
import io.github.rudtjr1106.switchboard.app.BuildInfo
import io.github.rudtjr1106.switchboard.app.ai.ModelStatus
import io.github.rudtjr1106.switchboard.app.di.AppContainer
import io.github.rudtjr1106.switchboard.app.platform.AppPaths
import io.github.rudtjr1106.switchboard.app.platform.DesktopActions
import io.github.rudtjr1106.switchboard.app.session.SessionState
import io.github.rudtjr1106.switchboard.app.ui.components.BoxedPageContent
import io.github.rudtjr1106.switchboard.app.ui.components.Caption
import io.github.rudtjr1106.switchboard.app.ui.components.KeyValueRow
import io.github.rudtjr1106.switchboard.app.ui.components.NoteBanner
import io.github.rudtjr1106.switchboard.app.ui.components.NoteKind
import io.github.rudtjr1106.switchboard.app.ui.components.PageScaffold
import io.github.rudtjr1106.switchboard.app.ui.components.SectionCard
import io.github.rudtjr1106.switchboard.github.TokenSource

/**
 * 설정 페이지. 왼쪽 위 뒤로가기나 Esc 로 원래 화면으로 돌아간다
 *
 * @param initialDialog 스크린샷 테스트에서 확인 창을 연 상태로 그릴 때 쓴다
 */
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, initialDialog: AccountDialog? = null) {
    val session by container.session.state.collectAsState()
    val ai by container.ai.state.collectAsState()
    val updateResult by container.updater.lastResult.collectAsState()
    var dialog by remember { mutableStateOf(initialDialog) }
    val signedIn = session as? SessionState.SignedIn

    PageScaffold(title = "설정", onBack = onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            BoxedPageContent {
                SectionCard("계정") {
                    if (signedIn != null) {
                        KeyValueRow("GitHub", "@${signedIn.user.login}" + (signedIn.user.name?.let { " · $it" } ?: ""))
                        KeyValueRow(
                            "로그인 방식",
                            when (signedIn.token.source) {
                                TokenSource.DEVICE_FLOW -> "브라우저 로그인 (자격 증명 저장소에 보관)"
                                TokenSource.GH_CLI -> "gh CLI 에서 빌려 씀"
                                TokenSource.MANUAL -> "직접 입력한 토큰"
                            },
                        )
                        if (signedIn.scopes.isNotEmpty()) KeyValueRow("권한", signedIn.scopes.joinToString(", "))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { dialog = AccountDialog.LOGOUT }) { Text("로그아웃") }
                            TextButton(onClick = { dialog = AccountDialog.DISCONNECT }) {
                                Text("연결 해제", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Caption("연결 해제는 탈퇴에 해당해요. 로그인 정보와 이 컴퓨터의 ${AppPaths.DISPLAY_NAME} 데이터를 지워요.")
                    } else {
                        Caption("로그인하지 않았어요")
                    }
                }

                SectionCard("온디바이스 AI") {
                    if (!ai.supported) {
                        NoteBanner("이 기기(${ai.platform})에는 미리 빌드된 llama.cpp 가 없어 AI 기능을 쓸 수 없어요.", NoteKind.WARNING)
                    } else {
                        Caption("문구 다듬기·화면 라벨·코드 적응에 쓰는 모델이에요. 파일은 ${container.aiModelsDir()} 에 저장돼요. 켜면 메모리를 2~3GB 써요.")
                        ModelCatalog.all.forEach { spec ->
                            ModelRow(container, spec, selected = ai.selected.id == spec.id, status = ai.statuses[spec.id] ?: ModelStatus.NotInstalled)
                        }
                    }
                }

                SectionCard("정보") {
                    KeyValueRow("버전", "${AppPaths.DISPLAY_NAME} ${BuildInfo.VERSION}")
                    updateResult?.let { Caption(it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { signedIn?.let { container.updater.check(it.api, userInitiated = true) } }, enabled = signedIn != null) { Text("업데이트 확인") }
                        OutlinedButton(onClick = { DesktopActions.reveal(AppPaths.settingsFile) }) { Text("데이터 폴더 열기") }
                        OutlinedButton(onClick = { DesktopActions.openUrl("https://github.com/${BuildInfo.REPOSITORY_OWNER}/${BuildInfo.REPOSITORY_NAME}") }) { Text("GitHub") }
                    }
                }
            }
        }
    }

    if (signedIn != null) {
        when (dialog) {
            AccountDialog.LOGOUT -> LogoutDialog(container, signedIn, onDone = { dialog = null; onBack() }, onDismiss = { dialog = null })
            AccountDialog.DISCONNECT -> DisconnectDialog(container, signedIn, onDone = { dialog = null; onBack() }, onDismiss = { dialog = null })
            null -> Unit
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
