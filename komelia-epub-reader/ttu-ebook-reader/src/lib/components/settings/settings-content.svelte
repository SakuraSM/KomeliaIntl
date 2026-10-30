<script lang="ts">
  import { faPlus, faSpinner} from '@fortawesome/free-solid-svg-icons';
  import ButtonToggleGroup from '$lib/components/button-toggle-group/button-toggle-group.svelte';
  import {getOptionsForToggle, type ToggleOption} from '$lib/components/button-toggle-group/toggle-option';
  import Ripple from '$lib/components/ripple.svelte';
  import SettingsCustomTheme from '$lib/components/settings/settings-custom-theme.svelte';
  import SettingsDimensionPopover from '$lib/components/settings/settings-dimension-popover.svelte';
  import SettingsFontSelector from '$lib/components/settings/settings-font-selector.svelte';
  import SettingsItemGroup from '$lib/components/settings/settings-item-group.svelte';
  import SettingsUserFontDialog from '$lib/components/settings/settings-user-font-dialog.svelte';
  import {inputClasses} from '$lib/css-classes';
  import {BlurMode} from '$lib/data/blur-mode';
  import {dialogManager} from '$lib/data/dialog-manager';
  import {FuriganaStyle} from '$lib/data/furigana-style';
  import {
    autoBookmark$,
    autoBookmarkTime$,
    autoPositionOnResize$,
    avoidPageBreak$,
    confirmClose$,
    customReadingPointEnabled$,
    customThemes$,
    disableWheelNavigation$,
    enableReaderWakeLock$,
    firstDimensionMargin$,
    fontSize$,
    furiganaStyle$,
    hideFurigana$,
    hideSpoilerImage$,
    hideSpoilerImageMode$,
    horizontalCustomReadingPosition$,
    lineHeight$,
    manualBookmark$,
    pageColumns$,
    sansFontFamily$,
    secondDimensionMaxValue$,
    selectionToBookmarkEnabled$,
    serifFontFamily$,
    showCharacterCounter$,
    swipeThreshold$,
    theme$,
    verticalCustomReadingPosition$,
    viewMode$,
    writingMode$
  } from '$lib/data/store';
  import {availableThemes as availableThemesMap} from '$lib/data/theme-option';
  import {ViewMode} from '$lib/data/view-mode';
  import type {WritingMode} from '$lib/data/writing-mode';
  import {dummyFn} from '$lib/functions/utils';
  import {defaultSansFont, defaultSerifFont} from "$lib/data/fonts";
  import Fa from "svelte-fa";
  import {faFolderOpen} from "@fortawesome/free-regular-svg-icons";
  import {t} from '$lib/i18n';

  const optionsForToggle = getOptionsForToggle(t);
  const BUILT_IN_THEME_LABELS: Record<string, string> = {
    'light-theme': 'Light theme',
    'ecru-theme': 'Paper theme',
    'water-theme': 'Blue theme',
    'gray-theme': 'Gray theme',
    'dark-theme': 'Dark theme',
    'black-theme': 'Black theme',
  };

  let optionsForTheme = $derived.by(() => {
    let availableThemes = [...Array.from(availableThemesMap.entries()), ...Object.entries($customThemes$)]
      .map(([theme, option]) => ({theme, option}))

    return availableThemes.map(({theme, option}) => ({
      id: theme,
      text: t(BUILT_IN_THEME_LABELS[theme] ?? theme),
      style: {
        color: option.fontColor,
        'background-color': option.backgroundColor,
        '--komelia-swatch-foreground': option.fontColor,
        '--komelia-swatch-background': option.backgroundColor
      },
      thickBorders: true,
      showIcons: true
    }))
  })

  const optionsForFuriganaStyle: ToggleOption<FuriganaStyle>[] = [
    {
      id: FuriganaStyle.Hide,
      text: t('Hide')
    },
    {
      id: FuriganaStyle.Partial,
      text: t('Partial')
    },
    {
      id: FuriganaStyle.Toggle,
      text: t('Toggle')
    },
    {
      id: FuriganaStyle.Full,
      text: t('Full')
    }
  ];

  const optionsForWritingMode: ToggleOption<WritingMode>[] = [
    {
      id: 'horizontal-tb',
      text: t('Horizontal')
    },
    {
      id: 'vertical-rl',
      text: t('Vertical')
    }
  ];

  const optionsForViewMode: ToggleOption<ViewMode>[] = [
    {
      id: ViewMode.Continuous,
      text: t('Continuous')
    },
    {
      id: ViewMode.Paginated,
      text: t('Paginated')
    }
  ];

  const optionsForBlurMode: ToggleOption<BlurMode>[] = [
    {
      id: BlurMode.ALL,
      text: t('All')
    },
    {
      id: BlurMode.AFTER_TOC,
      text: t('After ToC')
    }
  ];

  let showSpinner = false;
  let furiganaStyleTooltip = $state('');

  let autoBookmarkTooltip = $derived(t('Auto bookmark after {seconds} seconds without scrolling or page changes', {seconds: $autoBookmarkTime$}));
  let wakeLockSupported = $derived('wakeLock' in navigator);
  let verticalMode = $derived($writingMode$ === 'vertical-rl');
  let avoidPageBreakTooltip = $derived(
    $avoidPageBreak$
      ? t('Avoids breaking words/sentences into different pages')
      : t('Allow words/sentences to break into different pages')
  );
  $effect(() => {
    switch ($furiganaStyle$) {
      case FuriganaStyle.Hide:
        furiganaStyleTooltip = t('Always hidden');
        break;
      case FuriganaStyle.Toggle:
        furiganaStyleTooltip = t('Hidden by default, can be toggled on click');
        break;
      case FuriganaStyle.Full:
        furiganaStyleTooltip = t('Hidden by default, show on hover or click');
        break;
      default:
        furiganaStyleTooltip = t('Display furigana as grayed out text');
        break;
    }
  })
