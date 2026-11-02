<script lang="ts">
  import {faXmark} from '@fortawesome/free-solid-svg-icons';
  import Fa from 'svelte-fa';
  import {t} from '$lib/i18n';
  interface Props { onExit: () => void }
  let {onExit}: Props = $props();
</script>

<div class="komelia-sheet-handle" aria-hidden="true"></div>
<header class="komelia-settings-header">
  <h2>{t('Reader settings')}</h2>
  <button type="button" aria-label={t('Return')} onclick={onExit}><Fa icon={faXmark}/></button>
</header>

<style>
  .komelia-settings-header { display: flex; align-items: center; justify-content: space-between; min-height: 48px; padding: 0 8px 0 26px; }
  h2 { font-size: 22px; font-weight: 600; }
  button { display: flex; align-items: center; justify-content: center; width: 48px; height: 48px; border-radius: 12px; }
</style>
