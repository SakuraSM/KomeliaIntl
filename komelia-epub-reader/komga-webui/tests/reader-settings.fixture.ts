import '../../shared/chrome-appearance.css';
import {createApp, h, reactive} from 'vue';
import {createI18n} from 'vue-i18n';
import {VApp} from 'vuetify/components';
import ReaderSettingsSheet from '../src/components/ReaderSettingsSheet.vue';
import {registerPlugins} from '../src/plugins';
import en from '../src/locales/en.json';
import zhCN from '../src/locales/zh-CN.json';

const parameters = new URLSearchParams(location.search);
const theme = parameters.get('theme') ?? 'LIGHT';
const optionLabels = (parameters.get('locale') === 'zh-CN' ? zhCN : en).enums.epubreader;
document.documentElement.dataset.komeliaSurface = parameters.get('surface') ?? 'glass';
document.documentElement.dataset.komeliaTheme = theme;
const state = reactive({
  open: true, fontSize: 100, lineHeight: 1, appearance: 'readium-default-on', verticalScroll: false,
  columnCount: 'auto', readingDirection: 'auto', navigationClick: true, pageMargins: 1,
});
function record(name: string, value: number | string | boolean): void {
  const result = document.querySelector('output');
  if (result) result.textContent = JSON.stringify({name, value});
}
const app = createApp({
  render: () => h(VApp, {}, {default: () => [
    h('main', {style: 'height:100vh;background:#fff;color:#13283e;padding:24px'},
      Array.from({length: 16}, (_, index) => h('p', `Synthetic publication paragraph ${index + 1}.`))),
    h(ReaderSettingsSheet, {
      modelValue: state.open,
      theme: theme === 'LIGHT' ? 'light' : 'dark',
      fixedLayout: parameters.has('fixed-layout'),
      appearances: [
        {text: optionLabels.appearances.day, value: 'readium-default-on', color: 'white', class: 'black--text'},
        {text: optionLabels.appearances.sepia, value: 'readium-sepia-on', color: '#faf4e8', class: 'black--text'},
        {text: optionLabels.appearances.night, value: 'readium-night-on', color: 'black', class: 'white--text'},
      ],
      columnCounts: [{text: optionLabels.column_count.auto, value: 'auto'}, {text: optionLabels.column_count.one, value: '1'}, {text: optionLabels.column_count.two, value: '2'}],
      readingDirs: [{title: 'Automatic', value: 'auto'}, {title: 'Left to right', value: 'ltr'}],
      ...state,
      'onUpdate:modelValue': (value: boolean) => { state.open = value; record('open', value); },
      'onUpdate:fontSize': (value: number) => { state.fontSize = value; record('fontSize', value); },
      'onUpdate:lineHeight': (value: number) => { state.lineHeight = value; record('lineHeight', value); },
      'onUpdate:appearance': (value: string) => { state.appearance = value; record('appearance', value); },
      'onUpdate:verticalScroll': (value: boolean) => { state.verticalScroll = value; record('verticalScroll', value); },
      'onUpdate:columnCount': (value: string) => { state.columnCount = value; record('columnCount', value); },
      'onUpdate:readingDirection': (value: string) => { state.readingDirection = value; record('readingDirection', value); },
      'onUpdate:navigationClick': (value: boolean) => { state.navigationClick = value; record('navigationClick', value); },
      'onUpdate:pageMargins': (value: number) => { state.pageMargins = value; record('pageMargins', value); },
    }),
  ]}),
});
registerPlugins(app);
app.use(createI18n({locale: parameters.get('locale') ?? 'en', fallbackLocale: 'en', messages: {en, 'zh-CN': zhCN}}));
app.mount('#app');
