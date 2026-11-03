/**
 * plugins/vuetify.ts
 *
 * Framework documentation: https://vuetifyjs.com`
 */

// Styles
import 'vuetify/styles'
import {aliases, mdi} from 'vuetify/iconsets/mdi-svg'

// Composables
import {createVuetify} from 'vuetify'


export default createVuetify({
    theme: {
        defaultTheme: 'dark',
        themes: {
            dark: {colors: {primary: '#70D9FF', 'on-primary': '#00324A'}},
            light: {colors: {primary: '#00638D', 'on-primary': '#FFFFFF'}},
        },
    },
    icons: {
        defaultSet: 'mdi',
        aliases,
        sets: {
            mdi,
        },
    },
})
