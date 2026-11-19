<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import { buttonClasses } from '$lib/css-classes';
  import {t} from '$lib/i18n';
  import DialogTemplate from '$lib/components/dialog-template.svelte';
  import Ripple from '$lib/components/ripple.svelte';

  export let title: string;

  export let message: string;
  export let onRetry: (() => void) | undefined = undefined;
  export let onExitWithoutSaving: (() => void) | undefined = undefined;

  const dispatch = createEventDispatcher<{
    close: void;
  }>();
</script>

<DialogTemplate>
  <svelte:fragment slot="header">{title}</svelte:fragment>
  <svelte:fragment slot="content">
    <p>{message}</p>
  </svelte:fragment>
  <svelte:fragment slot="footer">
    <button class={buttonClasses} on:click={() => dispatch('close')}>
      {t(onRetry ? 'Keep reading' : 'Close')}
      <Ripple />
    </button>
    {#if onExitWithoutSaving}
      <button class={buttonClasses} on:click={() => { dispatch('close'); onExitWithoutSaving?.(); }}>
        {t('Exit without saving')}
        <Ripple />
      </button>
    {/if}
    {#if onRetry}
      <button class={buttonClasses} on:click={() => { dispatch('close'); onRetry?.(); }}>
        {t('Retry')}
        <Ripple />
      </button>
    {/if}
  </svelte:fragment>
</DialogTemplate>
