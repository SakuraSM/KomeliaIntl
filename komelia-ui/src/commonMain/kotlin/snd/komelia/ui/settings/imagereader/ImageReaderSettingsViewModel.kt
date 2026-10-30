package snd.komelia.ui.settings.imagereader

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import snd.komelia.AppNotification
import snd.komelia.AppNotificationMessageKey
import snd.komelia.AppNotifications
import snd.komelia.image.KomeliaPanelDetector
import snd.komelia.image.KomeliaUpscaler
import snd.komelia.image.ReduceKernel
import snd.komelia.image.UpsamplingMode
import snd.komelia.image.availableReduceKernels
import snd.komelia.image.availableUpsamplingModes
import snd.komelia.onnxruntime.OnnxRuntime
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.ui.settings.imagereader.onnxruntime.OnnxRuntimeSettingsState
import snd.komelia.updates.OnnxModelDownloader
import snd.komelia.updates.OnnxRuntimeInstaller

class ImageReaderSettingsViewModel(
    private val settingsRepository: ImageReaderSettingsRepository,
    private val appNotifications: AppNotifications,
    private val onnxRuntimeInstaller: OnnxRuntimeInstaller?,
    private val onnxRuntime: OnnxRuntime?,
    private val upscaler: KomeliaUpscaler?,
    private val panelDetector: KomeliaPanelDetector?,
    private val onnxModelDownloader: OnnxModelDownloader?,
    private val coilMemoryCache: MemoryCache?,
    private val coilDiskCache: DiskCache?,
    private val readerDiskCache: DiskCache?,
    colorCorrectionDependencies: DefaultColorCorrectionDependencies,
    private val writeScope: CoroutineScope? = null,
) : ScreenModel {
    private val settingsScope get() = writeScope ?: screenModelScope
    private val hintPreference = ReaderModeHintPreference(settingsRepository, appNotifications, settingsScope)
    val defaultColorCorrection = DefaultColorCorrectionState(colorCorrectionDependencies, settingsScope)

    val onnxRuntimeSettingsState = OnnxRuntimeSettingsState(
        onnxRuntimeInstaller = onnxRuntimeInstaller,
        onnxModelDownloader = onnxModelDownloader,

        onnxRuntime = onnxRuntime,
        panelDetector = panelDetector,
        upscaler = upscaler,

        settingsRepository = settingsRepository,
        coroutineScope = settingsScope
    )

    val upsamplingMode = MutableStateFlow(UpsamplingMode.NEAREST)
    val downsamplingKernel = MutableStateFlow(ReduceKernel.NEAREST)
    val linearLightDownsampling = MutableStateFlow(false)
    val loadThumbnailsPreview = MutableStateFlow(false)
    val volumeKeysNavigation = MutableStateFlow(false)
    val readerModeHintsEnabled = hintPreference.enabled
    val isSavingReaderModeHints = hintPreference.isSaving
    val availableUpsamplingModes = availableUpsamplingModes()
    val availableDownsamplingKernels = availableReduceKernels()


    suspend fun initialize() {
        hintPreference.initialize()
        defaultColorCorrection.initialize()

        upsamplingMode.value = settingsRepository.getUpsamplingMode().first()
        downsamplingKernel.value = settingsRepository.getDownsamplingKernel().first()
        linearLightDownsampling.value = settingsRepository.getLinearLightDownsampling().first()
        loadThumbnailsPreview.value = settingsRepository.getLoadThumbnailPreviews().first()
        volumeKeysNavigation.value = settingsRepository.getVolumeKeysNavigation().first()
        onnxRuntimeSettingsState.initialize()
    }

    fun onReaderModeHintsChange(enabled: Boolean) = hintPreference.change(enabled)

    fun onUpsamplingModeChange(mode: UpsamplingMode) {
        upsamplingMode.value = mode
        settingsScope.launch { settingsRepository.putUpsamplingMode(mode) }
    }

    fun onDownsamplingKernelChange(kernel: ReduceKernel) {
        downsamplingKernel.value = kernel
        settingsScope.launch { settingsRepository.putDownsamplingKernel(kernel) }
    }

    fun onLinearLightDownsamplingChange(linear: Boolean) {
        linearLightDownsampling.value = linear
        settingsScope.launch { settingsRepository.putLinearLightDownsampling(linear) }
    }

    fun onLoadThumbnailsPreviewChange(load: Boolean) {
        loadThumbnailsPreview.value = load
        settingsScope.launch { settingsRepository.putLoadThumbnailPreviews(load) }
    }

    fun onVolumeKeysNavigationChange(enable: Boolean) {
        volumeKeysNavigation.value = enable
        settingsScope.launch { settingsRepository.putVolumeKeysNavigation(enable) }
    }

    fun onClearImageCache() {
        clearImageCache()
        appNotifications.add(AppNotification.Success(AppNotificationMessageKey.IMAGE_CACHE_CLEARED))
    }

    private fun clearImageCache() {
        coilMemoryCache?.clear()
        coilDiskCache?.clear()
        readerDiskCache?.clear()
        upscaler?.clearCache()
    }
}
