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
  import {t} from '$lib/i18n';

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
    aria-label={t('Selection actions')}
>
  <button
      type="button"
      class="flex h-10 min-w-0 flex-1 items-center justify-center gap-1.5 rounded-full px-2 text-xs font-medium transition hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95"
      title={t('Highlight')}
      aria-label={t('Highlight selected text')}
      onclick={onHighlight}
  >
    <Fa icon={faHighlighter}/>
    <span>{t('Highlight')}</span>
  </button>
  <button
      type="button"
      class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95"
      title={t('Bookmark')}
      aria-label={t('Create bookmark from selected text')}
      onclick={onBookmark}
  >
    <Fa icon={faBookmark}/>
  </button>
  <button
      type="button"
      class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95"
      title={t('Copy')}
      aria-label={t('Copy selected text')}
      onclick={onCopy}
  >
    <Fa icon={faCopy}/>
  </button>
  <button
      type="button"
      class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95"
      title={t('Search web')}
      aria-label={t('Search web')}
      onclick={onWebSearch}
  >
    <Fa icon={faMagnifyingGlass}/>
  </button>
  <button
      type="button"
      class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 active:scale-95"
      title={t('Cancel selection')}
      aria-label={t('Cancel selection')}
      onclick={onCancel}
  >
    <Fa icon={faXmark}/>
  </button>
</div>
