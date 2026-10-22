package snd.komelia.ui.series.mangabaka

import androidx.compose.runtime.Composable
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.mangabaka_link_buy
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.mangabaka_link_info
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.mangabaka_link_other
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.mangabaka_link_publisher
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.mangabaka_link_read
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.mangabaka_link_social
import org.jetbrains.compose.resources.stringResource
import snd.komf.api.mangabaka.MangaBakaLinkType

@Composable
internal fun mangaBakaLinkGroupName(type: MangaBakaLinkType): String = stringResource(
    when (type) {
        MangaBakaLinkType.RETAILER -> Res.string.mangabaka_link_buy
        MangaBakaLinkType.PUBLISHER -> Res.string.mangabaka_link_publisher
        MangaBakaLinkType.WEBPLATFORM -> Res.string.mangabaka_link_read
        MangaBakaLinkType.SOCIAL -> Res.string.mangabaka_link_social
        MangaBakaLinkType.NEWS, MangaBakaLinkType.INFO -> Res.string.mangabaka_link_info
        MangaBakaLinkType.PIRACY, MangaBakaLinkType.OTHER -> Res.string.mangabaka_link_other
    }
)
