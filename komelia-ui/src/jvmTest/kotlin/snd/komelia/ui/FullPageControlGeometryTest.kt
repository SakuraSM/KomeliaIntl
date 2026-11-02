package snd.komelia.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import snd.komelia.ui.strings.AppStrings
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.local.LocalContent
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.home.LocalHomeBookSort
import snd.komelia.offline.local.AvailableBookSource
import snd.komelia.ui.common.components.LocalPageTopInset
import snd.komelia.ui.home.HeroReadingControls
import snd.komelia.ui.common.components.FilterDropdownChoice
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.common.components.PageSizeSelectionDropdown
import snd.komelia.ui.common.components.SwitchWithLabel
import androidx.compose.material3.Text
import snd.komelia.ui.reader.image.settings.ReaderModePickerActions
import snd.komelia.ui.reader.image.settings.ReaderModePicker
import snd.komelia.ui.reader.image.settings.ReaderModeSelection
import snd.komelia.ui.reader.image.settings.ImageReaderToolbar
import snd.komelia.ui.reader.image.settings.ImageReaderProgressBadge
import snd.komelia.settings.model.ContinuousReadingDirection
import snd.komelia.settings.model.PageDisplayLayout
import snd.komelia.settings.model.PagedReadingDirection
import snd.komelia.settings.model.ReaderType
import snd.komelia.ui.search.SearchToolBar
import snd.komelia.ui.search.SearchViewModel.SearchResultsTab
import snd.komelia.ui.book.ReadingActionFooter
import snd.komelia.ui.book.ReadingBookHeader
import snd.komelia.ui.book.BookToolBar
import snd.komelia.ui.common.cards.BookDetailedListCard
import snd.komelia.ui.common.components.LocalSurfaceAppearance
import snd.komelia.ui.common.components.SurfaceAppearance
import snd.komelia.ui.home.homeReadingBook
import snd.komelia.ui.library.LibraryLayoutOptions
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass
import snd.komelia.ui.settings.MobileReadingSettingsHome
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.sse.KomgaEvent
import kotlin.math.abs
import kotlin.test.assertTrue

/** Bounds are app-owned dp, with OS regions removed. Targets come from the approved PNGs. */
class FullPageControlGeometryTest {
    @get:Rule val compose = createComposeRule()
    private data class ReferenceRect(val x: Float, val y: Float, val width: Float, val height: Float)
    private data class FooterCase(val page: String, val width: Int, val height: Int,
        val window: WindowSizeClass, val text: Boolean, val inset: Int, val theme: AppTheme,
        val paint: ReferenceRect, val trackY: Float, val trackHeight: Float)

