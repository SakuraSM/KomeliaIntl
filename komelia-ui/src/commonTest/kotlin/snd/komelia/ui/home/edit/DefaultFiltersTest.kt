package snd.komelia.ui.home.edit

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DefaultFiltersTest {
    @Test
    fun recognizesPersistedDefaultLabelsInSupportedLanguages() {
        listOf(
            "Keep reading",
            "继续阅读",
            "On deck",
            "待读",
            "Recently released books",
            "最近发布的书籍",
            "Recently added books",
            "最近添加的书籍",
            "Recently added series",
            "最近添加的系列",
            "Recently updated series",
            "最近更新的系列",
            "Recently read books",
            "最近阅读的书籍",
        ).forEach { label ->
            assertNotNull(defaultHomeFilterLabelResource(label), "Missing default label mapping for $label")
        }
    }

    @Test
    fun preservesUserDefinedLabels() {
        assertNull(defaultHomeFilterLabelResource("My favourites"))
    }
}
