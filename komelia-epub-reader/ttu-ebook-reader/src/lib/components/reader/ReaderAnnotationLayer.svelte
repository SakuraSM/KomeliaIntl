<script lang="ts">
  import {tick} from 'svelte';
  import type {SectionWithProgress} from '$lib/components/book-reader/book-toc/book-toc';
  import type {ReaderAnnotation} from '$lib/data/reader-annotation';
  import {
    renderReaderMarks,
    type ReaderSearchResult
  } from '$lib/functions/reader-interactions';

  interface Props {
    annotations: ReaderAnnotation[];
    activeSearchResult?: ReaderSearchResult;
    sectionData?: SectionWithProgress[];
    htmlContent: string;
    exploredCharCount: number;
  }

  let {
    annotations,
    activeSearchResult,
    sectionData,
    htmlContent,
    exploredCharCount
  }: Props = $props();

  $effect(() => {
    htmlContent;
    exploredCharCount;
    annotations;
    activeSearchResult;
    sectionData;

    tick().then(() => {
      renderReaderMarks(
        document.querySelector('.book-content'),
        annotations,
        activeSearchResult,
        sectionData
      );
    });
  });
</script>