    @Test fun allReadingActionVariantsMatchTheirReferenceAndKeepTheFullTouchArea() {
        val cases = listOf(
            FooterCase("04",390,844,WindowSizeClass.COMPACT,true,0,AppTheme.DARK,ReferenceRect(20.1f,773.5f,350.2f,47.6f),747.4f,6.4f),
            FooterCase("13",390,844,WindowSizeClass.COMPACT,true,0,AppTheme.LIGHT,ReferenceRect(20.1f,773.5f,350.2f,47.6f),747.4f,6.4f),
            FooterCase("05",390,796,WindowSizeClass.COMPACT,false,0,AppTheme.DARK,ReferenceRect(22.9f,744f,344.3f,41.7f),721.1f,6.9f),
            FooterCase("15",482,1194,WindowSizeClass.EXPANDED,false,27,AppTheme.DARK,ReferenceRect(41.1f,1072.3f,398.7f,52.5f),1044.5f,9.5f),
            FooterCase("16",781,1024,WindowSizeClass.FULL,true,30,AppTheme.DARK,ReferenceRect(453.7f,911.7f,269.2f,61f),935.9f,11.6f),
        )
        val current = mutableStateOf(cases.first())
        var reads = 0
        compose.setContent {
            val scenario = current.value
            GeometryScope(scenario.window, scenario.theme) {
                val typography = when(scenario.window) {
                    WindowSizeClass.FULL -> KomeliaDesktopReadingTypography
                    WindowSizeClass.EXPANDED -> KomeliaTabletReadingTypography
                    else -> KomeliaTypography
                }
                MaterialTheme(typography = typography) {
                    Box(Modifier.requiredSize(scenario.width.dp,scenario.height.dp).testTag("canvas")) {
                        ReadingActionFooter(fixture(scenario.text), { reads++ }, Modifier.align(Alignment.BottomCenter), scenario.inset.dp)
                    }
                }
            }
        }
        cases.forEach { scenario ->
            compose.runOnIdle { current.value = scenario }
            compose.waitForIdle()
            assertRect(scenario.page,"reading-action-paint","reading-primary-paint",scenario.paint)
            val hit = compose.onAllNodes(hasClickAction())[0].getUnclippedBoundsInRoot()
            assertTrue((hit.bottom-hit.top).value >= 48f,"${scenario.page}: reading hit area shrank: $hit")
            run {
                val track = compose.onNodeWithTag("reading-footer-progress").getUnclippedBoundsInRoot()
                val canvas = compose.onNodeWithTag("canvas").getUnclippedBoundsInRoot()
                near(scenario.page,"progress.y",scenario.trackY,(track.top-canvas.top).value)
                near(scenario.page,"progress.height",scenario.trackHeight,(track.bottom-track.top).value)
            }
            compose.onAllNodes(hasClickAction())[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        }
        compose.runOnIdle { assertTrue(reads == cases.size,"Every visual variant must still read normally") }
    }

    @Test fun textAndImageDetailsUseTheMeasuredCoverAndMetadataColumns() {
        val current = mutableStateOf(true)
        compose.setContent {
            GeometryScope(WindowSizeClass.COMPACT,AppTheme.DARK) {
                Box(Modifier.requiredSize(390.dp,500.dp).testTag("canvas").padding(horizontal = if(current.value)20.dp else 22.dp)) {
                    key(current.value) { ReadingBookHeader(fixture(current.value).copy(downloaded = true),onParentSeries = {},onDownload = {},onContents = {}) }
                }
            }
        }
        assertRect("04","cover","detail-cover",ReferenceRect(22f,0f,131f,196.5f))
        var metadata = compose.onNodeWithTag("detail-metadata").getUnclippedBoundsInRoot()
        var canvas = compose.onNodeWithTag("canvas").getUnclippedBoundsInRoot()
        near("04","metadata.x",169f,(metadata.left-canvas.left).value)
        compose.runOnIdle { current.value = false }
        compose.waitForIdle()
        assertRect("05","cover","detail-cover",ReferenceRect(22f,0f,143.5f,215.3f))
        metadata = compose.onNodeWithTag("detail-metadata").getUnclippedBoundsInRoot()
        canvas = compose.onNodeWithTag("canvas").getUnclippedBoundsInRoot()
        near("05","metadata.x",187.5f,(metadata.left-canvas.left).value)
        near("05","metadata.y",8f,(metadata.top-canvas.top).value)
    }

    @Test fun libraryDisplayChoicesMatchTheMeasuredPaintAndRetainFortyEightPixelTargets() {
        val list = mutableStateOf(false)
        compose.setContent {
            GeometryScope(WindowSizeClass.COMPACT,AppTheme.DARK) {
                Box(Modifier.requiredSize(390.dp,100.dp).testTag("canvas").padding(horizontal=22.dp)) {
                    LibraryLayoutOptions(list.value) { list.value = it }
                }
            }
        }
        assertRect("09","grid-paint","library-grid-choice-paint",ReferenceRect(103.8f,5f,129.8f,43f))
        assertRect("09","list-paint","library-list-choice-paint",ReferenceRect(239f,5f,129.8f,43f))
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            assertTrue(node.boundsInRoot.height >= 48f,"Layout selection lost its hit target")
        }
        compose.onNodeWithText(runBlocking { getString(Res.string.reading_list_view) }).performClick()
        compose.runOnIdle { assertTrue(list.value) }
    }

