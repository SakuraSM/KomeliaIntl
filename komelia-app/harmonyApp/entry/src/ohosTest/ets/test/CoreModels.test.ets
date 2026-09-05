import { describe, expect, it } from '@ohos/hypium';
import { AppBuildInfo } from '../../../main/ets/core/AppBuildInfo';
import { CoverCaptionVariant, KomeliaAboutLayout, KomeliaColors, KomeliaCoverCaption, KomeliaDetailCover,
  KomeliaDetailMetadata, KomeliaGrid, KomeliaLayout, KomeliaMotion, KomeliaSettingsField, WindowClass } from
  '../../../main/ets/core/design/KomeliaDesign';
import { preferredLanguageFor, preferredLanguageMatches, shouldApplyPreferredLanguageOnStartup } from
  '../../../main/ets/core/i18n/AppLocale';
import { preferredAppOrientation } from '../../../main/ets/core/window/AppWindowPolicy';
import { formatLocalizedTemplate } from '../../../main/ets/core/i18n/LocalizedResources';
import { optionalRouteText, requiredRouteId } from '../../../main/ets/core/navigation/RouteParams';
import { bookDetailPage, seriesDetailPage } from '../../../main/ets/core/navigation/CatalogNavigation';
import { progressOutboxOperation, ProgressOutboxOperation } from
  '../../../main/ets/data/local/DatabaseModels';
import { primaryNavigationDecision } from
  '../../../main/ets/core/navigation/PrimaryNavigationBehavior';
import { epubLocatorMapper, shouldUseLocalEpubPosition } from
  '../../../main/ets/domain/service/EpubLocatorMapper';
import { calculateEpubViewport } from '../../../main/ets/domain/service/EpubReaderLayout';
import { decodeEpubEntities, epubMarkupToSpeechText, epubSpeechArticleId, isSpeechArticleForBook,
  resourceIndexFromSpeechArticleId } from '../../../main/ets/domain/service/EpubReadAloudContent';
import { findImageContentRegion } from '../../../main/ets/domain/service/ImageBorderCropper';
import { image } from '@kit.ImageKit';
import { ColorCorrectionType, CurvePointType, colorCorrectionIsActive, createCurveLookupTable,
  createLevelsLookupTable, defaultBookColorCorrection, mapRgbBytes } from
  '../../../main/ets/model/ColorCorrectionModels';
import { isPdfBytes, isPdfMediaType } from '../../../main/ets/domain/service/PageMediaDetector';
import { isReaderKitSupportedProduct } from '../../../main/ets/domain/service/ReaderCapabilities';
import { decodePanelDetections, PanelRect, sortDetectedPanels,
  suppressDuplicatePanels } from '../../../main/ets/domain/service/MindSporePanelDetector';
import { shouldPreferNnrt } from '../../../main/ets/domain/service/MindSporeImageEnhancer';
import { buildSuperResolutionTiles } from '../../../main/ets/domain/service/MindSporeSuperResolution';
import { ConcurrentTaskGate, ReaderRequestToken } from '../../../main/ets/domain/service/ReaderRequestToken';
import { panelDetectionCacheKey, readerPageByteCacheKey, readerPageImageCacheKey,
  readerPrefetchPageNumbers } from
  '../../../main/ets/domain/service/ReaderPageCache';
import { LatestAsyncTask } from '../../../main/ets/domain/service/LatestAsyncTask';
import { ProgressSyncCoordinator } from '../../../main/ets/domain/service/ProgressSyncCoordinator';
import { RecentFailureBackoff, RetainedAsyncLruCache } from
  '../../../main/ets/domain/service/RetainedAsyncLruCache';
import { hasDownloadSpace, requiredDownloadBytes } from
  '../../../main/ets/domain/service/DownloadStoragePolicy';
import { BackgroundTaskLeaseCoordinator } from
  '../../../main/ets/domain/service/BackgroundTaskLeaseCoordinator';
import { networkRouteCandidates } from '../../../main/ets/domain/service/NetworkRouteResolver';
import { bookMediaTypeLabel, bookReadingSummary, groupBookAuthors, isBookCompleted,
  isLocalDownloadOutdated } from '../../../main/ets/model/BookPresentation';
import { seriesActionAvailability, seriesPublicationStatusKey, seriesPublicationStatusValues } from
  '../../../main/ets/model/SeriesPresentation';
import { PageDisplayLayout, PagedReadingDirection } from '../../../main/ets/model/ImageReaderModels';
import { buildImageReaderSpreads, spreadIndexForPage } from '../../../main/ets/model/ImageReaderSpreads';
import { emptyHomeFeed, HomeFeed, KomgaBook, KomgaBookPage, normalizeHomeFeed } from
  '../../../main/ets/model/KomgaModels';
import { KomgaAuthor, KomgaSeries } from '../../../main/ets/model/KomgaModels';
import { cachedSeriesComplete, cachedSeriesFilterSupported, cachedSeriesReadStatus,
  matchesCachedSeriesFilter } from
  '../../../main/ets/data/repository/OfflineSeriesFilter';
import { calculateHomeGroupOverflowLayout, defaultHomeGroups, filterHomeGroupsForNavigation,
  homeGroupChipWidth, homeMoreChipWidth, homeOverviewItemLimit, parseHomeGroups } from
  '../../../main/ets/model/HomeGroupModels';
import { window } from '@kit.ArkUI';
import { searchMetadataTagLimit, searchResultCardMinimumHeight, searchResultCoverHeight,
  searchResultCoverWidth, searchResultMinimumHeight, searchTypeBarHorizontalInset,
  summarizeSearchMetadata } from '../../../main/ets/model/SearchPresentation';
import { copyLibrarySeriesFilter, emptyLibrarySeriesFilter, LibraryMetadataFilterKind,
  libraryMetadataNavigationRequest, libraryScopeNavigationRequest, librarySectionVisibility,
  librarySeriesFilterCount, safeLibraryPageSizeIndex, safeSeriesSortIndex, sameAuthorFilter,
  toggleAuthorFilterValue, toggleFilterValue } from
  '../../../main/ets/model/LibraryPresentation';
import { authorsForRole, formatAlternativeTitles, formatAuthorsForRole, formatWebLinks,
  parseAlternativeTitles, parseWebLinks, splitMetadataValues } from '../../../main/ets/model/MetadataTextCodec';
import { formatDateTime, userRoleResource } from '../../../main/ets/model/UserPresentation';
import { defaultKomfNotificationContext, komfDefaultProcessingSettings, komfDefaultProcessingUpdate,
  komfDefaultProviderPatch, komfDefaultProviders, komfNotificationSettings, komfProcessingSettings,
  komfProcessingUpdate, komfRemoveLibraryProcessingUpdate, normalizeKomfMediaServer,
  komfNotificationTargetSummary, komfProviderGlobalPatch, komfProviderGlobalSettings } from
  '../../../main/ets/model/KomfModels';
import { KomfClient, komfMetadataPath, komfSeriesSearchPath, parseKomfSseBlock } from
  '../../../main/ets/network/KomfClient';
import { mediaStatusBookSearch, readStatusBookSearch, recentReleaseBookSearch } from
  '../../../main/ets/network/KomgaClient';
import { evaluateOriginPolicy, OriginPolicyError } from '../../../main/ets/network/OriginPolicy';

