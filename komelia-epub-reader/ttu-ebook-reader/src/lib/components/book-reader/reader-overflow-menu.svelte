<script module lang="ts">
  import type {IconDefinition} from '@fortawesome/fontawesome-svg-core';

  export interface ReaderMenuAction {
    label: string;
    icon: IconDefinition;
    onSelect: () => void;
    active?: boolean;
  }
</script>

<script lang="ts">
  import {faEllipsisVertical} from '@fortawesome/free-solid-svg-icons';
  import {tick} from 'svelte';
  import Fa from 'svelte-fa';
  import {clickOutside} from '$lib/functions/use-click-outside';
  import {t} from '$lib/i18n';

  interface Props {
    actions: ReaderMenuAction[];
    extraInfo?: string;
  }

  let {actions, extraInfo}: Props = $props();
  let isOpen = $state(false);
  let trigger = $state<HTMLButtonElement>();
  let menu = $state<HTMLDivElement>();
  let container = $state<HTMLDivElement>();

  function closeMenu(restoreFocus = false): void {
    isOpen = false;
    if (restoreFocus) trigger?.focus();
  }

  async function openMenu(focusLast = false): Promise<void> {
    isOpen = true;
    await tick();
    const buttons = menu?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]');
    if (!buttons?.length) return;
    buttons[focusLast ? buttons.length - 1 : 0]?.focus();
  }

  function handleTriggerKeydown(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      event.stopPropagation();
      void openMenu(event.key === 'ArrowUp');
    }
  }

  function handleMenuKeydown(event: KeyboardEvent): void {
    event.stopPropagation();
    if (!menu) return;
    if (event.key === 'Escape' || event.key === 'Tab') {
      if (event.key === 'Escape') event.preventDefault();
      closeMenu(true);
      return;
    }
    const buttons = [...menu.querySelectorAll<HTMLButtonElement>('[role="menuitem"]')];
    const currentIndex = buttons.findIndex(button => button === document.activeElement);
    let nextIndex: number;
    if (event.key === 'ArrowDown') nextIndex = (currentIndex + 1) % buttons.length;
    else if (event.key === 'ArrowUp') nextIndex = (currentIndex - 1 + buttons.length) % buttons.length;
    else if (event.key === 'Home') nextIndex = 0;
    else if (event.key === 'End') nextIndex = buttons.length - 1;
    else return;
    event.preventDefault();
    buttons[nextIndex]?.focus();
  }

  function selectAction(action: ReaderMenuAction): void {
    closeMenu(true);
    action.onSelect();
  }
</script>

<div class="reader-overflow" bind:this={container} use:clickOutside={() => closeMenu()}
     onfocusout={(event) => { if (!(event.relatedTarget instanceof Node) || !container?.contains(event.relatedTarget)) closeMenu(); }}>
  <button type="button" class="komelia-reader-action" bind:this={trigger}
          aria-label={t('More options')} title={t('More options')} aria-haspopup="menu" aria-expanded={isOpen}
          onclick={() => { if (isOpen) closeMenu(); else void openMenu(); }} onkeydown={handleTriggerKeydown}>
    <Fa icon={faEllipsisVertical}/>
  </button>
  {#if isOpen}
    <div class="reader-menu" data-komelia-chrome role="menu" aria-label={t('More options')}
         bind:this={menu} onkeydown={handleMenuKeydown} tabindex="-1">
      {#each actions as action (action.label)}
        <button type="button" role="menuitem" tabindex="-1" data-komelia-highlight={action.active ?? false}
                onclick={() => selectAction(action)}>
          <Fa icon={action.icon}/><span>{action.label}</span>
        </button>
      {/each}
      {#if extraInfo}<p>{extraInfo}</p>{/if}
    </div>
  {/if}
</div>

<style>
  .reader-overflow { position: relative; flex: none; }
  .reader-menu {
    position: absolute; inset-inline-end: 0; top: calc(100% + 8px); z-index: 1;
    width: min(280px, calc(100vw - 24px)); max-height: min(70vh, 560px); overflow-y: auto;
    padding: 6px; border-radius: 16px; background: #1d2e42; color: #f4f7fc;
  }
  .reader-menu button { display: flex; align-items: center; gap: 12px; width: 100%; padding: 8px 12px; text-align: start; border-radius: 10px; font-size: 14px; }
  .reader-menu button :global(svg) { width: 20px; height: 20px; flex: none; }
  .reader-menu p { margin: 8px 12px; font-size: 14px; }
</style>