    @Test fun settingsRowsKeepTheSameColumnsAndRhythmAcrossThemes() {
        val theme = mutableStateOf(AppTheme.DARK)
        val titles = listOf(Res.string.reading_text_settings,Res.string.reading_image_settings,Res.string.settings_navigation_appearance,Res.string.settings_navigation_local_library,
            Res.string.reading_downloads_cache,Res.string.reading_account_connections,Res.string.reading_advanced)
            .map { runBlocking { getString(it) } }
        compose.setContent {
            GeometryScope(WindowSizeClass.COMPACT,theme.value) {
                Box(Modifier.requiredSize(390.dp,844.dp).testTag("canvas").padding(horizontal=14.dp)) {
                    MobileReadingSettingsHome(onNavigation={},offlineAvailable=true,textReaderAvailable=true)
                }
            }
        }
        listOf(AppTheme.DARK,AppTheme.LIGHT).forEach { mode ->
            compose.runOnIdle { theme.value = mode }
            compose.waitForIdle()
            val canvas = compose.onNodeWithTag("canvas").getUnclippedBoundsInRoot()
            val bounds = titles.map { compose.onNodeWithText(it,useUnmergedTree=true).getUnclippedBoundsInRoot() }
            bounds.forEachIndexed { index,rect ->
                near("08/$mode","row-$index.title.x",86f,(rect.left-canvas.left).value)
                if(index in listOf(1,2,4,6))near("08/$mode","row-$index.pitch",66f,(rect.top-bounds[index-1].top).value)
            }
        }
    }


    @Test fun everyCatalogRowVariantKeepsItsCoverAndTextColumns() {
        data class RowCase(val page:String,val compact:Boolean,val search:Boolean,val master:Boolean,val x:Float,val width:Float,val height:Float,val textX:Float)
        val cases=listOf(RowCase("06",false,false,false,22f,64f,96f,106f),
            RowCase("03/14",true,false,false,30f,64f,81f,114f),
            RowCase("15",true,false,true,22f,64f,81f,98f),
            RowCase("07",false,true,false,22f,76f,114f,118f))
        val current=mutableStateOf(cases.first())
        compose.setContent {
            GeometryScope(WindowSizeClass.COMPACT,AppTheme.DARK) {
                Box(Modifier.requiredSize(390.dp,200.dp).testTag("canvas").padding(horizontal=22.dp)) {
                    val row=current.value
                    key(row.page) { BookDetailedListCard(fixture(false),onClick={},compact=row.compact,searchResult=row.search,
                        compactCoverSlotWidth=if(row.master)64.dp else 80.dp,showTrailingAction=!row.master) }
                }
            }
        }
        cases.forEach { row ->
            compose.runOnIdle { current.value=row };compose.waitForIdle()
            assertRect(row.page,"catalog-cover","catalog-row-cover",ReferenceRect(row.x,8f,row.width,row.height))
            val rect=compose.onNodeWithTag("catalog-row-metadata",useUnmergedTree=true).getUnclippedBoundsInRoot()
            val canvas=compose.onNodeWithTag("canvas").getUnclippedBoundsInRoot()
            near(row.page,"catalog-metadata.x",row.textX,(rect.left-canvas.left).value)
        }
    }