</script>

<div class="komelia-reading-settings">
  <SettingsItemGroup title={t('Serif Font family')} inline>
    <div class="font-row-controls">
      <SettingsFontSelector bind:fontValue={$serifFontFamily$} defaultFont={defaultSerifFont} family="Serif"/>
      <button
          type="button"
          aria-label={t('Open Custom Font Dialog')}
          title={t('Open Custom Font Dialog')}
          onclick={() =>
              dialogManager.dialogs$.next([
                {
                  component: SettingsUserFontDialog,
                  props: {
                      currentFont: serifFontFamily$,
                      defaultFont: defaultSerifFont
                  }
                }
              ])}
      >
        <Fa icon={faFolderOpen}/>
      </button>
    </div>
  </SettingsItemGroup>
  <SettingsItemGroup title={t('Font size')}>
    <div class="flex items-center gap-4">
      <input type="range" min="1" max={Math.max(48, $fontSize$ || 16)} step="1"
             aria-label={t('Font size')} class="min-h-12 flex-1" bind:value={$fontSize$}/>
      <input type="number" min="1" step="1" aria-label={t('Font size')}
             class="w-20 min-h-12 rounded-xl border bg-transparent p-2" bind:value={$fontSize$}/>
    </div>
  </SettingsItemGroup>
  <div class="lg:col-span-2">
    <SettingsItemGroup title={t('Theme')}>
      <div slot="header" class="ml-auto">
        <button
            type="button"
            class="komelia-theme-add rounded-md border border-gray-400"
            aria-label={t('Add theme')}
            onclick={() =>
                  dialogManager.dialogs$.next([
                    {
                      component: SettingsCustomTheme,
                      props: { existingThemes: optionsForTheme }
                    }
                  ])}
        >
          <Fa icon={faPlus}/>
          {t('Add theme')}
          <Ripple/>
        </button>
      </div>
      <ButtonToggleGroup
          themeSwatches
          options={optionsForTheme}
          bind:selectedOptionId={$theme$}
          on:edit={({ detail }) =>
                dialogManager.dialogs$.next([
                  {
                    component: SettingsCustomTheme,
                    props: { selectedTheme: detail, existingThemes: optionsForTheme }
                  }
                ])}
          on:delete={({ detail }) => {
            $theme$ = optionsForTheme[optionsForTheme.length - 2]?.id || 'light-theme';
            delete $customThemes$[detail];
            $customThemes$ = { ...$customThemes$ };
          }}
      >

      </ButtonToggleGroup>
    </SettingsItemGroup>
  </div>
  <div class="h-full">
    <SettingsItemGroup title={t('View mode')}>
      <ButtonToggleGroup options={optionsForViewMode} bind:selectedOptionId={$viewMode$}/>
    </SettingsItemGroup>
  </div>
  <details class="komelia-advanced-settings">
    <summary class="min-h-12 cursor-pointer py-4">{t('Advanced')}</summary>
  <SettingsItemGroup title={t('Sans Font family')}>
    <div slot="header" class="flex items-center mx-2">
      <button
          type="button"
          aria-label={t('Open Custom Font Dialog')}
          title={t('Open Custom Font Dialog')}
          onclick={() =>
              dialogManager.dialogs$.next([
                {
                  component: SettingsUserFontDialog,
                  props: {
                      currentFont: sansFontFamily$,
                      defaultFont: defaultSansFont
                  }
                }
              ])}
      >
        <Fa icon={faFolderOpen}/>
      </button>
    </div>
    <SettingsFontSelector bind:fontValue={$sansFontFamily$} defaultFont={defaultSansFont} family="Sans-Serif"/>
  </SettingsItemGroup>
  <SettingsItemGroup title={t('Line Height')}>
    <input
        type="number"
        class={inputClasses}
        step="0.05"
        min="1"
        bind:value={$lineHeight$}
        onchange={() => {
              if (!$lineHeight$ || $lineHeight$ < 1) {
                $lineHeight$ = 1.65;
              }}
            }
    />
  </SettingsItemGroup>
  <SettingsItemGroup
      title={t(verticalMode ? 'Reader Left/right margin' : 'Reader Top/bottom margin')}
  >
    <SettingsDimensionPopover
        slot="header"
        isFirstDimension
        isVertical={verticalMode}
        bind:dimensionValue={$firstDimensionMargin$}
    />
    <input
        type="number"
        class={inputClasses}
        step="1"
        min="0"
        bind:value={$firstDimensionMargin$}
    />
  </SettingsItemGroup>
  <SettingsItemGroup title={t(verticalMode ? 'Reader Max height' : 'Reader Max width')}>
    <SettingsDimensionPopover
        slot="header"
        isVertical={verticalMode}
        bind:dimensionValue={$secondDimensionMaxValue$}
    />
    <input
        type="number"
        class={inputClasses}
        step="1"
        min="0"
        bind:value={$secondDimensionMaxValue$}
    />
  </SettingsItemGroup>
  <SettingsItemGroup
      title={t('Swipe Threshold')}
      tooltip={t('Distance which you need to swipe in order trigger a navigation')}
  >
    <input
        type="number"
        step="1"
        min="10"
        class={inputClasses}
        bind:value={$swipeThreshold$}
        onblur={() => {
              if ($swipeThreshold$ < 10 || typeof $swipeThreshold$ !== 'number') {
                $swipeThreshold$ = 10;
              }
            }}
    />
  </SettingsItemGroup>
  {#if $autoBookmark$}
    <SettingsItemGroup title={t('Auto Bookmark Time')} tooltip={t('Time in s for Auto Bookmark')}>
      <input
          type="number"
          step="1"
          min="1"
          class={inputClasses}
          bind:value={$autoBookmarkTime$}
          onblur={() => {
                if ($autoBookmarkTime$ < 1 || typeof $autoBookmarkTime$ !== 'number') {
                  $autoBookmarkTime$ = 3;
                }
              }}
      />
    </SettingsItemGroup>
  {/if}
  <SettingsItemGroup title={t('Writing mode')}>
    <ButtonToggleGroup options={optionsForWritingMode} bind:selectedOptionId={$writingMode$}/>
  </SettingsItemGroup>
  {#if wakeLockSupported}
    <SettingsItemGroup
        title={t('Enable Screen Lock')}
        tooltip={t('When enabled the reader site attempts to request a WakeLock that prevents device screens from dimming or locking')}
    >
      <ButtonToggleGroup
          options={optionsForToggle}
          bind:selectedOptionId={$enableReaderWakeLock$}
      />
    </SettingsItemGroup>
  {/if}
  <SettingsItemGroup title={t('Show Character Counter')}>
    <ButtonToggleGroup options={optionsForToggle} bind:selectedOptionId={$showCharacterCounter$}/>
  </SettingsItemGroup>
  <SettingsItemGroup title={t('Disable Wheel Navigation')}>
    <ButtonToggleGroup
        options={optionsForToggle}
        bind:selectedOptionId={$disableWheelNavigation$}
    />
  </SettingsItemGroup>
  <SettingsItemGroup
      title={t('Close Confirmation')}
      tooltip={t('When enabled asks for confirmation on closing/reloading a reader tab and unsaved changes were detected')}
  >
    <ButtonToggleGroup options={optionsForToggle} bind:selectedOptionId={$confirmClose$}/>
  </SettingsItemGroup>
  <SettingsItemGroup
      title={t('Manual Bookmark')}
      tooltip={t('If enabled current position will not be bookmarked when leaving the reader via menu elements')}
  >
    <ButtonToggleGroup options={optionsForToggle} bind:selectedOptionId={$manualBookmark$}/>
  </SettingsItemGroup>
  <SettingsItemGroup title={t('Auto Bookmark')} tooltip={autoBookmarkTooltip}>
    <ButtonToggleGroup options={optionsForToggle} bind:selectedOptionId={$autoBookmark$}/>
  </SettingsItemGroup>
  <SettingsItemGroup title={t('Blur image')}>
    <ButtonToggleGroup options={optionsForToggle} bind:selectedOptionId={$hideSpoilerImage$}/>
  </SettingsItemGroup>
  {#if $hideSpoilerImage$}
    <SettingsItemGroup
        title={t('Blur Mode')}
        tooltip={t('Determines if all or only images after the table of contents will be blurred')}
    >
      <ButtonToggleGroup options={optionsForBlurMode} bind:selectedOptionId={$hideSpoilerImageMode$}/>
    </SettingsItemGroup>
  {/if}
  <SettingsItemGroup title={t('Hide furigana')}>
    <ButtonToggleGroup options={optionsForToggle} bind:selectedOptionId={$hideFurigana$}/>
  </SettingsItemGroup>
  {#if $hideFurigana$}
    <SettingsItemGroup title={t('Hide furigana style')} tooltip={furiganaStyleTooltip}>
      <ButtonToggleGroup
          options={optionsForFuriganaStyle}
          bind:selectedOptionId={$furiganaStyle$}
      />
    </SettingsItemGroup>
  {/if}
  {#if $viewMode$ === ViewMode.Continuous}
    <SettingsItemGroup
        title={t('Custom Reading Point')}
        tooltip={t('Allows to set a persistent custom point in the reader from which the current progress and bookmark is calculated when enabled')}
    >
      <div class="flex items-center">
        <ButtonToggleGroup
            options={optionsForToggle}
            bind:selectedOptionId={$customReadingPointEnabled$}
        />
        {#if $customReadingPointEnabled$}
          <div
              tabindex="0"
              role="button"
              class="ml-4 hover:underline"
              onclick={() => {
                    verticalCustomReadingPosition$.next(100);
                    horizontalCustomReadingPosition$.next(0);
                  }}
              onkeyup={dummyFn}
          >
            {t('Reset Points')}
          </div>
        {/if}
      </div>
    </SettingsItemGroup>
    <SettingsItemGroup title={t('Auto position on resize')}>
      <ButtonToggleGroup
          options={optionsForToggle}
          bind:selectedOptionId={$autoPositionOnResize$}
      />
    </SettingsItemGroup>
  {:else}
    <SettingsItemGroup title={t('Avoid Page Break')} tooltip={avoidPageBreakTooltip}>
      <ButtonToggleGroup options={optionsForToggle} bind:selectedOptionId={$avoidPageBreak$}/>
    </SettingsItemGroup>
    <SettingsItemGroup
        title={t('Selection to Bookmark')}
        tooltip={t('When enabled bookmarks will be placed to a near paragraph of current/previous selected text instead of page start')}
    >
      <ButtonToggleGroup
          options={optionsForToggle}
          bind:selectedOptionId={$selectionToBookmarkEnabled$}
      />
    </SettingsItemGroup>
    {#if !verticalMode}
      <SettingsItemGroup title={t('Page Columns')}>
        <input type="number"
               class={inputClasses}
               step="1" min="0"
               bind:value={$pageColumns$}/>
      </SettingsItemGroup>
    {/if}
  {/if}
  {#if showSpinner}
    <div class="tap-highlight-transparent fixed inset-0 bg-black/[.2]"></div>
    <div class="fixed inset-0 flex h-full w-full items-center justify-center text-7xl">
      <Fa icon={faSpinner} spin/>
    </div>
  {/if}
  </details>
</div>

<style>
  .font-row-controls { display: flex; align-items: center; gap: 4px; min-width: 0; }
  .font-row-controls > :global(div) { flex: 1; min-width: 0; }
  .font-row-controls > button { flex: none; width: 48px; min-height: 48px; }
  .komelia-theme-add { display: flex; align-items: center; justify-content: center; gap: 8px; min-height: 48px; margin: 0; padding: 0 8px; font-size: 14px; }
</style>
