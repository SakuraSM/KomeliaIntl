package snd.komelia.ui

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.navigator.Navigator

/** Destination navigation survives reader overlays, but not the owning login session. */
internal class SessionNavigators : ScreenModel {
    private val destinations = mutableMapOf<AppDestination, Navigator>()

    fun register(destination: AppDestination, navigator: Navigator) {
        destinations[destination] = navigator
    }

    @OptIn(cafe.adriel.voyager.core.annotation.InternalVoyagerApi::class)
    override fun onDispose() {
        destinations.values.forEach { navigator ->
            navigator.items.toList().forEach(navigator::dispose)
        }
        destinations.clear()
    }
}
