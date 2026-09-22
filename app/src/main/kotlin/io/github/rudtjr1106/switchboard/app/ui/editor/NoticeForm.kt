package io.github.rudtjr1106.switchboard.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import io.github.rudtjr1106.switchboard.app.editor.EditorState
import io.github.rudtjr1106.switchboard.app.ui.components.Caption
import io.github.rudtjr1106.switchboard.app.ui.components.CharacterCounter
import io.github.rudtjr1106.switchboard.app.ui.components.SectionCard
import io.github.rudtjr1106.switchboard.app.ui.theme.Dimens
import io.github.rudtjr1106.switchboard.app.ui.theme.status
import io.github.rudtjr1106.switchboard.config.ConfigDates
import io.github.rudtjr1106.switchboard.config.Notice
import io.github.rudtjr1106.switchboard.config.NoticeStatus
import io.github.rudtjr1106.switchboard.config.NoticeTemplate
import io.github.rudtjr1106.switchboard.config.ScreenCatalog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun NoticeForm(container: AppContainer, editor: EditorModel, state: EditorState, notice: Notice) {
    val schema = state.schema ?: return
    val issues = state.issuesFor(notice.id).associate { it.field to it.message }
    val today = LocalDate.now()
    val aiState by container.ai.state.collectAsState()
    var aiMode by remember { mutableStateOf<AiCopyMode?>(null) }
    fun update(transform: (Notice) -> Notice) = editor.updateNotice(notice.id, transform)

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.widthIn(max = Dimens.contentMaxWidth).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard("노출") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("켜기", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = notice.enabled, onCheckedChange = { v -> update { it.copy(enabled = v) } })
                }
                if (notice.status(today) == NoticeStatus.EXPIRED) Caption("종료일이 지나 앱에 뜨지 않아요", color = MaterialTheme.status.expired)
            }

            SectionCard("대상") {
                ScreenPicker(catalog = schema.catalog, selected = notice.screen, error = issues["screen"]) { id -> update { it.copy(screen = id) } }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("모양", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        schema.templateIds.forEachIndexed { index, id ->
                            SegmentedButton(
                                selected = notice.template == id,
                                onClick = { update { it.copy(template = id) } },
                                shape = SegmentedButtonDefaults.itemShape(index, schema.templateIds.size),
                            ) { Text(NoticeTemplate.fromId(id)?.label ?: id) }
                        }
                    }
                    NoticeTemplate.fromId(notice.template)?.let { Caption(it.summary) }
                    if (notice.isBlocking) Caption("닫을 수 없는 화면이에요. 끝나면 꼭 꺼 주세요", color = MaterialTheme.colorScheme.error)
                    issues["template"]?.let { Caption(it, color = MaterialTheme.colorScheme.error) }
                }
            }

            SectionCard(
                "문구",
                trailing = {
                    val reason = container.ai.unavailableReason()
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { aiMode = AiCopyMode.DRAFT }, enabled = reason == null && !aiState.isBusy) { Text("상황으로 초안 만들기") }
                        OutlinedButton(onClick = { aiMode = AiCopyMode.POLISH }, enabled = reason == null && !aiState.isBusy && (notice.title.isNotBlank() || notice.body.isNotBlank())) {
                            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("AI 로 다듬기")
                        }
                    }
                },
            ) {
                container.ai.unavailableReason()?.let { Caption(it) }
                OutlinedTextField(
                    value = notice.title,
                    onValueChange = { v -> update { it.copy(title = v) } },
                    label = { Text("제목") },
                    placeholder = { Text("서비스 점검 중이에요") },
                    singleLine = true,
                    isError = issues["title"] != null,
                    supportingText = { issues["title"]?.let { Text(it) } },
                    trailingIcon = { Box(Modifier.padding(end = 12.dp)) { CharacterCounter(notice.title, schema.titleLimit) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notice.body,
                    onValueChange = { v -> update { it.copy(body = v) } },
                    label = { Text("본문") },
                    placeholder = { Text("더 나은 서비스를 위해 점검하고 있어요. 잠시 후 다시 이용해주세요.") },
                    minLines = 4,
                    isError = issues["body"] != null,
                    supportingText = {
                        Row(Modifier.fillMaxWidth()) {
                            Text(issues["body"] ?: "", Modifier.weight(1f))
                            CharacterCounter(notice.body, schema.bodyLimit)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
            }

            SectionCard("기간") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("종료일", style = MaterialTheme.typography.bodyLarge)
                        Caption(if (notice.until != null) "이 날짜 당일까지 떠요" else "기한 없이 떠요")
                    }
                    Switch(checked = notice.until != null, onCheckedChange = { on -> update { it.copy(until = if (on) ConfigDates.format(today) else null) } })
                }
                val until = notice.until
                if (until != null) {
                    UntilPicker(until, error = issues["until"]) { value -> update { it.copy(until = value) } }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { editor.deleteNotice(notice.id) }) {
                    Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text("안내 삭제", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    aiMode?.let { mode ->
        AiCopyDialog(
            container = container,
            mode = mode,
            notice = notice,
            titleLimit = schema.titleLimit,
            bodyLimit = schema.bodyLimit,
            onApply = { draft -> update { it.copy(title = draft.title, body = draft.body) }; aiMode = null },
            onClose = { aiMode = null },
        )
    }
}

@Composable
private fun ScreenPicker(catalog: ScreenCatalog, selected: String, error: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = catalog.label(selected) + if (selected !in catalog) "  (스키마에 없음)" else "",
            onValueChange = {},
            readOnly = true,
            label = { Text("화면") },
            isError = error != null,
            supportingText = { Text(error ?: if (selected == ScreenCatalog.ALL) "앱을 켜는 순간부터 모든 화면에 적용돼요" else "경로 이름: $selected") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 420.dp)) {
            catalog.groups.forEach { group ->
                group.name?.let { name ->
                    Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                group.screens.forEach { screen ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(screen.label)
                                if (screen.label != screen.id) Caption(screen.id)
                            }
                        },
                        onClick = { onSelect(screen.id); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun UntilPicker(value: String, error: String?, onChange: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text("날짜 (yyyy-MM-dd)") },
            singleLine = true,
            isError = error != null,
            supportingText = { error?.let { Text(it) } },
            modifier = Modifier.width(220.dp),
        )
        OutlinedButton(onClick = { showPicker = true }) { Text("달력") }
    }
    if (showPicker) {
        val initial = ConfigDates.parse(value) ?: LocalDate.now()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onChange(ConfigDates.format(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()))
                    }
                    showPicker = false
                }) { Text("선택") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("취소") } },
        ) { DatePicker(state = pickerState) }
    }
}
