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
  import {tick, onDestroy} from 'svelte';
  import {skipKeyDownListener$} from '$lib/data/store';
  import {setLocale, t} from '$lib/i18n';
  import {externalFunctions} from '$lib/external';
  import {focusableDialogElements, trapDialogFocus} from '$lib/functions/dialog-focus';

  let showSettings = $state(false)
  let settingsContainer: HTMLElement | undefined = $state();
  let settingsTrigger: HTMLElement | null = null;

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

  function getFocusableDialogElements(): HTMLElement[] { return focusableDialogElements(dialogContainer); }

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


  function closeSettings(): void {
    showSettings = false;
    settingsTrigger?.focus();
    settingsTrigger = null;
  }

  function handleSettingsKeydown(event: KeyboardEvent): void {
    if (event.defaultPrevented || dialogs.length) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      closeSettings();
      return;
    }
    if (event.key !== 'Tab') return;
    trapDialogFocus(settingsContainer, event);
  }

  $effect(() => {
    skipKeyDownListener$.next(showSettings || dialogs.length > 0);
    if (!showSettings) return;
    settingsTrigger ??= document.activeElement instanceof HTMLElement ? document.activeElement : null;
    void tick().then(() => { if (showSettings) settingsContainer?.focus({preventScroll: true}); });
  });

  const dialogSubscription = dialogManager.dialogs$.subscribe((d) => {
    clickOnCloseDisabled = d[0]?.disableCloseOnClick ?? false;
    zIndex = d[0]?.zIndex ?? '';
    dialogs = d;
  });
  onDestroy(() => dialogSubscription.unsubscribe());

</script>

<svelte:window onkeydown={(event) => showSettings && !dialogs.length ? handleSettingsKeydown(event) : handleDialogKeydown(event)}/>

{#if dialogs.length > 0}
  <div class="writing-horizontal-tb fixed inset-0 z-50 h-full w-full" style:z-index={zIndex}>
    <button
        type="button"
        aria-label={t('Close dialog')}
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
        aria-label={t('Reader dialog')}
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
  <div class="h-full" inert={showSettings}>
    <Reader onSettingsClick={() => showSettings = true}/>
  </div>
  {#if showSettings}
    <div class="komelia-settings-overlay">
      <button type="button" class="komelia-settings-scrim" aria-label={t('Close dialog')}
              onclick={closeSettings} onwheel={(event) => event.preventDefault()}></button>
      <div bind:this={settingsContainer} role="dialog" aria-modal="true" aria-label={t('Settings')}
               tabindex="-1"
               data-komelia-chrome data-komelia-popup data-komelia-reader-settings class="komelia-settings-panel">
        <SettingsHeader onExit={closeSettings}/>
        <div class="komelia-settings-content"><SettingsContent/></div>
      </div>
    </div>
  {/if}

  <span style={`font-family: "${$serifFontFamily$.familyName}"`}></span>
{:catch error}
  <p style="color: red">{error.message}</p>
{/await}

<style>
  .komelia-settings-overlay { position: fixed; inset: 0; z-index: 30; }
  .komelia-settings-scrim { position: absolute; inset: 0; border: 0; background: rgb(3 9 17 / 40%); touch-action: none; }
  .komelia-settings-panel { position: absolute; inset-inline: 0; bottom: 0; margin-inline: auto; width: 100%; max-width: 600px; max-height: 82dvh; border-radius: 24px 24px 0 0; overflow: auto; overscroll-behavior: contain; }
  .komelia-settings-content { padding: 12px 20px max(20px, env(safe-area-inset-bottom)); }
  @media (min-width: 840px) { .komelia-settings-panel { inset-inline: auto 16px; top: 16px; bottom: 16px; width: 380px; max-height: calc(100dvh - 32px); border-radius: 24px; } }
</style>