    @Test fun localEntryUsesItsCompactHeaderAndDirectorySpacing() {
        compose.setContent {
            GeometryScope(WindowSizeClass.COMPACT,AppTheme.DARK) {
                CompositionLocalProvider(LocalPageTopInset provides 24.dp) {
                    Box(Modifier.requiredSize(390.dp,796.dp).testTag("canvas")) {
                        LocalContent(query="",onQueryChange={},selectedSource=AvailableBookSource.ALL,onSourceChange={},
                            selectedSort=LocalHomeBookSort.RECENTLY_ADDED,onSortChange={},books=emptyList(),currentPage=1,totalPages=1,
                            onPageChange={},cardWidth=160.dp,bookMenuActions=BookMenuActions({},{},{},{},{},{},{}),loading=false,
                            onBookClick={},onBookReadClick={_,_->})
                    }
                }
            }
        }
        val field=compose.onNode(hasSetTextAction()).getUnclippedBoundsInRoot()
        val canvas=compose.onNodeWithTag("canvas").getUnclippedBoundsInRoot()
        near("06","search.x",14.2f,(field.left-canvas.left).value)
        near("06","search.y",72f,(field.top-canvas.top).value)
        near("06","search.width",362f,(field.right-field.left).value)
        near("06","search.height",48f,(field.bottom-field.top).value)
        val icon=compose.onNodeWithTag("local-directory-icon",useUnmergedTree=true).getUnclippedBoundsInRoot()
        near("06","directory-icon.x",22f,(icon.left-canvas.left).value)
        near("06","directory-icon.width",32f,(icon.right-icon.left).value)
        val text=compose.onNodeWithText(runBlocking{getString(Res.string.settings_navigation_local_library)},useUnmergedTree=true).getUnclippedBoundsInRoot()
        near("06","directory-text.x",74.5f,(text.left-canvas.left).value)
    }


    @Test fun searchResultTabsUseMeasuredPaintAndStillSwitchResultTypes() {
        var selected:SearchResultsTab?=null
        compose.setContent {
            GeometryScope(WindowSizeClass.COMPACT,AppTheme.DARK) {
                Box(Modifier.requiredSize(390.dp,80.dp).testTag("canvas")) {
                    SearchToolBar(SearchResultsTab.SERIES,{selected=it},Modifier.fillMaxWidth().padding(horizontal=16.dp),showAll=true)
                }
            }
        }
        listOf("all","series","books").forEachIndexed { index,id ->
            assertRect("07","$id-tab","search-result-$id-paint",ReferenceRect(16.5f+122f*index,12.2f,114.8f,33.4f))
        }
        compose.onNodeWithText(runBlocking{getString(Res.string.search_books_tab)}+" 0").performClick()
        compose.runOnIdle { assertTrue(selected==SearchResultsTab.BOOKS) }
    }

    @Test fun heroReadingControlsKeepTheCompactPaintAndTheNormalReadingCallback() {
        var reads=0
        compose.setContent {
            GeometryScope(WindowSizeClass.COMPACT,AppTheme.DARK) {
                Box(Modifier.requiredSize(192.dp,84.dp).testTag("canvas")) { HeroReadingControls(fixture(false),onRead={reads++}) }
            }
        }
        assertRect("01","hero-action","home-reading-action-paint",ReferenceRect(0f,48f,191.1f,36.6f))
        val action=compose.onAllNodes(hasClickAction())[0]
        val hit=action.getUnclippedBoundsInRoot()
        assertTrue((hit.bottom-hit.top).value>=48)
        action.performClick();compose.runOnIdle { assertTrue(reads==1) }
    }

    @Test fun imageReaderModesMatchTheReferenceWhileKeepingTheirRealModeActions() {
        var layout: PageDisplayLayout? = null
        var type: ReaderType? = null
        var direction: PagedReadingDirection? = null
        compose.setContent {
            GeometryScope(WindowSizeClass.COMPACT, AppTheme.DARK) {
                Box(Modifier.requiredSize(390.dp, 300.dp).testTag("canvas").padding(horizontal = 22.5.dp)) {
                    ReaderModePicker(
                        ReaderModeSelection(ReaderType.PAGED, PageDisplayLayout.SINGLE_PAGE,
                            PagedReadingDirection.RIGHT_TO_LEFT, ContinuousReadingDirection.entries.first(), true),
                        ReaderModePickerActions({ layout = it }, { type = it }, { direction = it }, {}),
                    )
                }
            }
        }
        listOf("single", "double", "continuous").forEachIndexed { index, mode ->
            assertRect("12", "$mode-paint", "reader-mode-$mode-paint", ReferenceRect(22.5f + 115f * index, 3f, 115f, 58f))
        }
        PagedReadingDirection.entries.forEachIndexed { index, value ->
            val tag = "reader-direction-${value.name.lowercase()}-paint"
            assertRect("12", "$value-paint", tag, ReferenceRect(22.5f + 176.5f * index, 167.5f, 168.5f, 43f))
            compose.onNodeWithTag(tag, useUnmergedTree = true).performClick()
            compose.runOnIdle { assertTrue(direction == value) }
        }
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach {
            assertTrue(it.boundsInRoot.height >= 48f, "Reader mode lost its touch area: ${it.boundsInRoot}")
        }
        compose.onNodeWithTag("reader-mode-double-paint").performClick()
        compose.runOnIdle { assertTrue(layout == PageDisplayLayout.DOUBLE_PAGES) }
        compose.onNodeWithTag("reader-mode-continuous-paint").performClick()
        compose.runOnIdle { assertTrue(type == ReaderType.CONTINUOUS) }
    }

