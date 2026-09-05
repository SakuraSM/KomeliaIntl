import { KomeliaColors } from '../core/design/KomeliaDesign';
import { router } from '@kit.ArkUI';
import measure from '@ohos.measure';
import { BookCard, SeriesCard } from '../components/CoverCards';
import { EmptyState, ErrorState, LoadingState, SectionTitle } from '../components/CommonUi';
import { KomeliaGrid, KomeliaLayout, KomeliaLayoutSpec, WindowClass } from '../core/design/KomeliaDesign';
import { localizedString } from '../core/i18n/LocalizedResources';
import { bookDetailPage, seriesDetailPage } from '../core/navigation/CatalogNavigation';
import { hasHomeFeedItems, KomgaBook, KomgaSeries } from '../model/KomgaModels';
import { calculateHomeGroupOverflowLayout, defaultHomeGroups, filterHomeGroupsForNavigation, HomeGroup,
  HomeGroupOverflowLayout, homeGroupChipWidth, homeGroupLabel, homeMoreChipWidth, homeOverviewItemLimit,
  parseHomeGroups } from
  '../model/HomeGroupModels';
import { AppPreferences } from '../persistence/AppPreferences';
import { AppState } from '../state/AppState';
import { AvailableBookSource, LocalBookSort, availableBookPage } from '../model/LocalContentModels';

@Component
export struct HomePage {
  @ObjectLink state: AppState;
  @State areaWidth: number = 360;
  @StorageLink('gridCardWidth') cardWidth: number = KomeliaGrid.defaultCardWidth;
  @State groups: HomeGroup[] = defaultHomeGroups();
  @State activeGroupId: string = 'all';
  @State compact: boolean = true;
  @State availableGroupWidth: number = 336;
  @State groupSheetVisible: boolean = false;
  @State windowClass: WindowClass = WindowClass.COMPACT;

  private layout(): KomeliaLayoutSpec {
    return KomeliaLayout.spec(this.windowClass);
  }

  aboutToAppear(): void {
    this.restoreGroups();
  }

  private async restoreGroups(): Promise<void> {
    this.groups = parseHomeGroups(await AppPreferences.getString('homeGroups'));
    if (this.activeGroupId !== 'all' &&
      !this.groups.some((group: HomeGroup) => group.id === this.activeGroupId && group.enabled)) {
      this.activeGroupId = 'all';
    }
  }

  private navigationGroups(): HomeGroup[] {
    const counts = this.groups.map((group: HomeGroup) => this.groupCount(group));
    return filterHomeGroupsForNavigation(this.groups, counts);
  }

  private effectiveActiveGroupId(): string {
    if (this.activeGroupId === 'all') return 'all';
    return this.navigationGroups().some((group: HomeGroup) => group.id === this.activeGroupId) ?
      this.activeGroupId : 'all';
  }

  private visibleGroups(): HomeGroup[] {
    const enabled = this.navigationGroups();
    const layout = this.groupOverflowLayout(enabled);
    const result: HomeGroup[] = [];
    for (let index = 0; index < layout.visibleGroupIndices.length; index++) {
      result.push(enabled[layout.visibleGroupIndices[index]]);
    }
    return result;
  }

  private overflowGroups(): HomeGroup[] {
    const enabled = this.navigationGroups();
    const layout = this.groupOverflowLayout(enabled);
    const result: HomeGroup[] = [];
    for (let index = 0; index < layout.overflowGroupIndices.length; index++) {
      result.push(enabled[layout.overflowGroupIndices[index]]);
    }
    return result;
  }

  private chipTextWidth(label: ResourceStr, fontSize: number): number {
    return px2vp(measure.measureText({
      textContent: label,
      fontSize: fontSize,
      fontWeight: FontWeight.Bold
    }));
  }

  private groupOverflowLayout(enabled: HomeGroup[]): HomeGroupOverflowLayout {
    const groupWidths: number[] = [];
    for (let index = 0; index < enabled.length; index++) {
      groupWidths.push(homeGroupChipWidth(this.chipTextWidth(homeGroupLabel(enabled[index].id), 14)));
    }
    const allWidth = homeGroupChipWidth(this.chipTextWidth($r('app.string.overview'), 14));
    const moreWidth = homeMoreChipWidth(this.chipTextWidth($r('app.string.more'), 14));
    const activeGroupId = this.effectiveActiveGroupId();
    const activeIndex = activeGroupId === 'all' ? -1 :
      enabled.findIndex((group: HomeGroup) => group.id === activeGroupId);
    return calculateHomeGroupOverflowLayout(this.availableGroupWidth, allWidth, moreWidth,
      groupWidths, activeIndex, 8);
  }

  private groupCount(group: HomeGroup): number {
    return this.isSeriesGroup(group.id) ? this.seriesForGroup(group.id).length : this.booksForGroup(group.id).length;
  }

