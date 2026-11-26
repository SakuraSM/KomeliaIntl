import '../../shared/chrome-appearance.css';
import '../src/app.scss';
import {mount} from 'svelte';
import App from '../src/App.svelte';
import * as settings from '../src/lib/data/store';

const params = new URLSearchParams(location.search);
const chapter = 'https://komelia.invalid/resource/text/chapter.xhtml';
const notes = 'https://komelia.invalid/resource/notes/end.xhtml';
const first = '<p>𠮟る <ruby>東京<rp>(</rp><rt>とうきょう</rt><rp>)</rp></ruby>駅。' +
  '<a epub:type="noteref" href="#n">①</a> <a role="doc-noteref" href="../notes/end.xhtml#n">②</a></p>' +
  '<aside id="n" epub:type="footnote">Same chapter note</aside>' + '<p>More text.</p>'.repeat(40);
const last = '<aside id="n" role="doc-endnote"><p>Other chapter note</p><p>Second paragraph</p></aside>';
const sections = [chapter, notes].map((reference, i) => ({
  reference, label: 'Chapter ' + (i + 1), charactersWeight: 400, characters: 400, startCharacter: i * 400,
}));
const book = {id: 'test', title: 'Synthetic EPUB', imageUrls: [], characters: 800, styleSheet: '', sections,
  elementHtml: '<div id="' + chapter + '">' + first + '</div><div id="' + notes + '">' + last + '</div>'};
const defaults = Object.fromEntries(Object.entries(settings).flatMap(([name, store]) =>
  name.endsWith('$') && store && 'getValue' in Object(store) ?
    [[name.slice(0, -1), (store as {getValue(): unknown}).getValue()]] : []));
const answer = (result?: unknown) => async () => ({result});
Object.assign(window, {
  getLocale: answer('en'), getStoredFonts: answer([]), getAvailableFonts: answer([]), getCurrentBookId: answer('test'),
  getSettings: answer({...defaults, viewMode: params.get('mode') || 'continuous', writingMode: 'horizontal-tb', autoBookmark: false}),
  getBookData: answer(book), getBookmark: answer(), isFullscreenAvailable: answer(false),
  putSettings: answer(), putBookmark: answer(),
  waitForReaderExit: () => new Promise(resolve => { Object.assign(window, {requestTestBack: () => resolve({result:true})}); }),
  closeBook: () => {document.body.dataset.closed = 'true'; return Promise.resolve({result:true});}
});
mount(App, {target: document.querySelector('#app')!});
