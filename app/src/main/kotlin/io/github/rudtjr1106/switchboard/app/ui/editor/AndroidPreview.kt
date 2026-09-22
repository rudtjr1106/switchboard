package io.github.rudtjr1106.switchboard.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rudtjr1106.switchboard.app.ui.components.Caption
import io.github.rudtjr1106.switchboard.app.ui.theme.status
import io.github.rudtjr1106.switchboard.config.Notice
import io.github.rudtjr1106.switchboard.config.NoticeTemplate

/** 안내가 Android 앱에서 어떤 모양으로 뜨는지. Material 3 다이얼로그와 전체 화면 차단 화면을 흉내 낸다 */
@Composable
fun AndroidPreview(notice: Notice, modifier: Modifier = Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Caption("Android 미리보기")
        Phone {
            when (NoticeTemplate.fromId(notice.template)) {
                NoticeTemplate.BLOCKING -> BlockingScreen(notice)
                else -> Box(Modifier.fillMaxSize()) {
                    FakeApp()
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)), contentAlignment = Alignment.Center) {
                        InfoDialog(notice)
                    }
                }
            }
        }
        Caption(
            when (NoticeTemplate.fromId(notice.template)) {
                NoticeTemplate.BLOCKING -> "하단바까지 덮는 전체 화면. 확인 버튼이 없고 '앱 종료' 만 있어요."
                NoticeTemplate.INFO -> "확인을 누르면 앱을 다시 켜기 전까지 다시 뜨지 않아요."
                null -> "앱이 모르는 모양(${notice.template})이라 표시되지 않아요."
            },
        )
    }
}

@Composable
private fun Phone(content: @Composable () -> Unit) {
    val bezel = MaterialTheme.status.phoneBezel
    Box(
        Modifier
            .width(250.dp)
            .height(520.dp)
            .background(bezel, RoundedCornerShape(30.dp))
            .padding(7.dp),
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Color(0xFFFCFCFF))) {
            content()
            Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(10.dp).background(Color.Black, CircleShape))
        }
    }
}

@Composable
private fun FakeApp() {
    val block = Color(0xFFE3E4EC)
    Column(Modifier.fillMaxSize().padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(90.dp).height(14.dp).background(block, RoundedCornerShape(4.dp)))
        Box(Modifier.fillMaxWidth().height(84.dp).background(block, RoundedCornerShape(12.dp)))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(66.dp).background(block, RoundedCornerShape(12.dp)))
            Box(Modifier.weight(1f).height(66.dp).background(block, RoundedCornerShape(12.dp)))
        }
        Box(Modifier.fillMaxWidth().height(110.dp).background(block, RoundedCornerShape(12.dp)))
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().height(48.dp).background(Color(0xFFF1F2F8), RoundedCornerShape(16.dp)), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { Box(Modifier.size(18.dp).background(if (it == 0) Color(0xFF4A5AE8) else Color(0xFFB7B9C6), CircleShape)) }
        }
    }
}

@Composable
private fun InfoDialog(notice: Notice) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFFFFFFF), modifier = Modifier.width(200.dp), shadowElevation = 6.dp) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(notice.title.ifBlank { "제목" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (notice.title.isBlank()) Color(0xFFB0B2BD) else Color(0xFF1B1B1F), lineHeight = 18.sp)
            Text(notice.body.ifBlank { "본문" }, fontSize = 10.sp, color = if (notice.body.isBlank()) Color(0xFFB0B2BD) else Color(0xFF45464F), lineHeight = 14.sp)
            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.CenterEnd) {
                Text("확인", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4A5AE8))
            }
        }
    }
}

@Composable
private fun BlockingScreen(notice: Notice) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFFFCFCFF)).padding(horizontal = 22.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Build, null, Modifier.size(34.dp), tint = Color(0xFF8E90A0))
        Spacer(Modifier.height(14.dp))
        Text(notice.title.ifBlank { "제목" }, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = if (notice.title.isBlank()) Color(0xFFB0B2BD) else Color(0xFF1B1B1F), lineHeight = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text(notice.body.ifBlank { "본문" }, fontSize = 10.5.sp, textAlign = TextAlign.Center, color = if (notice.body.isBlank()) Color(0xFFB0B2BD) else Color(0xFF45464F), lineHeight = 15.sp)
        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth().height(36.dp).background(Color(0xFF4A5AE8), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Text("앱 종료", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
