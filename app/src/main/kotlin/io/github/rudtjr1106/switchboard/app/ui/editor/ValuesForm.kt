package io.github.rudtjr1106.switchboard.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.rudtjr1106.switchboard.app.editor.EditorModel
import io.github.rudtjr1106.switchboard.app.editor.EditorState
import io.github.rudtjr1106.switchboard.app.ui.components.Caption
import io.github.rudtjr1106.switchboard.app.ui.components.ConfirmDialog
import io.github.rudtjr1106.switchboard.app.ui.components.SectionCard
import io.github.rudtjr1106.switchboard.app.ui.icons.AppIcons
import io.github.rudtjr1106.switchboard.app.ui.theme.Dimens
import io.github.rudtjr1106.switchboard.config.ValueSpec
import io.github.rudtjr1106.switchboard.config.ValueType
import kotlinx.serialization.json.JsonPrimitive

/**
 * 앱이 읽어가는 자유 값 편집
 *
 * 값 자체는 app-config.json 에, 키·타입·기본값 같은 정의는 schema.json 에 들어간다. 정의를 고치면 적용할 때
 * 두 파일이 한 PR 로 같이 올라간다.
 */
@Composable
fun ValuesForm(editor: EditorModel, state: EditorState) {
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<ValueSpec?>(null) }

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.widthIn(max = Dimens.contentMaxWidth).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("값", style = MaterialTheme.typography.headlineSmall)
                    Caption("앱이 읽어가는 key-value 예요. 앱을 새로 배포하지 않고 바꿀 수 있어요.")
                }
                OutlinedButton(onClick = { adding = true }) {
                    Icon(AppIcons.Add, null, Modifier.width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("값 추가")
                }
            }

            if (state.valueSpecs.isEmpty()) {
                SectionCard("아직 값이 없어요") {
                    Caption("값 추가를 눌러 키를 만들면 여기에서 고칠 수 있어요. 안드로이드 쪽에서는 RemoteValues 클래스로 읽어요.")
                }
            }

            for (spec in state.valueSpecs) {
                val current = state.draft.value(spec)
                val error = state.issues.firstOrNull { it.field == "values.${spec.key}" }?.message
                SectionCard(spec.displayLabel) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(spec.key, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            spec.description?.takeIf { it.isNotBlank() }?.let { Caption(it) }
                        }
                        IconButton(onClick = { removing = spec }) {
                            Icon(AppIcons.Delete, "값 삭제", Modifier.width(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    ValueField(spec, current, error) { editor.setValue(spec, it) }
                }
            }
        }
    }

    if (adding) {
        AddValueDialog(onAdd = editor::addValueSpec, onClose = { adding = false })
    }
    removing?.let { spec ->
        ConfirmDialog(
            title = "'${spec.displayLabel}' 값을 지울까요?",
            confirmLabel = "지우기",
            onConfirm = { editor.removeValueSpec(spec.key); removing = null },
            onDismiss = { removing = null },
        ) {
            Text(
                "schema.json 의 정의와 설정 파일의 값이 함께 빠져요. 앱이 이 값을 읽고 있으면 코드를 먼저 고쳐 주세요.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** 타입에 맞는 입력 칸 */
@Composable
private fun ValueField(spec: ValueSpec, current: JsonPrimitive, error: String?, onChange: (JsonPrimitive) -> Unit) {
    when {
        spec.type == ValueType.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = current.content.toBooleanStrictOrNull() == true, onCheckedChange = { onChange(JsonPrimitive(it)) })
            Spacer(Modifier.width(10.dp))
            Text(if (current.content.toBooleanStrictOrNull() == true) "켜짐" else "꺼짐", style = MaterialTheme.typography.bodyMedium)
        }

        spec.options.isNotEmpty() -> {
            var open by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { open = true }) { Text(current.content.ifBlank { "고르기" }) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    spec.options.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { onChange(JsonPrimitive(option)); open = false })
                    }
                }
            }
            error?.let { Caption(it, color = MaterialTheme.colorScheme.error) }
        }

        else -> OutlinedTextField(
            value = current.content,
            onValueChange = { onChange(parseInput(spec, it)) },
            singleLine = true,
            isError = error != null,
            supportingText = { Text(error ?: hint(spec)) },
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        )
    }
}

/** 입력한 글을 타입에 맞는 JSON 값으로. 숫자 칸에 글자를 넣으면 그대로 두고 검사가 잡는다 */
private fun parseInput(spec: ValueSpec, text: String): JsonPrimitive = when (spec.type) {
    ValueType.INTEGER -> text.toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(text)
    ValueType.NUMBER -> text.toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(text)
    else -> JsonPrimitive(text)
}

private fun hint(spec: ValueSpec): String = buildList {
    add(spec.type.label)
    spec.minimum?.let { add("${format(it, spec)} 이상") }
    spec.maximum?.let { add("${format(it, spec)} 이하") }
    spec.maxLength?.let { add("${it}자까지") }
    add("기본값 ${spec.default.content.ifBlank { "빈 값" }}")
}.joinToString(" · ")

private fun format(value: Double, spec: ValueSpec): String =
    if (spec.type == ValueType.INTEGER) value.toLong().toString() else value.toString()

@Composable
private fun AddValueDialog(onAdd: (ValueSpec) -> String?, onClose: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ValueType.BOOLEAN) }
    var description by remember { mutableStateOf("") }
    var options by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var typeMenu by remember { mutableStateOf(false) }

    ConfirmDialog(
        title = "값 추가",
        confirmLabel = "추가",
        confirmEnabled = key.isNotBlank(),
        onConfirm = {
            val list = options.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val spec = ValueSpec(
                key = key.trim(),
                type = type,
                label = label.trim().ifBlank { key.trim() },
                description = description.trim().takeIf { it.isNotBlank() },
                default = if (type == ValueType.STRING && list.isNotEmpty()) JsonPrimitive(list.first()) else ValueSpec.defaultFor(type),
                options = if (type == ValueType.STRING) list else emptyList(),
            )
            error = onAdd(spec)
            if (error == null) onClose()
        },
        onDismiss = onClose,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it; error = null },
                label = { Text("키") },
                placeholder = { Text("showEventBanner") },
                singleLine = true,
                isError = error != null,
                supportingText = { Text(error ?: "안드로이드 코드의 프로퍼티 이름이 돼요. 영문 소문자로 시작해요") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("타입", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { typeMenu = true }) { Text(type.label) }
                DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                    ValueType.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = { type = option; typeMenu = false })
                    }
                }
            }
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("이름 (선택)") },
                placeholder = { Text("이벤트 배너") },
                singleLine = true,
                supportingText = { Text("편집기 목록에 보일 이름이에요. 비우면 키를 그대로 써요") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (type == ValueType.STRING) {
                OutlinedTextField(
                    value = options,
                    onValueChange = { options = it },
                    label = { Text("고를 수 있는 값 (선택)") },
                    placeholder = { Text("recent, popular") },
                    singleLine = true,
                    supportingText = { Text("쉼표로 나눠 적으면 드롭다운이 돼요. 비우면 자유 입력이에요") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("설명 (선택)") },
                singleLine = true,
                supportingText = { Text("생성되는 안드로이드 코드의 주석으로도 들어가요") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
