package snd.komelia.ui.color.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.color_correction
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import snd.komelia.color.BookColorCorrectionMode
import snd.komelia.ui.platform.BackPressHandler
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.platform.PlatformTitleBar
import snd.komga.client.book.KomgaBookId
import kotlin.uuid.Uuid

class ColorCorrectionScreen(
    val bookId: KomgaBookId,
    val page: Int
) : Screen {
    private var editorId: String? = Uuid.random().toString()
    override val key: String
        get() = editorId ?: Uuid.random().toString().also { editorId = it }

    private companion object {
        private const val serialVersionUID = -4733174237648432562L
    }

    @OptIn(cafe.adriel.voyager.core.annotation.InternalVoyagerApi::class)
    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getCurvesViewModel(bookId, page) }
        LaunchedEffect(Unit) { vm.initialize() }
        val navigator = LocalNavigator.currentOrThrow

        val coroutineScope = rememberCoroutineScope()
        val onLeave: () -> Unit = {
            coroutineScope.launch {
                if (vm.state.value !is LoadState.Success || vm.onSave()) {
                    if (navigator.pop()) navigator.dispose(this@ColorCorrectionScreen)
                }
            }
        }
        BackPressHandler(onLeave)
        Column(Modifier.fillMaxSize().onKeyEvent {
            if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) {
                onLeave()
                true
            } else false
        }) {
            PlatformTitleBar {
                IconButton(
                    onClick = onLeave,
                    enabled = !vm.isSaving.collectAsState().value,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .height(48.dp)
                        .widthIn(min = 48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        stringResource(Res.string.navigation_back),
                    )
                }
                Spacer(Modifier.width(10.dp).align(Alignment.Start).nonInteractive())
                Text(
                    text = stringResource(Res.string.color_correction),
                    modifier = Modifier.heightIn(max = 32.dp).align(Alignment.Start).nonInteractive()
                )

            }
            when (val state = vm.state.collectAsState().value) {
                LoadState.Loading, LoadState.Uninitialized -> LoadingMaxSizeIndicator()
                is LoadState.Error -> ErrorContent(state.exception, onExit = onLeave)
                is LoadState.Success<Unit> -> {
                    val mode = vm.mode.collectAsState().value
                    BookColorCorrectionModeControl(mode, vm.isSaving.collectAsState().value, vm::onModeChange)
                    if (mode == BookColorCorrectionMode.CUSTOM) ColorCorrectionContent(
                    currentCurveType = vm.correctionType.collectAsState().value,
                    onCurveTypeChange = vm::onCurveTypeChange,
                    curvesState = vm.curvesState,
                    levelsState = vm.levelsState,
                    displayImage = vm.displayImage.collectAsState().value,
                    onImageMaxSizeChange = vm::onImageMaxSizeChange
                    ) else Box(Modifier.weight(1f).fillMaxSize().onSizeChanged(vm::onImageMaxSizeChange)) {
                        vm.displayImage.collectAsState().value?.let { bitmap ->
                            Image(bitmap, stringResource(Res.string.color_correction), Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}
