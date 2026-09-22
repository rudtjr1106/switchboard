package io.github.rudtjr1106.switchboard.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.rudtjr1106.switchboard.ai.NoticeDraft
import io.github.rudtjr1106.switchboard.app.di.AppContainer
import io.github.rudtjr1106.switchboard.app.ui.components.DialogHeader
import io.github.rudtjr1106.switchboard.app.ui.components.Caption
import io.github.rudtjr1106.switchboard.app.ui.components.NoteBanner
import io.github.rudtjr1106.switchboard.app.ui.components.NoteKind
import io.github.rudtjr1106.switchboard.app.ui.components.SectionCard
import io.github.rudtjr1106.switchboard.app.ui.theme.Dimens
import io.github.rudtjr1106.switchboard.config.Notice
import io.github.rudtjr1106.switchboard.config.NoticeTemplate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class AiCopyMode { POLISH, DRAFT }

/** 온디바이스 AI 로 문구를 다듬거나 초안을 만든다. 결과는 사용자가 확인한 뒤에만 폼에 들어간다 */
@Composable
fun AiCopyDialog(
    container: AppContainer,
    mode: AiCopyMode,
    notice: Notice,
    titleLimit: Int,
    bodyLimit: Int,
    onApply: (NoticeDraft) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var situation by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<NoticeDraft?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val template = NoticeTemplate.fromId(notice.template) ?: NoticeTemplate.INFO

    fun run() {
        job?.cancel()
        running = true
        error = null
        result = null
        job = scope.launch {
            try {
                result = when (mode) {
                    AiCopyMode.POLISH -> container.ai.copywriter.polish(notice.title, notice.body, template, titleLimit, bodyLimit)
                    AiCopyMode.DRAFT -> container.ai.copywriter.draft(situation, template, titleLimit, bodyLimit)
                }
            } catch (e: Exception) {
                error = e.message ?: "문구를 만들지 못했어요"
            } finally {
                running = false
            }
        }
    }

    LaunchedEffect(mode) { if (mode == AiCopyMode.POLISH) run() }

    Dialog(onDismissRequest = { job?.cancel(); onClose() }) {
        Surface(shape = RoundedCornerShape(Dimens.radiusLarge), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column(Modifier.width(520.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DialogHeader(if (mode == AiCopyMode.POLISH) "AI 로 다듬기" else "상황으로 초안 만들기", onClose = { job?.cancel(); onClose() })
                Caption("모델은 이 컴퓨터에서만 돌아가요. 문구가 밖으로 나가지 않아요.")
                if (mode == AiCopyMode.DRAFT) {
                    OutlinedTextField(
                        value = situation,
                        onValueChange = { situation = it },
                        label = { Text("무슨 상황인가요?") },
                        placeholder = { Text("예: 9월 20일 새벽 2시부터 4시까지 서버 점검") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (running) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Caption("문구를 만드는 중… 몇 초에서 수십 초 걸릴 수 있어요")
                    }
                }
                error?.let { NoteBanner(it, NoteKind.ERROR) }
                result?.let { draft ->
                    SectionCard("제안") {
                        Text(draft.title, style = MaterialTheme.typography.titleMedium)
                        Text(draft.body, style = MaterialTheme.typography.bodyMedium)
                        Caption("제목 ${draft.title.codePointCount(0, draft.title.length)}/$titleLimit · 본문 ${draft.body.codePointCount(0, draft.body.length)}/$bodyLimit")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { job?.cancel(); onClose() }) { Text("닫기") }
                    Spacer(Modifier.width(8.dp))
                    if (mode == AiCopyMode.DRAFT || result != null || error != null) {
                        TextButton(onClick = ::run, enabled = !running && (mode == AiCopyMode.POLISH || situation.isNotBlank())) {
                            Text(if (result == null && error == null) "만들기" else "다시 만들기")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(onClick = { result?.let(onApply) }, enabled = result != null && !running) { Text("폼에 넣기") }
                }
            }
        }
    }
}
