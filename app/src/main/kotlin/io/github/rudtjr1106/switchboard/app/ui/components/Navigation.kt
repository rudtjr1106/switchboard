package io.github.rudtjr1106.switchboard.app.ui.components

import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import io.github.rudtjr1106.switchboard.app.ui.theme.Dimens

/**
 * 메인 화면 위에 여는 페이지(설정, 프로젝트 세팅)의 틀. 왼쪽 위 뒤로가기와 Esc 로 닫힌다
 *
 * @param backEnabled false 면 뒤로가기를 막는다. 진행 중인 작업이 중간에 끊기면 안 될 때 쓴다
 * @param backDisabledReason 막혀 있을 때 버튼에 마우스를 올리면 보여줄 이유
 */
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backEnabled: Boolean = true,
    backDisabledReason: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val focus = remember { FocusRequester() }
    // 페이지가 열리면 포커스를 가져와야 Esc 를 받을 수 있다. 안쪽 입력 칸으로 포커스가 옮겨가도 조상인 여기서 먼저 받는다
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && backEnabled) {
                    onBack()
                    true
                } else {
                    false
                }
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BackButton(onBack = onBack, enabled = backEnabled, disabledReason = backDisabledReason)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            actions()
        }
        HorizontalDivider()
        content()
    }
}

@Composable
fun BackButton(onBack: () -> Unit, enabled: Boolean = true, disabledReason: String? = null, label: String = "뒤로 (Esc)") {
    Hint(if (enabled) label else disabledReason ?: label) {
        IconButton(onClick = onBack, enabled = enabled) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
        }
    }
}

/** 마우스를 올리면 뜨는 짧은 설명 */
@Composable
fun Hint(text: String, content: @Composable () -> Unit) {
    TooltipArea(tooltip = {
        Surface(shape = RoundedCornerShape(Dimens.radiusSmall), color = MaterialTheme.colorScheme.inverseSurface, tonalElevation = 4.dp) {
            Text(text, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelSmall)
        }
    }) { content() }
}

/** 다이얼로그 맨 위 줄. 제목과 오른쪽 닫기(X). [closeEnabled] 가 false 면 X 를 숨긴다 */
@Composable
fun DialogHeader(title: String, onClose: () -> Unit, closeEnabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (closeEnabled) {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "닫기", modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * 되돌리기 어려운 동작 전에 띄우는 확인 창
 *
 * @param destructive true 면 확인 버튼을 빨간색으로 그린다
 * @param confirmEnabled false 면 확인 버튼을 끈다. 그 이유는 [body] 에 적는다
 */
@Composable
fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    body: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = body) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = if (destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 확인 창 본문의 한 줄 항목 */
@Composable
fun BulletLine(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium, color = color)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
fun BoxedPageContent(maxWidth: androidx.compose.ui.unit.Dp = Dimens.contentMaxWidth, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
