<script lang="ts">
  import {createEventDispatcher} from 'svelte';
  import {t} from '$lib/i18n';
  export let text: string;
  export let paperColor: string;
  export let inkColor: string;
  const dispatch = createEventDispatcher<{close: void}>();
</script>

<section class="rounded-2xl px-5 py-4 shadow-lg" style:background-color={paperColor} style:color={inkColor}>
  <header class="mb-2 flex items-center justify-between gap-8">
    <h2 class="font-semibold">{t('Footnote')}</h2>
    <button type="button" class="min-h-11 px-2 underline" on:click={() => dispatch('close')}>{t('Close')}</button>
  </header>
  <div class="note-text">{text}</div>
</section>

<style>
  section { width: min(78vw, 32rem); }
  .note-text { max-height: min(55vh, 26rem); overflow: auto; overscroll-behavior: contain;
    white-space: pre-line; overflow-wrap: anywhere; line-height: 1.65; }
</style>
