package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snd.komelia.settings.model.AppSurfaceStyle

/** Keeps the same scroll range while allowing content to move beneath floating chrome. */
@Composable
fun KomeliaPageScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (topContentInset: Dp) -> Unit,
) {
    val overlay = LocalSurfaceAppearance.current.style == AppSurfaceStyle.LIQUID_GLASS
    GlassBackdropScope {
        SubcomposeLayout(modifier.fillMaxSize()) { constraints ->
            val header = subcompose(PageSlot.Header) { KomeliaSafeTopBar(topBar) }.single().measure(
                constraints.copy(minHeight = 0),
            )
            val topInset = if (overlay) header.height.toDp() else 0.dp
            val footer = subcompose(PageSlot.Footer) { Box { bottomBar() } }.single().measure(
                constraints.copy(minHeight = 0),
            )
            val body = subcompose(PageSlot.Content) {
                CompositionLocalProvider(LocalPageTopInset provides 0.dp) {
                    Box(Modifier.fillMaxSize().glassBackdrop().background(MaterialTheme.colorScheme.background)) { content(topInset) }
                }
            }.single().measure(
                Constraints.fixed(
                    constraints.maxWidth,
                    if (overlay) constraints.maxHeight else (constraints.maxHeight - header.height).coerceAtLeast(0),
                ),
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                body.placeRelative(0, if (overlay) 0 else header.height)
                header.placeRelative(0, 0, zIndex = 1f)
                footer.placeRelative(0, constraints.maxHeight - footer.height, zIndex = 1f)
            }
        }
    }
}

private enum class PageSlot { Header, Content, Footer }
