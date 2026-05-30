<script lang="ts">
  import {
    faBackwardStep,
    faBars,
    faChevronLeft,
    faChevronRight,
    faCopy,
    faForwardStep,
    faList,
    faMinus,
    faPause,
    faPlay,
    faPlus
  } from '@fortawesome/free-solid-svg-icons';
  import Fa from 'svelte-fa';
  import {ViewMode} from '$lib/data/view-mode';

  interface Props {
    showHeader: boolean;
    showImageGallery: boolean;
    navigationOpen: boolean;
    hasBookData: boolean;
    hasChapterData: boolean;
    hasPageManager: boolean;
    showFooter: boolean;
    bookCharCount: number;
    showCharacterCounter: boolean;
    readingProgressPercent: number;
    readingProgressLabel: string;
    activeChapterLabel: string;
    tooltipColor?: string;
    viewMode: ViewMode;
    autoScrollMultiplier: number;
    autoScrollEnabled: boolean;
    onShowMenu: () => void;
    onOpenNavigation: () => void;
    onPreviousPage: () => void;
    onNextPage: () => void;
    onPreviousChapter: () => void;
    onNextChapter: () => void;
    onCopyProgress: (event: MouseEvent) => void;
    onToggleFooter: () => void;
    onToggleAutoScroll: () => void;
    onAutoScrollMultiplierChange: (offset: number) => void;
  }

  let {
    showHeader,
    showImageGallery,
    navigationOpen,
    hasBookData,
    hasChapterData,
    hasPageManager,
    showFooter,
    bookCharCount,
    showCharacterCounter,
    readingProgressPercent,
    readingProgressLabel,
    activeChapterLabel,
    tooltipColor,
    viewMode,
    autoScrollMultiplier,
    autoScrollEnabled,
    onShowMenu,
    onOpenNavigation,
    onPreviousPage,
    onNextPage,
    onPreviousChapter,
    onNextChapter,
    onCopyProgress,
    onToggleFooter,
    onToggleAutoScroll,
    onAutoScrollMultiplierChange
  }: Props = $props();

  let readerControlsVisible = $derived(hasBookData && !showHeader && !showImageGallery && !navigationOpen);
</script>

<button
    type="button"
    class="fixed inset-x-0 top-0 z-10 h-10 w-full bg-gradient-to-b from-black/20 to-transparent opacity-0 transition-opacity hover:opacity-100 focus-visible:opacity-100 focus-visible:outline-none"
    aria-label="打开阅读菜单"
    onclick={onShowMenu}
></button>

