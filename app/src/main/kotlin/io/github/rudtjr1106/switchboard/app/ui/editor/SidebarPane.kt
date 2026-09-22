package io.github.rudtjr1106.switchboard.app.ui.editor

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rudtjr1106.switchboard.app.editor.EditorModel
import io.github.rudtjr1106.switchboard.app.editor.EditorState
import io.github.rudtjr1106.switchboard.app.editor.Selection
import io.github.rudtjr1106.switchboard.app.ui.components.StatusDot
import io.github.rudtjr1106.switchboard.app.ui.theme.Dimens
import io.github.rudtjr1106.switchboard.app.ui.theme.status
import io.github.rudtjr1106.switchboard.config.Notice
import io.github.rudtjr1106.switchboard.config.NoticeStatus
import io.github.rudtjr1106.switchboard.config.NoticeTemplate
import java.time.LocalDate

@Composable
fun SidebarPane(editor: EditorModel, state: EditorState, modifier: Modifier = Modifier) {
    val schema = state.schema ?: return
    val today = LocalDate.now()
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
            if (schema.supportsMinimumVersion) {
                item { SectionHeader("앱") }
                item {
                    val selected = state.selection == Selection.MinimumVersion
                    SidebarRow(selected = selected, onClick = { editor.select(Selection.MinimumVersion) }) {
                        Icon(Icons.Outlined.SystemUpdate, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("강제 업데이트", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                state.draft.minimumVersion?.takeIf { it.isNotBlank() }?.let { "최소 버전 $it" } ?: "꺼짐",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("화면 안내", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = editor::addNotice, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Add, "안내 추가", Modifier.size(18.dp)) }
                }
            }
            if (state.draft.notices.isEmpty()) {
                item {
                    Text(
                        "안내가 없어요. + 를 눌러 추가하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(state.draft.notices, key = { it.id.value }) { notice ->
                val selected = state.selection == Selection.NoticeItem(notice.id)
                ContextMenuArea(items = {
                    listOf(
                        ContextMenuItem("복제") { editor.duplicateNotice(notice.id) },
                        ContextMenuItem("삭제") { editor.deleteNotice(notice.id) },
                    )
                }) {
                    SidebarRow(selected = selected, onClick = { editor.select(Selection.NoticeItem(notice.id)) }) {
                        NoticeRowContent(notice, schema.catalog.label(notice.screen), hasIssues = state.issuesFor(notice.id).isNotEmpty(), today = today)
                    }
                }
            }
        }
        Legend()
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SidebarRow(selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(Dimens.radiusSmall))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun RowScope.NoticeRowContent(notice: Notice, screenLabel: String, hasIssues: Boolean, today: LocalDate) {
    val status = MaterialTheme.status
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        when {
            hasIssues -> Icon(Icons.Outlined.Warning, "입력 오류", Modifier.size(16.dp), tint = status.warning)
            notice.isBlocking && notice.status(today) == NoticeStatus.LIVE -> Icon(Icons.Outlined.Block, "차단", Modifier.size(16.dp), tint = status.blocking)
            else -> StatusDot(
                when (notice.status(today)) {
                    NoticeStatus.LIVE -> status.live
                    NoticeStatus.OFF -> status.off
                    NoticeStatus.EXPIRED -> status.expired
                },
            )
        }
    }
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
        Text(notice.displayTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "$screenLabel · ${NoticeTemplate.fromId(notice.template)?.label ?: notice.template}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Legend() {
    val status = MaterialTheme.status
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendRow(status.live, "켜진 안내")
        LegendRow(status.off, "꺼진 안내")
        LegendRow(status.expired, "켜졌지만 종료일이 지남")
    }
}

@Composable
private fun LegendRow(color: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusDot(color, size = 7.dp)
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
