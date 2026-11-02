<script lang="ts" generics="OptionId">
  import {faCheck, faPen, faTrash} from '@fortawesome/free-solid-svg-icons';
  import type {ToggleOption} from '$lib/components/button-toggle-group/toggle-option';
  import Ripple from '$lib/components/ripple.svelte';
  import {availableThemes} from '$lib/data/theme-option';
  import {createEventDispatcher} from 'svelte';
  import Fa from 'svelte-fa';

  export let options: ToggleOption<OptionId>[];
  export let selectedOptionId: OptionId;
  export let themeSwatches = false;

  const dispatch = createEventDispatcher<{
    edit: OptionId;
    delete: OptionId;
  }>();

  function mapToStyleString(style: Record<string, string> | undefined): string {
    if (!style) return '';

    return Object.entries(style)
      .map(([key, value]) => `${key}: ${value}`)
      .join(';');
  }
</script>

<div class="-m-1 flex flex-wrap" class:theme-grid={themeSwatches}>
  {#each options as option}
    <div class="flex">
      <button
          type="button"
          title={String(option.id)}
          data-komelia-toggle-option
          data-komelia-theme-swatch={themeSwatches ? '' : undefined}
          aria-label={option.text}
          aria-pressed={option.id === selectedOptionId}
          class="m-1 rounded-md border-2 border-gray-400 p-2 text-black text-lg"
          class:border-4={option.thickBorders && option.id === selectedOptionId}
          class:border-blue-300={option.id === selectedOptionId}
          class:bg-gray-700={option.id === selectedOptionId}
          class:text-white={(option.id === selectedOptionId)}
          class:bg-white={(option.id !== selectedOptionId )}
          style={mapToStyleString(option.style)}
          on:click={() => (selectedOptionId = option.id)}
      >
        {#if themeSwatches}
          <span class="theme-sample" aria-hidden="true">Aa</span>
          {#if option.id === selectedOptionId}<span class="theme-selected" aria-hidden="true"><Fa icon={faCheck}/></span>{/if}
        {:else}{option.text}{/if}
        <Ripple/>
      </button>
      {#if themeSwatches}<span class="theme-name" aria-hidden="true">{option.text}</span>{/if}
      {#if option.showIcons && option.id === selectedOptionId && !(typeof option.id === 'string' && availableThemes.has(option.id))}
        <div class="flex flex-col justify-around mr-2">
          <button on:click={() => dispatch('edit', option.id)}>
            <Fa icon={faPen}/>
          </button>
          <button on:click={() => dispatch('delete', option.id)}>
            <Fa icon={faTrash}/>
          </button>
        </div>
      {/if}
    </div>
  {/each}

  <slot/>
</div>

<style>
  .theme-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin: 0; }
  .theme-grid > div { min-width: 0; flex-direction: column; gap: 6px; }
  .theme-grid [data-komelia-theme-swatch] { width: 100%; position: relative; height: 56px; min-height: 56px; margin: 0; padding: 6px; border-width: 2px; font-size: 14px; line-height: 20px; overflow-wrap: anywhere; }
  .theme-sample { font: 24px/1.2 Georgia, serif; }
  .theme-name { text-align: center; font-size: 12px; line-height: 18px; }
  .theme-selected { position: absolute; display: grid; place-items: center; top: 4px; right: 4px; width: 16px; height: 16px; border-radius: 50%; background: #70d9ff; color: #00324a; font-size: 10px; }
</style>
