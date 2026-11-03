<script lang="ts">
  import { faCircleQuestion } from '@fortawesome/free-solid-svg-icons';
  import Fa from 'svelte-fa';
  import Popover from '../popover/popover.svelte';

  export let title: string;
  export let tooltip = '';
  export let applyHeaderClasses = true;
  export let inline = false;
</script>

<section class="pb-4" class:inline-row={inline}>
  <div class="flex items-center">
    <h2 class="mb-2" class:text-sm={applyHeaderClasses} class:font-medium={applyHeaderClasses}>
      {#if tooltip}
        <Popover contentText={tooltip} contentStyles="padding: 0.5rem;">
          <Fa icon={faCircleQuestion} slot="icon" class="mx-2" />
          <span class="capitalize">{title}</span>
        </Popover>
      {:else}
        <span class="capitalize">{title}</span>
      {/if}
    </h2>
    <slot name="header" />
  </div>
  <div>
    <slot />
  </div>
</section>

<style>
  .inline-row { display: flex; align-items: center; gap: 12px; min-height: 68px; }
  .inline-row > div:first-child { flex: none; max-width: 40%; }
  .inline-row > div:last-child { flex: 1; min-width: 0; }
  .inline-row h2 { margin: 0; }
</style>
