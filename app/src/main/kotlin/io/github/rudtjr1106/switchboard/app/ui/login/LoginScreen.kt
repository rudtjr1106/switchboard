package io.github.rudtjr1106.switchboard.app.ui.login

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rudtjr1106.switchboard.app.BuildInfo
import io.github.rudtjr1106.switchboard.app.platform.DesktopActions
import io.github.rudtjr1106.switchboard.app.session.SessionManager
import io.github.rudtjr1106.switchboard.app.session.SessionState
import io.github.rudtjr1106.switchboard.app.ui.components.Caption
import io.github.rudtjr1106.switchboard.app.ui.components.NoteBanner
import io.github.rudtjr1106.switchboard.app.ui.components.NoteKind
import io.github.rudtjr1106.switchboard.app.ui.theme.Dimens

@Composable
fun LoginScreen(session: SessionManager, state: SessionState, onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxSize()) {
        BrandPanel(Modifier.width(400.dp).fillMaxHeight())
        Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 440.dp).padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (state) {
                    is SessionState.SigningIn -> SigningInCard(state, onCancel = session::cancelSignIn)
                    is SessionState.SignedOut -> SignedOutCard(session, state, onOpenSettings)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun BrandPanel(modifier: Modifier) {
    Box(
        modifier.background(
            Brush.linearGradient(listOf(Color(0xFF4A5AE8), Color(0xFF2B36A6))),
        ),
    ) {
        Column(Modifier.padding(40.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            androidx.compose.foundation.Image(
                painter = painterResource("icon.png"),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text("Switchboard", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "앱을 다시 배포하지 않고도 화면 안내와 점검 공지를 켜고 끕니다.\nGitHub 저장소 하나가 곧 원격 설정이에요.",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.weight(1f))
            Text("v${BuildInfo.VERSION}", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SignedOutCard(session: SessionManager, state: SessionState.SignedOut, onOpenSettings: () -> Unit) {
    var showToken by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    val hasClientId = session.clientId != null

    Text("GitHub 으로 로그인", style = MaterialTheme.typography.headlineSmall)
    Text(
        "설정 저장소를 읽고 PR 을 만들어 머지하기 때문에 저장소에 쓰기 권한이 있는 계정이어야 해요.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.error?.let { NoteBanner(it, NoteKind.ERROR) }

    if (hasClientId) {
        Button(onClick = session::signInWithDeviceFlow, modifier = Modifier.fillMaxWidth()) {
            Text("GitHub 으로 로그인")
        }
        Caption("브라우저가 열리고 8자리 코드를 입력하면 끝나요. 토큰은 이 Mac/PC 의 자격 증명 저장소에만 보관돼요.")
    } else {
        NoteBanner(
            "브라우저 로그인을 쓰려면 GitHub OAuth App 의 Client ID 가 필요해요. 설정에서 넣거나 아래 방법으로 로그인하세요.",
            NoteKind.INFO,
            action = { TextButton(onClick = onOpenSettings) { Text("설정") } },
        )
    }

    if (state.ghCliAvailable) {
        OutlinedButton(onClick = session::signInWithGhCli, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Terminal, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("gh CLI 로그인 사용")
        }
        Caption("터미널에서 gh auth login 한 계정의 토큰을 빌려 써요. 토큰을 따로 저장하지 않아요.")
    }

    TextButton(onClick = { showToken = !showToken }) { Text(if (showToken) "토큰 직접 입력 닫기" else "Personal Access Token 직접 입력") }
    if (showToken) {
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Personal Access Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("repo · workflow · read:org 스코프가 필요해요") },
        )
        FilledTonalButton(onClick = { session.signInWithToken(token); token = "" }, enabled = token.isNotBlank()) { Text("토큰으로 로그인") }
    }
}

@Composable
private fun SigningInCard(state: SessionState.SigningIn, onCancel: () -> Unit) {
    Text("GitHub 에서 승인해 주세요", style = MaterialTheme.typography.headlineSmall)
    if (state.userCode == null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("로그인 준비 중…", style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        Text("브라우저에서 아래 코드를 입력하면 로그인이 끝나요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(Dimens.radiusMedium)).padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(state.userCode, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { state.verificationUri?.let(DesktopActions::openUrl) }) {
                Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("브라우저 열기")
            }
            OutlinedButton(onClick = { DesktopActions.copyToClipboard(state.userCode) }) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("코드 복사")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Caption("승인을 기다리는 중… ${state.verificationUri ?: ""}")
        }
    }
    TextButton(onClick = onCancel) { Text("취소") }
}
