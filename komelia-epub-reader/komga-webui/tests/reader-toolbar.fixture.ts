import {createApp, h, reactive} from 'vue';
import {createI18n} from 'vue-i18n';
import {VApp} from 'vuetify/components';
import EpubReaderToolbar from '../src/components/EpubReaderToolbar.vue';
import {registerPlugins} from '../src/plugins';
import en from '../src/locales/en.json';
import zhCN from '../src/locales/zh-CN.json';
import '../../shared/chrome-appearance.css';

const parameters = new URLSearchParams(location.search);
document.documentElement.dataset.komeliaSurface = parameters.get('surface') ?? 'glass';
document.documentElement.dataset.komeliaTheme = parameters.get('theme') ?? 'LIGHT';
const state = reactive({isTocOpen: false, isSettingsOpen: false});
const record = (name: string): void => { document.querySelector('output')!.textContent = name; };
const app = createApp({
  render: () => h(VApp, {}, { default: () => h(EpubReaderToolbar, {
    ...state,
    title: 'A long synthetic series title for narrow reader toolbars',
    subtitle: 'Chapter 2: A very long book title that should truncate',
    hasNavigation: true, isFullscreenAvailable: true,
    onClose: () => record('close'),
    onToggleToc: () => { state.isTocOpen = !state.isTocOpen; record('toc'); },
    onToggleSettings: () => { state.isSettingsOpen = !state.isSettingsOpen; record('settings'); },
    onToggleHelp: () => record('help'), onFullscreen: () => record('fullscreen'),
  }) }),
});
registerPlugins(app);
app.use(createI18n({locale: parameters.get('locale') ?? 'en', fallbackLocale: 'en', messages: {en, 'zh-CN': zhCN}}));
app.mount('#app');
