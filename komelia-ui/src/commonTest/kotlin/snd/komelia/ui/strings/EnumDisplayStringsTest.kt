package snd.komelia.ui.strings

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EnumDisplayStringsTest {
    @Test
    fun homeFilterEnumIdentifiersHaveDisplayResources() {
        val identifiers = listOf(
            "Book", "Series", "Custom", "OnDeck", "RecentlyAdded", "RecentlyUpdated",
            "Any", "All", "ReadDate", "DESC", "ReadStatus", "Equals", "IN_PROGRESS",
            "AnyOf", "AllOf", "MediaProfile", "MediaStatus", "ReleaseDate", "TitleSort",
            "IsNull", "IsNotNull", "DoesNotContain", "IsInLast", "GreaterThan",
            "UNREAD", "READ", "ONGOING", "ERROR", "DIVINA", "USER_UPLOADED",
            "VALUE", "RED", "GREEN", "BLUE", "MANGA", "NOVEL", "COMIC", "WEBTOON",
            "EXACT", "CLOSEST_MATCH", "LEFT_TO_RIGHT", "RIGHT_TO_LEFT", "VERTICAL",
            "COMIC_INFO", "DATABASE", "API", "WRITER", "PENCILLER", "INKER", "COLORIST",
            "LETTERER", "COVER", "EDITOR", "TRANSLATOR", "MANGA_DEX", "ANILIST", "KITSU",
            "AMAZON", "ANIME_PLANET",
            "BOOKWALKER_JP", "MANGA_UPDATES", "NOVEL_UPDATES", "EBOOK_JAPAN",
            "MY_ANIME_LIST", "CD_JAPAN", "RAW", "ENGLISH_TL",
        )

        identifiers.forEach { identifier ->
            assertNotNull(enumDisplayResource(identifier), "Missing localized label for $identifier")
        }
    }

    @Test
    fun unknownIdentifiersKeepTheirOriginalLabel() {
        assertNull(enumDisplayResource("SERVER_DEFINED_VALUE"))
    }
}
