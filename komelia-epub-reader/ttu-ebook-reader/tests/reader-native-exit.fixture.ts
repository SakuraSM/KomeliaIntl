import '../../shared/chrome-appearance.css';
import '../src/app.scss';
import {mount} from 'svelte';
import Reader from '../src/lib/components/Reader.svelte';
import {bookId$, manualBookmark$, writingMode$} from '../src/lib/data/store';
import {dialogManager} from '../src/lib/data/dialog-manager';
import {setLocale} from '../src/lib/i18n';

declare global {
  interface Window {
    exitTrace: string[];
    requestNativeExit: () => void;
    finishBookmark: () => void;
    failBookmark: () => void;
    dismissExitError: () => void;
    exitDialogCount: () => number;
    exitDialogMessage: () => string;
    exitWithoutSaving: () => void;
    setCloseFailure: (fail: boolean) => void;
    retryExit: () => void;
  }
}

type Response<T> = {result: T};
let releaseExit: ((response: Response<boolean>) => void) | undefined;
let releaseBookmark: ((response: Response<undefined>) => void) | undefined;
let rejectBookmark: ((error: unknown) => void) | undefined;
let failClose = false;
window.exitTrace = [];
Object.assign(window, {
  getBookData: async () => ({result: {
    id: 'synthetic', title: 'Synthetic', styleSheet: '',
    elementHtml: '<div id="chapter-one"><p>' + 'Synthetic reading text. '.repeat(300) + '</p></div>',
    imageUrls: [], hasThumb: false, characters: 7200,
    sections: [{reference: 'chapter-one', label: 'First chapter', startCharacter: 0, characters: 7200, charactersWeight: 1}],
    lastBookModified: 1, lastBookOpen: 1,
  }}),
  getBookmark: async () => ({result: undefined}),
  isFullscreenAvailable: async () => ({result: false}),
  getChromeAppearance: async () => ({result: null}),
  putBookmark: () => {
    window.exitTrace.push('bookmark-start');
    return new Promise<Response<undefined>>((resolve, reject) => {
      releaseBookmark = resolve;
      rejectBookmark = reject;
    });
  },
  closeBook: async () => {
    window.exitTrace.push('close');
    if (failClose) throw {result: 'Synthetic close failure'};
    return {result: undefined};
  },
  waitForReaderExit: () => {
    window.exitTrace.push('wait');
    return new Promise<Response<boolean>>(resolve => { releaseExit = resolve; });
  },
});
window.requestNativeExit = () => { releaseExit?.({result: true}); };
window.finishBookmark = () => { releaseBookmark?.({result: undefined}); };
window.failBookmark = () => { rejectBookmark?.({result: 'Synthetic save failure'}); };
window.dismissExitError = () => { dialogManager.dialogs$.next([]); };
window.exitDialogCount = () => dialogManager.dialogs$.getValue().length;
window.exitDialogMessage = () => String(dialogManager.dialogs$.getValue()[0]?.props?.message ?? '');
window.exitWithoutSaving = () => {
  const action = dialogManager.dialogs$.getValue()[0]?.props?.onExitWithoutSaving;
  if (typeof action === 'function') action();
};
window.setCloseFailure = (fail) => { failClose = fail; };
window.retryExit = () => {
  const action = dialogManager.dialogs$.getValue()[0]?.props?.onRetry;
  if (typeof action === 'function') action();
};
bookId$.next('synthetic');
manualBookmark$.next(new URLSearchParams(location.search).has('manual'));
writingMode$.next('horizontal-tb');
setLocale(new URLSearchParams(location.search).get('locale') ?? 'en');
mount(Reader, {target: document.querySelector('#app')!, props: {onSettingsClick: () => {}}});
