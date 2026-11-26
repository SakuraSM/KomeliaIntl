package snd.komelia.ui.color

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import snd.komelia.AppNotifications
import snd.komelia.color.ColorChannel
import snd.komelia.color.CurvePoint
import snd.komelia.color.CurvePointType
import snd.komelia.color.Histogram
import snd.komelia.color.HistogramPaths
import snd.komelia.color.repository.BookColorCorrectionRepository
import snd.komelia.color.repository.ColorCurvePresetRepository
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.color.view.ColorCurvesContent
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass
import snd.komga.client.book.KomgaBookId
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurvesKeyboardTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun focusedCurvePassesEscapeAndOnlyConsumesDeleteWithASelection() {
        lateinit var state: CurvesState
        var canvasPresses = 0
        var escapes = 0
        var unhandledDeletes = 0
        compose.setContent {
            val scope = rememberCoroutineScope()
            state = remember {
                CurvesState(
                    AppNotifications(),
                    unusedRepository<ColorCurvePresetRepository>(),
                    MutableStateFlow(Histogram(byteArrayOf())),
                    scope,
                    KomgaBookId("synthetic"),
                    unusedRepository<BookColorCorrectionRepository>(),
                )
            }
            MaterialTheme {
                CompositionLocalProvider(
                    LocalWindowWidth provides WindowSizeClass.EXPANDED,
                    LocalPlatform provides PlatformType.DESKTOP,
                ) {
                    Box(Modifier.size(850.dp, 520.dp).onKeyEvent { event ->
                        when {
                            event.key == Key.Escape && event.type == KeyEventType.KeyDown -> {
                                escapes++
                                true
                            }
                            event.key == Key.Delete && event.type == KeyEventType.KeyUp -> {
                                unhandledDeletes++
                                true
                            }
                            else -> false
                        }
                    }) {
                        ColorCurvesContent(
                            curvePathData = CurveDrawData.EMPTY,
                            histogramPathData = HistogramPaths(null, null, null, null),
                            selectedChannel = ColorChannel.VALUE,
                            onChannelChange = {},
                            onChannelReset = {},
                            onAllChannelsReset = {},
                            selectedPoint = null,
                            currentPointOffset = null,
                            onPointChange = { _, _ -> },
                            pointType = CurvePointType.SMOOTH,
                            onPointTypeChange = {},
                            pointerIcon = PointerIcon.Crosshair,
                            onKeyEvent = state::onKeyEvent,
                            onPointerEvent = { if (it.type == PointerEventType.Press) canvasPresses++ },
                            onCanvasSizeChange = {},
                            onDensityChange = {},
                            curvePointerPosition = Offset.Unspecified,
                            presetsState = state.presetsState,
                        )
                    }
                }
            }
        }

        compose.onRoot().performTouchInput { click(center) }
        val focusedCurve = compose.onNode(isFocused())
        focusedCurve.performKeyInput {
            pressKey(Key.Escape)
            pressKey(Key.Delete)
        }
        compose.runOnIdle {
            assertEquals(1, canvasPresses)
            assertEquals(1, escapes)
            assertEquals(1, unhandledDeletes)
            state.colorCurve.addPoint(1, CurvePoint(.5f, .5f, CurvePointType.SMOOTH))
            state.selectedPoint.value = SelectedPoint(index = 1, isMoving = false, isRemoved = false)
        }

        focusedCurve.performKeyInput { pressKey(Key.Delete) }
        compose.runOnIdle {
            assertEquals(1, unhandledDeletes)
            assertEquals(2, state.colorCurve.points.value.size)
            assertNull(state.selectedPoint.value)
        }
    }

    private inline fun <reified T> unusedRepository(): T = T::class.java.cast(
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            error("Curve keyboard test unexpectedly called ${method.name}")
        }
    )
}
