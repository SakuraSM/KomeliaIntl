<script lang="ts">
  import {faChevronDown, faChevronUp, faMagnifyingGlass} from '@fortawesome/free-solid-svg-icons';
  import Fa from 'svelte-fa';
  import type {ReaderSearchResult} from '$lib/functions/reader-interactions';

  interface Props {
    query: string;
    results: ReaderSearchResult[];
    activeResultId?: string;
    onQueryInput: (query: string) => void;
    onPreviousResult: () => void;
    onNextResult: () => void;
    onResultClick: (result: ReaderSearchResult) => void;
  }

  let {
    query,
    results,
    activeResultId,
    onQueryInput,
    onPreviousResult,
    onNextResult,
    onResultClick
  }: Props = $props();
</script>

<section class="flex min-h-0 flex-1 flex-col gap-3" aria-label="书内搜索">
  <label class="flex items-center gap-2 rounded-lg border border-current/15 px-3 py-2 focus-within:ring-2 focus-within:ring-cyan-400/70">
    <Fa icon={faMagnifyingGlass} class="text-current/55"/>
    <span class="sr-only">搜索本书</span>
    <input
        class="min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-current/45"
        type="search"
        value={query}
        placeholder="搜索本书"
        autocomplete="off"
        oninput={(event) => onQueryInput(event.currentTarget.value)}
    />
  </label>

  <div class="flex items-center justify-between gap-3 text-sm">
    <span class="text-current/65">
      {#if query.trim()}
        {results.length ? `${results.length} 条结果` : '没有结果'}
      {:else}
        输入关键词后在当前书籍内查找
      {/if}
    </span>
    <div class="flex gap-1">
      <button
          type="button"
          class="flex h-9 w-9 items-center justify-center rounded-full transition hover:bg-current/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/70 disabled:pointer-events-none disabled:opacity-35"
          aria-label="上一条搜索结果"
          disabled={!results.length}
          onclick={onPreviousResult}
      >
        <Fa icon={faChevronUp}/>
      </button>
      <button
          type="button"
          class="flex h-9 w-9 items-center justify-center rounded-full transition hover:bg-current/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/70 disabled:pointer-events-none disabled:opacity-35"
          aria-label="下一条搜索结果"
          disabled={!results.length}
          onclick={onNextResult}
      >
        <Fa icon={faChevronDown}/>
      </button>
    </div>
  </div>

  <div class="min-h-0 flex-1 overflow-auto pb-8">
    {#if results.length}
      <ol class="space-y-1">
        {#each results as result, index (result.id)}
          <li>
            <button
                type="button"
                class={`w-full rounded-lg px-3 py-2 text-left transition hover:bg-current/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/70 ${result.id === activeResultId ? 'bg-cyan-400/15' : ''}`}
                aria-current={result.id === activeResultId ? 'true' : undefined}
                onclick={() => onResultClick(result)}
            >
              <span class="mb-1 block text-xs text-current/55">
                {index + 1}. {result.chapterLabel}
              </span>
              <span class="block text-sm leading-6">
                <span class="text-current/65">{result.snippetBefore}</span>
                <mark class="rounded bg-yellow-300/75 px-0.5 text-slate-950">{result.snippetMatch}</mark>
                <span class="text-current/65">{result.snippetAfter}</span>
              </span>
            </button>
          </li>
        {/each}
      </ol>
    {/if}
  </div>
</section>