export default function coreModelsTest(): void {
  describe('retained async cover cache', () => {
    it('deduplicates concurrent loads and synchronously reuses retained values', 0, async () => {
      let loads = 0;
      const disposed: string[] = [];
      const cache = new RetainedAsyncLruCache<string>(2, (value: string): void => {
        disposed.push(value);
      });
      const loader = async (): Promise<string> => {
        loads += 1;
        return 'cover-a';
      };

      const values = await Promise.all([cache.acquire('a', loader), cache.acquire('a', loader)]);
      expect(loads).assertEqual(1);
      expect(values[0]).assertEqual('cover-a');
      expect(values[1]).assertEqual('cover-a');
      cache.release('a', values[0]);
      cache.release('a', values[1]);

      const retained = cache.acquireCached('a');
      expect(retained).assertEqual('cover-a');
      if (retained !== undefined) cache.release('a', retained);

      const coverB = await cache.acquire('b', async (): Promise<string> => 'cover-b');
      cache.release('b', coverB);
      const coverC = await cache.acquire('c', async (): Promise<string> => 'cover-c');
      cache.release('c', coverC);
      expect(cache.size()).assertEqual(2);
      expect(disposed.includes('cover-a')).assertTrue();
    });

    it('backs off failed thumbnails temporarily and retries after cooldown', 0, () => {
      const backoff = new RecentFailureBackoff(30000, 2);
      expect(backoff.shouldRetry('missing-cover', 1000)).assertTrue();
      backoff.markFailure('missing-cover', 1000);
      expect(backoff.shouldRetry('missing-cover', 30999)).assertFalse();
      expect(backoff.shouldRetry('missing-cover', 31000)).assertTrue();
    });

    it('evicts released images by byte budget as well as entry count', 0, async () => {
      const disposed: string[] = [];
      const cache = new RetainedAsyncLruCache<string>(8, (value: string): void => { disposed.push(value); },
        10, (value: string): number => value.length);
      const first = await cache.acquire('first', async (): Promise<string> => '123456');
      cache.release('first', first);
      const second = await cache.acquire('second', async (): Promise<string> => 'abcdef');
      cache.release('second', second);
      expect(cache.size()).assertEqual(1);
      expect(cache.weight()).assertEqual(6);
      expect(disposed.includes('123456')).assertTrue();
    });
  });

  describe('reader first-page priority', () => {
    it('prefetches next pages before one previous page and excludes the visible page', 0, () => {
      const pages = readerPrefetchPageNumbers([8, 9, 10, 11, 12], 2);
      expect(pages.length).assertEqual(3);
      expect(pages[0]).assertEqual(11);
      expect(pages[1]).assertEqual(9);
      expect(pages[2]).assertEqual(12);
      expect(pages.includes(10)).assertFalse();
    });

    it('separates cached panel order by reading direction', 0, () => {
      const left = panelDetectionCacheKey('page-key', PagedReadingDirection.LEFT_TO_RIGHT);
      const right = panelDetectionCacheKey('page-key', PagedReadingDirection.RIGHT_TO_LEFT);
      expect(left === right).assertFalse();
    });
  });

  describe('catalog detail routing', () => {
    it('routes oneshot series and books to one combined detail page', 0, () => {
      expect(seriesDetailPage(true)).assertEqual('pages/OneshotDetail');
      expect(bookDetailPage(true)).assertEqual('pages/OneshotDetail');
      expect(seriesDetailPage(false)).assertEqual('pages/SeriesDetail');
      expect(bookDetailPage(false)).assertEqual('pages/BookDetail');
    });
  });

  describe('series action parity', () => {
    it('exposes Android-equivalent online series actions by permission and read state', 0, () => {
      const partialAdmin = seriesActionAvailability(true, true, 10, 4, 5, true);
      expect(partialAdmin.analyze).assertTrue();
      expect(partialAdmin.refreshMetadata).assertTrue();
      expect(partialAdmin.addToCollection).assertTrue();
      expect(partialAdmin.markAsRead).assertTrue();
      expect(partialAdmin.markAsUnread).assertTrue();
      expect(partialAdmin.edit).assertTrue();
      expect(partialAdmin.komf).assertTrue();

      const readUser = seriesActionAvailability(true, false, 10, 10, 0, true);
      expect(readUser.analyze).assertFalse();
      expect(readUser.addToCollection).assertFalse();
      expect(readUser.markAsRead).assertFalse();
      expect(readUser.markAsUnread).assertTrue();

      const offline = seriesActionAvailability(false, true, 10, 4, 5, true);
      expect(offline.analyze).assertFalse();
      expect(offline.markAsRead).assertTrue();
      expect(offline.markAsUnread).assertTrue();
      expect(offline.komf).assertFalse();

      const withoutKomf = seriesActionAvailability(true, true, 10, 4, 5, false);
      expect(withoutKomf.analyze).assertTrue();
      expect(withoutKomf.edit).assertTrue();
      expect(withoutKomf.komf).assertFalse();
    });
  });

  describe('progress outbox operations', () => {
    it('preserves deletes and safely treats migrated or unknown values as updates', 0, () => {
      expect(progressOutboxOperation('DELETE')).assertEqual(ProgressOutboxOperation.DELETE);
      expect(progressOutboxOperation('UPDATE')).assertEqual(ProgressOutboxOperation.UPDATE);
      expect(progressOutboxOperation('')).assertEqual(ProgressOutboxOperation.UPDATE);
      expect(progressOutboxOperation('UNKNOWN')).assertEqual(ProgressOutboxOperation.UPDATE);
    });
  });

  describe('primary navigation behavior', () => {
    it('does not rebuild the current root destination when its tab is tapped again', 0, () => {
      const decision = primaryNavigationDecision(1, 1, false);
      expect(decision.updateDestination).assertFalse();
      expect(decision.returnToRoot).assertFalse();
    });

    it('changes destinations once and lets nested tabs return to the root shell', 0, () => {
      const rootSwitch = primaryNavigationDecision(1, 2, false);
      expect(rootSwitch.updateDestination).assertTrue();
      expect(rootSwitch.returnToRoot).assertFalse();

      const nestedCurrent = primaryNavigationDecision(1, 1, true);
      expect(nestedCurrent.updateDestination).assertFalse();
      expect(nestedCurrent.returnToRoot).assertTrue();

      const nestedSwitch = primaryNavigationDecision(1, 0, true);
      expect(nestedSwitch.updateDestination).assertTrue();
      expect(nestedSwitch.returnToRoot).assertTrue();
    });
  });

  describe('route parameter safety', () => {
    it('turns absent route values into safe defaults', 0, () => {
      expect(requiredRouteId(undefined)).assertEqual('');
      expect(optionalRouteText(undefined)).assertEqual('');
    });

    it('normalizes required identifiers without changing display text', 0, () => {
      expect(requiredRouteId('  book-42  ')).assertEqual('book-42');
      expect(optionalRouteText('  原始标题  ')).assertEqual('  原始标题  ');
    });
  });

  describe('localized resource formatting', () => {
    it('formats sequential numeric and string placeholders', 0, () => {
      expect(formatLocalizedTemplate('%d / %s 页', [12, '167'])).assertEqual('12 / 167 页');
      expect(formatLocalizedTemplate('请求失败（%d: %s）', [401, 'Unauthorized']))
        .assertEqual('请求失败（401: Unauthorized）');
    });

    it('supports positional and escaped placeholders', 0, () => {
      expect(formatLocalizedTemplate('%2$s · %1$d%%', [25, '进度'])).assertEqual('进度 · 25%');
      expect(formatLocalizedTemplate('%d 项', [])).assertEqual('%d 项');
    });
  });

  describe('application locale restart policy', () => {
    it('does not reapply an equivalent preferred locale', 0, () => {
      expect(preferredLanguageMatches('zh-Hans-CN', preferredLanguageFor('ZH_CN'))).assertTrue();
      expect(preferredLanguageMatches('en-US', preferredLanguageFor('EN'))).assertTrue();
      expect(preferredLanguageMatches('default', preferredLanguageFor('SYSTEM'))).assertTrue();
      expect(preferredLanguageMatches('en-US', preferredLanguageFor('ZH_CN'))).assertFalse();
    });

    it('does not loop on a resolved system locale during cold startup', 0, () => {
      expect(shouldApplyPreferredLanguageOnStartup('zh-Hans-CN', preferredLanguageFor('SYSTEM'))).assertFalse();
      expect(shouldApplyPreferredLanguageOnStartup('en-US', preferredLanguageFor('SYSTEM'))).assertFalse();
      expect(shouldApplyPreferredLanguageOnStartup('en-US', preferredLanguageFor('ZH_CN'))).assertTrue();
      expect(shouldApplyPreferredLanguageOnStartup('zh-Hans-CN', preferredLanguageFor('ZH_CN'))).assertFalse();
    });
  });

  describe('responsive layout and motion', () => {
    it('lets the system rotation policy honor the user rotation lock', 0, () => {
      expect(preferredAppOrientation()).assertEqual(window.Orientation.AUTO_ROTATION_RESTRICTED);
    });

    it('exposes one HarmonyOS runtime version and user agent', 0, () => {
      expect(AppBuildInfo.versionName).assertEqual('0.25.0');
      expect(AppBuildInfo.userAgent).assertEqual('Komelia-Harmony/0.25.0');
    });

    it('keeps the OLED surface hierarchy distinct from the dark palette', 0, () => {
      expect(KomeliaColors.oledSurface).assertEqual('#08080C');
      expect(KomeliaColors.oledSurfaceContainer).assertEqual('#12111A');
      expect(KomeliaColors.oledSurfaceContainerHigh).assertEqual('#191824');
      expect(KomeliaColors.oledSurfaceHigh).assertEqual('#000000');
    });

    it('keeps the About repository rows on the Android compact rhythm', 0, () => {
      expect(KomeliaAboutLayout.repositoryRowHeight).assertEqual(58);
      expect(KomeliaAboutLayout.sectionSpacing).assertEqual(20);
      expect(KomeliaAboutLayout.cardVerticalPadding).assertEqual(10);
    });

    it('maps all supported breakpoints', 0, () => {
      expect(KomeliaLayout.windowClass(360)).assertEqual(WindowClass.COMPACT);
      expect(KomeliaLayout.windowClass(600)).assertEqual(WindowClass.MEDIUM);
      expect(KomeliaLayout.windowClass(840)).assertEqual(WindowClass.EXPANDED);
      expect(KomeliaLayout.windowClass(1200)).assertEqual(WindowClass.FULL);
    });

    it('keeps Reader Kit viewport aligned with ArkUI margins and density', 0, () => {
      const compact = calculateEpubViewport(360, 800, 16, 3, 3.2, 1080, 2400);
      expect(compact.widthPixels).assertEqual(984);
      expect(compact.heightPixels).assertEqual(2400);
      expect(compact.scaledDensity).assertEqual(3.2);

      const fallback = calculateEpubViewport(0, 0, 24, 2, 0, 1200, 2000);
      expect(fallback.widthPixels).assertEqual(1104);
      expect(fallback.heightPixels).assertEqual(2000);
      expect(fallback.scaledDensity).assertEqual(1);
    });

    it('matches the Android layout specification at every breakpoint', 0, () => {
      const compact = KomeliaLayout.spec(WindowClass.COMPACT);
      expect(compact.pageHorizontalPadding).assertEqual(12);
      expect(compact.pageVerticalPadding).assertEqual(12);
      expect(compact.gridSpacing).assertEqual(12);
      expect(compact.sectionSpacing).assertEqual(16);
      expect(compact.minimumTouchTarget).assertEqual(48);

      const medium = KomeliaLayout.spec(WindowClass.MEDIUM);
      expect(medium.pageHorizontalPadding).assertEqual(16);
      expect(medium.pageVerticalPadding).assertEqual(16);
      expect(medium.sectionSpacing).assertEqual(20);

      const expanded = KomeliaLayout.spec(WindowClass.EXPANDED);
      expect(expanded.pageHorizontalPadding).assertEqual(24);
      expect(expanded.pageVerticalPadding).assertEqual(20);
      expect(expanded.sectionSpacing).assertEqual(24);

      const full = KomeliaLayout.spec(WindowClass.FULL, true);
      expect(full.pageVerticalPadding).assertEqual(24);
      expect(full.contentMaxWidth).assertEqual(1200);
      expect(full.minimumTouchTarget).assertEqual(40);
    });

    it('matches Android cover caption metrics without content-driven height changes', 0, () => {
      expect(KomeliaCoverCaption.height(CoverCaptionVariant.TITLE_ONLY, true)).assertEqual(48);
      expect(KomeliaCoverCaption.height(CoverCaptionVariant.TITLE_WITH_SUPPORTING, true)).assertEqual(68);
      expect(KomeliaCoverCaption.height(CoverCaptionVariant.TITLE_ONLY, false)).assertEqual(52);
      expect(KomeliaCoverCaption.height(CoverCaptionVariant.TITLE_WITH_SUPPORTING, false)).assertEqual(72);
      expect(KomeliaCoverCaption.titleFontSize(true)).assertEqual(13);
      expect(KomeliaCoverCaption.titleFontSize(false)).assertEqual(14);
      expect(KomeliaCoverCaption.titleLineHeight(true)).assertEqual(18);
      expect(KomeliaCoverCaption.supportingLineHeight(true)).assertEqual(16);
    });

    it('matches Android mobile grid density and adaptive card width semantics', 0, () => {
      expect(KomeliaGrid.columnCount(360, WindowClass.COMPACT, 150)).assertEqual(3);
      expect(KomeliaGrid.columnCount(600, WindowClass.MEDIUM, 350)).assertEqual(3);
      expect(KomeliaGrid.columnCount(840, WindowClass.EXPANDED, 240)).assertEqual(3);
      expect(KomeliaGrid.columnCount(1200, WindowClass.FULL, 240)).assertEqual(4);
      expect(KomeliaGrid.columnCount(1200, WindowClass.FULL, 150)).assertEqual(7);
      expect(KomeliaGrid.normalizedCardWidth(147)).assertEqual(150);
      expect(KomeliaGrid.normalizedCardWidth(356)).assertEqual(350);
      expect(KomeliaGrid.template(4)).assertEqual('1fr 1fr 1fr 1fr');
    });

    it('matches Android detail cover widths and aspect ratio across window classes', 0, () => {
      expect(KomeliaDetailCover.width(WindowClass.COMPACT)).assertEqual(116);
      expect(KomeliaDetailCover.width(WindowClass.MEDIUM)).assertEqual(116);
      expect(KomeliaDetailCover.width(WindowClass.EXPANDED)).assertEqual(220);
      expect(KomeliaDetailCover.width(WindowClass.FULL)).assertEqual(220);
      expect(KomeliaDetailCover.height(WindowClass.COMPACT)).assertEqual(165);
      expect(KomeliaDetailCover.height(WindowClass.EXPANDED)).assertEqual(313);
    });

    it('matches Android detail metadata rows and compact chips at every breakpoint', 0, () => {
      expect(KomeliaDetailMetadata.labelWidth(WindowClass.COMPACT)).assertEqual(96);
      expect(KomeliaDetailMetadata.labelWidth(WindowClass.MEDIUM)).assertEqual(96);
      expect(KomeliaDetailMetadata.labelWidth(WindowClass.EXPANDED)).assertEqual(120);
      expect(KomeliaDetailMetadata.labelWidth(WindowClass.FULL)).assertEqual(120);
      expect(KomeliaDetailMetadata.rowFontSize).assertEqual(14);
      expect(KomeliaDetailMetadata.rowLineHeight).assertEqual(20);
      expect(KomeliaDetailMetadata.chipFontSize).assertEqual(12);
      expect(KomeliaDetailMetadata.chipLineHeight).assertEqual(16);
      expect(KomeliaDetailMetadata.chipHorizontalPadding).assertEqual(10);
      expect(KomeliaDetailMetadata.chipVerticalPadding).assertEqual(5);
      expect(KomeliaDetailMetadata.chipRadius).assertEqual(12);
      expect(KomeliaDetailMetadata.chipSpacing).assertEqual(5);
    });

    it('matches the measured Android compact settings field geometry', 0, () => {
      expect(KomeliaSettingsField.width).assertEqual(250);
      expect(KomeliaSettingsField.height).assertEqual(62);
      expect(KomeliaSettingsField.selectHeight).assertEqual(32);
    });

    it('removes motion when requested', 0, () => {
      expect(KomeliaMotion.duration(KomeliaMotion.navigation, true)).assertEqual(0);
      expect(KomeliaMotion.duration(KomeliaMotion.navigation, false)).assertEqual(200);
    });
  });

  describe('EPUB locator mapping', () => {
    it('round trips Reader Kit position', 0, () => {
      const locator = epubLocatorMapper.toLocator(7, '#point(/1/4)', 'chapter-8.xhtml', '第八章');
      const position = epubLocatorMapper.fromLocator(locator);
      expect(position?.resourceIndex).assertEqual(7);
      expect(position?.domPos).assertEqual('#point(/1/4)');
    });

    it('keeps pending local progress ahead of a newer server timestamp', 0, () => {
      expect(shouldUseLocalEpubPosition({
        hasLocalPosition: true,
        localReadDate: '2026-08-20T00:00:00.000Z',
        hasPendingUpload: true,
        hasServerPosition: true,
        serverReadDate: '2026-08-21T00:00:00.000Z'
      })).assertTrue();
    });

    it('selects the newest synchronized EPUB position across devices', 0, () => {
      expect(shouldUseLocalEpubPosition({
        hasLocalPosition: true,
        localReadDate: '2026-08-20T00:00:00.000Z',
        hasPendingUpload: false,
        hasServerPosition: true,
        serverReadDate: '2026-08-21T00:00:00.000Z'
      })).assertFalse();
      expect(shouldUseLocalEpubPosition({
        hasLocalPosition: true,
        localReadDate: '2026-08-22T00:00:00.000Z',
        hasPendingUpload: false,
        hasServerPosition: true,
        serverReadDate: '2026-08-21T00:00:00.000Z'
      })).assertTrue();
    });

    it('uses server progress for legacy local positions without a timestamp', 0, () => {
      expect(shouldUseLocalEpubPosition({
        hasLocalPosition: true,
        localReadDate: '',
        hasPendingUpload: false,
        hasServerPosition: true,
        serverReadDate: '2026-08-21T00:00:00.000Z'
      })).assertFalse();
    });
  });

  describe('EPUB platform capability', () => {
    it('blocks Reader Kit pages on the emulator', 0, () => {
      expect(isReaderKitSupportedProduct('emulator')).assertFalse();
      expect(isReaderKitSupportedProduct('Mate 70 Pro')).assertTrue();
    });
  });

  describe('EPUB read-aloud content', () => {
    it('extracts readable text without scripts, styles, or ruby annotations', 0, () => {
      const markup = '<?xml version="1.0"?><html><head><style>p{color:red}</style></head><body>' +
        '<h1>第一章</h1><p>你好&nbsp;<ruby>世界<rt>せかい</rt></ruby>。</p>' +
        '<script>alert(1)</script><p>第二段<br/>换行</p></body></html>';
      expect(JSON.stringify(epubMarkupToSpeechText(markup)))
        .assertEqual(JSON.stringify('第一章\n你好 世界。\n第二段\n换行'));
    });

    it('decodes named and numeric entities while preserving unknown entities', 0, () => {
      expect(decodeEpubEntities('&ldquo;A&#x4E2D;&#25991;&rdquo; &unknown;'))
        .assertEqual('“A中文” &unknown;');
    });

    it('uses stable article identifiers for books and spine resources', 0, () => {
      const articleId = epubSpeechArticleId('book:with:separator', 12);
      expect(articleId).assertEqual('komelia-epub:19:book:with:separator:12');
      expect(isSpeechArticleForBook(articleId, 'book:with:separator')).assertTrue();
      expect(isSpeechArticleForBook(articleId, 'book')).assertFalse();
      expect(resourceIndexFromSpeechArticleId(articleId)).assertEqual(12);
      expect(resourceIndexFromSpeechArticleId('other:12')).assertUndefined();
    });
  });

  describe('book presentation', () => {
    it('treats a null read progress as incomplete', 0, () => {
      const book = JSON.parse('{"readProgress":null}') as KomgaBook;
      expect(isBookCompleted(book)).assertFalse();
    });

    it('shows completed state only when explicitly completed', 0, () => {
      const book = JSON.parse('{"readProgress":{"page":12,"completed":true,"readDate":"2026-08-23"}}') as KomgaBook;
      expect(isBookCompleted(book)).assertTrue();
    });

    it('matches Android read percentage and clamps invalid progress', 0, () => {
      const reading = JSON.parse('{"media":{"pagesCount":167},' +
        '"readProgress":{"page":12,"completed":false,"readDate":"2026-08-23"}}') as KomgaBook;
      expect(bookReadingSummary(reading)?.percentage).assertEqual(7);
      expect(bookReadingSummary(reading)?.pagesLeft).assertEqual(155);
      const overflow = JSON.parse('{"media":{"pagesCount":10},' +
        '"readProgress":{"page":12,"completed":false,"readDate":"2026-08-23"}}') as KomgaBook;
      expect(bookReadingSummary(overflow)?.percentage).assertEqual(100);
      expect(bookReadingSummary(overflow)?.pagesLeft).assertEqual(0);
    });

    it('maps file formats and detects a stale offline copy', 0, () => {
      expect(bookMediaTypeLabel('application/epub+zip')).assertEqual('EPUB');
      expect(bookMediaTypeLabel('application/vnd.comicbook-rar')).assertEqual('CBR / RAR');
      expect(bookMediaTypeLabel('image/jpeg')).assertEqual('image/jpeg');
      expect(isLocalDownloadOutdated('2026-08-23T10:00:00.000Z',
        new Date('2026-08-23T09:00:00.000Z').getTime())).assertTrue();
      expect(isLocalDownloadOutdated('2026-08-23T08:00:00.000Z',
        new Date('2026-08-23T09:00:00.000Z').getTime())).assertFalse();
    });

    it('groups every author role in Android display order without losing filter semantics', 0, () => {
      const authors: KomgaAuthor[] = [
        { name: '译者甲', role: 'translator' },
        { name: '作者甲', role: 'writer' },
        { name: '画师甲', role: 'penciller' },
        { name: '作者甲', role: 'writer' },
        { name: '顾问甲', role: 'consultant' }
      ];
      const groups = groupBookAuthors(authors);
      expect(groups.map((group) => group.role).join(','))
        .assertEqual('writer,penciller,translator,consultant');
      expect(groups[0].authors.length).assertEqual(1);
      expect(groups[0].authors[0].name).assertEqual('作者甲');
      expect(groups[2].authors[0].role).assertEqual('translator');
    });
  });

  describe('series presentation', () => {
    it('maps every Komga publication status without changing unknown values', 0, () => {
      expect(seriesPublicationStatusKey('ENDED')).assertEqual('ended');
      expect(seriesPublicationStatusKey('ongoing')).assertEqual('ongoing');
      expect(seriesPublicationStatusKey(' ABANDONED ')).assertEqual('abandoned');
      expect(seriesPublicationStatusKey('HIATUS')).assertEqual('hiatus');
      expect(seriesPublicationStatusKey('FUTURE_STATUS')).assertEqual('unknown');
      expect(seriesPublicationStatusValues('ONGOING').join(',')).assertEqual('ENDED,ONGOING,ABANDONED,HIATUS');
      expect(seriesPublicationStatusValues('FUTURE_STATUS').join(','))
        .assertEqual('FUTURE_STATUS,ENDED,ONGOING,ABANDONED,HIATUS');
    });
  });

  describe('Komga media analysis request', () => {
    it('matches the polymorphic JSON expected by Komga', 0, () => {
      const request = mediaStatusBookSearch(['ERROR', 'UNSUPPORTED']);
      expect(JSON.stringify(request)).assertEqual(
        '{"condition":{"type":"AllOfBook","allOf":[{"type":"MediaStatus","mediaStatus":' +
          '{"operator":"is","value":"ERROR"}},{"type":"MediaStatus","mediaStatus":' +
          '{"operator":"is","value":"UNSUPPORTED"}}]}}');
    });
  });

  describe('Android-compatible home groups', () => {
    it('keeps the seven server groups followed by local and downloads', 0, () => {
      const values = defaultHomeGroups();
      expect(values.map((item) => item.id).join(',')).assertEqual(
        'keepReading,onDeck,recentlyReleased,latest,series,updatedSeries,recentlyRead,local,downloads');
    });

    it('migrates the legacy three-group setting without losing disabled states', 0, () => {
      const values = parseHomeGroups(JSON.stringify([
        { id: 'series', label: 'series', enabled: true },
        { id: 'onDeck', label: 'onDeck', enabled: false },
        { id: 'latest', label: 'latest', enabled: true }
      ]));
      expect(values.map((item) => item.id).join(',')).assertEqual(
        'keepReading,onDeck,recentlyReleased,latest,series,updatedSeries,recentlyRead,local,downloads');
      expect(values.find((item) => item.id === 'onDeck')?.enabled).assertFalse();
      expect(values.find((item) => item.id === 'keepReading')?.enabled).assertTrue();
    });

    it('preserves custom order after the new group model is present', 0, () => {
      const values = parseHomeGroups(JSON.stringify([
        { id: 'recentlyRead', label: 'recentlyRead', enabled: false },
        { id: 'keepReading', label: 'keepReading', enabled: true }
      ]));
      expect(values[0].id).assertEqual('recentlyRead');
      expect(values[0].enabled).assertFalse();
      expect(values[1].id).assertEqual('keepReading');
      expect(values.length).assertEqual(9);
      expect(values[7].id).assertEqual('local');
      expect(values[8].id).assertEqual('downloads');
    });

    it('matches Komga read and release condition JSON', 0, () => {
      expect(JSON.stringify(readStatusBookSearch('IN_PROGRESS'))).assertEqual(
        '{"condition":{"type":"AllOfBook","allOf":[{"type":"ReadStatus","readStatus":' +
          '{"operator":"is","value":"IN_PROGRESS"}}]}}');
      expect(JSON.stringify(recentReleaseBookSearch())).assertEqual(
        '{"condition":{"type":"AllOfBook","allOf":[{"type":"ReleaseDate","releaseDate":' +
          '{"operator":"isInTheLast","duration":"PT720H"}}]}}');
    });

    it('normalizes legacy home caches and empty state', 0, () => {
      const legacy = JSON.parse('{"onDeck":[],"latest":[{"id":"legacy-book"}],"series":' +
        '[{"id":"legacy-series"}]}') as HomeFeed;
      const normalized = normalizeHomeFeed(legacy);
      expect(normalized.latestBooks[0].id).assertEqual('legacy-book');
      expect(normalized.recentlyAddedSeries[0].id).assertEqual('legacy-series');
      expect(normalized.keepReading.length).assertEqual(0);
      expect(emptyHomeFeed().recentlyRead.length).assertEqual(0);
    });

    it('shows every group only when all measured chips fit', 0, () => {
      const allFit = calculateHomeGroupOverflowLayout(500, 60, 70, [80, 90, 100], -1, 8);
      expect(allFit.visibleGroupIndices.join(',')).assertEqual('0,1,2');
      expect(allFit.overflowGroupIndices.length).assertEqual(0);
      const overflow = calculateHomeGroupOverflowLayout(260, 60, 70, [80, 90, 100], -1, 8);
      expect(overflow.visibleGroupIndices.join(',')).assertEqual('0');
      expect(overflow.overflowGroupIndices.join(',')).assertEqual('1,2');
    });

    it('promotes an active overflow group without changing overflow order', 0, () => {
      const result = calculateHomeGroupOverflowLayout(260, 60, 70, [80, 90, 100], 2, 8);
      expect(result.visibleGroupIndices.join(',')).assertEqual('2');
      expect(result.overflowGroupIndices.join(',')).assertEqual('0,1');
      const narrow = calculateHomeGroupOverflowLayout(120, 60, 70, [200], 0, 8);
      expect(narrow.visibleGroupIndices.length).assertEqual(0);
      expect(narrow.overflowGroupIndices.join(',')).assertEqual('0');
    });

    it('matches Android by hiding disabled and empty groups from home navigation', 0, () => {
      const groups = defaultHomeGroups();
      groups[2].enabled = false;
      const visible = filterHomeGroupsForNavigation(groups, [4, 0, 9, 3, 0, 2, 1]);
      expect(visible.map((group) => group.id).join(',')).assertEqual(
        'keepReading,latest,updatedSeries,recentlyRead');
      expect(filterHomeGroupsForNavigation(groups, []).length).assertEqual(0);
    });

    it('matches Android home chip padding and more-icon geometry', 0, () => {
      expect(homeGroupChipWidth(28)).assertEqual(60);
      expect(homeGroupChipWidth(56)).assertEqual(88);
      expect(homeMoreChipWidth(28)).assertEqual(84);
    });

    it('limits overview rendering to two rows while keeping a selected group complete', 0, () => {
      expect(homeOverviewItemLimit(3, true)).assertEqual(6);
      expect(homeOverviewItemLimit(5, true)).assertEqual(10);
      expect(homeOverviewItemLimit(3, false)).assertEqual(Number.MAX_SAFE_INTEGER);
    });
  });

  describe('Android-compatible search presentation', () => {
    it('keeps search result geometry aligned with Android', 0, () => {
      expect(searchResultCoverWidth(WindowClass.COMPACT)).assertEqual(96);
      expect(searchResultMinimumHeight(WindowClass.COMPACT)).assertEqual(161);
      expect(searchResultCoverWidth(WindowClass.EXPANDED)).assertEqual(130);
      expect(searchResultCoverHeight(WindowClass.COMPACT, 200)).assertEqual(137);
      expect(searchResultCoverHeight(WindowClass.MEDIUM, 200)).assertEqual(137);
      expect(searchResultCoverHeight(WindowClass.EXPANDED, 200)).assertEqual(168);
      expect(searchResultCoverHeight(WindowClass.EXPANDED, 220)).assertEqual(185);
      expect(searchTypeBarHorizontalInset(WindowClass.COMPACT)).assertEqual(24);
      expect(searchTypeBarHorizontalInset(WindowClass.MEDIUM)).assertEqual(32);
      expect(searchTypeBarHorizontalInset(WindowClass.EXPANDED)).assertEqual(48);
      expect(searchResultMinimumHeight(WindowClass.EXPANDED)).assertEqual(209);
      expect(searchResultCardMinimumHeight(WindowClass.EXPANDED, 200)).assertEqual(200);
      expect(searchResultCardMinimumHeight(WindowClass.EXPANDED, 220)).assertEqual(209);
    });

    it('limits, deduplicates and summarizes metadata tags by breakpoint', 0, () => {
      expect(searchMetadataTagLimit(WindowClass.COMPACT)).assertEqual(3);
      expect(searchMetadataTagLimit(WindowClass.MEDIUM)).assertEqual(5);
      expect(searchMetadataTagLimit(WindowClass.FULL)).assertEqual(8);
      const summary = summarizeSearchMetadata([' 轻小说 ', '冒险', '', '轻小说', '奇幻', '异世界'], 3);
      expect(summary.visible.join(',')).assertEqual('轻小说,冒险,奇幻');
      expect(summary.hiddenCount).assertEqual(1);
    });
  });

  describe('Android-compatible library toolbar', () => {
    it('hides redundant tabs when collections and read lists are empty', 0, () => {
      const onlySeries = librarySectionVisibility(0, 0);
      expect(onlySeries.showSeries).assertFalse();
      expect(onlySeries.showCollections).assertFalse();
      expect(onlySeries.showReadLists).assertFalse();
      const mixed = librarySectionVisibility(2, 0);
      expect(mixed.showSeries).assertTrue();
      expect(mixed.showCollections).assertTrue();
      expect(mixed.showReadLists).assertFalse();
    });

    it('falls back to Android-compatible page size and title sort', 0, () => {
      expect(safeLibraryPageSizeIndex(20)).assertEqual(0);
      expect(safeLibraryPageSizeIndex(37)).assertEqual(0);
      expect(safeSeriesSortIndex('metadata.titleSort,asc')).assertEqual(0);
      expect(safeSeriesSortIndex('unknown')).assertEqual(0);
    });

    it('keeps the advanced filter immutable and counts active constraints', 0, () => {
      const original = emptyLibrarySeriesFilter();
      original.readStatus = toggleFilterValue(original.readStatus, 'UNREAD');
      original.includeGenres = toggleFilterValue(original.includeGenres, 'Fantasy');
      original.complete = 'YES';
      const copy = copyLibrarySeriesFilter(original);
      copy.readStatus = toggleFilterValue(copy.readStatus, 'READ');
      expect(original.readStatus.join(',')).assertEqual('UNREAD');
      expect(copy.readStatus.join(',')).assertEqual('UNREAD,READ');
      expect(librarySeriesFilterCount(original)).assertEqual(3);
      expect(librarySeriesFilterCount(emptyLibrarySeriesFilter())).assertEqual(0);
    });

    it('builds scoped detail-chip navigation without sharing mutable filter state', 0, () => {
      const publisher = libraryMetadataNavigationRequest('library-1',
        LibraryMetadataFilterKind.PUBLISHER, 'Example Press');
      const writer = libraryMetadataNavigationRequest('library-1',
        LibraryMetadataFilterKind.AUTHOR, 'Author A', 'writer');
      expect(publisher.libraryId).assertEqual('library-1');
      expect(publisher.filter.publishers.join(',')).assertEqual('Example Press');
      expect(writer.filter.authors[0].name).assertEqual('Author A');
      expect(writer.filter.authors[0].role).assertEqual('writer');
      const scope = libraryScopeNavigationRequest('library-2');
      expect(scope.libraryId).assertEqual('library-2');
      expect(librarySeriesFilterCount(scope.filter)).assertEqual(0);
      publisher.filter.publishers[0] = 'Changed';
      expect(writer.filter.publishers.length).assertEqual(0);
    });

    it('distinguishes same-name authors by role', 0, () => {
      const writer: KomgaAuthor = { name: 'Same Name', role: 'writer' };
      const penciller: KomgaAuthor = { name: 'Same Name', role: 'penciller' };
      let authors = toggleAuthorFilterValue([], writer);
      authors = toggleAuthorFilterValue(authors, penciller);
      expect(authors.length).assertEqual(2);
      expect(sameAuthorFilter(authors[0], authors[1])).assertFalse();
      authors = toggleAuthorFilterValue(authors, writer);
      expect(authors.length).assertEqual(1);
      expect(authors[0].role).assertEqual('penciller');
    });

    it('applies cached series filters with the same include and exclude semantics', 0, () => {
      const series: KomgaSeries = {
        id: 'series-1', libraryId: 'library-1', name: 'Example', booksCount: 10,
        booksReadCount: 5, booksUnreadCount: 4, booksInProgressCount: 1, oneshot: false,
        metadata: {
          title: '异世界示例', titleSort: '异世界示例', summary: '', publisher: 'Example Press',
          language: 'zh', status: 'ENDED', ageRating: 13, genres: ['Fantasy', 'Adventure'],
          tags: ['Translated'], totalBookCount: 10
        },
        booksMetadata: {
          releaseDate: '2024-01-09', summary: '', summaryNumber: '', tags: ['Book tag'],
          authors: [{ name: 'Author A', role: 'writer' }]
        }
      };
      expect(cachedSeriesReadStatus(series)).assertEqual('IN_PROGRESS');
      expect(cachedSeriesComplete(series)).assertTrue();
      expect(matchesCachedSeriesFilter(series, {
        libraryId: 'library-1', readStatus: ['IN_PROGRESS'], publicationStatus: ['ENDED'],
        includeGenres: ['Fantasy'], includeTags: ['Translated'], inclusionMode: 'ALL',
        excludeGenres: ['Horror'], exclusionMode: 'ANY', publishers: ['Example Press'],
        languages: ['zh'], ageRatings: ['13'], authors: [{ name: 'Author A', role: 'writer' }],
        releaseYears: ['2024'],
        complete: 'YES', oneshot: 'NO'
      })).assertTrue();
      expect(matchesCachedSeriesFilter(series, {
        excludeGenres: ['Fantasy'], exclusionMode: 'ANY'
      })).assertFalse();
    });

    it('uses aggregated metadata when present and rejects only incomplete legacy caches', 0, () => {
      const enriched = JSON.parse('{"booksMetadata":{"authors":[],"tags":[],"summary":"",' +
        '"summaryNumber":"","releaseDate":"2026-01-01"}}') as KomgaSeries;
      expect(cachedSeriesFilterSupported({ readStatus: ['UNREAD'], languages: ['zh'] })).assertTrue();
      const author: KomgaAuthor = { name: 'Author', role: 'writer' };
      expect(cachedSeriesFilterSupported({ authors: [author] })).assertFalse();
      expect(cachedSeriesFilterSupported({ authors: [author] }, [enriched])).assertTrue();
      expect(cachedSeriesFilterSupported({ releaseYears: ['2026'] }, [enriched])).assertTrue();
      expect(cachedSeriesFilterSupported({ complete: 'YES' })).assertTrue();
    });
  });

  describe('reader page media detection', () => {
    it('routes PDF payloads to PDF Kit', 0, () => {
      const bytes = new ArrayBuffer(5);
      new Uint8Array(bytes).set([0x25, 0x50, 0x44, 0x46, 0x2D]);
      expect(isPdfBytes(bytes)).assertTrue();
      expect(isPdfMediaType('application/pdf')).assertTrue();
    });

    it('keeps image payloads on Image Kit', 0, () => {
      const bytes = new ArrayBuffer(4);
      new Uint8Array(bytes).set([0xFF, 0xD8, 0xFF, 0xE0]);
      expect(isPdfBytes(bytes)).assertFalse();
      expect(isPdfMediaType('image/jpeg')).assertFalse();
    });
  });

  describe('image reader spreads', () => {
    const portrait = (number: number): KomgaBookPage => ({
      number: number, fileName: `${number}.jpg`, mediaType: 'image/jpeg', width: 800, height: 1200
    });
    const landscape = (number: number): KomgaBookPage => ({
      number: number, fileName: `${number}.jpg`, mediaType: 'image/jpeg', width: 1600, height: 900
    });

    it('keeps adjacent and processed reader pages in distinct cache entries', 0, () => {
      expect(readerPageByteCacheKey(4, 'server:user', 'book-a', 28))
        .assertEqual(readerPageByteCacheKey(4, 'server:user', 'book-a', 28));
      expect(readerPageImageCacheKey({
        scope: 4, namespace: 'server:user', bookId: 'book-a', page: 28, cropBorders: false,
        colorCorrection: false, colorCorrectionRevision: 0, aiEnabled: false
      }) === readerPageImageCacheKey({
        scope: 4, namespace: 'server:user', bookId: 'book-a', page: 29, cropBorders: false,
        colorCorrection: false, colorCorrectionRevision: 0, aiEnabled: false
      })).assertFalse();
      expect(readerPageImageCacheKey({
        scope: 4, namespace: 'server:user', bookId: 'book-a', page: 28, cropBorders: false,
        colorCorrection: false, colorCorrectionRevision: 0, aiEnabled: false
      }) === readerPageImageCacheKey({
        scope: 4, namespace: 'server:user', bookId: 'book-a', page: 28, cropBorders: false,
        colorCorrection: true, colorCorrectionRevision: 2, aiEnabled: false
      })).assertFalse();
    });

    it('keeps the cover and landscape pages independent', 0, () => {
      const spreads = buildImageReaderSpreads([
        portrait(1), portrait(2), portrait(3), landscape(4), portrait(5), portrait(6)
      ], PageDisplayLayout.DOUBLE_PAGES, false);
      expect(spreads.map((spread) => spread.key).join('|')).assertEqual('1|2-3|4|5-6');
      expect(spreadIndexForPage(spreads, 2)).assertEqual(1);
    });

    it('supports no-cover and segment offsets without changing page order', 0, () => {
      const pages = [portrait(1), portrait(2), portrait(3), landscape(4), portrait(5), portrait(6), portrait(7)];
      const noCover = buildImageReaderSpreads(pages, PageDisplayLayout.DOUBLE_PAGES_NO_COVER, false);
      expect(noCover.map((spread) => spread.key).join('|')).assertEqual('1-2|3|4|5-6|7');
      const offset = buildImageReaderSpreads(pages, PageDisplayLayout.DOUBLE_PAGES_NO_COVER, true);
      expect(offset.map((spread) => spread.key).join('|')).assertEqual('1|2-3|4|5|6-7');
    });
  });

  describe('panel reader model contract', () => {
    const panel = (x: number, y: number, width: number, height: number,
      confidence: number = 0.9): PanelRect => ({
      x: x, y: y, width: width, height: height, confidence: confidence
    });

    it('keeps rows top-to-bottom and mirrors the order within a row', 0, () => {
      const source = [panel(10, 10, 40, 40), panel(60, 12, 40, 38), panel(8, 70, 92, 45)];
      const leftToRight = sortDetectedPanels(source, PagedReadingDirection.LEFT_TO_RIGHT);
      const rightToLeft = sortDetectedPanels(source, PagedReadingDirection.RIGHT_TO_LEFT);
      expect(leftToRight.map((item: PanelRect) => item.x).join(',')).assertEqual('10,60,8');
      expect(rightToLeft.map((item: PanelRect) => item.x).join(',')).assertEqual('60,10,8');
    });

    it('maps letterboxed model coordinates back to the source image', 0, () => {
      const output = new Float32Array([
        256, 128, 768, 896, 0.95, 0,
        10, 10, 20, 20, 0.99, 1,
        0, 0, 0, 0, 0.1, 0
      ]);
      const decoded = decodePanelDetections(output, 1000, 1500, 1024 / 1500, 170, 0);
      expect(decoded.length).assertEqual(1);
      expect(Math.round(decoded[0].x)).assertEqual(126);
      expect(Math.round(decoded[0].y)).assertEqual(188);
      expect(Math.round(decoded[0].width)).assertEqual(750);
      expect(Math.round(decoded[0].height)).assertEqual(1125);
    });

    it('decodes channels-first detector output without changing coordinates', 0, () => {
      const output = new Float32Array([
        256, 10,
        128, 10,
        768, 20,
        896, 20,
        0.95, 0.99,
        0, 1
      ]);
      const decoded = decodePanelDetections(output, 1000, 1500, 1024 / 1500, 170, 0, [1, 6, 2]);
      expect(decoded.length).assertEqual(1);
      expect(Math.round(decoded[0].x)).assertEqual(126);
      expect(Math.round(decoded[0].height)).assertEqual(1125);
    });

    it('suppresses overlapping duplicate detections by confidence', 0, () => {
      const result = suppressDuplicatePanels([
        panel(0, 0, 100, 100, 0.95), panel(4, 4, 98, 98, 0.8), panel(120, 0, 80, 100, 0.75)
      ]);
      expect(result.length).assertEqual(2);
      expect(result[0].confidence).assertEqual(0.95);
    });

    it('uses CPU directly on the emulator and keeps NNRT for real devices', 0, () => {
      expect(shouldPreferNnrt('emulator')).assertFalse();
      expect(shouldPreferNnrt('Mate 70 Pro')).assertTrue();
    });
  });

  describe('super-resolution tiling', () => {
    it('covers a page once with deterministic overlapped inputs', 0, () => {
      const tiles = buildSuperResolutionTiles(500, 300);
      expect(tiles.length).assertEqual(6);
      expect(tiles[0].coreX).assertEqual(0);
      expect(tiles[0].inputX).assertEqual(-16);
      expect(tiles[1].coreX).assertEqual(224);
      expect(tiles[2].coreWidth).assertEqual(52);
      expect(tiles[3].coreY).assertEqual(224);
      expect(tiles[3].coreHeight).assertEqual(76);
    });

    it('keeps tiny pages inside one padded model tile', 0, () => {
      const tiles = buildSuperResolutionTiles(32, 48);
      expect(tiles.length).assertEqual(1);
      expect(tiles[0].coreWidth).assertEqual(32);
      expect(tiles[0].coreHeight).assertEqual(48);
    });
  });

  describe('image reader border crop', () => {
    it('finds a protected content rectangle inside a uniform border', 0, () => {
      const width = 12;
      const height = 10;
      const bytes = new Uint8Array(width * height * 4);
      bytes.fill(255);
      for (let y = 3; y <= 6; y += 1) {
        for (let x = 4; x <= 7; x += 1) {
          const offset = (y * width + x) * 4;
          bytes[offset] = 0;
          bytes[offset + 1] = 0;
          bytes[offset + 2] = 0;
          bytes[offset + 3] = 255;
        }
      }
      const region = findImageContentRegion(bytes, width, height, width * 4,
        image.PixelMapFormat.RGBA_8888);
      expect(region?.x).assertEqual(2);
      expect(region?.y).assertEqual(1);
      expect(region?.width).assertEqual(8);
      expect(region?.height).assertEqual(8);
    });

    it('does not crop blank or already edge-to-edge pages', 0, () => {
      const blank = new Uint8Array(20 * 20 * 4);
      blank.fill(255);
      expect(findImageContentRegion(blank, 20, 20, 80, image.PixelMapFormat.RGBA_8888))
        .assertUndefined();
      blank[0] = 0;
      blank[1] = 0;
      blank[2] = 0;
      blank[3] = 255;
      expect(findImageContentRegion(blank, 20, 20, 80, image.PixelMapFormat.RGBA_8888))
        .assertUndefined();
    });
  });

  describe('image reader color correction', () => {
    it('keeps the Android identity settings inactive', 0, () => {
      const value = defaultBookColorCorrection();
      expect(colorCorrectionIsActive(value)).assertFalse();
      expect(createCurveLookupTable(value.curves.color)).assertUndefined();
      expect(createLevelsLookupTable(value.levels.color)).assertUndefined();
    });

    it('maps levels with the Android black, white, and gamma formula', 0, () => {
      const lut = createLevelsLookupTable({
        lowInput: 0.25, highInput: 0.75, lowOutput: 0, highOutput: 1, gamma: 1
      });
      expect(lut?.[0]).assertEqual(0);
      expect(lut?.[64]).assertEqual(0);
      expect(lut?.[128]).assertEqual(129);
      expect(lut?.[192]).assertEqual(255);
    });

    it('samples curves and applies value before individual RGB channels', 0, () => {
      const curve = createCurveLookupTable([
        { x: 0, y: 0, type: CurvePointType.SMOOTH },
        { x: 0.5, y: 0.75, type: CurvePointType.SMOOTH },
        { x: 1, y: 1, type: CurvePointType.SMOOTH }
      ]);
      expect((curve?.[128] || 0) > 180).assertTrue();
      const invert = new Uint8Array(256);
      const red = new Uint8Array(256);
      for (let index = 0; index < 256; index += 1) {
        invert[index] = 255 - index;
        red[index] = Math.round(index / 2);
      }
      const bytes = new Uint8Array([10, 20, 30, 255]);
      mapRgbBytes(bytes, 0, 1, 2, 4, { value: invert, red: red });
      expect(bytes[0]).assertEqual(123);
      expect(bytes[1]).assertEqual(235);
      expect(bytes[2]).assertEqual(225);
      expect(bytes[3]).assertEqual(255);
      const settings = defaultBookColorCorrection();
      settings.type = ColorCorrectionType.COLOR_CURVES;
      settings.curves.color = [
        { x: 0, y: 0, type: CurvePointType.SMOOTH },
        { x: 1, y: 0.8, type: CurvePointType.SMOOTH }
      ];
      expect(colorCorrectionIsActive(settings)).assertTrue();
    });
  });

  describe('reader request lifecycle', () => {
    it('cancels stale page work before native rendering', 0, () => {
      const token = new ReaderRequestToken();
      expect(token.isCancelled()).assertFalse();
      token.cancel();
      expect(token.isCancelled()).assertTrue();
      let cancelled = false;
      try {
        token.throwIfCancelled();
      } catch (error) {
        cancelled = (error as Error).message === 'READER_REQUEST_CANCELLED';
      }
      expect(cancelled).assertTrue();
    });

    it('queues work beyond the concurrency limit', 0, async () => {
      const gate = new ConcurrentTaskGate(2);
      await gate.enter();
      await gate.enter();
      let thirdEntered = false;
      const third = gate.enter().then(() => thirdEntered = true);
      await Promise.resolve();
      expect(thirdEntered).assertFalse();
      gate.leave();
      await third;
      expect(thirdEntered).assertTrue();
      gate.leave();
      gate.leave();
    });
  });

  describe('reader progress ordering', () => {
    it('serializes uploads and coalesces queued progress to the latest position', 0, async () => {
      const processed: number[] = [];
      const task = new LatestAsyncTask<number>(async (value: number) => {
        processed.push(value);
        if (value === 1) await Promise.resolve();
      });

      task.submit(1);
      task.submit(2);
      task.submit(3);
      await task.flush();

      expect(processed.join(',')).assertEqual('1,3');
      expect(task.isIdle()).assertTrue();
    });

    it('serializes reconnect flushes with newer direct progress writes', 0, async () => {
      const coordinator = new ProgressSyncCoordinator();
      const events: string[] = [];
      let releaseFlush: () => void = (): void => {};
      const flushGate = new Promise<void>((resolve: () => void): void => { releaseFlush = resolve; });
      const flushing = coordinator.run('account\nbook', async (): Promise<void> => {
        events.push('flush-start');
        await flushGate;
        events.push('flush-end');
      });
      const writing = coordinator.run('account\nbook', async (): Promise<void> => { events.push('write'); });
      await Promise.resolve();
      expect(events.join(',')).assertEqual('flush-start');
      releaseFlush();
      await Promise.all([flushing, writing]);
      expect(events.join(',')).assertEqual('flush-start,flush-end,write');
    });

    it('does not block progress for another book behind a reconnect upload', 0, async () => {
      const coordinator = new ProgressSyncCoordinator();
      const events: string[] = [];
      let releaseFlush: () => void = (): void => {};
      const flushGate = new Promise<void>((resolve: () => void): void => { releaseFlush = resolve; });
      const flushing = coordinator.run('account\nbook-a', async (): Promise<void> => {
        events.push('flush-start');
        await flushGate;
        events.push('flush-end');
      });
      await coordinator.run('account\nbook-b', async (): Promise<void> => { events.push('other-book'); });
      expect(events.join(',')).assertEqual('flush-start,other-book');
      releaseFlush();
      await flushing;
      expect(events.join(',')).assertEqual('flush-start,other-book,flush-end');
    });
  });

  describe('download background task lifecycle', () => {
    it('shares one continuous task across concurrent downloads', 0, async () => {
      let starts = 0;
      let stops = 0;
      const coordinator = new BackgroundTaskLeaseCoordinator(
        async (): Promise<void> => { starts++; },
        async (): Promise<void> => { stops++; }
      );
      await Promise.all([coordinator.enter(), coordinator.enter()]);
      expect(starts).assertEqual(1);
      expect(coordinator.activeCount()).assertEqual(2);
      expect(coordinator.isRunning()).assertTrue();
      await coordinator.leave();
      expect(stops).assertEqual(0);
      await coordinator.leave();
      expect(stops).assertEqual(1);
      expect(coordinator.isRunning()).assertFalse();
    });

    it('keeps the task running when a new lease arrives before stop begins', 0, async () => {
      let starts = 0;
      let stops = 0;
      const coordinator = new BackgroundTaskLeaseCoordinator(
        async (): Promise<void> => { starts++; },
        async (): Promise<void> => { stops++; }
      );
      await coordinator.enter();
      const leaving = coordinator.leave();
      const entering = coordinator.enter();
      await Promise.all([leaving, entering]);
      expect(starts).assertEqual(1);
      expect(stops).assertEqual(0);
      expect(coordinator.activeCount()).assertEqual(1);
      expect(coordinator.isRunning()).assertTrue();
      await coordinator.leave();
      expect(stops).assertEqual(1);
    });

    it('recovers after a failed start transition', 0, async () => {
      let starts = 0;
      const coordinator = new BackgroundTaskLeaseCoordinator(
        async (): Promise<void> => {
          starts++;
          if (starts === 1) throw new Error('start failed');
        },
        async (): Promise<void> => {}
      );
      let failed = false;
      try {
        await coordinator.enter();
      } catch (_) {
        failed = true;
      }
      expect(failed).assertTrue();
      await coordinator.enter();
      expect(starts).assertEqual(2);
      expect(coordinator.isRunning()).assertTrue();
      await coordinator.leave();
      await coordinator.leave();
    });
  });

  describe('download storage policy', () => {
    it('keeps a reserve and rejects downloads that would exhaust storage', 0, () => {
      const oneHundredMiB = 100 * 1024 * 1024;
      const required = requiredDownloadBytes(oneHundredMiB);
      expect(required).assertEqual(189 * 1024 * 1024);
      expect(hasDownloadSpace(required, oneHundredMiB)).assertTrue();
      expect(hasDownloadSpace(required - 1, oneHundredMiB)).assertFalse();
      expect(hasDownloadSpace(-1, oneHundredMiB)).assertTrue();
    });
  });

  describe('user presentation', () => {
    it('preserves unknown Komga roles', 0, () => {
      expect(userRoleResource('UNKNOWN_ROLE')).assertEqual('UNKNOWN_ROLE');
    });

    it('formats server timestamps without exposing raw ISO separators', 0, () => {
      const value = formatDateTime('2026-08-23T15:42:02.436Z');
      expect(value.includes('T')).assertFalse();
      expect(value.includes('Z')).assertFalse();
      expect(formatDateTime('not-a-date')).assertEqual('not-a-date');
    });
  });

  describe('metadata edit codecs', () => {
    it('preserves author roles and multilingual separators', 0, () => {
      const authors = authorsForRole('作者甲、 Author B，作者丙', 'writer');
      expect(authors.length).assertEqual(3);
      expect(authors[1].role).assertEqual('writer');
      expect(formatAuthorsForRole(authors, 'writer')).assertEqual('作者甲、Author B、作者丙');
      expect(splitMetadataValues('一, 二，三、四').length).assertEqual(4);
    });

    it('round trips links and alternative titles without mixing labels', 0, () => {
      const links = parseWebLinks('官网|https://example.com\n无效行\n资料|https://example.com/wiki');
      expect(links.length).assertEqual(2);
      expect(formatWebLinks(links)).assertEqual('官网|https://example.com\n资料|https://example.com/wiki');
      const titles = parseAlternativeTitles('zh|中文标题\nen|English title');
      expect(titles.length).assertEqual(2);
      expect(formatAlternativeTitles(titles)).assertEqual('zh|中文标题\nen|English title');
    });
  });

  describe('network route selection', () => {
    it('tries LAN first and keeps the primary route as fallback', 0, () => {
      const routes = networkRouteCandidates('https://primary.example/', false,
        'http://192.168.1.10:25600/', true, true);
      expect(routes.length).assertEqual(2);
      expect(routes[0].origin).assertEqual('http://192.168.1.10:25600');
      expect(routes[0].usingLan).assertTrue();
      expect(routes[1].origin).assertEqual('https://primary.example');
      expect(routes[1].usingLan).assertFalse();
    });

    it('does not duplicate identical primary and LAN routes', 0, () => {
      const routes = networkRouteCandidates('https://komga.example', false,
        'https://komga.example/', false, true);
      expect(routes.length).assertEqual(1);
      expect(routes[0].usingLan).assertFalse();
    });
  });

  describe('Komf connection policy', () => {
    it('requires explicit HTTP confirmation', 0, () => {
      const blocked = evaluateOriginPolicy('http://192.168.1.10:8085', false);
      const allowed = evaluateOriginPolicy('http://192.168.1.10:8085/', true);
      expect(blocked.error).assertEqual(OriginPolicyError.HTTP_CONFIRMATION_REQUIRED);
      expect(allowed.error).assertEqual(OriginPolicyError.NONE);
      expect(allowed.normalizedOrigin).assertEqual('http://192.168.1.10:8085');
    });

    it('parses typed SSE progress events', 0, () => {
      const event = parseKomfSseBlock('event: ProviderBookEvent\n' +
        'data: {"provider":"MANGA_UPDATES","totalBooks":12,"bookProgress":5}');
      expect(event?.type).assertEqual('ProviderBookEvent');
      expect(event?.provider).assertEqual('MANGA_UPDATES');
      expect(event?.totalBooks).assertEqual(12);
      expect(event?.bookProgress).assertEqual(5);
    });

    it('keeps marker events without a data payload', 0, () => {
      const event = parseKomfSseBlock('event: PostProcessingStartEvent\n');
      expect(event?.type).assertEqual('PostProcessingStartEvent');
      expect(parseKomfSseBlock('data: {}')).assertUndefined();
    });

    it('builds Komf metadata paths for Android-compatible Komga operations', 0, () => {
      expect(komfMetadataPath(' KOMGA ')).assertEqual('/api/komga/metadata');
      expect(komfSeriesSearchPath('komga', ' Re: Zero ', 'library / 1', 'series?2'))
        .assertEqual('/api/komga/metadata/search?name=Re%3A%20Zero&libraryId=library%20%2F%201&seriesId=series%3F2');
      expect(komfSeriesSearchPath('kavita', 'Title'))
        .assertEqual('/api/kavita/metadata/search?name=Title');
    });

    it('uses the Android-compatible sample notification context', 0, () => {
      const context = defaultKomfNotificationContext();
      expect(context.mediaServer).assertEqual('KOMGA');
      expect(context.series.bookCount).assertEqual(1);
      expect(context.books.length).assertEqual(1);
      expect(context.books[0].metadata.authors.length).assertEqual(2);
    });

    it('summarizes notification targets without exposing their values', 0, () => {
      const config = JSON.parse('{"notifications":{"discord":{"webhooks":["one","two"],' +
        '"seriesCover":true},"apprise":{"urls":["three"],"seriesCover":false}}}') as Object;
      const summary = komfNotificationTargetSummary(config);
      expect(summary.discordCount).assertEqual(2);
      expect(summary.appriseCount).assertEqual(1);
      expect(summary.discordSeriesCover).assertTrue();
      expect(summary.appriseSeriesCover).assertFalse();
      const settings = komfNotificationSettings(config);
      expect(settings.discordWebhooks.length).assertEqual(2);
      expect(settings.appriseUrls.length).assertEqual(1);
    });

    it('round trips Android-compatible default processing settings', 0, () => {
      const config = JSON.parse('{"komga":{"metadataUpdate":{"default":{"libraryType":"NOVEL",' +
        '"aggregate":true,"mergeTags":true,"mergeGenres":false,"bookCovers":true,' +
        '"seriesCovers":true,"overrideExistingCovers":false,"lockCovers":true,' +
        '"updateModes":["API","COMIC_INFO"],"postProcessing":{"seriesTitle":true,' +
        '"alternativeSeriesTitles":true,"seriesTitleLanguage":"zh",' +
        '"alternativeSeriesTitleLanguages":["en","ja"],"fallbackToAltTitle":true,' +
        '"orderBooks":true,"readingDirectionValue":"RIGHT_TO_LEFT","languageValue":"zh"}}}}}') as Object;
      const settings = komfDefaultProcessingSettings(config);
      expect(settings.libraryType).assertEqual('NOVEL');
      expect(settings.updateModes.length).assertEqual(2);
      expect(settings.alternativeSeriesTitleLanguages.join(',')).assertEqual('en,ja');
      const update = komfDefaultProcessingUpdate(settings);
      expect(update.komga?.metadataUpdate.default?.postProcessing.readingDirectionValue)
        .assertEqual('RIGHT_TO_LEFT');
      expect(update.komga?.metadataUpdate.default?.lockCovers).assertTrue();
    });

    it('selects and updates Kavita library-specific processing rules', 0, () => {
      const config = JSON.parse('{"kavita":{"metadataUpdate":{"default":{"libraryType":"MANGA"},' +
        '"library":{"library-7":{"libraryType":"NOVEL","aggregate":true,' +
        '"postProcessing":{"languageValue":"zh"}}}}}}') as Object;
      expect(normalizeKomfMediaServer('KAVITA')).assertEqual('kavita');
      const settings = komfProcessingSettings(config, 'kavita', 'library-7');
      expect(settings.libraryType).assertEqual('NOVEL');
      expect(settings.aggregate).assertTrue();
      expect(settings.languageValue).assertEqual('zh');
      const update = komfProcessingUpdate(settings, 'kavita', 'library-7');
      expect(update.kavita?.metadataUpdate.library?.['library-7']?.libraryType).assertEqual('NOVEL');
      expect(update.komga).assertUndefined();
      const removal = komfRemoveLibraryProcessingUpdate('kavita', 'library-7');
      expect(removal.kavita?.metadataUpdate.library?.['library-7']).assertEqual(null);
    });

    it('orders and updates Android-compatible default providers', 0, () => {
      const config = JSON.parse('{"metadataProviders":{"nameMatchingMode":"EXACT",' +
        '"malClientId":"mal-id","comicVineClientId":"comic-id","defaultProviders":{' +
        '"mangaDex":{"enabled":true,"priority":2},"mal":{"enabled":true,"priority":1},' +
        '"comicVine":{"enabled":false,"priority":9}}}}') as Object;
      const providers = komfDefaultProviders(config, ['COMIC_VINE', 'MANGADEX', 'MAL']);
      expect(providers.length).assertEqual(3);
      expect(providers[0].id).assertEqual('MAL');
      expect(providers[1].id).assertEqual('MANGADEX');
      expect(providers[2].enabled).assertFalse();

      const settings = komfProviderGlobalSettings(config);
      expect(settings.nameMatchingMode).assertEqual('EXACT');
      expect(settings.malClientId).assertEqual('mal-id');
      expect(settings.comicVineClientId).assertEqual('comic-id');

      expect(JSON.stringify(komfDefaultProviderPatch('MANGADEX', true, 3)))
        .assertEqual('{"metadataProviders":{"defaultProviders":{"mangaDex":{"enabled":true,"priority":3}}}}');
      expect(JSON.stringify(komfProviderGlobalPatch('CLOSEST_MATCH', 'new-mal', 'new-comic')))
        .assertEqual('{"metadataProviders":{"nameMatchingMode":"CLOSEST_MATCH",' +
          '"malClientId":"new-mal","comicVineClientId":"new-comic"}}');
    });
  });
}
