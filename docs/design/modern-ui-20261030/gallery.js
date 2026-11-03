/** @typedef {{key:string, title:string, number:number, file:string, width:number, height:number, theme:string, group:string, kind:string}} DesignPage */
const ALL_FILTER = 'all';
const THEME_LABELS = {DARK:'深色', LIGHT:'浅色', PAPER:'阅读纸色'};
/** @type {DesignPage[]} */
const pages = window.KOMELIA_DESIGN_PAGES;
const gallery = document.querySelector('#gallery');
const counter = document.querySelector('#count');
const viewer = document.querySelector('#viewer');
const viewerImage = document.querySelector('#viewer-image');
const viewerTitle = document.querySelector('#viewer-title');
const originalLink = document.querySelector('#original');
const previousButton = document.querySelector('#previous');
const nextButton = document.querySelector('#next');
let activeFilter = ALL_FILTER;
/** @type {DesignPage[]} */
let viewerPages = [];
let viewerIndex = 0;
/** @type {HTMLElement|null} */
let lastTrigger = null;

/** @param {DesignPage} page @returns {boolean} */
function matchesFilter(page) {
  if (activeFilter === ALL_FILTER) return true;
  if (activeFilter === 'light') return page.theme === 'LIGHT';
  if (activeFilter === 'wide') return page.kind !== 'mobile';
  if (activeFilter === 'reader') return page.group === 'reader';
  return page.theme === 'DARK' && page.kind === 'mobile';
}
/** @param {DesignPage} page @returns {HTMLElement} */
function createCard(page) {
  const card = document.createElement('article');
  card.className = page.kind === 'mobile' ? 'card' : 'card wide';
  card.dataset.key = page.key;
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'image-button';
  button.setAttribute('aria-label', `查看${page.title}视觉稿`);
  const picture = document.createElement('img');
  picture.src = page.file;
  picture.alt = `${page.title}设计稿`;
  picture.loading = 'lazy';
  picture.decoding = 'async';
  button.append(picture);
  button.addEventListener('click', () => openViewer(page.key));
  const title = document.createElement('h2');
  title.textContent = `${String(page.number).padStart(2,'0')} · ${page.title}`;
  const caption = document.createElement('p');
  caption.textContent = `目标 ${page.width} × ${page.height} · ${THEME_LABELS[page.theme]}`;
  card.append(button,title,caption);
  return card;
}
/** @returns {void} */
function applyFilter() {
  let count = 0;
  for (const page of pages) {
    const card = gallery.querySelector(`[data-key="${page.key}"]`);
    card.hidden = !matchesFilter(page);
    if (!card.hidden) count++;
  }
  counter.textContent = `${count} / ${pages.length} 页`;
}
/** @param {string} pageKey @returns {void} */
function openViewer(pageKey) {
  lastTrigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  viewerPages = pages.filter(matchesFilter);
  viewerIndex = viewerPages.findIndex(page => page.key === pageKey);
  updateViewer();
  viewer.showModal();
}
/** @returns {void} */
function updateViewer() {
  const page = viewerPages[viewerIndex];
  viewerImage.src = page.file;
  viewerImage.alt = `${page.title}原始视觉稿`;
  viewerTitle.textContent = `${String(page.number).padStart(2,'0')} · ${page.title}`;
  originalLink.href = page.file;
  previousButton.disabled = viewerIndex === 0;
  nextButton.disabled = viewerIndex === viewerPages.length - 1;
}
/** @param {number} step @returns {void} */
function navigateViewer(step) {
  const targetIndex = viewerIndex + step;
  if (targetIndex < 0 || targetIndex >= viewerPages.length) return;
  viewerIndex = targetIndex;
  updateViewer();
}
for (const page of pages) gallery.append(createCard(page));
for (const button of document.querySelectorAll('[data-filter]')) {
  button.addEventListener('click', () => {
    activeFilter = button.dataset.filter;
    for (const choice of document.querySelectorAll('[data-filter]')) choice.setAttribute('aria-pressed', String(choice === button));
    applyFilter();
  });
}
previousButton.addEventListener('click', () => navigateViewer(-1));
nextButton.addEventListener('click', () => navigateViewer(1));
document.querySelector('#close').addEventListener('click', () => viewer.close());
viewer.addEventListener('close', () => lastTrigger?.focus());
viewer.addEventListener('click', event => { if (event.target === viewer) viewer.close(); });
viewer.addEventListener('keydown', event => {
  if (event.key === 'ArrowLeft') { event.preventDefault(); navigateViewer(-1); }
  if (event.key === 'ArrowRight') { event.preventDefault(); navigateViewer(1); }
});
applyFilter();
