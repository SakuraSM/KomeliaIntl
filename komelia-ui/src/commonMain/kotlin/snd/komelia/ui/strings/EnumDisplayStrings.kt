package snd.komelia.ui.strings

import androidx.compose.runtime.Composable
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Converts serialized enum identifiers into user-facing labels without changing
 * the values persisted by settings and home filter repositories.
 */
fun enumDisplayResource(name: String): StringResource? = when (name) {
    "Book" -> Res.string.enum_book
    "Series" -> Res.string.enum_series
    "Custom" -> Res.string.enum_custom
    "OnDeck" -> Res.string.enum_on_deck
    "RecentlyAdded" -> Res.string.enum_recently_added
    "RecentlyUpdated" -> Res.string.enum_recently_updated
    "Any" -> Res.string.enum_any
    "All" -> Res.string.enum_all
    "Title" -> Res.string.enum_title
    "CreatedDate" -> Res.string.enum_created_date
    "SeriesTitle" -> Res.string.enum_series_title
    "PagesCount" -> Res.string.enum_pages_count
    "ReleaseDate" -> Res.string.enum_release_date
    "LastModified", "LastModifiedDate" -> Res.string.enum_last_modified
    "Number" -> Res.string.enum_number
    "ReadDate" -> Res.string.enum_read_date
    "Unsorted" -> Res.string.enum_unsorted
    "BookCount" -> Res.string.enum_book_count
    "AnyOf" -> Res.string.enum_any_of
    "AllOf" -> Res.string.enum_all_of
    "Author" -> Res.string.enum_author
    "Deleted" -> Res.string.enum_deleted
    "Library" -> Res.string.enum_library
    "MediaProfile" -> Res.string.enum_media_profile
    "MediaStatus" -> Res.string.enum_media_status
    "NumberSort" -> Res.string.enum_number_sort
    "Oneshot" -> Res.string.enum_oneshot
    "Poster" -> Res.string.enum_poster
    "ReadList" -> Res.string.enum_read_list
    "ReadStatus" -> Res.string.enum_read_status
    "Tag" -> Res.string.enum_tag
    "AgeRating" -> Res.string.enum_age_rating
    "Collection" -> Res.string.enum_collection
    "Complete" -> Res.string.enum_complete
    "Genre" -> Res.string.enum_genre
    "Language" -> Res.string.enum_language
    "Publisher" -> Res.string.enum_publisher
    "SharingLabel" -> Res.string.enum_sharing_label
    "Status" -> Res.string.enum_status
    "TitleSort" -> Res.string.enum_title_sort
    "Equals" -> Res.string.enum_equals
    "NotEquals" -> Res.string.enum_not_equals
    "IsNull" -> Res.string.enum_is_null
    "IsNotNull" -> Res.string.enum_is_not_null
    "True" -> Res.string.enum_true
    "False" -> Res.string.enum_false
    "Contains" -> Res.string.enum_contains
    "DoesNotContain" -> Res.string.enum_does_not_contain
    "BeginsWith" -> Res.string.enum_begins_with
    "DoesNotBeginWith" -> Res.string.enum_does_not_begin_with
    "EndsWith" -> Res.string.enum_ends_with
    "DoesNotEndWith" -> Res.string.enum_does_not_end_with
    "IsBefore" -> Res.string.enum_is_before
    "IsAfter" -> Res.string.enum_is_after
    "IsInLast" -> Res.string.enum_is_in_last
    "IsNotInLast" -> Res.string.enum_is_not_in_last
    "EqualTo" -> Res.string.enum_equal_to
    "NotEqualTo" -> Res.string.enum_not_equal_to
    "GreaterThan" -> Res.string.enum_greater_than
    "LessThan" -> Res.string.enum_less_than
    "ASC" -> Res.string.enum_ascending
    "DESC" -> Res.string.enum_descending
    "UNREAD" -> Res.string.enum_unread
    "IN_PROGRESS" -> Res.string.enum_in_progress
    "READ" -> Res.string.enum_read
    "ENDED" -> Res.string.enum_ended
    "ONGOING" -> Res.string.enum_ongoing
    "ABANDONED" -> Res.string.enum_abandoned
    "HIATUS" -> Res.string.enum_hiatus
    "READY" -> Res.string.enum_ready
    "UNKNOWN" -> Res.string.enum_unknown
    "ERROR" -> Res.string.enum_error
    "OUTDATED" -> Res.string.enum_outdated
    "UNSUPPORTED" -> Res.string.enum_unsupported
    "DIVINA" -> Res.string.enum_divina
    "GENERATED" -> Res.string.enum_generated
    "USER_UPLOADED" -> Res.string.enum_user_uploaded
    "VALUE" -> Res.string.enum_value_channel
    "RED" -> Res.string.enum_red_channel
    "GREEN" -> Res.string.enum_green_channel
    "BLUE" -> Res.string.enum_blue_channel
    "MANGA" -> Res.string.enum_manga
    "NOVEL" -> Res.string.enum_novel
    "COMIC" -> Res.string.enum_comic
    "WEBTOON" -> Res.string.enum_webtoon
    "EXACT" -> Res.string.enum_exact_match
    "CLOSEST_MATCH" -> Res.string.enum_closest_match
    "LEFT_TO_RIGHT" -> Res.string.enum_left_to_right
    "RIGHT_TO_LEFT" -> Res.string.enum_right_to_left
    "VERTICAL" -> Res.string.enum_vertical
    "COMIC_INFO" -> Res.string.enum_comic_info
    "DATABASE" -> Res.string.enum_database
    "API" -> Res.string.enum_api
    "WRITER" -> Res.string.enum_writer
    "PENCILLER" -> Res.string.enum_penciller
    "INKER" -> Res.string.enum_inker
    "COLORIST" -> Res.string.enum_colorist
    "LETTERER" -> Res.string.enum_letterer
    "COVER" -> Res.string.enum_cover_artist
    "EDITOR" -> Res.string.enum_editor
    "TRANSLATOR" -> Res.string.enum_translator
    "MANGA_DEX" -> Res.string.enum_manga_dex
    "ANILIST" -> Res.string.enum_anilist
    "KITSU" -> Res.string.enum_kitsu
    "AMAZON" -> Res.string.enum_amazon
    "ANIME_PLANET" -> Res.string.enum_anime_planet
    "BOOKWALKER_JP" -> Res.string.enum_bookwalker_jp
    "MANGA_UPDATES" -> Res.string.enum_manga_updates
    "NOVEL_UPDATES" -> Res.string.enum_novel_updates
    "EBOOK_JAPAN" -> Res.string.enum_ebook_japan
    "MY_ANIME_LIST" -> Res.string.enum_my_anime_list
    "CD_JAPAN" -> Res.string.enum_cd_japan
    "RAW" -> Res.string.enum_raw_link
    "ENGLISH_TL" -> Res.string.enum_english_translation
    else -> null
}

@Composable
fun localizedEnumLabel(value: Any?, fallback: String): String {
    val enumName = (value as? Enum<*>)?.name ?: return fallback
    if (fallback != enumName) return fallback
    return enumDisplayResource(enumName)?.let { stringResource(it) } ?: fallback
}
