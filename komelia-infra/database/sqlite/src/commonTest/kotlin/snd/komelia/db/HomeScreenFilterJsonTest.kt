package snd.komelia.db

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import snd.komelia.homefilters.HomeScreenFilter
import snd.komelia.homefilters.BooksHomeScreenFilter
import snd.komelia.homefilters.KomgaSearchRequestSerializersModule
import snd.komelia.homefilters.SeriesHomeScreenFilter
import snd.komga.client.search.KomgaSearchCondition
import snd.komga.client.search.KomgaSearchOperator
import snd.komga.client.series.KomgaSeriesSearch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeScreenFilterJsonTest {
    @Test
    fun titleEqualityConditionsRoundTrip() {
        val filters: List<HomeScreenFilter> = listOf(
            SeriesHomeScreenFilter.CustomFilter(
                order = 0,
                label = "Empty group",
                filter = KomgaSearchCondition.Title(KomgaSearchOperator.Is("no-match")),
            ),
            BooksHomeScreenFilter.CustomFilter(
                order = 1,
                label = "Excluded title",
                filter = KomgaSearchCondition.Title(KomgaSearchOperator.IsNot("hidden")),
            ),
        )

        assertRoundTrip(filters)
    }

    @Test
    fun concreteTitleOperatorRemainsCompatible() {
        val filters: List<HomeScreenFilter> = listOf(
            SeriesHomeScreenFilter.CustomFilter(
                order = 0,
                label = "Contains title",
                filter = KomgaSearchCondition.Title(KomgaSearchOperator.Contains("match")),
            ),
        )

        assertRoundTrip(filters)
    }

    @Test
    fun networkTitleEqualityKeepsPrimitivePayload() {
        val json = Json {
            serializersModule = KomgaSearchRequestSerializersModule
        }
        val payload = json.encodeToString(
            KomgaSeriesSearch(
                condition = KomgaSearchCondition.Title(KomgaSearchOperator.Is("no-match")),
            ),
        )

        assertTrue(payload.contains("\"value\":\"no-match\""))
        assertFalse(payload.contains("kotlin.String"))
    }

    private fun assertRoundTrip(filters: List<HomeScreenFilter>) {
        val encoded = JsonDbDefault.encodeToString(filters)
        val decoded = JsonDbDefault.decodeFromString<List<HomeScreenFilter>>(encoded)

        assertEquals(filters, decoded)
    }
}
