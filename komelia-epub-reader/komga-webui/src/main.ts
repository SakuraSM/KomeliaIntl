import {registerPlugins} from '@/plugins'
import App from './App.vue'
import {createApp} from 'vue'
import {createI18n} from "vue-i18n";
import en from './locales/en.json'
import zhCN from './locales/zh-CN.json'
import './external'
import ExternalFunctions from "@/external";

export const externalFunctions = new ExternalFunctions()

const browserLanguage = navigator.language.toLowerCase()
const locale = browserLanguage === 'zh' || browserLanguage.startsWith('zh-') ? 'zh-CN' : 'en'

const i18n = createI18n({
  locale,
  fallbackLocale: 'en',
  messages: {
    en,
    'zh-CN': zhCN
  },
})
const app = createApp(App)
registerPlugins(app)
app.use(i18n)
app.mount('#app')
