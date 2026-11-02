import '../../shared/chrome-appearance.css';
import '../src/app.scss';
import {setLocale} from '../src/lib/i18n';

setLocale(new URLSearchParams(location.search).get('locale') ?? 'en');
void import('./reader-toolbar.fixture');
