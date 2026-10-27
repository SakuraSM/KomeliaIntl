package snd.komelia.ui.common.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

object AppSliderDefaults {
    @Composable
    fun Thumb() {
        Box(Modifier.size(22.dp).shadow(2.dp, CircleShape).background(MaterialTheme.colorScheme.onSurface, CircleShape))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Track(state: SliderState) {
        val active = MaterialTheme.colorScheme.primary
        val inactive = MaterialTheme.colorScheme.outlineVariant
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            val start = if (rtl) size.width else 0f
            val end = if (rtl) 0f else size.width
            val span = state.valueRange.endInclusive - state.valueRange.start
            val fraction = if (span > 0f) ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f) else 0f
            val y = size.height / 2
            drawLine(inactive, Offset(start, y), Offset(end, y), size.height, StrokeCap.Round)
            if (fraction > 0f) {
                drawLine(active, Offset(start, y), Offset(start + (end - start) * fraction, y), size.height, StrokeCap.Round)
            }
        }
    }

    @Composable
    fun colors(
        thumbColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
        activeTrackColor: Color = MaterialTheme.colorScheme.tertiary,
        activeTickColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
        inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceBright,
        inactiveTickColor: Color = MaterialTheme.colorScheme.surfaceVariant,
        disabledThumbColor: Color = Color.Unspecified,
        disabledActiveTrackColor: Color = Color.Unspecified,
        disabledActiveTickColor: Color = Color.Unspecified,
        disabledInactiveTrackColor: Color = Color.Unspecified,
        disabledInactiveTickColor: Color = Color.Unspecified
    ) = SliderDefaults.colors(

        thumbColor = thumbColor,
        activeTrackColor = activeTrackColor,
        activeTickColor = activeTickColor,
        inactiveTrackColor = inactiveTrackColor,
        inactiveTickColor = inactiveTickColor,
        disabledThumbColor = disabledThumbColor,
        disabledActiveTrackColor = disabledActiveTrackColor,
        disabledActiveTickColor = disabledActiveTickColor,
        disabledInactiveTrackColor = disabledInactiveTrackColor,
        disabledInactiveTickColor = disabledInactiveTickColor
    )
}
