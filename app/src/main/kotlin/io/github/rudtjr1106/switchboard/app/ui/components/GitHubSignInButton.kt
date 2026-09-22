package io.github.rudtjr1106.switchboard.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * GitHub 공식 마크 (Primer Octicons `mark-github-24`, MIT License, © GitHub Inc.)
 *
 * https://github.com/primer/octicons/blob/main/icons/mark-github-24.svg 의 path 를 그대로 옮겼다.
 * GitHub 로고 사용 규칙에 따라 모양·비율을 바꾸지 않고 단색(검정 또는 흰색)으로만 쓴다.
 */
val GitHubMark: ImageVector by lazy {
    ImageVector.Builder(name = "GitHubMark", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = addPathNodes(MARK_PATH), fill = SolidColor(Color.Black))
        .build()
}

private const val MARK_PATH =
    "M10.226 17.284c-2.965-.36-5.054-2.493-5.054-5.256 0-1.123.404-2.336 1.078-3.144-.292-.741-.247-2.314.09-2.965.898-.112 2.111.36 2.83 1.01.853-.269 1.752-.404 2.853-.404 1.1 0 1.999.135 2.807.382.696-.629 1.932-1.1 2.83-.988.315.606.36 2.179.067 2.942.72.854 1.101 2 1.101 3.167 0 2.763-2.089 4.852-5.098 5.234.763.494 1.28 1.572 1.28 2.807v2.336c0 .674.561 1.056 1.235.786 4.066-1.55 7.255-5.615 7.255-10.646C23.5 6.188 18.334 1 11.978 1 5.62 1 .5 6.188.5 12.545c0 4.986 3.167 9.12 7.435 10.669.606.225 1.19-.18 1.19-.786V20.63a2.9 2.9 0 0 1-1.078.224c-1.483 0-2.359-.808-2.987-2.313-.247-.607-.517-.966-1.034-1.033-.27-.023-.359-.135-.359-.27 0-.27.45-.471.898-.471.652 0 1.213.404 1.797 1.235.45.651.921.943 1.483.943.561 0 .92-.202 1.437-.719.382-.381.674-.718.944-.943"

/** 흔히 쓰는 'GitHub 계정으로 로그인' 버튼 모양. 검은 바탕, 왼쪽 GitHub 마크, 가운데 문구 */
@Composable
fun GitHubSignInButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, text: String = "GitHub 계정으로 로그인") {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF3A3A3A),
            disabledContentColor = Color(0xFFB0B0B0),
        ),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Icon(GitHubMark, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp).size(24.dp))
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
        }
    }
}
