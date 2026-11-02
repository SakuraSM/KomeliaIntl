<script lang="ts">
  import SettingsContent from './settings-content.svelte';
  import SettingsHeader from './settings-header.svelte';
  import {t} from '$lib/i18n';
  interface Props { onExit: () => void; element?: HTMLElement }
  let {onExit, element = $bindable()}: Props = $props();
</script>

    <div class="komelia-settings-overlay">
      <button type="button" class="komelia-settings-scrim" aria-label={t('Close dialog')}
              onclick={onExit} onwheel={(event) => event.preventDefault()}></button>
      <div bind:this={element} role="dialog" aria-modal="true" aria-label={t('Settings')}
               tabindex="-1"
               data-komelia-chrome data-komelia-popup data-komelia-reader-settings class="komelia-settings-panel">
        <SettingsHeader onExit={onExit}/>
        <div class="komelia-settings-content"><SettingsContent/></div>
      </div>
    </div>

<style>
  .komelia-settings-overlay { position: fixed; inset: 0; z-index: 30; }
  .komelia-settings-scrim { position: absolute; inset: 0; border: 0; background: rgb(3 9 17 / 40%); touch-action: none; }
  .komelia-settings-panel { position: absolute; inset-inline: 0; bottom: 0; margin-inline: auto; width: 100%; max-width: 600px; height: 570px; max-height: 82dvh; border-radius: 24px 24px 0 0; overflow: auto; overscroll-behavior: contain; }
  .komelia-settings-content { padding: 0 26px max(22px, env(safe-area-inset-bottom)); }
  @media (min-width: 840px) { .komelia-settings-panel { inset-inline: auto 16px; top: 16px; bottom: 16px; width: 380px; height: auto; max-height: calc(100dvh - 32px); border-radius: 24px; } }
</style>
