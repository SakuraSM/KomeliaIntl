import '../../shared/chrome-appearance.css'
import { refreshChromeAppearance } from '../../shared/chrome-appearance.mjs'
import {mount} from 'svelte'
import './app.scss'
import App from './App.svelte'

void refreshChromeAppearance()

const app = mount(App, {
  target: document.getElementById('app')!,
})

declare global {
  interface HTMLElement {
    scrollIntoViewIfNeeded(arg?: boolean): void;
  }
  interface Navigator {
    msMaxTouchPoints: number;
    standalone: boolean | undefined;
  }
}

export default app
