package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.ui.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KomeliaModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
    dragHandle: @Composable (() -> Unit)? = {
        Box(Modifier.padding(top = 10.dp, bottom = 8.dp).size(34.dp, 4.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f), RoundedCornerShape(4.dp)))
    },
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    val appearance = LocalSurfaceAppearance.current
    val glassStyle = appearance.style == AppSurfaceStyle.LIQUID_GLASS
    val colors = MaterialTheme.colorScheme
    val panelColors = if (glassStyle) Theme.valueOf(appearance.theme.name).colorScheme else colors
    val materialHandle: @Composable (() -> Unit)? = dragHandle?.let { handle ->
        {
            Box(
                modifier = Modifier.fillMaxWidth().then(
                    if (glassStyle) Modifier.komeliaChrome(KomeliaChromeStyle.sheetHandleShape,
                        tintOpacity = KomeliaChromeStyle.panelTintOpacity) else Modifier,
                ),
                contentAlignment = Alignment.Center,
            ) {
                MaterialTheme(colorScheme = panelColors) { handle() }
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = KomeliaChromeStyle.sheetHandleShape,
        dragHandle = materialHandle,
        scrimColor = scrimColor,
        // Editing can pause capture before a new sheet has any backdrop frame. The native sheet
        // still needs its own base; the glass effect can draw over it when sampling is available.
        containerColor = if (glassStyle) glassTintColor(appearance.theme, colors) else containerColor,
        contentColor = colors.onSurface,
    ) {
        // The native sheet translates its content after applying its outer modifier. Keep material
        // inside that translation so its clip and backdrop move with the actual sheet.
        Column(Modifier.fillMaxWidth().then(
            if (glassStyle) Modifier.komeliaChrome(
                if (dragHandle == null) KomeliaChromeStyle.dialogShape else KomeliaChromeStyle.sheetBodyShape,
                tintOpacity = KomeliaChromeStyle.panelTintOpacity,
            ) else Modifier,
        )) {
            MaterialTheme(colorScheme = panelColors) { content() }
        }
    }
}
