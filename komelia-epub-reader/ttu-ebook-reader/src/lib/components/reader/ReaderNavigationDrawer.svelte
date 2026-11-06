<script lang="ts">
  import {faChevronDown, faChevronRight, faTrashCan, faXmark} from '@fortawesome/free-solid-svg-icons';
  import Fa from 'svelte-fa';
  import {fly} from 'svelte/transition';
  import {quintInOut} from 'svelte/easing';
  import {
    getChapterData,
    nextChapter$,
    type SectionWithProgress
  } from '$lib/components/book-reader/book-toc/book-toc';
  import type {ReaderAnnotation} from '$lib/data/reader-annotation';
  import type {ReaderSearchResult} from '$lib/functions/reader-interactions';
  import ReaderSearchPanel from '$lib/components/reader/ReaderSearchPanel.svelte';
  import {t} from '$lib/i18n';
  import {tick, onMount} from 'svelte';
  import {focusableDialogElements, trapDialogFocus} from '$lib/functions/dialog-focus';
  import {prefersReducedChromeMotion} from '../../../../../shared/chrome-appearance.mjs';

  type DrawerTab = 'toc' | 'search' | 'marks';

  interface Props {
    sectionData: SectionWithProgress[];
    verticalMode: boolean;
    exploredCharCount: number;
    fontColor?: string;
    backgroundColor?: string;
    searchQuery: string;
    searchResults: ReaderSearchResult[];
    activeSearchResultId?: string;
    annotations: ReaderAnnotation[];
    onClose: () => void;
    onSearchQueryInput: (query: string) => void;
    onPreviousSearchResult: () => void;
    onNextSearchResult: () => void;
    onSearchResultClick: (result: ReaderSearchResult) => void;
    onAnnotationClick: (annotation: ReaderAnnotation) => void;
    onAnnotationDelete: (annotation: ReaderAnnotation) => void;
    bookTitle?: string;
  }

  let {
    sectionData,
    verticalMode,
    fontColor,
    backgroundColor,
    searchQuery,
    searchResults,
    activeSearchResultId,
    annotations,
    onClose,
    onSearchQueryInput,
    onPreviousSearchResult,
    onNextSearchResult,
    onSearchResultClick,
    onAnnotationClick,
    onAnnotationDelete,
    bookTitle = ''
  }: Props = $props();

  let activeTab: DrawerTab = $state('toc');

  let chapters = $derived(sectionData.filter((section) => !section.parentChapter));
  let currentChapterIndex = $derived(sectionData.length ? getChapterData(sectionData)[1] : -1);

  let drawer: HTMLElement | undefined = $state();
  onMount(() => {
    const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    void tick().then(() => focusableDialogElements(drawer)[0]?.focus());
    return () => trigger?.focus();
  });
  function handleDrawerKeydown(event: KeyboardEvent): void {
    if (event.defaultPrevented || !drawer?.contains(document.activeElement)) return;
    if (event.key === 'Escape') { event.preventDefault(); onClose(); return; }
    trapDialogFocus(drawer, event);
  }

  function goToChapter(chapter: SectionWithProgress) {
    nextChapter$.next(chapter.reference);
    onClose();
  }


</script>

<svelte:window onkeydown={handleDrawerKeydown}/>
<button type="button" class="komelia-navigation-scrim" aria-label={t('Close navigation')} onclick={onClose}></button>
<aside
    bind:this={drawer}
    data-komelia-chrome data-komelia-popup data-komelia-reader-contents
    class="komelia-navigation-sheet writing-horizontal-tb flex flex-col shadow-2xl shadow-black/25"
    style:color={fontColor}
    style:background-color={backgroundColor}
    in:fly|local={{ y: 40, duration: prefersReducedChromeMotion() ? 0 : 130, easing: quintInOut }}
    role="dialog"
    aria-modal="true"
    aria-label={t('Reader navigation')}
