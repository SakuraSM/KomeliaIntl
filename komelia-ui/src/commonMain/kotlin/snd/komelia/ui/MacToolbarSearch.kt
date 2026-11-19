package snd.komelia.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/** Hosts the active page's search without changing the page's query owner. */
@Stable
internal class MacToolbarSlotHost {
    private data class Entry(val owner: Any, val content: @Composable () -> Unit)
    private val entries = mutableStateListOf<Entry>()

    fun register(owner: Any, content: @Composable () -> Unit) {
        entries.removeAll { it.owner === owner }
        entries.add(Entry(owner, content))
    }

    fun unregister(owner: Any) {
        entries.removeAll { it.owner === owner }
    }

    @Composable
    fun Content(fallback: @Composable () -> Unit) {
        val active = entries.lastOrNull()
        if (active == null) fallback() else key(active.owner) { active.content() }
    }
}

internal val LocalMacToolbarSearch = staticCompositionLocalOf<MacToolbarSlotHost?> { null }
internal val LocalMacToolbarAccessory = staticCompositionLocalOf<MacToolbarSlotHost?> { null }

@Composable
internal fun RegisterMacToolbarSearch(content: @Composable () -> Unit) {
    RegisterMacToolbarSlot(LocalMacToolbarSearch.current, content)
}

@Composable
internal fun RegisterMacToolbarAccessory(content: @Composable () -> Unit) {
    RegisterMacToolbarSlot(LocalMacToolbarAccessory.current, content)
}

@Composable
private fun RegisterMacToolbarSlot(host: MacToolbarSlotHost?, content: @Composable () -> Unit) {
    host ?: return
    val owner = remember(host) { Any() }
    val latest by rememberUpdatedState(content)
    DisposableEffect(host, owner) {
        host.register(owner) { latest() }
        onDispose { host.unregister(owner) }
    }
}
