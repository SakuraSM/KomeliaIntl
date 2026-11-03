package snd.komelia.ui.reader.image.settings

import snd.komelia.settings.model.PageDisplayLayout
import snd.komelia.settings.model.ReaderType

class ReaderModeActions(val selectType: (ReaderType) -> Unit, val selectPagedLayout: (PageDisplayLayout) -> Unit)
