export const PAGES = [
 ['01','首页','home','继续阅读与最近阅读'],['02','书库 / 丛书总览','library','三列封面，标题栏内筛选'],['03','丛书卷册','series','紧凑头部与卷册顺序'],['04','文字书详情','ebook','完整封面与单一阅读入口'],['05','漫画单册详情','comic','相邻卷册与阅读位置'],['06','本地与下载','local','离线内容、下载和不可用状态'],['07','搜索','search','无封面、长标题与未知进度'],['08','设置','settings','常用阅读偏好优先'],['09','筛选面板','filter','玻璃浮层与明确应用动作'],['10','EPUB 目录','toc','分层目录与当前章节'],['11','文字阅读设置','text-settings','字号、主题、翻页'],['12','漫画 / PDF 设置','image-settings','布局、方向与持久化提示开关'],['13','文字书 · 冷白','ebook-light','相同布局的冷白玻璃对照'],['14','丛书 · 冷白','series-light','相同密度的冷白玻璃对照'],['15','平板','tablet','834 × 1194，目录与详情'],['16','桌面','desktop','1440 × 1024，侧栏与主从布局']
];
export const BOOKS = [
 {id:'reading',title:'阅读的边界',author:'林知远',format:'EPUB',state:'已读 34% · 已下载',library:'1册 · 阅读中',image:'reading-boundaries-cover.png',target:'ebook'},
 {id:'tokyo',title:'雨后的东京',author:'青山遥',format:'漫画',state:'已读 86%',library:'24册 · 读至12册',image:'rainy-tokyo-cover.png',target:'series'},
 {id:'mountain',title:'山海之间',author:'陆向南',format:'PDF',state:'未读',library:'1册 · 未读',tone:'green',target:'comic'},
 {id:'city',title:'城市慢行',author:'陈亦舟',format:'EPUB',state:'已读完',library:'3册 · 已读完',tone:'slate',target:'ebook'},
 {id:'paper',title:'纸间漫游',author:'许一',format:'EPUB',state:'已下载',library:'5册 · 已下载',tone:'sand',target:'ebook'},
 {id:'sea',title:'海风来信',author:'许知夏',format:'EPUB',state:'未读',library:'2册 · 未读',tone:'blue',target:'ebook'},
 {id:'far',title:'远山',author:'陆向南',format:'EPUB',state:'未读',library:'1册 · 未读',tone:'cream',target:'ebook'},
 {id:'light',title:'光的笔记',author:'',format:'EPUB',state:'未读',library:'1册 · 未读',tone:'missing',target:'ebook'},
 {id:'world',title:'理解世界的另一种方式',author:'周明川',format:'EPUB',state:'阅读中',library:'1册 · 阅读中',tone:'sand',target:'ebook'}
];
const ICON_PATHS = {
 back:'<path d="m14 5-7 7 7 7M7 12h14"/>',more:'<circle cx="12" cy="5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="19" r="1"/>',search:'<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',filter:'<path d="M4 6h16M4 12h16M4 18h16"/><circle cx="8" cy="6" r="2" fill="var(--surface)"/><circle cx="16" cy="12" r="2" fill="var(--surface)"/><circle cx="10" cy="18" r="2" fill="var(--surface)"/>',home:'<path d="m3 10 9-7 9 7v10h-6v-7H9v7H3z"/>',library:'<rect x="3" y="4" width="4" height="16" rx="1"/><rect x="9" y="4" width="4" height="16" rx="1"/><path d="m15 5 4-1 3 15-4 1z"/>',folder:'<path d="M3 6h6l2 2h10v12H3zM3 6V4h7l2 2h9"/>',settings:'<path d="m10 3-1 3-3 1-3 3 2 2-1 4 3 3 3-1 2 3 3-2 3 1 3-4-2-3 1-3-3-3-3 1-2-3z"/><circle cx="12" cy="12" r="3"/>',chevron:'<path d="m9 5 7 7-7 7"/>',down:'<path d="m5 9 7 7 7-7"/>',download:'<path d="M12 3v12m-5-5 5 5 5-5M4 17v4h16v-4"/>',bookmark:'<path d="M6 3h12v18l-6-4-6 4z"/>',book:'<path d="M12 5C8 2 5 3 2 4v16c4-2 7-1 10 1 3-2 6-3 10-1V4c-3-1-6-2-10 1v16"/>',list:'<path d="M8 6h13M8 12h13M8 18h13M3 6h1M3 12h1M3 18h1"/>',sort:'<path d="M7 4v16m-4-4 4 4 4-4M14 5h7M14 10h5M14 15h3"/>',check:'<path d="m4 12 5 5L20 6"/>',pause:'<path d="M8 5v14M16 5v14"/>',close:'<path d="m6 6 12 12M6 18 18 6"/>',image:'<rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8" cy="8" r="1.5"/><path d="m3 18 6-6 4 4 3-3 5 5"/>',user:'<circle cx="12" cy="7" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/>',moon:'<path d="M20 15A9 9 0 0 1 9 3a9 9 0 1 0 11 12z"/>',info:'<circle cx="12" cy="12" r="9"/><path d="M12 10v7M12 6v1"/>',warning:'<path d="m12 3 10 18H2zM12 9v5M12 17v1"/>'
};
/** @param {string} name @returns {string} */
export function icon(name){return `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${ICON_PATHS[name]||ICON_PATHS.chevron}</svg>`;}
/** @param {string} screen @returns {string} */
export function route(screen){return `?screen=${screen}`;}
/** @param {{name:string,label:string,target?:string}} action @returns {string} */
export function iconButton({name,label,target}){return target?`<a class="icon-button" href="${route(target)}" aria-label="${label}">${icon(name)}</a>`:`<button class="icon-button" aria-label="${label}" data-note="${label}">${icon(name)}</button>`;}
/** @param {{title:string,secondary?:boolean,search?:boolean,filter?:boolean}} config @returns {string} */
export function toolbar({title,secondary=false,search=false,filter=false}){return `<header class="topbar glass ${secondary?'secondary':''}">${secondary?iconButton({name:'back',label:'返回',target:'library'}):''}<h1>${title}</h1>${search?iconButton({name:'search',label:'搜索',target:'search'}):''}${filter?iconButton({name:'filter',label:'筛选与显示',target:'filter'}):''}${secondary?iconButton({name:'more',label:'更多操作'}):''}</header>`;}
/** @param {string} active @returns {string} */
export function navigation(active){return `<nav class="navbar glass" aria-label="主导航">${navItems(active)}</nav>`;}
/** @param {string} active @returns {string} */
export function navItems(active){return [['home','home','首页'],['library','library','书库'],['local','folder','本地'],['search','search','搜索'],['settings','settings','设置']].map(([target,name,label])=>`<a class="nav-item ${active===target?'active':''}" ${active===target?'aria-current="page"':''} href="${route(target)}">${icon(name)}<span>${label}</span></a>`).join('');}
/** @param {typeof BOOKS[number]} book @returns {string} */
export function cover(book){return `<div class="book-cover">${book.image?`<img class="cover" src="../assets/${book.image}" alt="${book.title}封面">`:`<div class="cover-type ${book.tone}" role="img" aria-label="${book.title}${book.tone==='missing'?'，暂无封面':'，设计示例封面'}"><span class="cover-title">${book.title}</span><span class="cover-bottom">${book.tone==='missing'?'暂无封面':book.author+' 著'}</span></div>`}</div>`;}
/** @param {{book:typeof BOOKS[number],library?:boolean}} config @returns {string} */
export function bookCard({book,library=false}){return `<a class="book-card" href="${route(book.target)}">${cover(book)}<h3>${book.title}</h3><p class="meta">${library?book.library:book.state}</p></a>`;}
/** @param {{value:number,label:string,position?:string}} config @returns {string} */
export function progress({value,label,position=''}){return `<div class="progress-label"><span>${label}</span><span class="meta number">${position}</span></div><div class="progress" style="--progress:${value}%" role="progressbar" aria-label="${label}" aria-valuenow="${value}" aria-valuemin="0" aria-valuemax="100"><span></span></div>`;}
/** @param {{label:string,target?:string}} config @returns {string} */
export function primary({label,target}){return target?`<a class="primary" href="${route(target)}">${icon('book')}${label}</a>`:`<button class="primary" data-apply>${label}</button>`;}
/** @param {string} kind @returns {string} */
export function readingFooter(kind){const isEbook=kind==='ebook';return `<footer class="reading-footer glass">${progress({value:isEbook?34:86,label:isEbook?'已读 34%':'已读 86%',position:isEbook?'':'24 / 28页'})}${primary({label:'继续阅读',target:isEbook?'reader-text':'reader-image'})}</footer>`;}
/** @param {{book:typeof BOOKS[number],title?:string,subtitle?:string,state?:string,target?:string,current?:boolean,endIcon?:string}} config @returns {string} */
export function bookRow({book,title=book.title,subtitle=`${book.author} · ${book.format}`,state=book.state,target=book.target,current=false,endIcon='chevron'}){return `<a class="row ${current?'current':''}" href="${route(target)}">${cover(book)}<div class="row-copy"><h3>${title}</h3><div class="meta">${subtitle}</div>${state?`<div class="meta ${current?'accent':''}">${state}</div>`:''}</div>${icon(endIcon)}</a>`;}
/** @param {{title:string,subtitle?:string,name:string,target?:string,value?:string}} config @returns {string} */
export function settingRow({title,subtitle='',name,target='image-settings',value=''}){return `<a class="row" href="${route(target)}">${icon(name)}<div class="row-copy"><h3>${title}</h3>${subtitle?`<p class="meta">${subtitle}</p>`:''}</div>${value?`<span class="row-value">${value}</span>`:''}${icon('chevron')}</a>`;}
