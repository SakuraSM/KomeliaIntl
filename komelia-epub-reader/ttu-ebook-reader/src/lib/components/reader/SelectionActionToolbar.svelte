<script lang="ts">
  import {
    faBookmark,
    faCopy,
    faHighlighter,
    faMagnifyingGlass,
    faXmark
  } from '@fortawesome/free-solid-svg-icons';
  import Fa from 'svelte-fa';
  import type {SelectionToolbarState} from '$lib/functions/reader-interactions';

  interface Props {
    selection: SelectionToolbarState;
    onHighlight: () => void;
    onBookmark: () => void;
    onCopy: () => void;
    onWebSearch: () => void;
    onCancel: () => void;
  }

  let {
    selection,
    onHighlight,
    onBookmark,
    onCopy,
    onWebSearch,
    onCancel
  }: Props = $props();

  const TOOLBAR_WIDTH = 304;
  const TOOLBAR_OFFSET = 12;

  let left = $derived(Math.max(8, Math.min(window.innerWidth - TOOLBAR_WIDTH - 8, selection.rect.left + selection.rect.width / 2 - TOOLBAR_WIDTH / 2)));
  let top = $derived(Math.max(8, selection.rect.top - 48 - TOOLBAR_OFFSET));
</script>

<div
    class="writing-horizontal-tb fixed z-[70] flex w-[304px] items-center gap-1 rounded-full bg-slate-950/90 p-1 text-white shadow-2xl shadow-black/35 backdrop-blur-md"
    style:left={`${left}px`}
    style:top={`${top}px`}
    role="toolbar"
    aria-label="选中文字操作"
>
  <button
      type="button"
      class="flex h-10 min-w-0 flex-1 items-center justify-center gap-1.5 rounded-full px-2 text-xs font-medium transition hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95"
      title="高亮"
      aria-label="高亮选中文字"
      onclick={onHighlight}
  >
    <Fa icon={faHighlighter}/>
    <span>高亮</span>
  </button>
  <button
      type="button"
      class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95"
      title="书签"
      aria-label="用选中文字创建书签"
      onclick={onBookmark}
  >
    <Fa icon={faBookmark}/>
  </button>
  <button
      type="button"
      class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95"
      title="复制"
      aria-label="复制选中文字"
      onclick={onCopy}
  >
    <Fa icon={faCopy}/>
  </button>
  <button
      type="button"
      class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95"
      title="搜索网络"
      aria-label="搜索网络"
      onclick={onWebSearch}
  >
    <Fa icon={faMagnifyingGlass}/>
  </button>
  <button
      type="button"
      class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95"
      title="取消选择"
      aria-label="取消选择"
      onclick={onCancel}
  >
    <Fa icon={faXmark}/>
  </button>
</div>