  private isSeriesGroup(id: string): boolean {
    return id === 'series' || id === 'updatedSeries';
  }

  private booksForGroup(id: string): KomgaBook[] {
    if (id === 'local' || id === 'downloads') return availableBookPage(this.state.availableBooks,
      id === 'local' ? AvailableBookSource.LOCAL : AvailableBookSource.DOWNLOADED, '', LocalBookSort.RECENTLY_ADDED)
      .content.map(value => value.book);
    if (id === 'keepReading') return this.state.homeFeed.keepReading;
    if (id === 'onDeck') return this.state.homeFeed.onDeck;
    if (id === 'recentlyReleased') return this.state.homeFeed.recentlyReleased;
    if (id === 'latest') return this.state.homeFeed.latestBooks;
    if (id === 'recentlyRead') return this.state.homeFeed.recentlyRead;
    return [];
  }

  private seriesForGroup(id: string): KomgaSeries[] {
    if (id === 'series') return this.state.homeFeed.recentlyAddedSeries;
    if (id === 'updatedSeries') return this.state.homeFeed.recentlyUpdatedSeries;
    return [];
  }

  @Builder
  private groupChip(id: string, label: ResourceStr) {
    Button() {
      Text(label)
        .fontSize(14)
        .fontWeight(this.effectiveActiveGroupId() === id ? FontWeight.Bold : FontWeight.Medium)
        .fontColor(this.effectiveActiveGroupId() === id ? $r('app.color.primary') :
          $r('app.color.on_surface_variant'))
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
    }
      .height(48)
      .borderRadius(16)
      .padding({ left: 16, right: 16 })
      .backgroundColor(this.effectiveActiveGroupId() === id ? $r('app.color.primary_container') :
        KomeliaColors.surfaceContainer())
      .onClick(() => this.activeGroupId = id)
  }

  @Builder
  private groupPicker() {
    Column({ space: 8 }) {
      Row() {
        Text($r('app.string.home_groups')).fontSize(20).fontWeight(FontWeight.Bold).layoutWeight(1)
        Button($r('app.string.done')).height(40).onClick(() => this.groupSheetVisible = false)
      }.height(56).width('100%')
      List({ space: 4 }) {
        ForEach(this.overflowGroups(), (group: HomeGroup) => {
          ListItem() {
            Row({ space: 12 }) {
              Text(this.effectiveActiveGroupId() === group.id ? '✓' : '')
                .width(24).fontSize(18).fontColor($r('app.color.primary'))
              Column({ space: 2 }) {
                Text(homeGroupLabel(group.id)).fontSize(16).fontWeight(FontWeight.Medium).width('100%')
                Text(localizedString($r('app.string.item_count'), this.groupCount(group))).fontSize(12)
                  .fontColor($r('app.color.on_surface_variant')).width('100%')
              }.layoutWeight(1).alignItems(HorizontalAlign.Start)
            }
            .width('100%').constraintSize({ minHeight: 56 }).padding({ left: 12, right: 12 })
            .borderRadius(14)
            .backgroundColor(this.effectiveActiveGroupId() === group.id ? $r('app.color.primary_container') :
              Color.Transparent)
            .onClick(() => {
              this.activeGroupId = group.id;
              this.groupSheetVisible = false;
            })
          }
        }, (group: HomeGroup) => group.id)
      }.layoutWeight(1).width('100%')
    }
    .padding({ left: 16, right: 16, bottom: 24 })
    .width('100%').height('100%').backgroundColor(KomeliaColors.surfaceHigh())
  }

  private gridTemplate(): string {
    return KomeliaGrid.template(this.gridColumnCount());
  }

  private gridColumnCount(): number {
    return KomeliaGrid.columnCount(this.areaWidth, this.windowClass, this.cardWidth);
  }

  private displayedBooks(group: HomeGroup): KomgaBook[] {
    return this.booksForGroup(group.id).slice(0,
      homeOverviewItemLimit(this.gridColumnCount(), this.effectiveActiveGroupId() === 'all'));
  }

  private displayedSeries(group: HomeGroup): KomgaSeries[] {
    return this.seriesForGroup(group.id).slice(0,
      homeOverviewItemLimit(this.gridColumnCount(), this.effectiveActiveGroupId() === 'all'));
  }

  @Builder
  private bookGrid(groupId: string, books: KomgaBook[]) {
    Grid() {
      ForEach(books, (book: KomgaBook, index: number) => {
        GridItem() {
          BookCard({
            book: book,
            supporting: true,
            compact: this.compact,
            onOpen: (id: string) => router.pushUrl({ url: bookDetailPage(book.oneshot, id), params: { bookId: id } })
          })
        }
        .id(`home_book_card_${groupId}_${index}`)
      }, (book: KomgaBook) => book.id)
    }
    .columnsTemplate(this.gridTemplate())
    .columnsGap(this.layout().gridSpacing)
    .rowsGap(this.layout().gridSpacing)
    .width('100%')
  }