    @Test fun compactFilterPaintKeepsAFullTouchAndKeyboardAnchor() {
        var selected = 1
        compose.setContent {
            GeometryScope(WindowSizeClass.COMPACT, AppTheme.DARK) {
                Box(Modifier.requiredSize(390.dp,100.dp).testTag("canvas").padding(start=104.dp,end=22.dp)) {
                    FilterDropdownChoice(LabeledEntry(1,"First"), listOf(LabeledEntry(1,"First"),LabeledEntry(2,"Second")),
                        {selected=it.value}, null, Modifier.fillMaxWidth(), paintedHeight=38.dp)
                }
            }
        }
        assertRect("09","filter-field","compact-dropdown-paint",ReferenceRect(104f,0f,264f,38f))
        val hit = compose.onNode(hasClickAction()).getUnclippedBoundsInRoot()
        assertTrue((hit.bottom-hit.top).value>=48f)
        compose.onNode(hasClickAction()).performClick()
        compose.onNodeWithText("Second").performClick()
        compose.runOnIdle { assertTrue(selected==2) }
    }

    @Test fun compactDetailToolbarUsesTheReferenceIconBoundsWithoutMovingTheTouchTargets() {
        val text = mutableStateOf(true)
        compose.setContent {
            GeometryScope(WindowSizeClass.COMPACT, AppTheme.DARK) {
                Box(Modifier.requiredSize(390.dp,48.dp).testTag("canvas")) {
                    BookToolBar(fixture(text.value),BookMenuActions({},{},{},{},{},{},{}),{},showTitle=false,onParentSeriesClick={})
                }
            }
        }
        listOf(true,false).forEach { value ->
            compose.runOnIdle { text.value=value };compose.waitForIdle()
            assertRect(if(value)"04"else"05","toolbar-back","detail-toolbar-back-paint",ReferenceRect(if(value)12f else 22f,10f,20f,20f))
            assertRect(if(value)"04"else"05","toolbar-more","detail-toolbar-more-paint",ReferenceRect(356f,10f,20f,20f))
            compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach {
                assertTrue(it.boundsInRoot.height>=48f)
            }
        }
    }

    @Test fun libraryPageSizeSelectorUsesTheFullMeasuredFieldWidthAndKeepsItsOptions() {
        var selected=20
        compose.setContent {
            GeometryScope(WindowSizeClass.COMPACT,AppTheme.DARK) {
                Box(Modifier.requiredSize(390.dp,100.dp).testTag("canvas").padding(start=282.dp,end=22.dp)) {
                    PageSizeSelectionDropdown(20,{selected=it},minWidth=86.dp,endPadding=0.dp)
                }
            }
        }
        assertRect("09","page-size","page-size-selection",ReferenceRect(282f,0f,86f,34f))
        compose.onNodeWithText("20").performClick();compose.onNodeWithText("50").performClick()
        compose.runOnIdle { assertTrue(selected==50) }
    }

