import '../../shared/chrome-appearance.css';
import '../src/app.scss';
import {mount} from 'svelte';
import MessageDialog from '../src/lib/components/message-dialog.svelte';
import {setLocale} from '../src/lib/i18n';

setLocale(new URLSearchParams(location.search).get('locale') ?? 'en');
const result = document.querySelector('output')!;
mount(MessageDialog, {target: document.querySelector('#app')!, props: {
  title: 'Error', message: 'Synthetic message',
  onRetry: () => { result.textContent = 'retry'; },
  onExitWithoutSaving: () => { result.textContent = 'exit'; },
}});
