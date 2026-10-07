package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcTimerSand: ImageVector
    get() {
        if (_TimerSand != null) {
            return _TimerSand!!
        }
        _TimerSand = ImageVector.Builder(
            name = "TimerSand",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 2f)
                horizontalLineTo(18f)
                verticalLineTo(8f)
                horizontalLineTo(18f)
                verticalLineTo(8f)
                lineTo(14f, 12f)
                lineTo(18f, 16f)
                verticalLineTo(16f)
                horizontalLineTo(18f)
                verticalLineTo(22f)
                horizontalLineTo(6f)
                verticalLineTo(16f)
                horizontalLineTo(6f)
                verticalLineTo(16f)
                lineTo(10f, 12f)
                lineTo(6f, 8f)
                verticalLineTo(8f)
                horizontalLineTo(6f)
                verticalLineTo(2f)
                moveTo(16f, 16.5f)
                lineTo(12f, 12.5f)
                lineTo(8f, 16.5f)
                verticalLineTo(20f)
                horizontalLineTo(16f)
                verticalLineTo(16.5f)
                moveTo(12f, 11.5f)
                lineTo(16f, 7.5f)
                verticalLineTo(4f)
                horizontalLineTo(8f)
                verticalLineTo(7.5f)
                lineTo(12f, 11.5f)
                moveTo(10f, 6f)
                horizontalLineTo(14f)
                verticalLineTo(6.75f)
                lineTo(12f, 8.75f)
                lineTo(10f, 6.75f)
                verticalLineTo(6f)
                close()
            }
        }.build()

        return _TimerSand!!
    }

@Suppress("ObjectPropertyName")
private var _TimerSand: ImageVector? = null
