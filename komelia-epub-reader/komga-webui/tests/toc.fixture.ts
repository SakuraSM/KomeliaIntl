import '../../shared/chrome-appearance.css';
import {createApp, h} from 'vue';
import {createI18n} from 'vue-i18n';
import {VApp, VNavigationDrawer} from 'vuetify/components';
import 'vuetify/components/VList';
import 'vuetify/components/VIcon';
import 'vuetify/directives';
import TocList from '../src/components/TocList.vue';
import {registerPlugins} from '../src/plugins';
import type {TocEntry} from '../src/types/epub';
import en from '../src/locales/en.json';
import zhCN from '../src/locales/zh-CN.json';

const parameters = new URLSearchParams(location.search);
document.documentElement.dataset.komeliaSurface = parameters.get('surface') ?? 'glass';
document.documentElement.dataset.komeliaTheme = parameters.get('theme') ?? 'LIGHT';
const entries: TocEntry[] = [
  {title: 'Introduction', href: 'introduction.xhtml'},
  {title: 'Empty section', href: 'empty.xhtml', children: []},
  {title: 'A long synthetic chapter title with nested sections', href: 'chapter.xhtml', children: [
    {title: 'First section', href: 'section-one.xhtml'},
    {title: 'Second section', href: 'section-two.xhtml'},
  ]},
];
const app = createApp({
  render: () => h(VApp, {}, {default: () => [
    h('main', {style: 'height:100vh;background:linear-gradient(30deg,#f5e4bf,#b9cbe0)'}, 'Synthetic reading content'),
    h(VNavigationDrawer, {modelValue: true, temporary: true, width: 310, 'data-komelia-chrome': ''}, {
      default: () => h(TocList, {toc: entries, onGoto: (entry: TocEntry): void => {
        const result = document.querySelector('output');
        if (result) result.textContent = entry.href ?? '';
      }}),
    }),
  ]}),
});
registerPlugins(app);
app.use(createI18n({locale: parameters.get('locale') ?? 'en', fallbackLocale: 'en', messages: {en, 'zh-CN': zhCN}}));
app.mount('#app');