    @Test fun parentSeriesLinksAcceptTapsAcrossFortyEightPixelsAroundTheirCompactPaint() {
        val text=mutableStateOf(true)
        var opens=0
        compose.setContent {
            GeometryScope(WindowSizeClass.COMPACT,AppTheme.DARK) {
                Box(Modifier.requiredSize(390.dp,500.dp)) {
                    key(text.value) { ReadingBookHeader(fixture(text.value).copy(seriesTitle="Measured series"),
                        onParentSeries={opens++},onDownload={}) }
                }
            }
        }
        listOf(true,false).forEachIndexed { index,value ->
            compose.runOnIdle { text.value=value };compose.waitForIdle()
            val label=if(value)runBlocking { getString(Res.string.reading_parent_series,"Measured series") } else "Measured series"
            val bounds=compose.onNode(hasClickAction() and hasText(label)).getUnclippedBoundsInRoot()
            compose.onRoot().performTouchInput {
                click(Offset((bounds.left.value+bounds.right.value)/2,(bounds.top.value+bounds.bottom.value)/2+21))
            }
            compose.runOnIdle { assertTrue(opens==index+1,"The compact parent-series paint lost its 48px touch area") }
        }
    }

    @Test fun compactReaderSwitchKeepsItsTrackAndThumbSizeInBothStates() {
        val checked=mutableStateOf(false)
        compose.setContent {
            GeometryScope(WindowSizeClass.COMPACT,AppTheme.DARK) {
                Box(Modifier.requiredSize(390.dp,100.dp).testTag("canvas").padding(horizontal=22.5.dp)) {
                    SwitchWithLabel(checked.value,{checked.value=it},label={Text("Volume keys")},compactVisual=true)
                }
            }
        }
        listOf(false,true).forEach { selected ->
            compose.runOnIdle { checked.value=selected };compose.waitForIdle()
            assertRect("12","switch-track","reader-switch-track",ReferenceRect(322.5f,12f,44f,24f))
            assertRect("12","switch-thumb","reader-switch-thumb",ReferenceRect(if(selected)344.5f else 324.5f,14f,20f,20f))
            val row=compose.onNode(isToggleable())
            val hit=row.getUnclippedBoundsInRoot();assertTrue((hit.bottom-hit.top).value>=48f)
            row.performClick();compose.runOnIdle { assertTrue(checked.value!=selected) }
        }
    }

    @Test fun readerDirectionLabelsRemainVisibleAtNarrowWidthAndLargeText() {
        compose.setContent {
            GeometryScope(WindowSizeClass.COMPACT,AppTheme.DARK) {
                CompositionLocalProvider(LocalDensity provides Density(1f,1.3f)) {
                    Box(Modifier.requiredSize(360.dp,500.dp).padding(horizontal=22.5.dp)) {
                        ReaderModePicker(ReaderModeSelection(ReaderType.PAGED,PageDisplayLayout.SINGLE_PAGE,
                            PagedReadingDirection.RIGHT_TO_LEFT,ContinuousReadingDirection.entries.first(),false),
                            ReaderModePickerActions({},{},{},{}))
                    }
                }
            }
        }
        PagedReadingDirection.entries.forEach { direction ->
            val label=runBlocking { getString(AppStrings.forReadingDirection(direction)) }
            val layouts=mutableListOf<TextLayoutResult>()
            val text=compose.onNodeWithText(label,useUnmergedTree=true)
            text.performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val bounds=text.getUnclippedBoundsInRoot()
            val paint=compose.onNodeWithTag("reader-direction-${direction.name.lowercase()}-paint",useUnmergedTree=true).getUnclippedBoundsInRoot()
            assertTrue(layouts.isNotEmpty())
            layouts.forEach { layout ->
                for(line in 0 until layout.lineCount) {
                    val left=bounds.left.value+layout.getLineLeft(line)
                    val right=bounds.left.value+layout.getLineRight(line)
                    val bottom=bounds.top.value+layout.getLineBottom(line)
                    assertTrue(left>=paint.left.value-2 && right<=paint.right.value+2 && bottom<=paint.bottom.value+2,
                        "Direction ink falls outside its button: $label, [$left,$right,$bottom], $paint")
                }
            }
        }
    }

