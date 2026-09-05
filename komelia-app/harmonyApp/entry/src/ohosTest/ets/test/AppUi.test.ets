import { Want } from '@kit.AbilityKit';
import { display } from '@kit.ArkUI';
import { abilityDelegatorRegistry, DisplayRotation, Driver, ON, On } from '@kit.TestKit';
import { describe, expect, it } from '@ohos/hypium';

async function componentExists(driver: Driver, selector: On): Promise<boolean> {
  try {
    const component = await driver.findComponent(selector);
    return component !== undefined && component !== null;
  } catch (_) {
    return false;
  }
}

async function waitForComponent(driver: Driver, selector: On, timeoutMs: number = 5000): Promise<boolean> {
  const intervalMs = 100;
  let elapsedMs = 0;
  while (elapsedMs < timeoutMs) {
    if (await componentExists(driver, selector)) return true;
    await driver.delayMs(intervalMs);
    elapsedMs += intervalMs;
  }
  return false;
}

async function waitForEitherComponent(driver: Driver, firstId: string, secondId: string,
  timeoutMs: number = 5000): Promise<string> {
  const intervalMs = 100;
  let elapsedMs = 0;
  while (elapsedMs < timeoutMs) {
    if (await componentExists(driver, ON.id(firstId))) return firstId;
    if (await componentExists(driver, ON.id(secondId))) return secondId;
    await driver.delayMs(intervalMs);
    elapsedMs += intervalMs;
  }
  return '';
}

async function clickWhenAvailable(driver: Driver, selector: On, timeoutMs: number = 5000): Promise<boolean> {
  const intervalMs = 100;
  let elapsedMs = 0;
  while (elapsedMs < timeoutMs) {
    try {
      const component = await driver.findComponent(selector);
      if (component !== undefined && component !== null) {
        await component.click();
        return true;
      }
    } catch (_) {}
    await driver.delayMs(intervalMs);
    elapsedMs += intervalMs;
  }
  return false;
}

async function clickFirstAvailableInScroll(driver: Driver, scrollId: string, componentIds: string[]): Promise<boolean> {
  if (!await componentExists(driver, ON.id(scrollId))) return false;
  const scroll = await driver.findComponent(ON.id(scrollId));
  for (const componentId of componentIds) {
    try {
      const component = await scroll.scrollSearch(ON.id(componentId));
      if (component !== undefined && component !== null) {
        await component.click();
        return true;
      }
    } catch (_) {}
  }
  return false;
}

async function expectTopAlignedPage(driver: Driver, navigationId: string, pageId: string,
  expectedTop?: number): Promise<number> {
  const root = await driver.findComponent(ON.id('komelia_root'));
  const shell = await driver.findComponent(ON.id('main_shell'));
  const navigation = await driver.findComponent(ON.id(navigationId));
  await navigation.click();
  expect(await waitForComponent(driver, ON.id(pageId))).assertTrue();
  const page = await driver.findComponent(ON.id(pageId));
  const rootBounds = await root.getBounds();
  const shellBounds = await shell.getBounds();
  const pageBounds = await page.getBounds();
  expect(Math.abs(shellBounds.top - rootBounds.top) <= 2).assertTrue();
  expect(pageBounds.top >= shellBounds.top).assertTrue();
  expect(pageBounds.bottom - pageBounds.top >= (shellBounds.bottom - shellBounds.top) * 0.6).assertTrue();
  if (expectedTop !== undefined) {
    expect(Math.abs(pageBounds.top - expectedTop) <= 2).assertTrue();
  }
  return pageBounds.top;
}