  @Builder
  private seriesGrid(groupId: string, seriesValues: KomgaSeries[]) {
    Grid() {
      ForEach(seriesValues, (series: KomgaSeries, index: number) => {
        GridItem() {
          SeriesCard({
            series: series,
            compact: this.compact,
            onOpen: (id: string) => router.pushUrl({
              url: seriesDetailPage(series.oneshot, id), params: { seriesId: id }
            })
          })
        }
        .id(`home_series_card_${groupId}_${index}`)
      }, (series: KomgaSeries) => series.id)
    }
    .columnsTemplate(this.gridTemplate()).columnsGap(this.layout().gridSpacing)
    .rowsGap(this.layout().gridSpacing).width('100%')
  }

  @Builder
  private groupContent(group: HomeGroup) {
    if (this.isSeriesGroup(group.id)) {
      if (this.seriesForGroup(group.id).length === 0) {
        if (this.activeGroupId !== 'all') {
          SectionTitle(homeGroupLabel(group.id))
          EmptyState($r('app.string.empty'))
        }
      } else {
        SectionTitle(homeGroupLabel(group.id))
        this.seriesGrid(group.id, this.displayedSeries(group))
      }
    } else {
      if (this.booksForGroup(group.id).length === 0) {
        if (this.activeGroupId !== 'all') {
          SectionTitle(homeGroupLabel(group.id))
          EmptyState($r('app.string.empty'))
        }
      } else {
        SectionTitle(homeGroupLabel(group.id))
        this.bookGrid(group.id, this.displayedBooks(group))
      }
    }
  }

  build() {
    Column() {
      if (this.navigationGroups().length > 1) {
        Row({ space: 8 }) {
          this.groupChip('all', $r('app.string.overview'))
          ForEach(this.visibleGroups(), (group: HomeGroup) => {
            this.groupChip(group.id, homeGroupLabel(group.id))
          }, (group: HomeGroup) => group.id)
          if (this.overflowGroups().length > 0) {
            Button() {
              Row({ space: 8 }) {
                Image($r('app.media.home_more')).width(24).height(24).fillColor($r('app.color.primary'))
                Text($r('app.string.more')).fontSize(14).fontColor($r('app.color.on_surface_variant'))
              }
            }
            .height(48).borderRadius(16).backgroundColor(KomeliaColors.surfaceContainer())
            .padding({ left: 12, right: 12 })
            .onClick(() => this.groupSheetVisible = true)
          }
        }
        .width('100%').padding({
          left: this.layout().pageHorizontalPadding,
          right: this.layout().pageHorizontalPadding,
          bottom: this.layout().itemSpacing
        })
      }

      Scroll() {
        Column({ space: this.layout().gridSpacing }) {
          if (this.state.homeLoading && !hasHomeFeedItems(this.state.homeFeed)) {
            LoadingState()
          } else if (this.state.homeErrorMessage.length > 0 && !hasHomeFeedItems(this.state.homeFeed)) {
            ErrorState(this.state.homeErrorMessage, () => this.state.refreshHome())
          } else if (!hasHomeFeedItems(this.state.homeFeed)) {
            EmptyState($r('app.string.empty'))
          } else {
            ForEach(this.groups, (group: HomeGroup) => {
              if (group.enabled && (this.effectiveActiveGroupId() === 'all' ||
                this.effectiveActiveGroupId() === group.id)) {
                this.groupContent(group)
              }
            }, (group: HomeGroup) => group.id)
          }
        }
        .padding({
          left: this.layout().pageHorizontalPadding,
          right: this.layout().pageHorizontalPadding,
          top: this.layout().sectionSpacing,
          bottom: this.layout().gridBottomPadding
        })
        .width('100%')
      }
      .layoutWeight(1).width('100%').align(Alignment.Top).edgeEffect(EdgeEffect.Spring)
    }
    .width('100%').height('100%').id('home_page')
    .align(Alignment.Top).justifyContent(FlexAlign.Start)
    .bindSheet($$this.groupSheetVisible, this.groupPicker(), {
      height: SheetSize.MEDIUM,
      dragBar: true,
      showClose: false
    })
    .onAreaChange((_, area) => {
      const width = Number(area.width);
      const windowClass = KomeliaLayout.windowClass(width);
      this.windowClass = windowClass;
      this.areaWidth = width;
      this.compact = width < 600;
      this.availableGroupWidth = Math.max(0,
        width - KomeliaLayout.spec(windowClass).pageHorizontalPadding * 2);
    })
  }
}
