<script lang="ts">
  import {faPen, faTrash} from '@fortawesome/free-solid-svg-icons';
  import type {ToggleOption} from '$lib/components/button-toggle-group/toggle-option';
  import Ripple from '$lib/components/ripple.svelte';
  import {availableThemes} from '$lib/data/theme-option';
  import {createEventDispatcher} from 'svelte';
  import Fa from 'svelte-fa';

  export let options: ToggleOption<any>[];
  export let selectedOptionId: any;
  export let themeSwatches = false;

  const dispatch = createEventDispatcher<{
    edit: string;
    delete: string;
  }>();

  function mapToStyleString(style: Record<string, string> | undefined) {
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
          title={option.id}
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
        {option.text}
        <Ripple/>
      </button>
      {#if option.showIcons && option.id === selectedOptionId && !availableThemes.has(option.id)}
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
  .theme-grid > div { min-width: 0; }
  .theme-grid [data-komelia-theme-swatch] { width: 100%; min-height: 48px; margin: 0; padding: 6px; border-width: 2px; font-size: 14px; line-height: 20px; overflow-wrap: anywhere; }
</style>
