package snd.komelia.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** App-owned geometry measured on the approved 390 × 844 reading designs. */
internal object ReadingDesign {
    val pageInset = 20.dp
    val browseInset = 14.dp
    val homeInset = 22.dp
    val sectionGap = 24.dp
    val coverGap = 10.dp
    val coverShape = RoundedCornerShape(8.dp)
    val desktopNavigationWidth = 237.dp
    val tabletNavigationWidth = 92.dp
    val desktopMasterWidth = 422.dp
    val tabletMasterWidth = 260.dp
    val navigationHeight = 70.dp
    val navigationInset = 14.dp
    val primaryHeaderHeight = 72.dp
    val toolbarHeight = 48.dp
    val sheetInset = 20.dp
    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}