{#if !showHeader}
  <div class="writing-horizontal-tb fixed left-3 top-3 z-20 flex gap-2">
    {#if hasChapterData}
      <button
          type="button"
          title="打开导航"
          aria-label="打开导航"
          class="flex h-10 items-center gap-2 rounded-full bg-slate-950/72 px-3 text-sm font-medium text-white shadow-lg shadow-black/20 backdrop-blur-md transition hover:bg-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95"
          onclick={onOpenNavigation}
      >
        <Fa icon={faList}/>
        <span>导航</span>
      </button>
    {/if}
    <button
        type="button"
        title="打开阅读菜单"
        aria-label="打开阅读菜单"
        class="flex h-10 items-center gap-2 rounded-full bg-slate-950/72 px-3 text-sm font-medium text-white shadow-lg shadow-black/20 backdrop-blur-md transition hover:bg-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95"
        onclick={onShowMenu}
    >
      <Fa icon={faBars}/>
      <span>菜单</span>
    </button>
  </div>
{/if}

{#if readerControlsVisible}
  <button
      type="button"
      class="writing-horizontal-tb fixed left-3 top-1/2 z-10 hidden h-14 w-11 -translate-y-1/2 items-center justify-center rounded-full bg-slate-950/40 text-white shadow-lg shadow-black/20 backdrop-blur-md transition hover:bg-slate-950/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95 disabled:pointer-events-none disabled:opacity-30 md:flex"
      aria-label="上一页"
      title="上一页"
      disabled={!hasPageManager}
      onclick={onPreviousPage}
  >
    <Fa icon={faChevronLeft}/>
  </button>
  <button
      type="button"
      class="writing-horizontal-tb fixed right-3 top-1/2 z-10 hidden h-14 w-11 -translate-y-1/2 items-center justify-center rounded-full bg-slate-950/40 text-white shadow-lg shadow-black/20 backdrop-blur-md transition hover:bg-slate-950/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95 disabled:pointer-events-none disabled:opacity-30 md:flex"
      aria-label="下一页"
      title="下一页"
      disabled={!hasPageManager}
      onclick={onNextPage}
  >
    <Fa icon={faChevronRight}/>
  </button>
{/if}

<div
    id="ttu-page-footer"
    class="writing-horizontal-tb pointer-events-none fixed inset-x-0 bottom-0 z-10"
    aria-live="polite"
>
  {#if bookCharCount}
    <div class="h-1 w-full bg-black/10">
      <div
          class="h-full rounded-r-full bg-cyan-400 shadow-[0_0_12px_rgba(34,211,238,0.55)] transition-[width] duration-300 ease-out"
          style:width={`${readingProgressPercent}%`}
      ></div>
    </div>
  {/if}

  {#if showFooter && bookCharCount}
    <div class="mx-auto mb-3 flex max-w-[calc(100vw-1.5rem)] items-center justify-center gap-2 px-3 sm:mb-4">
      <div class="pointer-events-auto flex max-w-full items-center gap-1 overflow-hidden rounded-full bg-slate-950/78 p-1 text-white shadow-xl shadow-black/25 backdrop-blur-md">
        <button
            type="button"
            class="hidden h-10 w-10 shrink-0 items-center justify-center rounded-full text-white/85 transition hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95 sm:flex"
            title="上一章"
            aria-label="上一章"
            onclick={onPreviousChapter}
        >
          <Fa icon={faBackwardStep}/>
        </button>
        <button
            type="button"
            class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-white/85 transition hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95 disabled:pointer-events-none disabled:opacity-40"
            title="上一页"
            aria-label="上一页"
            disabled={!hasPageManager}
            onclick={onPreviousPage}
        >
          <Fa icon={faChevronLeft}/>
        </button>

        <button
            type="button"
            title="复制阅读进度"
            aria-label="复制阅读进度"
            class="min-w-0 rounded-full px-3 py-2 text-left transition hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80"
            class:invisible={!showCharacterCounter}
            style:color={tooltipColor}
            onclick={onCopyProgress}
        >
          <span class="block max-w-[44vw] truncate text-[11px] font-medium leading-4 text-white/80 sm:max-w-xs">
            {activeChapterLabel}
          </span>
          <span class="flex items-center gap-2 text-xs font-semibold leading-4 text-white">
            <Fa icon={faCopy}/>
            {readingProgressLabel}
          </span>
        </button>

        <button
            type="button"
            class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-white/85 transition hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95 disabled:pointer-events-none disabled:opacity-40"
            title="下一页"
            aria-label="下一页"
            disabled={!hasPageManager}
            onclick={onNextPage}
        >
          <Fa icon={faChevronRight}/>
        </button>
        <button
            type="button"
            class="hidden h-10 w-10 shrink-0 items-center justify-center rounded-full text-white/85 transition hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95 sm:flex"
            title="下一章"
            aria-label="下一章"
            onclick={onNextChapter}
        >
          <Fa icon={faForwardStep}/>
        </button>

        {#if viewMode === ViewMode.Continuous}
          <div class="hidden h-8 w-px bg-white/15 sm:block"></div>
          <button
              type="button"
              class="hidden h-10 w-10 shrink-0 items-center justify-center rounded-full text-white/85 transition hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95 sm:flex"
              title="降低自动滚动速度"
              aria-label="降低自动滚动速度"
              onclick={() => onAutoScrollMultiplierChange(-1)}
          >
            <Fa icon={faMinus}/>
          </button>
          <button
              type="button"
              class="hidden h-10 items-center justify-center gap-2 rounded-full px-3 text-xs font-semibold text-white transition hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95 sm:flex"
              title="切换自动滚动"
              aria-label="切换自动滚动"
              aria-pressed={autoScrollEnabled}
              onclick={onToggleAutoScroll}
          >
            <Fa icon={autoScrollEnabled ? faPause : faPlay}/>
            {autoScrollMultiplier}x
          </button>
          <button
              type="button"
              class="hidden h-10 w-10 shrink-0 items-center justify-center rounded-full text-white/85 transition hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95 sm:flex"
              title="提高自动滚动速度"
              aria-label="提高自动滚动速度"
              onclick={() => onAutoScrollMultiplierChange(1)}
          >
            <Fa icon={faPlus}/>
          </button>
        {/if}
      </div>
    </div>
  {/if}

  <button
      type="button"
      class="pointer-events-auto fixed bottom-2 left-2 h-9 rounded-full bg-slate-950/60 px-3 text-xs font-medium text-white shadow-lg shadow-black/20 backdrop-blur-md transition hover:bg-slate-950/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95"
      aria-label={showFooter ? '隐藏底部阅读控制' : '显示底部阅读控制'}
      aria-expanded={showFooter}
      onclick={onToggleFooter}
  >
    {showFooter ? '隐藏' : '进度'}
  </button>
</div>