    @Test fun imageReaderHeaderKeepsBothTitlesAndTheRealReadOnlyPageValue() {
        val page=mutableStateOf("24")
        compose.setContent {
            GeometryScope(WindowSizeClass.COMPACT,AppTheme.DARK) {
                Box(Modifier.requiredSize(390.dp,820.dp).testTag("canvas")) {
                    val book=fixture(false).let { it.copy(seriesTitle="Measured series",metadata=it.metadata.copy(title="Measured volume")) }
                    ImageReaderToolbar(book,{}, {})
                    ImageReaderProgressBadge(page.value,28,Modifier.align(Alignment.TopEnd).padding(top=58.dp,end=12.dp))
                }
            }
        }
        assertRect("12","toolbar-frame","image-reader-toolbar-paint",ReferenceRect(12f,0f,366f,44f))
        assertRect("12","page-badge","image-reader-page-badge",ReferenceRect(312f,58f,66f,26f))
        compose.onNodeWithText("Measured series",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("Measured volume",useUnmergedTree=true).assertIsDisplayed()
        val location=compose.onNodeWithText("24 / 28")
        location.assertHasNoClickAction()
        val text=location.getUnclippedBoundsInRoot()
        val canvas=compose.onNodeWithTag("canvas").getUnclippedBoundsInRoot()
        near("12","badge-text.center-x",345f,((text.left+text.right)/2-canvas.left).value)
        near("12","badge-text.top",61f,(text.top-canvas.top).value)
        compose.runOnIdle { page.value="25" }
        compose.onNodeWithText("25 / 28").assertIsDisplayed()
    }

    private fun fixture(text: Boolean): KomeliaBook = homeReadingBook("A short title").let {
        if(text)it.copy(media=it.media.copy(mediaType="application/epub+zip"))else it
    }

    @Composable private fun GeometryScope(window: WindowSizeClass,theme: AppTheme,content:@Composable ()->Unit) {
        val events = remember { MutableSharedFlow<KomgaEvent>() }
        val libraries = remember { MutableStateFlow<List<KomgaLibrary>>(emptyList()) }
        val authentication = remember {
            snd.komelia.KomgaAuthenticationState(
                MutableStateFlow(noApi<snd.komelia.komga.api.KomgaUserApi>()),
                MutableStateFlow(noApi<snd.komelia.komga.api.KomgaLibraryApi>()),
                MutableStateFlow(null), MutableStateFlow("https://fixture.invalid"))
        }
        CompositionLocalProvider(LocalDensity provides Density(1f),LocalPlatform provides PlatformType.MOBILE,
            LocalWindowWidth provides window,LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.MOBILE,window),
            LocalKomgaEvents provides events,LocalLibraries provides libraries,LocalBookDownloadEvents provides null,
            LocalKomgaState provides authentication, LocalOfflineMode provides remember { MutableStateFlow(false) },
            LocalSurfaceAppearance provides SurfaceAppearance(style=AppSurfaceStyle.LIQUID_GLASS,theme=theme)) {
            KomeliaTheme(theme) { content() }
        }
    }

    private inline fun <reified T> noApi(): T = java.lang.reflect.Proxy.newProxyInstance(
        T::class.java.classLoader,arrayOf(T::class.java)) { _, method, _ -> error("Geometry must not call ${method.name}") } as T

    private fun assertRect(page:String,id:String,tag:String,expected:ReferenceRect) {
        val canvas=compose.onNodeWithTag("canvas").getUnclippedBoundsInRoot()
        val rect=compose.onNodeWithTag(tag,useUnmergedTree=true).getUnclippedBoundsInRoot()
        near(page,"$id.x",expected.x,(rect.left-canvas.left).value)
        near(page,"$id.y",expected.y,(rect.top-canvas.top).value)
        near(page,"$id.width",expected.width,(rect.right-rect.left).value)
        near(page,"$id.height",expected.height,(rect.bottom-rect.top).value)
    }
    private fun near(page:String,control:String,expected:Float,actual:Float) {
        println("CONTROL_GEOMETRY page=$page control=$control expected=$expected actual=$actual delta=${abs(expected-actual)}")
        assertTrue(abs(expected-actual)<=2f,"$page/$control: expected $expected, got $actual")
    }
}
