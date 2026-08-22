<script lang="ts">
  import {serifFontFamily$, loadExternalSettings, userFonts$} from '$lib/data/store';
  import {type Dialog, dialogManager} from '$lib/data/dialog-manager';
  import {isMobile, isMobile$} from '$lib/functions/utils';
  import SettingsContent from "$lib/components/settings/settings-content.svelte";
  import SettingsHeader from "$lib/components/settings/settings-header.svelte";
  import Reader from "$lib/components/Reader.svelte";
  import {pxScreen} from "$lib/css-classes";
  import {faSpinner} from "@fortawesome/free-solid-svg-icons";
  import {loadFont} from "$lib/data/fonts";
  import Fa from "svelte-fa";
  import {logger} from "$lib/data/logger";
  import {tick} from 'svelte';
  import {setLocale} from '$lib/i18n';
  import {externalFunctions} from '$lib/external';

  let showSettings = $state(false)

  let dialogs: Dialog[] = $state([]);
  let clickOnCloseDisabled = $state(false);
  let zIndex = $state('');
  let dialogContainer: HTMLDivElement | undefined = $state();
  let dialogTrigger: HTMLElement | null = null;

  let initPromise = init()

  async function init() {
    setLocale((await externalFunctions.getLocale()) ?? navigator.language);
    await loadExternalSettings()
    isMobile$.next(isMobile(window));

    try {
      await Promise.all($userFonts$.map((font) => loadFont(font)))
    } catch (error: unknown) {
      logger.error(getErrorMessage(error))
    }
  }

  function closeAllDialogs() {
    dialogManager.dialogs$.next([]);
    clickOnCloseDisabled = false;
    zIndex = '';
  }

  function getErrorMessage(error: unknown): string {
    return error instanceof Error ? error.message : String(error);
  }

  function getFocusableDialogElements(): HTMLElement[] {
    if (!dialogContainer) return [];
    const selector = [
      'a[href]',
      'button:not([disabled])',
      'textarea:not([disabled])',
      'input:not([disabled])',
      'select:not([disabled])',
      '[tabindex]:not([tabindex="-1"])',
    ].join(',');
    return Array.from(dialogContainer.querySelectorAll<HTMLElement>(selector));
  }

  function handleDialogKeydown(event: KeyboardEvent): void {
    if (!dialogs.length) return;
    if (event.key === 'Escape' && !clickOnCloseDisabled) {
      event.preventDefault();
      closeAllDialogs();
      return;
    }
    if (event.key !== 'Tab') return;

    const focusableElements = getFocusableDialogElements();
    if (!focusableElements.length) {
      event.preventDefault();
      dialogContainer?.focus();
      return;
    }
    const firstElement = focusableElements[0];
    const lastElement = focusableElements[focusableElements.length - 1];
    if (event.shiftKey && document.activeElement === firstElement) {
      event.preventDefault();
      lastElement.focus();
    } else if (!event.shiftKey && document.activeElement === lastElement) {
      event.preventDefault();
      firstElement.focus();
    }
  }

  $effect(() => {
    if (!dialogs.length) {
      dialogTrigger?.focus();
      dialogTrigger = null;
      return;
    }
    dialogTrigger ??= document.activeElement instanceof HTMLElement ? document.activeElement : null;
    void tick().then(() => {
      getFocusableDialogElements()[0]?.focus();
      if (document.activeElement === dialogTrigger) dialogContainer?.focus();
    });
  });

  dialogManager.dialogs$.subscribe((d) => {
    clickOnCloseDisabled = d[0]?.disableCloseOnClick ?? false;
    zIndex = d[0]?.zIndex ?? '';
    dialogs = d;
  });

</script>

<svelte:window onkeydown={handleDialogKeydown}/>

{#if dialogs.length > 0}
  <div class="writing-horizontal-tb fixed inset-0 z-50 h-full w-full" style:z-index={zIndex}>
    <button
        type="button"
        aria-label="关闭弹窗"
        class="tap-highlight-transparent absolute inset-0 bg-black/[.38] backdrop-blur-[2px]"
        onclick={() => {
          if (!clickOnCloseDisabled) {
            closeAllDialogs();
          }
        }}
    ></button>

    <div
        bind:this={dialogContainer}
        role="dialog"
        aria-modal="true"
        aria-label="阅读器弹窗"
        tabindex="-1"
        class="relative top-1/2 left-1/2 inline-block max-w-[80vw] -translate-x-1/2 -translate-y-1/2"
    >
      {#each dialogs as dialog}
        {#if typeof dialog.component === 'string'}
          {@html dialog.component}
        {:else}
          <dialog.component {...dialog.component} {...dialog.props} on:close={closeAllDialogs}/>
        {/if}
      {/each}
    </div>
  </div>
{/if}
{#await initPromise}
  <div class="fixed inset-0 flex h-full w-full items-center justify-center text-7xl">
    <Fa icon={faSpinner} spin/>
  </div>
{:then _}
  {#if showSettings}
    <div class="elevation-4 fixed inset-x-0 top-0 z-10">
      <SettingsHeader onExit={() => showSettings = false}/>
    </div>

    <div class="{pxScreen} h-full pt-16 xl:pt-14">
      <div class="max-w-5xl">
        <SettingsContent/>
      </div>
    </div>
  {:else}
    <Reader
        onSettingsClick={() => showSettings=true}
    />
  {/if}

  <span style={`font-family: "${$serifFontFamily$.familyName}"`}></span>
{:catch error}
  <p style="color: red">{error.message}</p>
{/await}