async function expectCenteredDetailContent(driver: Driver, pageId: string, contentId: string): Promise<void> {
  const page = await driver.findComponent(ON.id(pageId));
  const content = await driver.findComponent(ON.id(contentId));
  const pageBounds = await page.getBounds();
  const contentBounds = await content.getBounds();
  const density = display.getDefaultDisplaySync().densityPixels;
  const leftInset = contentBounds.left - pageBounds.left;
  const rightInset = pageBounds.right - contentBounds.right;
  expect(contentBounds.left >= pageBounds.left).assertTrue();
  expect(contentBounds.right <= pageBounds.right).assertTrue();
  expect(contentBounds.right - contentBounds.left <= Math.min(pageBounds.right - pageBounds.left,
    1200 * density) + 2).assertTrue();
  expect(Math.abs(leftInset - rightInset) <= 2).assertTrue();
  if ((pageBounds.right - pageBounds.left) / density > 1200) {
    expect(Math.abs(contentBounds.right - contentBounds.left - 1200 * density) <= 2).assertTrue();
    expect(leftInset > 0 && rightInset > 0).assertTrue();
  }
}

function approximately(value: number, expected: number, tolerance: number = 2): boolean {
  return Math.abs(value - expected) <= tolerance;
}

export default function appUiTest(): void {
  describe('Komelia application shell', () => {
    it('launches and exposes either the authenticated shell or login page', 0, async () => {
      const delegator = abilityDelegatorRegistry.getAbilityDelegator();
      const want: Want = {
        bundleName: 'io.github.sakurasm.komeliaintl',
        abilityName: 'EntryAbility'
      };
      await delegator.startAbility(want);
      const driver = Driver.create();
      expect(await waitForComponent(driver, ON.id('komelia_root'))).assertTrue();
      const hasMainShell = await waitForComponent(driver, ON.id('main_shell'));
      const hasLoginPage = hasMainShell ? false : await waitForComponent(driver, ON.id('login_page'));
      expect(hasMainShell || hasLoginPage).assertTrue();
    });

    it('keeps every primary destination aligned to the top of the application shell', 0, async () => {
      const driver = Driver.create();
      if (!await componentExists(driver, ON.id('main_shell'))) return;
      const top = await expectTopAlignedPage(driver, 'nav_home', 'home_page');
      await expectTopAlignedPage(driver, 'nav_library', 'library_page', top);
      await expectTopAlignedPage(driver, 'nav_search', 'search_page', top);
      await expectTopAlignedPage(driver, 'nav_settings', 'settings_page', top);
    });

    it('keeps primary navigation active on ordinary nested routes', 0, async () => {
      const driver = Driver.create();
      if (!await componentExists(driver, ON.id('main_shell'))) return;
      const settingsNavigation = await driver.findComponent(ON.id('nav_settings'));
      await settingsNavigation.click();
      expect(await waitForComponent(driver, ON.id('settings_page'))).assertTrue();
      const settings = await driver.findComponent(ON.id('settings_page'));
      const appearance = await settings.scrollSearch(ON.id('settings_appearance'));
      await appearance.click();
      expect(await waitForComponent(driver, ON.id('appearance_settings_page'))).assertTrue();
      expect(await componentExists(driver, ON.id('nav_settings'))).assertTrue();

      const homeNavigation = await driver.findComponent(ON.id('nav_home'));
      await homeNavigation.click();
      expect(await waitForComponent(driver, ON.id('home_page'))).assertTrue();
      expect(await componentExists(driver, ON.id('appearance_settings_page'))).assertFalse();
    });

    it('switches authenticated phone layout from bottom bar to rail in landscape', 0, async () => {
      const driver = Driver.create();
      if (!await componentExists(driver, ON.id('main_shell'))) return;
      const originalRotation = await driver.getDisplayRotation();
      const originalHasBottomBar = await componentExists(driver, ON.id('primary_navigation_bar'));
      try {
        await driver.setDisplayRotationEnabled(true);
        await driver.setDisplayRotation(DisplayRotation.ROTATION_90);
        await driver.delayMs(700);
        expect(await componentExists(driver, ON.id('primary_navigation_rail'))).assertTrue();
        expect(await componentExists(driver, ON.id('primary_navigation_bar'))).assertFalse();

        await (await driver.findComponent(ON.id('nav_library'))).click();
        expect(await waitForComponent(driver, ON.id('library_page'))).assertTrue();
        const density = display.getDefaultDisplaySync().densityPixels;
        const rootBounds = await (await driver.findComponent(ON.id('komelia_root'))).getBounds();
        const pageBounds = await (await driver.findComponent(ON.id('library_page'))).getBounds();
        const logicalWidth = (pageBounds.right - pageBounds.left) / density;
        expect(await componentExists(driver, ON.id('library_scope_supporting_pane')))
          .assertEqual(logicalWidth >= 1200);
        expect(pageBounds.left >= rootBounds.left).assertTrue();
        expect(pageBounds.right <= rootBounds.right).assertTrue();

        await (await driver.findComponent(ON.id('library_filter_button'))).click();
        expect(await waitForComponent(driver, ON.id('library_filter_sheet'))).assertTrue();
        expect(await componentExists(driver, ON.id('library_scope_sheet'))).assertFalse();
        const filterSheet = await driver.findComponent(ON.id('library_filter_sheet'));
        const sheetBounds = await filterSheet.getBounds();
        expect(sheetBounds.left >= rootBounds.left).assertTrue();
        expect(sheetBounds.right <= rootBounds.right).assertTrue();
        expect(await waitForComponent(driver, ON.id('library_filter_query'))).assertTrue();
        const filterBounds = await (await driver.findComponent(ON.id('library_filter_query'))).getBounds();
        expect(filterBounds.left >= rootBounds.left).assertTrue();
        expect(filterBounds.right <= rootBounds.right).assertTrue();
        await driver.pressBack();
      } finally {
        await driver.setDisplayRotation(originalRotation);
        await driver.delayMs(500);
      }
      expect(await componentExists(driver, ON.id('primary_navigation_bar'))).assertEqual(originalHasBottomBar);
      expect(await componentExists(driver, ON.id('primary_navigation_rail'))).assertEqual(!originalHasBottomBar);
    });

    it('keeps Full library supporting pane and content geometry stable', 0, async () => {
      const driver = Driver.create();
      if (!await componentExists(driver, ON.id('main_shell'))) return;
      const originalRotation = await driver.getDisplayRotation();
      try {
        await driver.setDisplayRotationEnabled(true);
        await driver.setDisplayRotation(DisplayRotation.ROTATION_90);
        await driver.delayMs(700);
        await (await driver.findComponent(ON.id('nav_library'))).click();
        expect(await waitForComponent(driver, ON.id('library_page'))).assertTrue();

        const density = display.getDefaultDisplaySync().densityPixels;
        const tolerance = Math.max(2, density);
        const pageBounds = await (await driver.findComponent(ON.id('library_page'))).getBounds();
        const logicalPageWidth = (pageBounds.right - pageBounds.left) / density;
        if (logicalPageWidth < 1200) return;

        expect(await componentExists(driver, ON.id('library_scope_supporting_pane'))).assertTrue();
        const paneBounds = await (await driver.findComponent(ON.id('library_scope_supporting_pane'))).getBounds();
        const allLibrariesBounds = await (await driver.findComponent(ON.id('library_scope_item_0'))).getBounds();
        const contentBounds = await (await driver.findComponent(ON.id('library_content_column'))).getBounds();
        const headerBounds = await (await driver.findComponent(ON.id('library_header'))).getBounds();
        const gridBounds = await (await driver.findComponent(ON.id('library_series_grid'))).getBounds();

        expect(approximately(paneBounds.right - paneBounds.left, 232 * density, tolerance)).assertTrue();
        expect(approximately(paneBounds.left, pageBounds.left, tolerance)).assertTrue();
        expect(approximately(paneBounds.right, contentBounds.left, tolerance)).assertTrue();
        expect(approximately(contentBounds.right, pageBounds.right, tolerance)).assertTrue();
        expect(approximately(allLibrariesBounds.left - paneBounds.left, 24 * density, tolerance)).assertTrue();
        expect(approximately(paneBounds.right - allLibrariesBounds.right, 24 * density, tolerance)).assertTrue();
        expect(approximately(allLibrariesBounds.top - paneBounds.top, 24 * density, tolerance)).assertTrue();
        expect(approximately(allLibrariesBounds.bottom - allLibrariesBounds.top, 56 * density, tolerance)).assertTrue();
        expect(approximately(headerBounds.left - contentBounds.left, 24 * density, tolerance)).assertTrue();
        expect(approximately(contentBounds.right - headerBounds.right, 24 * density, tolerance)).assertTrue();
        expect(approximately(gridBounds.left - contentBounds.left, 24 * density, tolerance)).assertTrue();
        expect(approximately(contentBounds.right - gridBounds.right, 24 * density, tolerance)).assertTrue();

        const seriesCountBounds = await (await driver.findComponent(ON.id('library_series_count'))).getBounds();
        const filterBounds = await (await driver.findComponent(ON.id('library_filter_button'))).getBounds();
        const pageSizeBounds = await (await driver.findComponent(ON.id('library_page_size'))).getBounds();
        expect(approximately(seriesCountBounds.bottom - seriesCountBounds.top, 48 * density, tolerance)).assertTrue();
        expect(approximately(filterBounds.bottom - filterBounds.top, 48 * density, tolerance)).assertTrue();
        expect(approximately(pageSizeBounds.bottom - pageSizeBounds.top, 48 * density, tolerance)).assertTrue();

        if (await componentExists(driver, ON.id('library_series_card_0')) &&
          await componentExists(driver, ON.id('library_series_card_1'))) {
          const firstCardBounds = await (await driver.findComponent(ON.id('library_series_card_0'))).getBounds();
          const secondCardBounds = await (await driver.findComponent(ON.id('library_series_card_1'))).getBounds();
          expect(approximately(firstCardBounds.left, gridBounds.left, tolerance)).assertTrue();
          expect(approximately(firstCardBounds.top, secondCardBounds.top, tolerance)).assertTrue();
          expect(approximately(secondCardBounds.left - firstCardBounds.right,
            16 * density, tolerance)).assertTrue();
        }
      } finally {
        await driver.setDisplayRotation(originalRotation);
        await driver.delayMs(500);
      }
    });

    it('centers capped series and book detail content in landscape', 0, async () => {
      const driver = Driver.create();
      if (!await componentExists(driver, ON.id('main_shell'))) return;
      const originalRotation = await driver.getDisplayRotation();
      try {
        await driver.setDisplayRotationEnabled(true);
        await driver.setDisplayRotation(DisplayRotation.ROTATION_90);
        await driver.delayMs(700);
        await (await driver.findComponent(ON.id('nav_library'))).click();
        if (!await waitForComponent(driver, ON.id('library_page'))) return;
        if (!await waitForComponent(driver, ON.id('library_series_card_0'))) return;
        await (await driver.findComponent(ON.id('library_series_card_0'))).click();
        const detailPage = await waitForEitherComponent(driver, 'series_detail_page', 'oneshot_detail_page');
        if (detailPage.length === 0) return;
        await expectCenteredDetailContent(driver, detailPage,
          detailPage === 'series_detail_page' ? 'series_detail_content' : 'oneshot_detail_content');
        if (detailPage === 'series_detail_page') {
          const openedBook = await clickFirstAvailableInScroll(driver, 'series_detail_scroll', [
            'series_book_card_0', 'series_book_card_1', 'series_book_card_2'
          ]);
          if (openedBook && await waitForComponent(driver, ON.id('book_detail_page'))) {
            await expectCenteredDetailContent(driver, 'book_detail_page', 'book_detail_content');
          }
        }
      } finally {
        await driver.setDisplayRotation(originalRotation);
        await driver.delayMs(500);
      }
    });

    it('applies, restores, and resets advanced library filter chips', 0, async () => {
      const driver = Driver.create();
      if (!await componentExists(driver, ON.id('main_shell'))) return;
      const libraryNavigation = await driver.findComponent(ON.id('nav_library'));
      await libraryNavigation.click();
      expect(await waitForComponent(driver, ON.id('library_page'))).assertTrue();
      const filterButton = await driver.findComponent(ON.id('library_filter_button'));
      await filterButton.click();
      expect(await waitForComponent(driver, ON.id('library_filter_sheet'))).assertTrue();
      expect(await componentExists(driver, ON.id('library_scope_sheet'))).assertFalse();
      let filterScroll = await driver.findComponent(ON.id('library_filter_scroll'));
      const unread = await filterScroll.scrollSearch(ON.id('library_filter_read_UNREAD'));
      expect(unread !== undefined && unread !== null).assertTrue();
      expect(await unread.isChecked()).assertFalse();
      await unread.click();
      await driver.delayMs(200);
      const selectedUnread = await filterScroll.scrollSearch(ON.id('library_filter_read_UNREAD'));
      expect(selectedUnread !== undefined && selectedUnread !== null).assertTrue();
      expect(await selectedUnread.isChecked()).assertTrue();

      const apply = await driver.findComponent(ON.id('library_filter_apply'));
      await apply.click();
      await driver.delayMs(400);
      expect(await componentExists(driver, ON.id('library_filter_read_UNREAD'))).assertFalse();

      await (await driver.findComponent(ON.id('library_filter_button'))).click();
      expect(await waitForComponent(driver, ON.id('library_filter_sheet'))).assertTrue();
      filterScroll = await driver.findComponent(ON.id('library_filter_scroll'));
      expect(await (await filterScroll.scrollSearch(ON.id('library_filter_read_UNREAD'))).isChecked()).assertTrue();

      await (await driver.findComponent(ON.id('library_filter_reset'))).click();
      await driver.delayMs(400);
      expect(await componentExists(driver, ON.id('library_filter_read_UNREAD'))).assertFalse();
      await (await driver.findComponent(ON.id('library_filter_button'))).click();
      expect(await waitForComponent(driver, ON.id('library_filter_sheet'))).assertTrue();
      filterScroll = await driver.findComponent(ON.id('library_filter_scroll'));
      expect(await (await filterScroll.scrollSearch(ON.id('library_filter_read_UNREAD'))).isChecked()).assertFalse();
      await driver.pressBack();
      expect(await componentExists(driver, ON.id('library_page'))).assertTrue();
    });

    it('routes an available detail metadata chip back into the scoped library', 0, async () => {
      const driver = Driver.create();
      if (!await componentExists(driver, ON.id('main_shell'))) return;
      await (await driver.findComponent(ON.id('nav_library'))).click();
      if (!await waitForComponent(driver, ON.id('library_page'))) return;
      if (!await waitForComponent(driver, ON.id('library_series_card_0'))) return;
      await (await driver.findComponent(ON.id('library_series_card_0'))).click();
      const detailPage = await waitForEitherComponent(driver, 'series_detail_page', 'oneshot_detail_page');
      if (detailPage.length === 0) return;

      await expectCenteredDetailContent(driver, detailPage,
        detailPage === 'series_detail_page' ? 'series_detail_content' : 'oneshot_detail_content');

      let navigated = false;
      if (detailPage === 'series_detail_page') {
        navigated = await clickFirstAvailableInScroll(driver, 'series_detail_scroll', [
          'series_filter_status', 'series_filter_age', 'series_filter_language',
          'series_filter_publisher_0', 'series_filter_genre_0', 'series_filter_tag_0',
          'series_filter_writer_0', 'series_filter_penciller_0'
        ]);
        if (!navigated && await componentExists(driver, ON.id('series_library_scope'))) {
          await (await driver.findComponent(ON.id('series_library_scope'))).click();
          navigated = true;
        }
      } else {
        navigated = await clickFirstAvailableInScroll(driver, 'oneshot_detail_scroll', [
          'oneshot_filter_publisher_0', 'oneshot_filter_genre_0', 'oneshot_filter_tag_0',
          'oneshot_filter_author_writer_0', 'oneshot_filter_author_penciller_0'
        ]);
        if (!navigated && await componentExists(driver, ON.id('oneshot_library_scope'))) {
          await (await driver.findComponent(ON.id('oneshot_library_scope'))).click();
          navigated = true;
        }
      }
      if (!navigated) {
        await driver.pressBack();
        return;
      }
      expect(await waitForComponent(driver, ON.id('library_page'))).assertTrue();
      expect(await componentExists(driver, ON.id('library_filter_button'))).assertTrue();
    });

    it('opens a series book detail with Android-equivalent toolbar and actions', 0, async () => {
      const driver = Driver.create();
      if (!await componentExists(driver, ON.id('main_shell'))) return;
      await (await driver.findComponent(ON.id('nav_library'))).click();
      if (!await waitForComponent(driver, ON.id('library_page'))) return;

      let openedBook = false;
      for (let seriesIndex = 0; seriesIndex < 8 && !openedBook; seriesIndex += 1) {
        const cardId = `library_series_card_${seriesIndex}`;
        if (!await componentExists(driver, ON.id(cardId))) continue;
        await (await driver.findComponent(ON.id(cardId))).click();
        const detailPage = await waitForEitherComponent(driver, 'series_detail_page', 'oneshot_detail_page');
        if (detailPage === 'series_detail_page') {
          const clicked = await clickFirstAvailableInScroll(driver, 'series_detail_scroll', [
            'series_book_card_0', 'series_book_card_1', 'series_book_card_2'
          ]);
          if (clicked && await waitForComponent(driver, ON.id('book_detail_page'))) openedBook = true;
        }
        if (!openedBook) {
          await driver.pressBack();
          await waitForComponent(driver, ON.id('library_page'));
        }
      }
      expect(openedBook).assertTrue();

      await expectCenteredDetailContent(driver, 'book_detail_page', 'book_detail_content');
      expect(await componentExists(driver, ON.id('book_detail_cover'))).assertTrue();
      expect(await componentExists(driver, ON.id('book_detail_read'))).assertTrue();
      expect(await componentExists(driver, ON.id('book_detail_read_menu'))).assertTrue();
      expect(await componentExists(driver, ON.id('book_detail_download'))).assertTrue();
      expect(await componentExists(driver, ON.id('book_detail_more'))).assertTrue();
      const scroll = await driver.findComponent(ON.id('book_detail_scroll'));
      expect(await scroll.scrollSearch(ON.id('book_file_information')) !== undefined).assertTrue();

      await (await driver.findComponent(ON.id('book_detail_more'))).click();
      const progressAction = await waitForEitherComponent(driver, 'book_action_mark_read', 'book_action_mark_unread');
      expect(progressAction.length > 0).assertTrue();
      await driver.pressBack();
      expect(await componentExists(driver, ON.id('book_detail_page'))).assertTrue();
      await (await driver.findComponent(ON.id('nav_library'))).click();
      expect(await waitForComponent(driver, ON.id('library_page'))).assertTrue();
    });

    it('opens Android-equivalent series actions without executing a server mutation', 0, async () => {
      const driver = Driver.create();
      if (!await componentExists(driver, ON.id('main_shell'))) return;
      await (await driver.findComponent(ON.id('nav_library'))).click();
      if (!await waitForComponent(driver, ON.id('library_page'))) return;

      let openedSeries = false;
      for (let seriesIndex = 0; seriesIndex < 8 && !openedSeries; seriesIndex += 1) {
        const cardId = `library_series_card_${seriesIndex}`;
        if (!await componentExists(driver, ON.id(cardId))) continue;
        await (await driver.findComponent(ON.id(cardId))).click();
        const detailPage = await waitForEitherComponent(driver, 'series_detail_page', 'oneshot_detail_page');
        if (detailPage === 'series_detail_page') openedSeries = true;
        else {
          await driver.pressBack();
          await waitForComponent(driver, ON.id('library_page'));
        }
      }
      expect(openedSeries).assertTrue();

      await (await driver.findComponent(ON.id('series_detail_more'))).click();
      const progressAction = await waitForEitherComponent(driver, 'series_action_mark_read',
        'series_action_mark_unread');
      expect(progressAction.length > 0).assertTrue();
      if (await componentExists(driver, ON.id('series_action_analyze'))) {
        expect(await componentExists(driver, ON.id('series_action_refresh'))).assertTrue();
        expect(await componentExists(driver, ON.id('series_action_edit'))).assertTrue();
      }
      if (await componentExists(driver, ON.id('series_action_komf_identify'))) {
        const actionsScroll = await driver.findComponent(ON.id('series_actions_scroll'));
        expect(await actionsScroll.scrollSearch(ON.id('series_action_komf_reset')) !== undefined).assertTrue();
      }
      await driver.pressBack();
      expect(await componentExists(driver, ON.id('series_detail_page'))).assertTrue();
      await (await driver.findComponent(ON.id('nav_library'))).click();
      expect(await waitForComponent(driver, ON.id('library_page'))).assertTrue();
    });

    it('filters oneshots and validates Android-equivalent detail when content is available', 0, async () => {
      const driver = Driver.create();
      if (!await componentExists(driver, ON.id('main_shell'))) return;
      await (await driver.findComponent(ON.id('nav_library'))).click();
      if (!await waitForComponent(driver, ON.id('library_page'))) return;

      await (await driver.findComponent(ON.id('library_filter_button'))).click();
      expect(await waitForComponent(driver, ON.id('library_filter_sheet'))).assertTrue();
      const filterScroll = await driver.findComponent(ON.id('library_filter_scroll'));
      const oneshotFilter = await filterScroll.scrollSearch(ON.id('library_filter_oneshot_YES'));
      expect(oneshotFilter !== undefined && oneshotFilter !== null).assertTrue();
      await oneshotFilter.click();
      await driver.delayMs(300);
      await (await driver.findComponent(ON.id('library_filter_apply'))).click();
      const result = await waitForEitherComponent(driver, 'library_series_oneshot_card_0',
        'library_empty_state', 15000);
      expect(result.length > 0).assertTrue();
      if (result === 'library_empty_state') return;
      expect(await clickWhenAvailable(driver, ON.id(result))).assertTrue();
      expect(await waitForComponent(driver, ON.id('oneshot_detail_page'))).assertTrue();

      await expectCenteredDetailContent(driver, 'oneshot_detail_page', 'oneshot_detail_content');
      expect(await componentExists(driver, ON.id('oneshot_detail_cover'))).assertTrue();
      expect(await componentExists(driver, ON.id('oneshot_detail_read'))).assertTrue();
      expect(await componentExists(driver, ON.id('oneshot_detail_read_menu'))).assertTrue();
      expect(await componentExists(driver, ON.id('oneshot_detail_download'))).assertTrue();
      expect(await componentExists(driver, ON.id('oneshot_detail_more'))).assertTrue();
      const scroll = await driver.findComponent(ON.id('oneshot_detail_scroll'));
      expect(await scroll.scrollSearch(ON.id('oneshot_file_information')) !== undefined).assertTrue();

      await (await driver.findComponent(ON.id('oneshot_detail_more'))).click();
      expect(await componentExists(driver, ON.id('oneshot_actions_scroll'))).assertTrue();
      const progressAction = await waitForEitherComponent(driver, 'oneshot_action_mark_read',
        'oneshot_action_mark_unread');
      expect(progressAction.length > 0).assertTrue();
      if (await componentExists(driver, ON.id('oneshot_action_komf_identify'))) {
        const actionsScroll = await driver.findComponent(ON.id('oneshot_actions_scroll'));
        expect(await actionsScroll.scrollSearch(ON.id('oneshot_action_komf_reset')) !== undefined).assertTrue();
      }
      await driver.pressBack();
      expect(await componentExists(driver, ON.id('oneshot_detail_page'))).assertTrue();
      await (await driver.findComponent(ON.id('nav_library'))).click();
      expect(await waitForComponent(driver, ON.id('library_page'))).assertTrue();
    });

    it('requires confirmation before signing out', 0, async () => {
      const driver = Driver.create();
      if (!await componentExists(driver, ON.id('main_shell'))) return;
      const navigation = await driver.findComponent(ON.id('nav_settings'));
      await navigation.click();
      expect(await waitForComponent(driver, ON.id('settings_page'))).assertTrue();
      const settings = await driver.findComponent(ON.id('settings_page'));
      const logout = await settings.scrollSearch(ON.id('settings_logout'));
      await logout.click();
      await driver.delayMs(250);
      expect(await componentExists(driver, ON.id('main_shell'))).assertTrue();
      await driver.pressBack();
      expect(await componentExists(driver, ON.id('main_shell'))).assertTrue();
    });

  });
}
