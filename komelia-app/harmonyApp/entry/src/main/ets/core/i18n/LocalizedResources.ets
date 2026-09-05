import { i18n } from '@kit.LocalizationKit';
import { AppContainer } from '../di/AppContainer';

export function formatLocalizedTemplate(template: string, args: Array<string | number>): string {
  if (args.length === 0) return template;
  const pattern = /%%|%(?:[1-9][0-9]*\$)?[sdif]/g;
  let result = '';
  let cursor = 0;
  let sequentialIndex = 0;
  let match = pattern.exec(template);
  while (match !== null) {
    result += template.substring(cursor, match.index);
    const token = match[0];
    if (token === '%%') {
      result += '%';
    } else {
      const dollar = token.indexOf('$');
      const index = dollar > 1 ? Number(token.substring(1, dollar)) - 1 : sequentialIndex++;
      if (index < 0 || index >= args.length) {
        result += token;
      } else {
        const value = args[index];
        const kind = token.charAt(token.length - 1);
        if ((kind === 'd' || kind === 'i') && typeof value === 'number') {
          result += `${Math.trunc(value)}`;
        } else {
          result += `${value}`;
        }
      }
    }
    cursor = match.index + token.length;
    match = pattern.exec(template);
  }
  return result + template.substring(cursor);
}

export function localizedString(resource: Resource, ...args: Array<string | number>): string {
  const resourceManager = AppContainer.requireContext().resourceManager;
  const preferredLanguage = i18n.System.getAppPreferredLanguage();
  let template: string;
  if (preferredLanguage === 'default' || preferredLanguage.length === 0) {
    template = resourceManager.getStringSync(resource.id);
  } else {
    const configuration = resourceManager.getConfigurationSync();
    configuration.locale = preferredLanguage;
    try {
      template = resourceManager.getOverrideResourceManager(configuration).getStringSync(resource.id);
    } catch (_) {
      template = resourceManager.getStringSync(resource.id);
    }
  }
  return formatLocalizedTemplate(template, args);
}
