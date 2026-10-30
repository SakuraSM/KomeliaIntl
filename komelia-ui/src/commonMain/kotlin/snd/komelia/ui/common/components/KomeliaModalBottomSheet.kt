package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KomeliaModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    val appearance = LocalSurfaceAppearance.current
    val glassStyle = appearance.style == AppSurfaceStyle.LIQUID_GLASS
    val colors = MaterialTheme.colorScheme
    val materialHandle: @Composable (() -> Unit)? = dragHandle?.let { handle ->
        {
            Box(
                modifier = Modifier.fillMaxWidth().then(
                    if (glassStyle) Modifier.komeliaChrome(KomeliaChromeStyle.sheetHandleShape) else Modifier,
                ),
                contentAlignment = Alignment.Center,
            ) {
                KomeliaChromeContent { handle() }
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = KomeliaChromeStyle.dialogShape,
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
            ) else Modifier,
        )) {
            KomeliaChromeContent { content() }
        }
    }
}
