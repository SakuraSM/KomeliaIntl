package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.testTag

@Composable
fun SwitchWithLabel(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: @Composable () -> Unit,
    supportingText: @Composable () -> Unit = {},
    supportingTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    compactVisual: Boolean = false,
    indicatorOffsetY: Dp = 0.dp,
) {
    val contentColor = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = .4f)
    val actualSupportingTextColor = if (enabled) supportingTextColor else LocalContentColor.current.copy(alpha = .4f)
    val colors = SwitchDefaults.colors(
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        checkedThumbColor = Color.White
    )
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.heightIn(min = 48.dp).toggleable(
                value = checked,
                role = Role.Switch,
                enabled = enabled,
                onValueChange = onCheckedChange,
            )
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(contentPadding),
        ) {
            Column(Modifier.weight(1f)) {
                label()
                CompositionLocalProvider(LocalContentColor provides actualSupportingTextColor) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) { supportingText() }
                }
            }
            if (compactVisual) Box(Modifier.size(52.dp,48.dp).offset(x=3.dp,y=indicatorOffsetY),contentAlignment=Alignment.Center) {
                Box(Modifier.size(44.dp,24.dp).background(
                    if (checked) MaterialTheme.colorScheme.primary.copy(alpha=if(enabled)1f else .4f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=if(enabled).5f else .2f),
                    RoundedCornerShape(12.dp)).testTag("reader-switch-track")) {
                    Box(Modifier.align(Alignment.CenterStart).offset(x=if(checked)22.dp else 2.dp).size(20.dp)
                        .background(Color.White.copy(alpha=if(enabled)1f else .4f),CircleShape).testTag("reader-switch-thumb"))
                }
            } else Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                colors = colors,
            )
        }

    }
}
