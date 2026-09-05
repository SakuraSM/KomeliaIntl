const BLOCK_TAG_PATTERN: RegExp = /<\/?(?:address|article|aside|blockquote|div|dl|dt|dd|figcaption|figure|footer|h[1-6]|header|hr|li|main|nav|ol|p|pre|section|table|tbody|td|tfoot|th|thead|tr|ul)\b[^>]*>/gi;
const BREAK_TAG_PATTERN: RegExp = /<br\s*\/?>/gi;
const REMOVED_BLOCK_PATTERN: RegExp = /<(?:head|script|style|svg)\b[^>]*>[\s\S]*?<\/(?:head|script|style|svg)\s*>/gi;
const RUBY_ANNOTATION_PATTERN: RegExp = /<(?:rp|rt)\b[^>]*>[\s\S]*?<\/(?:rp|rt)\s*>/gi;
const TAG_PATTERN: RegExp = /<[^>]+>/g;

function decodeEntity(entity: string): string | undefined {
  switch (entity) {
    case 'amp': return '&';
    case 'apos': return "'";
    case 'gt': return '>';
    case 'lt': return '<';
    case 'nbsp': return ' ';
    case 'quot': return '"';
    case 'ensp': return ' ';
    case 'emsp': return ' ';
    case 'hellip': return '…';
    case 'mdash': return '—';
    case 'ndash': return '–';
    case 'middot': return '·';
    case 'ldquo': return '“';
    case 'rdquo': return '”';
    case 'lsquo': return '‘';
    case 'rsquo': return '’';
    default: break;
  }
  if (entity.startsWith('#x') || entity.startsWith('#X')) {
    const value = Number.parseInt(entity.substring(2), 16);
    if (Number.isFinite(value) && value > 0 && value <= 0x10FFFF) return String.fromCodePoint(value);
  }
  if (entity.startsWith('#')) {
    const value = Number.parseInt(entity.substring(1), 10);
    if (Number.isFinite(value) && value > 0 && value <= 0x10FFFF) return String.fromCodePoint(value);
  }
  return undefined;
}

export function decodeEpubEntities(value: string): string {
  let result = '';
  let cursor = 0;
  while (cursor < value.length) {
    const ampersand = value.indexOf('&', cursor);
    if (ampersand < 0) {
      result += value.substring(cursor);
      break;
    }
    result += value.substring(cursor, ampersand);
    const semicolon = value.indexOf(';', ampersand + 1);
    if (semicolon < 0 || semicolon - ampersand > 16) {
      result += '&';
      cursor = ampersand + 1;
      continue;
    }
    const entity = value.substring(ampersand + 1, semicolon);
    const decoded = decodeEntity(entity);
    if (decoded === undefined) {
      result += value.substring(ampersand, semicolon + 1);
    } else {
      result += decoded;
    }
    cursor = semicolon + 1;
  }
  return result;
}

/** Convert one EPUB XHTML spine resource into stable plain text for SpeechKit. */
export function epubMarkupToSpeechText(markup: string): string {
  if (markup.trim().length === 0) return '';
  const withoutComments = markup.replace(/<!--[\s\S]*?-->/g, ' ');
  const withoutMetadata = withoutComments.replace(REMOVED_BLOCK_PATTERN, ' ')
    .replace(RUBY_ANNOTATION_PATTERN, ' ');
  const withBoundaries = withoutMetadata.replace(BREAK_TAG_PATTERN, '\n')
    .replace(BLOCK_TAG_PATTERN, '\n');
  const decoded = decodeEpubEntities(withBoundaries.replace(TAG_PATTERN, ' '))
    .replace(/\u00A0/g, ' ')
    .replace(/[\t\f\v ]+/g, ' ')
    .replace(/ +([，。！？；：、,.!?;:])/g, '$1')
    .replace(/ *\n */g, '\n')
    .replace(/\n{2,}/g, '\n');
  return decoded.trim();
}

export function epubSpeechArticleId(bookId: string, resourceIndex: number): string {
  return `${epubSpeechArticlePrefix(bookId)}${resourceIndex}`;
}

export function isSpeechArticleForBook(articleId: string, bookId: string): boolean {
  if (!articleId.startsWith(epubSpeechArticlePrefix(bookId))) return false;
  return resourceIndexFromSpeechArticleId(articleId) !== undefined;
}

function epubSpeechArticlePrefix(bookId: string): string {
  return `komelia-epub:${bookId.length}:${bookId}:`;
}

export function resourceIndexFromSpeechArticleId(articleId: string): number | undefined {
  if (!articleId.startsWith('komelia-epub:')) return undefined;
  const separator = articleId.lastIndexOf(':');
  if (separator < 0 || separator === articleId.length - 1) return undefined;
  const value = Number(articleId.substring(separator + 1));
  return Number.isInteger(value) && value >= 0 ? value : undefined;
}
