<script lang="ts">
  import {faBookmark, faList, faMagnifyingGlass, faTrashCan, faXmark} from '@fortawesome/free-solid-svg-icons';
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
  }

  let {
    sectionData,
    verticalMode,
    exploredCharCount,
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
    onAnnotationDelete
  }: Props = $props();

  let activeTab: DrawerTab = $state('toc');

  let chapters = $derived(sectionData.filter((section) => !section.parentChapter));
  let currentChapterData = $derived(getChapterData(sectionData));
  let currentChapterIndex = $derived(currentChapterData[1]);

  function goToChapter(chapter: SectionWithProgress) {
    nextChapter$.next(chapter.reference);
    onClose();
  }

  function getChapterProgressLabel(chapter: SectionWithProgress) {
    const start = chapter.startCharacter || 0;
    const characters = chapter.characters || 0;
    const current = Math.min(Math.max(exploredCharCount - start, 0), characters);

    return `${current} / ${characters}`;
  }
</script>

<aside
    class="writing-horizontal-tb fixed left-0 top-0 z-[60] flex h-full w-full max-w-xl flex-col shadow-2xl shadow-black/25"
    style:color={fontColor}
    style:background-color={backgroundColor}
    in:fly|local={{ x: -100, duration: 130, easing: quintInOut }}
    role="dialog"
    aria-modal="true"
    aria-label="阅读导航"
>
  <div class="flex items-center justify-between gap-3 border-b border-current/10 px-4 py-3">
    <nav class="flex rounded-full bg-current/10 p-1" aria-label="导航类型">
      <button
          type="button"
          class={`flex h-9 items-center gap-2 rounded-full px-3 text-sm transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/70 ${activeTab === 'toc' ? 'bg-cyan-400/20' : ''}`}
          aria-current={activeTab === 'toc' ? 'page' : undefined}
          onclick={() => (activeTab = 'toc')}
      >
        <Fa icon={faList}/>
        目录
      </button>
      <button
          type="button"
          class={`flex h-9 items-center gap-2 rounded-full px-3 text-sm transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/70 ${activeTab === 'search' ? 'bg-cyan-400/20' : ''}`}
          aria-current={activeTab === 'search' ? 'page' : undefined}
          onclick={() => (activeTab = 'search')}
      >
        <Fa icon={faMagnifyingGlass}/>
        搜索
      </button>
      <button
          type="button"
          class={`flex h-9 items-center gap-2 rounded-full px-3 text-sm transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/70 ${activeTab === 'marks' ? 'bg-cyan-400/20' : ''}`}
          aria-current={activeTab === 'marks' ? 'page' : undefined}
          onclick={() => (activeTab = 'marks')}
      >
        <Fa icon={faBookmark}/>
        标记
      </button>
    </nav>
    <button
        type="button"
        class="flex h-10 w-10 items-center justify-center rounded-full transition hover:bg-current/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/70"
        aria-label="关闭导航"
        onclick={onClose}
    >
      <Fa icon={faXmark}/>
    </button>
  </div>

  <div class="min-h-0 flex-1 overflow-hidden px-4 pt-4">
    {#if activeTab === 'toc'}
      <section class="flex h-full flex-col gap-3" aria-label="目录">
        <div class="flex items-center justify-between text-sm text-current/65">
          <span>{chapters.length} 个章节</span>
          <span>{verticalMode ? '竖排' : '横排'}</span>
        </div>
        <ol class="min-h-0 flex-1 space-y-1 overflow-auto pb-8">
          {#each chapters as chapter, index (chapter.reference)}
            <li>
              <button
                  type="button"
                  class={`w-full rounded-lg px-3 py-2 text-left transition hover:bg-current/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/70 ${index === currentChapterIndex ? 'bg-cyan-400/15' : ''}`}
                  class:opacity-45={chapter.progress === 100 && index !== currentChapterIndex}
                  aria-current={index === currentChapterIndex ? 'location' : undefined}
                  onclick={() => goToChapter(chapter)}
              >
                <span class="block text-sm font-medium">{chapter.label || `章节 ${index + 1}`}</span>
                <span class="mt-1 block text-xs text-current/55">{getChapterProgressLabel(chapter)}</span>
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
      <section class="flex h-full flex-col gap-3" aria-label="标记">
        <div class="text-sm text-current/65">{annotations.length ? `${annotations.length} 条高亮` : '还没有高亮标记'}</div>
        <ol class="min-h-0 flex-1 space-y-2 overflow-auto pb-8">
          {#each annotations as annotation (annotation.id)}
            <li class="rounded-lg border border-current/10 p-2">
              <button
                  type="button"
                  class="w-full rounded-md px-2 py-2 text-left transition hover:bg-current/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/70"
                  onclick={() => onAnnotationClick(annotation)}
              >
                <span class="block max-h-[4.5rem] overflow-hidden text-sm leading-6">{annotation.text}</span>
                <span class="mt-1 block text-xs text-current/55">位置 {annotation.startCharacter}</span>
              </button>
              <div class="mt-1 flex justify-end">
                <button
                    type="button"
                    class="flex h-8 items-center gap-2 rounded-full px-3 text-xs text-current/70 transition hover:bg-red-500/15 hover:text-red-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-400/70"
                    aria-label="删除高亮"
                    onclick={() => onAnnotationDelete(annotation)}
                >
                  <Fa icon={faTrashCan}/>
                  删除
                </button>
              </div>
            </li>
          {/each}
        </ol>
      </section>
    {/if}
  </div>
</aside>
