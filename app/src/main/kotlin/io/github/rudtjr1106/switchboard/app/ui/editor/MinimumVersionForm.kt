package io.github.rudtjr1106.switchboard.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rudtjr1106.switchboard.app.editor.EditorModel
import io.github.rudtjr1106.switchboard.app.editor.EditorState
import io.github.rudtjr1106.switchboard.app.ui.components.Caption
import io.github.rudtjr1106.switchboard.app.ui.components.KeyValueRow
import io.github.rudtjr1106.switchboard.app.ui.components.SectionCard
import io.github.rudtjr1106.switchboard.app.ui.theme.Dimens

@Composable
fun MinimumVersionForm(editor: EditorModel, state: EditorState) {
    val value = state.draft.minimumVersion.orEmpty()
    val error = state.issues.firstOrNull { it.field == "minimumVersion" }?.message
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.widthIn(max = Dimens.contentMaxWidth).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("강제 업데이트", style = MaterialTheme.typography.headlineSmall)
            SectionCard("최소 버전") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = editor::setMinimumVersion,
                        placeholder = { Text("2.3.0") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = { Text(error ?: "이 버전보다 낮은 앱은 업데이트 화면으로 막혀요") },
                        modifier = Modifier.width(240.dp),
                    )
                    OutlinedButton(onClick = { editor.setMinimumVersion("") }, enabled = value.isNotEmpty()) { Text("지우기") }
                }
            }
            SectionCard("값에 따른 동작") {
                KeyValueRow("빈 값", "강제 업데이트를 하지 않아요")
                KeyValueRow("2.3 또는 2.3.0", "이 버전보다 낮은 앱을 막아요. 패치 자리까지 비교해요")
                Caption("v2.3.0 처럼 숫자와 점 말고 다른 글자가 섞이면 검사에서 막혀요.")
            }
        }
    }
}