>
  <div class="komelia-sheet-handle" aria-hidden="true"></div>
  <header class="navigation-header">
    <h2><span class="navigation-mode"><select aria-label={t('Navigation type')} value={activeTab}
        onchange={(event) => {
          const value = event.currentTarget.value;
          if (value === 'toc' || value === 'search' || value === 'marks') activeTab = value;
        }}>
      <option value="toc">{t('Table of Contents')}</option>
      <option value="search">{t('Search')}</option>
      <option value="marks">{t('Marks')}</option>
    </select><Fa icon={faChevronDown}/></span></h2>
    <button type="button" aria-label={t('Close navigation')} onclick={onClose}><Fa icon={faXmark}/></button>
  </header>
  <div class="navigation-summary" title={t(verticalMode ? 'Vertical' : 'Horizontal')}>
    <span>{bookTitle}</span><span>· {t('{count} chapters', {count: chapters.length})}</span>
  </div>

  <div class="navigation-body min-h-0 flex-1 overflow-hidden">
    {#if activeTab === 'toc'}
      <section class="flex h-full flex-col gap-3" aria-label={t('Table of Contents')}>
        <ol class="navigation-chapters min-h-0 flex-1 overflow-auto pb-8">
          {#each chapters as chapter, index (chapter.reference)}
            <li>
              <button
                  type="button"
                  class="navigation-chapter"

                  aria-current={index === currentChapterIndex ? 'location' : undefined}
                  onclick={() => goToChapter(chapter)}
              >
                <span class="block text-sm font-medium">{chapter.label || t('Chapter {number}', {number: index + 1})}</span>
                <Fa icon={faChevronRight}/>
                {#if index === currentChapterIndex}<span class="sr-only">{t('Current chapter')}</span>{/if}
              </button>
            </li>
          {/each}
        </ol>
      </section>
    {:else if activeTab === 'search'}
      <ReaderSearchPanel
          query={searchQuery}
          results={searchResults}
          activeResultId={activeSearchResultId}
          onQueryInput={onSearchQueryInput}
          onPreviousResult={onPreviousSearchResult}
          onNextResult={onNextSearchResult}
          onResultClick={onSearchResultClick}
      />
    {:else}
      <section class="flex h-full flex-col gap-3" aria-label={t('Marks')}>
        <div class="text-sm text-current">{annotations.length ? t('{count} highlights', {count: annotations.length}) : t('No highlights yet')}</div>
        <ol class="min-h-0 flex-1 space-y-2 overflow-auto pb-8">
          {#each annotations as annotation (annotation.id)}
            <li class="rounded-lg border border-current/10 p-2">
              <button
                  type="button"
                  class="w-full rounded-lg px-2 py-2 text-left transition hover:bg-current/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#70d9ff]/70"
                  onclick={() => onAnnotationClick(annotation)}
              >
                <span class="block max-h-[4.5rem] overflow-hidden text-sm leading-6">{annotation.text}</span>
                <span class="mt-1 block text-xs text-current">{t('Position {position}', {position: annotation.startCharacter})}</span>
              </button>
              <div class="mt-1 flex justify-end">
                <button
                    type="button"
                    class="flex h-8 items-center gap-2 rounded-xl px-3 text-xs text-current/70 transition hover:bg-red-500/15 hover:text-red-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-400/70"
                    aria-label={t('Delete highlight')}
                    onclick={() => onAnnotationDelete(annotation)}
                >
                  <Fa icon={faTrashCan}/>
                  {t('Delete')}
                </button>
              </div>
            </li>
          {/each}
        </ol>
      </section>
    {/if}
  </div>
</aside>

<style>
  .komelia-navigation-scrim { position: fixed; inset: 0; z-index: 55; background: rgb(3 9 17 / 40%); border: 0; touch-action: none; }
  .komelia-navigation-sheet { position: fixed; inset-inline: 0; bottom: 0; z-index: 60; height: min(75dvh, 570px); width: 100%; max-width: 600px; margin-inline: auto; overscroll-behavior: contain; padding-bottom: env(safe-area-inset-bottom, 0px); }
  @media (min-width: 840px) { .komelia-navigation-sheet { inset-inline: auto 16px; top: 16px; bottom: 16px; height: calc(100dvh - 32px); width: 380px; } }
  .navigation-header { display: flex; align-items: center; justify-content: space-between; min-height: 48px; padding-inline: 26px 8px; }
  .navigation-header h2 { font-size: 22px; font-weight: 600; }
  .navigation-header > button { display: grid; place-items: center; width: 48px; height: 48px; }
  .navigation-mode { position: relative; display: inline-flex; align-items: center; }
  .navigation-mode select { appearance: none; background: transparent; border: 0; min-height: 48px; max-width: 260px; padding: 0 24px 0 0; font: inherit; }
  .navigation-mode option { color: #13283e; background: #f4f7fb; }
  .navigation-mode :global(svg) { pointer-events: none; position: absolute; right: 0; width: 12px; height: 12px; opacity: .7; }
  .navigation-summary { display: flex; gap: 8px; align-items: center; margin-top: -8px; padding: 0 26px 10px; font-size: 12px; line-height: 18px; }
  @media (min-width: 840px) { .navigation-summary { margin-top: 0; } }
  .navigation-summary > span:first-child { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .navigation-summary > span:last-child { flex: none; }
  .navigation-body { padding: 0 26px; }
  .navigation-chapters { overflow-x: hidden; }
  .navigation-chapters > li { border-bottom: 1px solid rgb(176 211 242 / 17%); }
  .navigation-chapter { display: flex; width: calc(100% + 24px); margin-inline: -12px; min-height: 48px; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 12px; border-radius: 10px; text-align: left; }
  .navigation-chapter[aria-current="location"] { color: #70d9ff !important; background: #18384c !important; border-left: 3px solid #70d9ff !important; padding-left: 9px; }
  .navigation-chapter :global(svg) { flex: none; width: 16px; height: 16px; }
  :global([data-komelia-theme="LIGHT"]) .navigation-chapter[aria-current="location"] { color: #00638d !important; background: #dceef7 !important; }
</style>
