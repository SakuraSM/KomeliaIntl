package snd.komelia.ui.reader.image.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import snd.komelia.settings.model.PageDisplayLayout
import snd.komelia.settings.model.ReaderType
import snd.komelia.ui.reader.image.continuous.ContinuousReaderState
import snd.komelia.ui.reader.image.paged.PagedReaderState
import snd.komelia.ui.reader.image.panels.PanelsReaderState

@Composable
internal fun CompactReaderModes(
    type: ReaderType,
    onType: (ReaderType) -> Unit,
    onPagedLayout: ((PageDisplayLayout) -> Unit)?,
    paged: PagedReaderState,
    continuous: ContinuousReaderState,
    panels: PanelsReaderState?,
) {
    val layout = paged.layout.collectAsState().value
    val direction = if(type==ReaderType.PANELS)panels?.readingDirection?.collectAsState()?.value
        else paged.readingDirection.collectAsState().value
    ReaderModePicker(
        selection=ReaderModeSelection(type,layout,direction,continuous.readingDirection.collectAsState().value,panels!=null),
        actions=ReaderModePickerActions(
            onPagedLayout={target ->
                if(onPagedLayout!=null)onPagedLayout(target)
                else {onType(ReaderType.PAGED);paged.onLayoutChange(target)}
            },
            onType=onType,
            onPagedDirection={target ->if(type==ReaderType.PANELS)panels?.onReadingDirectionChange(target)else paged.onReadingDirectionChange(target)},
            onContinuousDirection=continuous::onReadingDirectionChange,
        ),
    )
}
