package snd.komelia.ui.home.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.home.HomeFilterData
import snd.komelia.ui.home.HomeScreen
import snd.komelia.ui.home.edit.view.FilterEditContent
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.settings.appearance.AppSettingsScreen
import kotlin.jvm.Transient

class FilterEditScreen(
    // FIXME should be serializable
    @Transient
    private val homeFilters: List<HomeFilterData>? = null
) : Screen {

    @Composable
    override fun Content() {
        FilterEditScreenContent(homeFilters = homeFilters, returnToHome = true)
    }
}

class HomeGroupsSettingsScreen : Screen {
    @Composable
    override fun Content() {
        FilterEditScreenContent(homeFilters = null, returnToHome = false)
    }
}

@Composable
private fun Screen.FilterEditScreenContent(
    homeFilters: List<HomeFilterData>?,
    returnToHome: Boolean,
) {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getFilterEditViewModel(homeFilters) }
        val navigator = LocalNavigator.currentOrThrow
        val coroutineScope = rememberCoroutineScope()
        val platform = LocalPlatform.current

        fun exitEditor() {
            when {
                returnToHome -> navigator.replaceAll(HomeScreen())
                platform == PlatformType.MOBILE -> navigator.pop()
                else -> navigator.replaceAll(AppSettingsScreen())
            }
        }

        LaunchedEffect(Unit) {
            vm.initialize()
        }

        when (val state = vm.state.collectAsState().value) {
            is LoadState.Error -> ErrorContent(
                exception = state.exception,
                onExit = ::exitEditor,
            )

            else -> FilterEditContent(
                filters = vm.filters.collectAsState().value,
                onFilterMove = vm::onFilterReorder,
                onExit = ::exitEditor,
                onEditEnd = {
                    coroutineScope.launch {
                        vm.onEditEnd()
                        exitEditor()
                    }
                },
                onFilterAdd = vm::onFilterAdd,
                onFilterRemove = vm::onFilterRemove,
                onFiltersReset = vm::onResetFiltersToDefault
            )
        }

        BackPressHandler(::exitEditor)
}
