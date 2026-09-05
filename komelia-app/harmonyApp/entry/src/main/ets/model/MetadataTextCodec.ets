import { KomgaAlternativeTitle, KomgaAuthor, KomgaWebLink } from './KomgaModels';

export function splitMetadataValues(value: string): string[] {
  return value.split(/[、,，]/).map((item: string) => item.trim())
    .filter((item: string) => item.length > 0);
}

export function formatAuthorsForRole(authors: KomgaAuthor[], role: string): string {
  return authors.filter((author: KomgaAuthor) => author.role === role)
    .map((author: KomgaAuthor) => author.name).join('、');
}

export function authorsForRole(value: string, role: string): KomgaAuthor[] {
  return splitMetadataValues(value).map((name: string) => ({ name: name, role: role } as KomgaAuthor));
}

export function formatWebLinks(links: KomgaWebLink[]): string {
  return links.map((link: KomgaWebLink) => `${link.label}|${link.url}`).join('\n');
}

export function parseWebLinks(value: string): KomgaWebLink[] {
  const result: KomgaWebLink[] = [];
  const lines = value.split(/\r?\n/);
  for (let index = 0; index < lines.length; index++) {
    const separator = lines[index].indexOf('|');
    if (separator <= 0) continue;
    const label = lines[index].substring(0, separator).trim();
    const url = lines[index].substring(separator + 1).trim();
    if (label.length > 0 && url.length > 0) result.push({ label: label, url: url });
  }
  return result;
}

export function formatAlternativeTitles(values: KomgaAlternativeTitle[]): string {
  return values.map((value: KomgaAlternativeTitle) => `${value.label}|${value.title}`).join('\n');
}

export function parseAlternativeTitles(value: string): KomgaAlternativeTitle[] {
  const result: KomgaAlternativeTitle[] = [];
  const lines = value.split(/\r?\n/);
  for (let index = 0; index < lines.length; index++) {
    const separator = lines[index].indexOf('|');
    if (separator <= 0) continue;
    const label = lines[index].substring(0, separator).trim();
    const title = lines[index].substring(separator + 1).trim();
    if (label.length > 0 && title.length > 0) result.push({ label: label, title: title });
  }
  return result;
}
