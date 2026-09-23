package io.github.rudtjr1106.switchboard.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 앱이 쓰는 Material 아이콘 27 개 (Apache License 2.0, Google)
 *
 * material-icons-extended 는 아이콘 전체가 들어 있어 jar 하나가 36MB 다. 설치 파일 용량을 줄이려고
 * 쓰는 것만 뽑아 두었다. GenerateAppIcons 테스트가 라이브러리에서 그대로 만들어 낸 파일이라 손으로 고치지 않는다.
 */
object AppIcons {

    private var _add: ImageVector? = null
    val Add: ImageVector
        get() = _add ?: ImageVector.Builder(
            name = "Outlined.Add",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(19f, 13f)
                 horizontalLineToRelative(-6f)
                 verticalLineToRelative(6f)
                 horizontalLineToRelative(-2f)
                 verticalLineToRelative(-6f)
                 horizontalLineTo(5f)
                 verticalLineToRelative(-2f)
                 horizontalLineToRelative(6f)
                 verticalLineTo(5f)
                 horizontalLineToRelative(2f)
                 verticalLineToRelative(6f)
                 horizontalLineToRelative(6f)
                 verticalLineToRelative(2f)
                 close()
             }
        }.build().also { _add = it }

    private var _addCircleOutline: ImageVector? = null
    val AddCircleOutline: ImageVector
        get() = _addCircleOutline ?: ImageVector.Builder(
            name = "Outlined.AddCircleOutline",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(13f, 7f)
                 horizontalLineToRelative(-2f)
                 verticalLineToRelative(4f)
                 lineTo(7f, 11f)
                 verticalLineToRelative(2f)
                 horizontalLineToRelative(4f)
                 verticalLineToRelative(4f)
                 horizontalLineToRelative(2f)
                 verticalLineToRelative(-4f)
                 horizontalLineToRelative(4f)
                 verticalLineToRelative(-2f)
                 horizontalLineToRelative(-4f)
                 lineTo(13f, 7f)
                 close()
                 moveTo(12f, 2f)
                 curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                 reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                 reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                 reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                 close()
                 moveTo(12f, 20f)
                 curveToRelative(-4.41f, 0f, -8f, -3.59f, -8f, -8f)
                 reflectiveCurveToRelative(3.59f, -8f, 8f, -8f)
                 reflectiveCurveToRelative(8f, 3.59f, 8f, 8f)
                 reflectiveCurveToRelative(-3.59f, 8f, -8f, 8f)
                 close()
             }
        }.build().also { _addCircleOutline = it }

    private var _arrowBack: ImageVector? = null
    val ArrowBack: ImageVector
        get() = _arrowBack ?: ImageVector.Builder(
            name = "AutoMirrored.Outlined.ArrowBack",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(20f, 11f)
                 horizontalLineTo(7.83f)
                 lineToRelative(5.59f, -5.59f)
                 lineTo(12f, 4f)
                 lineToRelative(-8f, 8f)
                 lineToRelative(8f, 8f)
                 lineToRelative(1.41f, -1.41f)
                 lineTo(7.83f, 13f)
                 horizontalLineTo(20f)
                 verticalLineToRelative(-2f)
                 close()
             }
        }.build().also { _arrowBack = it }

    private var _autoAwesome: ImageVector? = null
    val AutoAwesome: ImageVector
        get() = _autoAwesome ?: ImageVector.Builder(
            name = "Outlined.AutoAwesome",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(19f, 9f)
                 lineToRelative(1.25f, -2.75f)
                 lineToRelative(2.75f, -1.25f)
                 lineToRelative(-2.75f, -1.25f)
                 lineToRelative(-1.25f, -2.75f)
                 lineToRelative(-1.25f, 2.75f)
                 lineToRelative(-2.75f, 1.25f)
                 lineToRelative(2.75f, 1.25f)
                 close()
             }
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(19f, 15f)
                 lineToRelative(-1.25f, 2.75f)
                 lineToRelative(-2.75f, 1.25f)
                 lineToRelative(2.75f, 1.25f)
                 lineToRelative(1.25f, 2.75f)
                 lineToRelative(1.25f, -2.75f)
                 lineToRelative(2.75f, -1.25f)
                 lineToRelative(-2.75f, -1.25f)
                 close()
             }
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(11.5f, 9.5f)
                 lineTo(9f, 4f)
                 lineTo(6.5f, 9.5f)
                 lineTo(1f, 12f)
                 lineToRelative(5.5f, 2.5f)
                 lineTo(9f, 20f)
                 lineToRelative(2.5f, -5.5f)
                 lineTo(17f, 12f)
                 lineTo(11.5f, 9.5f)
                 close()
                 moveTo(9.99f, 12.99f)
                 lineTo(9f, 15.17f)
                 lineToRelative(-0.99f, -2.18f)
                 lineTo(5.83f, 12f)
                 lineToRelative(2.18f, -0.99f)
                 lineTo(9f, 8.83f)
                 lineToRelative(0.99f, 2.18f)
                 lineTo(12.17f, 12f)
                 lineTo(9.99f, 12.99f)
                 close()
             }
        }.build().also { _autoAwesome = it }

    private var _block: ImageVector? = null
    val Block: ImageVector
        get() = _block ?: ImageVector.Builder(
            name = "Outlined.Block",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(12f, 2f)
                 curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                 reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                 reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                 reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                 close()
                 moveTo(4f, 12f)
                 curveToRelative(0f, -4.42f, 3.58f, -8f, 8f, -8f)
                 curveToRelative(1.85f, 0f, 3.55f, 0.63f, 4.9f, 1.69f)
                 lineTo(5.69f, 16.9f)
                 curveTo(4.63f, 15.55f, 4f, 13.85f, 4f, 12f)
                 close()
                 moveTo(12f, 20f)
                 curveToRelative(-1.85f, 0f, -3.55f, -0.63f, -4.9f, -1.69f)
                 lineTo(18.31f, 7.1f)
                 curveTo(19.37f, 8.45f, 20f, 10.15f, 20f, 12f)
                 curveToRelative(0f, 4.42f, -3.58f, 8f, -8f, 8f)
                 close()
             }
        }.build().also { _block = it }

    private var _build: ImageVector? = null
    val Build: ImageVector
        get() = _build ?: ImageVector.Builder(
            name = "Outlined.Build",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(22.61f, 18.99f)
                 lineToRelative(-9.08f, -9.08f)
                 curveToRelative(0.93f, -2.34f, 0.45f, -5.1f, -1.44f, -7f)
                 curveTo(9.79f, 0.61f, 6.21f, 0.4f, 3.66f, 2.26f)
                 lineTo(7.5f, 6.11f)
                 lineTo(6.08f, 7.52f)
                 lineTo(2.25f, 3.69f)
                 curveTo(0.39f, 6.23f, 0.6f, 9.82f, 2.9f, 12.11f)
                 curveToRelative(1.86f, 1.86f, 4.57f, 2.35f, 6.89f, 1.48f)
                 lineToRelative(9.11f, 9.11f)
                 curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                 lineToRelative(2.3f, -2.3f)
                 curveToRelative(0.4f, -0.38f, 0.4f, -1.01f, 0f, -1.41f)
                 close()
                 moveTo(19.61f, 20.59f)
                 lineToRelative(-9.46f, -9.46f)
                 curveToRelative(-0.61f, 0.45f, -1.29f, 0.72f, -2f, 0.82f)
                 curveToRelative(-1.36f, 0.2f, -2.79f, -0.21f, -3.83f, -1.25f)
                 curveTo(3.37f, 9.76f, 2.93f, 8.5f, 3f, 7.26f)
                 lineToRelative(3.09f, 3.09f)
                 lineToRelative(4.24f, -4.24f)
                 lineToRelative(-3.09f, -3.09f)
                 curveToRelative(1.24f, -0.07f, 2.49f, 0.37f, 3.44f, 1.31f)
                 curveToRelative(1.08f, 1.08f, 1.49f, 2.57f, 1.24f, 3.96f)
                 curveToRelative(-0.12f, 0.71f, -0.42f, 1.37f, -0.88f, 1.96f)
                 lineToRelative(9.45f, 9.45f)
                 lineToRelative(-0.88f, 0.89f)
                 close()
             }
        }.build().also { _build = it }

    private var _checkCircle: ImageVector? = null
    val CheckCircle: ImageVector
        get() = _checkCircle ?: ImageVector.Builder(
            name = "Outlined.CheckCircle",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(12f, 2f)
                 curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                 reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                 reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                 reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                 close()
                 moveTo(12f, 20f)
                 curveToRelative(-4.41f, 0f, -8f, -3.59f, -8f, -8f)
                 reflectiveCurveToRelative(3.59f, -8f, 8f, -8f)
                 reflectiveCurveToRelative(8f, 3.59f, 8f, 8f)
                 reflectiveCurveToRelative(-3.59f, 8f, -8f, 8f)
                 close()
                 moveTo(16.59f, 7.58f)
                 lineTo(10f, 14.17f)
                 lineToRelative(-2.59f, -2.58f)
                 lineTo(6f, 13f)
                 lineToRelative(4f, 4f)
                 lineToRelative(8f, -8f)
                 close()
             }
        }.build().also { _checkCircle = it }

    private var _close: ImageVector? = null
    val Close: ImageVector
        get() = _close ?: ImageVector.Builder(
            name = "Outlined.Close",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(19f, 6.41f)
                 lineTo(17.59f, 5f)
                 lineTo(12f, 10.59f)
                 lineTo(6.41f, 5f)
                 lineTo(5f, 6.41f)
                 lineTo(10.59f, 12f)
                 lineTo(5f, 17.59f)
                 lineTo(6.41f, 19f)
                 lineTo(12f, 13.41f)
                 lineTo(17.59f, 19f)
                 lineTo(19f, 17.59f)
                 lineTo(13.41f, 12f)
                 lineTo(19f, 6.41f)
                 close()
             }
        }.build().also { _close = it }

    private var _cloudUpload: ImageVector? = null
    val CloudUpload: ImageVector
        get() = _cloudUpload ?: ImageVector.Builder(
            name = "Outlined.CloudUpload",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(19.35f, 10.04f)
                 curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
                 curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
                 curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
                 curveToRelative(0f, 3.31f, 2.69f, 6f, 6f, 6f)
                 horizontalLineToRelative(13f)
                 curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
                 curveToRelative(0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                 close()
                 moveTo(19f, 18f)
                 horizontalLineTo(6f)
                 curveToRelative(-2.21f, 0f, -4f, -1.79f, -4f, -4f)
                 curveToRelative(0f, -2.05f, 1.53f, -3.76f, 3.56f, -3.97f)
                 lineToRelative(1.07f, -0.11f)
                 lineToRelative(0.5f, -0.95f)
                 curveTo(8.08f, 7.14f, 9.94f, 6f, 12f, 6f)
                 curveToRelative(2.62f, 0f, 4.88f, 1.86f, 5.39f, 4.43f)
                 lineToRelative(0.3f, 1.5f)
                 lineToRelative(1.53f, 0.11f)
                 curveToRelative(1.56f, 0.1f, 2.78f, 1.41f, 2.78f, 2.96f)
                 curveToRelative(0f, 1.65f, -1.35f, 3f, -3f, 3f)
                 close()
                 moveTo(8f, 13f)
                 horizontalLineToRelative(2.55f)
                 verticalLineToRelative(3f)
                 horizontalLineToRelative(2.9f)
                 verticalLineToRelative(-3f)
                 horizontalLineTo(16f)
                 lineToRelative(-4f, -4f)
                 close()
             }
        }.build().also { _cloudUpload = it }

    private var _contentCopy: ImageVector? = null
    val ContentCopy: ImageVector
        get() = _contentCopy ?: ImageVector.Builder(
            name = "Outlined.ContentCopy",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(16f, 1f)
                 lineTo(4f, 1f)
                 curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                 verticalLineToRelative(14f)
                 horizontalLineToRelative(2f)
                 lineTo(4f, 3f)
                 horizontalLineToRelative(12f)
                 lineTo(16f, 1f)
                 close()
                 moveTo(19f, 5f)
                 lineTo(8f, 5f)
                 curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                 verticalLineToRelative(14f)
                 curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                 horizontalLineToRelative(11f)
                 curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                 lineTo(21f, 7f)
                 curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                 close()
                 moveTo(19f, 21f)
                 lineTo(8f, 21f)
                 lineTo(8f, 7f)
                 horizontalLineToRelative(11f)
                 verticalLineToRelative(14f)
                 close()
             }
        }.build().also { _contentCopy = it }

    private var _delete: ImageVector? = null
    val Delete: ImageVector
        get() = _delete ?: ImageVector.Builder(
            name = "Outlined.Delete",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(16f, 9f)
                 verticalLineToRelative(10f)
                 horizontalLineTo(8f)
                 verticalLineTo(9f)
                 horizontalLineToRelative(8f)
                 moveToRelative(-1.5f, -6f)
                 horizontalLineToRelative(-5f)
                 lineToRelative(-1f, 1f)
                 horizontalLineTo(5f)
                 verticalLineToRelative(2f)
                 horizontalLineToRelative(14f)
                 verticalLineTo(4f)
                 horizontalLineToRelative(-3.5f)
                 lineToRelative(-1f, -1f)
                 close()
                 moveTo(18f, 7f)
                 horizontalLineTo(6f)
                 verticalLineToRelative(12f)
                 curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                 horizontalLineToRelative(8f)
                 curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                 verticalLineTo(7f)
                 close()
             }
        }.build().also { _delete = it }

    private var _edit: ImageVector? = null
    val Edit: ImageVector
        get() = _edit ?: ImageVector.Builder(
            name = "Outlined.Edit",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(14.06f, 9.02f)
                 lineToRelative(0.92f, 0.92f)
                 lineTo(5.92f, 19f)
                 lineTo(5f, 19f)
                 verticalLineToRelative(-0.92f)
                 lineToRelative(9.06f, -9.06f)
                 moveTo(17.66f, 3f)
                 curveToRelative(-0.25f, 0f, -0.51f, 0.1f, -0.7f, 0.29f)
                 lineToRelative(-1.83f, 1.83f)
                 lineToRelative(3.75f, 3.75f)
                 lineToRelative(1.83f, -1.83f)
                 curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                 lineToRelative(-2.34f, -2.34f)
                 curveToRelative(-0.2f, -0.2f, -0.45f, -0.29f, -0.71f, -0.29f)
                 close()
                 moveTo(14.06f, 6.19f)
                 lineTo(3f, 17.25f)
                 lineTo(3f, 21f)
                 horizontalLineToRelative(3.75f)
                 lineTo(17.81f, 9.94f)
                 lineToRelative(-3.75f, -3.75f)
                 close()
             }
        }.build().also { _edit = it }

    private var _error: ImageVector? = null
    val Error: ImageVector
        get() = _error ?: ImageVector.Builder(
            name = "Outlined.Error",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(12f, 2f)
                 curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                 reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                 reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                 reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                 close()
                 moveTo(13f, 17f)
                 horizontalLineToRelative(-2f)
                 verticalLineToRelative(-2f)
                 horizontalLineToRelative(2f)
                 verticalLineToRelative(2f)
                 close()
                 moveTo(13f, 13f)
                 horizontalLineToRelative(-2f)
                 lineTo(11f, 7f)
                 horizontalLineToRelative(2f)
                 verticalLineToRelative(6f)
                 close()
             }
        }.build().also { _error = it }

    private var _folder: ImageVector? = null
    val Folder: ImageVector
        get() = _folder ?: ImageVector.Builder(
            name = "Outlined.Folder",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(9.17f, 6f)
                 lineToRelative(2f, 2f)
                 horizontalLineTo(20f)
                 verticalLineToRelative(10f)
                 horizontalLineTo(4f)
                 verticalLineTo(6f)
                 horizontalLineToRelative(5.17f)
                 moveTo(10f, 4f)
                 horizontalLineTo(4f)
                 curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                 lineTo(2f, 18f)
                 curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                 horizontalLineToRelative(16f)
                 curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                 verticalLineTo(8f)
                 curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                 horizontalLineToRelative(-8f)
                 lineToRelative(-2f, -2f)
                 close()
             }
        }.build().also { _folder = it }

    private var _folderOpen: ImageVector? = null
    val FolderOpen: ImageVector
        get() = _folderOpen ?: ImageVector.Builder(
            name = "Outlined.FolderOpen",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(20f, 6f)
                 horizontalLineToRelative(-8f)
                 lineToRelative(-2f, -2f)
                 lineTo(4f, 4f)
                 curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                 lineTo(2f, 18f)
                 curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                 horizontalLineToRelative(16f)
                 curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                 lineTo(22f, 8f)
                 curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                 close()
                 moveTo(20f, 18f)
                 lineTo(4f, 18f)
                 lineTo(4f, 8f)
                 horizontalLineToRelative(16f)
                 verticalLineToRelative(10f)
                 close()
             }
        }.build().also { _folderOpen = it }

    private var _info: ImageVector? = null
    val Info: ImageVector
        get() = _info ?: ImageVector.Builder(
            name = "Outlined.Info",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(11f, 7f)
                 horizontalLineToRelative(2f)
                 verticalLineToRelative(2f)
                 horizontalLineToRelative(-2f)
                 close()
                 moveTo(11f, 11f)
                 horizontalLineToRelative(2f)
                 verticalLineToRelative(6f)
                 horizontalLineToRelative(-2f)
                 close()
                 moveTo(12f, 2f)
                 curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                 reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                 reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                 reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                 close()
                 moveTo(12f, 20f)
                 curveToRelative(-4.41f, 0f, -8f, -3.59f, -8f, -8f)
                 reflectiveCurveToRelative(3.59f, -8f, 8f, -8f)
                 reflectiveCurveToRelative(8f, 3.59f, 8f, 8f)
                 reflectiveCurveToRelative(-3.59f, 8f, -8f, 8f)
                 close()
             }
        }.build().also { _info = it }

    private var _logout: ImageVector? = null
    val Logout: ImageVector
        get() = _logout ?: ImageVector.Builder(
            name = "AutoMirrored.Outlined.Logout",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(17f, 8f)
                 lineToRelative(-1.41f, 1.41f)
                 lineTo(17.17f, 11f)
                 horizontalLineTo(9f)
                 verticalLineToRelative(2f)
                 horizontalLineToRelative(8.17f)
                 lineToRelative(-1.58f, 1.58f)
                 lineTo(17f, 16f)
                 lineToRelative(4f, -4f)
                 lineTo(17f, 8f)
                 close()
                 moveTo(5f, 5f)
                 horizontalLineToRelative(7f)
                 verticalLineTo(3f)
                 horizontalLineTo(5f)
                 curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
                 verticalLineToRelative(14f)
                 curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                 horizontalLineToRelative(7f)
                 verticalLineToRelative(-2f)
                 horizontalLineTo(5f)
                 verticalLineTo(5f)
                 close()
             }
        }.build().also { _logout = it }

    private var _openInNew: ImageVector? = null
    val OpenInNew: ImageVector
        get() = _openInNew ?: ImageVector.Builder(
            name = "AutoMirrored.Outlined.OpenInNew",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(19f, 19f)
                 horizontalLineTo(5f)
                 verticalLineTo(5f)
                 horizontalLineToRelative(7f)
                 verticalLineTo(3f)
                 horizontalLineTo(5f)
                 curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
                 verticalLineToRelative(14f)
                 curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
                 horizontalLineToRelative(14f)
                 curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                 verticalLineToRelative(-7f)
                 horizontalLineToRelative(-2f)
                 verticalLineToRelative(7f)
                 close()
                 moveTo(14f, 3f)
                 verticalLineToRelative(2f)
                 horizontalLineToRelative(3.59f)
                 lineToRelative(-9.83f, 9.83f)
                 lineToRelative(1.41f, 1.41f)
                 lineTo(19f, 6.41f)
                 verticalLineTo(10f)
                 horizontalLineToRelative(2f)
                 verticalLineTo(3f)
                 horizontalLineToRelative(-7f)
                 close()
             }
        }.build().also { _openInNew = it }

    private var _refresh: ImageVector? = null
    val Refresh: ImageVector
        get() = _refresh ?: ImageVector.Builder(
            name = "Outlined.Refresh",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(17.65f, 6.35f)
                 curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
                 curveToRelative(-4.42f, 0f, -7.99f, 3.58f, -7.99f, 8f)
                 reflectiveCurveToRelative(3.57f, 8f, 7.99f, 8f)
                 curveToRelative(3.73f, 0f, 6.84f, -2.55f, 7.73f, -6f)
                 horizontalLineToRelative(-2.08f)
                 curveToRelative(-0.82f, 2.33f, -3.04f, 4f, -5.65f, 4f)
                 curveToRelative(-3.31f, 0f, -6f, -2.69f, -6f, -6f)
                 reflectiveCurveToRelative(2.69f, -6f, 6f, -6f)
                 curveToRelative(1.66f, 0f, 3.14f, 0.69f, 4.22f, 1.78f)
                 lineTo(13f, 11f)
                 horizontalLineToRelative(7f)
                 verticalLineTo(4f)
                 lineToRelative(-2.35f, 2.35f)
                 close()
             }
        }.build().also { _refresh = it }

    private var _removeCircleOutline: ImageVector? = null
    val RemoveCircleOutline: ImageVector
        get() = _removeCircleOutline ?: ImageVector.Builder(
            name = "Outlined.RemoveCircleOutline",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(7f, 11f)
                 verticalLineToRelative(2f)
                 horizontalLineToRelative(10f)
                 verticalLineToRelative(-2f)
                 lineTo(7f, 11f)
                 close()
                 moveTo(12f, 2f)
                 curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                 reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                 reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                 reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                 close()
                 moveTo(12f, 20f)
                 curveToRelative(-4.41f, 0f, -8f, -3.59f, -8f, -8f)
                 reflectiveCurveToRelative(3.59f, -8f, 8f, -8f)
                 reflectiveCurveToRelative(8f, 3.59f, 8f, 8f)
                 reflectiveCurveToRelative(-3.59f, 8f, -8f, 8f)
                 close()
             }
        }.build().also { _removeCircleOutline = it }

    private var _settings: ImageVector? = null
    val Settings: ImageVector
        get() = _settings ?: ImageVector.Builder(
            name = "Outlined.Settings",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(19.43f, 12.98f)
                 curveToRelative(0.04f, -0.32f, 0.07f, -0.64f, 0.07f, -0.98f)
                 curveToRelative(0f, -0.34f, -0.03f, -0.66f, -0.07f, -0.98f)
                 lineToRelative(2.11f, -1.65f)
                 curveToRelative(0.19f, -0.15f, 0.24f, -0.42f, 0.12f, -0.64f)
                 lineToRelative(-2f, -3.46f)
                 curveToRelative(-0.09f, -0.16f, -0.26f, -0.25f, -0.44f, -0.25f)
                 curveToRelative(-0.06f, 0f, -0.12f, 0.01f, -0.17f, 0.03f)
                 lineToRelative(-2.49f, 1f)
                 curveToRelative(-0.52f, -0.4f, -1.08f, -0.73f, -1.69f, -0.98f)
                 lineToRelative(-0.38f, -2.65f)
                 curveTo(14.46f, 2.18f, 14.25f, 2f, 14f, 2f)
                 horizontalLineToRelative(-4f)
                 curveToRelative(-0.25f, 0f, -0.46f, 0.18f, -0.49f, 0.42f)
                 lineToRelative(-0.38f, 2.65f)
                 curveToRelative(-0.61f, 0.25f, -1.17f, 0.59f, -1.69f, 0.98f)
                 lineToRelative(-2.49f, -1f)
                 curveToRelative(-0.06f, -0.02f, -0.12f, -0.03f, -0.18f, -0.03f)
                 curveToRelative(-0.17f, 0f, -0.34f, 0.09f, -0.43f, 0.25f)
                 lineToRelative(-2f, 3.46f)
                 curveToRelative(-0.13f, 0.22f, -0.07f, 0.49f, 0.12f, 0.64f)
                 lineToRelative(2.11f, 1.65f)
                 curveToRelative(-0.04f, 0.32f, -0.07f, 0.65f, -0.07f, 0.98f)
                 curveToRelative(0f, 0.33f, 0.03f, 0.66f, 0.07f, 0.98f)
                 lineToRelative(-2.11f, 1.65f)
                 curveToRelative(-0.19f, 0.15f, -0.24f, 0.42f, -0.12f, 0.64f)
                 lineToRelative(2f, 3.46f)
                 curveToRelative(0.09f, 0.16f, 0.26f, 0.25f, 0.44f, 0.25f)
                 curveToRelative(0.06f, 0f, 0.12f, -0.01f, 0.17f, -0.03f)
                 lineToRelative(2.49f, -1f)
                 curveToRelative(0.52f, 0.4f, 1.08f, 0.73f, 1.69f, 0.98f)
                 lineToRelative(0.38f, 2.65f)
                 curveToRelative(0.03f, 0.24f, 0.24f, 0.42f, 0.49f, 0.42f)
                 horizontalLineToRelative(4f)
                 curveToRelative(0.25f, 0f, 0.46f, -0.18f, 0.49f, -0.42f)
                 lineToRelative(0.38f, -2.65f)
                 curveToRelative(0.61f, -0.25f, 1.17f, -0.59f, 1.69f, -0.98f)
                 lineToRelative(2.49f, 1f)
                 curveToRelative(0.06f, 0.02f, 0.12f, 0.03f, 0.18f, 0.03f)
                 curveToRelative(0.17f, 0f, 0.34f, -0.09f, 0.43f, -0.25f)
                 lineToRelative(2f, -3.46f)
                 curveToRelative(0.12f, -0.22f, 0.07f, -0.49f, -0.12f, -0.64f)
                 lineToRelative(-2.11f, -1.65f)
                 close()
                 moveTo(17.45f, 11.27f)
                 curveToRelative(0.04f, 0.31f, 0.05f, 0.52f, 0.05f, 0.73f)
                 curveToRelative(0f, 0.21f, -0.02f, 0.43f, -0.05f, 0.73f)
                 lineToRelative(-0.14f, 1.13f)
                 lineToRelative(0.89f, 0.7f)
                 lineToRelative(1.08f, 0.84f)
                 lineToRelative(-0.7f, 1.21f)
                 lineToRelative(-1.27f, -0.51f)
                 lineToRelative(-1.04f, -0.42f)
                 lineToRelative(-0.9f, 0.68f)
                 curveToRelative(-0.43f, 0.32f, -0.84f, 0.56f, -1.25f, 0.73f)
                 lineToRelative(-1.06f, 0.43f)
                 lineToRelative(-0.16f, 1.13f)
                 lineToRelative(-0.2f, 1.35f)
                 horizontalLineToRelative(-1.4f)
                 lineToRelative(-0.19f, -1.35f)
                 lineToRelative(-0.16f, -1.13f)
                 lineToRelative(-1.06f, -0.43f)
                 curveToRelative(-0.43f, -0.18f, -0.83f, -0.41f, -1.23f, -0.71f)
                 lineToRelative(-0.91f, -0.7f)
                 lineToRelative(-1.06f, 0.43f)
                 lineToRelative(-1.27f, 0.51f)
                 lineToRelative(-0.7f, -1.21f)
                 lineToRelative(1.08f, -0.84f)
                 lineToRelative(0.89f, -0.7f)
                 lineToRelative(-0.14f, -1.13f)
                 curveToRelative(-0.03f, -0.31f, -0.05f, -0.54f, -0.05f, -0.74f)
                 reflectiveCurveToRelative(0.02f, -0.43f, 0.05f, -0.73f)
                 lineToRelative(0.14f, -1.13f)
                 lineToRelative(-0.89f, -0.7f)
                 lineToRelative(-1.08f, -0.84f)
                 lineToRelative(0.7f, -1.21f)
                 lineToRelative(1.27f, 0.51f)
                 lineToRelative(1.04f, 0.42f)
                 lineToRelative(0.9f, -0.68f)
                 curveToRelative(0.43f, -0.32f, 0.84f, -0.56f, 1.25f, -0.73f)
                 lineToRelative(1.06f, -0.43f)
                 lineToRelative(0.16f, -1.13f)
                 lineToRelative(0.2f, -1.35f)
                 horizontalLineToRelative(1.39f)
                 lineToRelative(0.19f, 1.35f)
                 lineToRelative(0.16f, 1.13f)
                 lineToRelative(1.06f, 0.43f)
                 curveToRelative(0.43f, 0.18f, 0.83f, 0.41f, 1.23f, 0.71f)
                 lineToRelative(0.91f, 0.7f)
                 lineToRelative(1.06f, -0.43f)
                 lineToRelative(1.27f, -0.51f)
                 lineToRelative(0.7f, 1.21f)
                 lineToRelative(-1.07f, 0.85f)
                 lineToRelative(-0.89f, 0.7f)
                 lineToRelative(0.14f, 1.13f)
                 close()
                 moveTo(12f, 8f)
                 curveToRelative(-2.21f, 0f, -4f, 1.79f, -4f, 4f)
                 reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
                 reflectiveCurveToRelative(4f, -1.79f, 4f, -4f)
                 reflectiveCurveToRelative(-1.79f, -4f, -4f, -4f)
                 close()
                 moveTo(12f, 14f)
                 curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
                 reflectiveCurveToRelative(0.9f, -2f, 2f, -2f)
                 reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
                 reflectiveCurveToRelative(-0.9f, 2f, -2f, 2f)
                 close()
             }
        }.build().also { _settings = it }

    private var _systemUpdate: ImageVector? = null
    val SystemUpdate: ImageVector
        get() = _systemUpdate ?: ImageVector.Builder(
            name = "Outlined.SystemUpdate",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(17f, 1.01f)
                 lineTo(7f, 1f)
                 curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                 verticalLineToRelative(18f)
                 curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                 horizontalLineToRelative(10f)
                 curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                 lineTo(19f, 3f)
                 curveToRelative(0f, -1.1f, -0.9f, -1.99f, -2f, -1.99f)
                 close()
                 moveTo(17f, 19f)
                 lineTo(7f, 19f)
                 lineTo(7f, 5f)
                 horizontalLineToRelative(10f)
                 verticalLineToRelative(14f)
                 close()
                 moveTo(16f, 13f)
                 horizontalLineToRelative(-3f)
                 lineTo(13f, 8f)
                 horizontalLineToRelative(-2f)
                 verticalLineToRelative(5f)
                 lineTo(8f, 13f)
                 lineToRelative(4f, 4f)
                 lineToRelative(4f, -4f)
                 close()
             }
        }.build().also { _systemUpdate = it }

    private var _terminal: ImageVector? = null
    val Terminal: ImageVector
        get() = _terminal ?: ImageVector.Builder(
            name = "Outlined.Terminal",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(20f, 4f)
                 horizontalLineTo(4f)
                 curveTo(2.89f, 4f, 2f, 4.9f, 2f, 6f)
                 verticalLineToRelative(12f)
                 curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
                 horizontalLineToRelative(16f)
                 curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                 verticalLineTo(6f)
                 curveTo(22f, 4.9f, 21.11f, 4f, 20f, 4f)
                 close()
                 moveTo(20f, 18f)
                 horizontalLineTo(4f)
                 verticalLineTo(8f)
                 horizontalLineToRelative(16f)
                 verticalLineTo(18f)
                 close()
                 moveTo(18f, 17f)
                 horizontalLineToRelative(-6f)
                 verticalLineToRelative(-2f)
                 horizontalLineToRelative(6f)
                 verticalLineTo(17f)
                 close()
                 moveTo(7.5f, 17f)
                 lineToRelative(-1.41f, -1.41f)
                 lineTo(8.67f, 13f)
                 lineToRelative(-2.59f, -2.59f)
                 lineTo(7.5f, 9f)
                 lineToRelative(4f, 4f)
                 lineTo(7.5f, 17f)
                 close()
             }
        }.build().also { _terminal = it }

    private var _toggleOff: ImageVector? = null
    val ToggleOff: ImageVector
        get() = _toggleOff ?: ImageVector.Builder(
            name = "Outlined.ToggleOff",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(17f, 6f)
                 lineTo(7f, 6f)
                 curveToRelative(-3.31f, 0f, -6f, 2.69f, -6f, 6f)
                 reflectiveCurveToRelative(2.69f, 6f, 6f, 6f)
                 horizontalLineToRelative(10f)
                 curveToRelative(3.31f, 0f, 6f, -2.69f, 6f, -6f)
                 reflectiveCurveToRelative(-2.69f, -6f, -6f, -6f)
                 close()
                 moveTo(17f, 16f)
                 lineTo(7f, 16f)
                 curveToRelative(-2.21f, 0f, -4f, -1.79f, -4f, -4f)
                 reflectiveCurveToRelative(1.79f, -4f, 4f, -4f)
                 horizontalLineToRelative(10f)
                 curveToRelative(2.21f, 0f, 4f, 1.79f, 4f, 4f)
                 reflectiveCurveToRelative(-1.79f, 4f, -4f, 4f)
                 close()
                 moveTo(7f, 9f)
                 curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
                 reflectiveCurveToRelative(1.34f, 3f, 3f, 3f)
                 reflectiveCurveToRelative(3f, -1.34f, 3f, -3f)
                 reflectiveCurveToRelative(-1.34f, -3f, -3f, -3f)
                 close()
             }
        }.build().also { _toggleOff = it }

    private var _toggleOn: ImageVector? = null
    val ToggleOn: ImageVector
        get() = _toggleOn ?: ImageVector.Builder(
            name = "Outlined.ToggleOn",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(17f, 6f)
                 lineTo(7f, 6f)
                 curveToRelative(-3.31f, 0f, -6f, 2.69f, -6f, 6f)
                 reflectiveCurveToRelative(2.69f, 6f, 6f, 6f)
                 horizontalLineToRelative(10f)
                 curveToRelative(3.31f, 0f, 6f, -2.69f, 6f, -6f)
                 reflectiveCurveToRelative(-2.69f, -6f, -6f, -6f)
                 close()
                 moveTo(17f, 16f)
                 lineTo(7f, 16f)
                 curveToRelative(-2.21f, 0f, -4f, -1.79f, -4f, -4f)
                 reflectiveCurveToRelative(1.79f, -4f, 4f, -4f)
                 horizontalLineToRelative(10f)
                 curveToRelative(2.21f, 0f, 4f, 1.79f, 4f, 4f)
                 reflectiveCurveToRelative(-1.79f, 4f, -4f, 4f)
                 close()
                 moveTo(17f, 9f)
                 curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
                 reflectiveCurveToRelative(1.34f, 3f, 3f, 3f)
                 reflectiveCurveToRelative(3f, -1.34f, 3f, -3f)
                 reflectiveCurveToRelative(-1.34f, -3f, -3f, -3f)
                 close()
             }
        }.build().also { _toggleOn = it }

    private var _undo: ImageVector? = null
    val Undo: ImageVector
        get() = _undo ?: ImageVector.Builder(
            name = "AutoMirrored.Outlined.Undo",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(12.5f, 8f)
                 curveToRelative(-2.65f, 0f, -5.05f, 0.99f, -6.9f, 2.6f)
                 lineTo(2f, 7f)
                 verticalLineToRelative(9f)
                 horizontalLineToRelative(9f)
                 lineToRelative(-3.62f, -3.62f)
                 curveToRelative(1.39f, -1.16f, 3.16f, -1.88f, 5.12f, -1.88f)
                 curveToRelative(3.54f, 0f, 6.55f, 2.31f, 7.6f, 5.5f)
                 lineToRelative(2.37f, -0.78f)
                 curveTo(21.08f, 11.03f, 17.15f, 8f, 12.5f, 8f)
                 close()
             }
        }.build().also { _undo = it }

    private var _warning: ImageVector? = null
    val Warning: ImageVector
        get() = _warning ?: ImageVector.Builder(
            name = "Outlined.Warning",
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false,
        ).apply {
             path(
                 fill = SolidColor(Color.Black),
                 fillAlpha = 1f,
                 strokeAlpha = 1f,
                 strokeLineWidth = 1f,
                 strokeLineCap = StrokeCap.Butt,
                 strokeLineJoin = StrokeJoin.Bevel,
                 strokeLineMiter = 1f,
                 pathFillType = PathFillType.NonZero,
             ) {
                 moveTo(1f, 21f)
                 horizontalLineToRelative(22f)
                 lineTo(12f, 2f)
                 lineTo(1f, 21f)
                 close()
                 moveTo(13f, 18f)
                 horizontalLineToRelative(-2f)
                 verticalLineToRelative(-2f)
                 horizontalLineToRelative(2f)
                 verticalLineToRelative(2f)
                 close()
                 moveTo(13f, 14f)
                 horizontalLineToRelative(-2f)
                 verticalLineToRelative(-4f)
                 horizontalLineToRelative(2f)
                 verticalLineToRelative(4f)
                 close()
             }
        }.build().also { _warning = it }
}
