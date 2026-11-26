import {isNodeGaiji} from '$lib/functions/is-node-gaiji';

interface RubyReading {
  start: number;
  end: number;
  soundStart: number;
  soundEnd: number;
}

interface TextSpan { start: number; end: number }

export interface SearchText {
  text: string;
  phonetic: string;
  readings: RubyReading[];
  fallbackCharacters: {offset: number; count: number}[];
}

function walkText(root: Element, visit: (node: Node) => void) {
  const walker = root.ownerDocument.createTreeWalker(root, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT, {
    acceptNode(node) {
      if (node instanceof Element && node.matches('[hidden],[aria-hidden],script,style')) return NodeFilter.FILTER_REJECT;
      return node.nodeType === Node.TEXT_NODE || isNodeGaiji(node) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_SKIP;
    }
  });
  while (walker.nextNode()) visit(walker.currentNode);
}

function nodeText(node: Node): string {
  return isNodeGaiji(node) ? '\uFFFC' : node.textContent || '';
}

function readerLength(text: string) {
  return Array.from(text.replace(/\s/g, '')).length;
}

export function projectSearchText(root: Element): SearchText {
  let text = '';
  let phonetic = '';
  let lastEnd = 0;
  let ruby: Element | null = null;
  let baseStart = 0;
  let activeReading: RubyReading | undefined;
  const readings: RubyReading[] = [];
  const fallbackCharacters: SearchText['fallbackCharacters'] = [];
  walkText(root, node => {
    const parent = node.parentElement;
    const owner = parent?.closest('ruby') ?? null;
    if (ruby !== owner) {
      ruby = owner;
      baseStart = text.length;
      activeReading = undefined;
    }
    const value = nodeText(node);
    if (parent?.closest('rp')) {
      // TTU's bookmark counter includes the fallback punctuation, but the query should not.
      fallbackCharacters.push({offset: text.length, count: readerLength(value)});
    } else if (owner && parent?.closest('rt')) {
      if (baseStart < text.length) {
        phonetic += text.slice(lastEnd, baseStart);
        activeReading = {start: baseStart, end: text.length, soundStart: phonetic.length, soundEnd: phonetic.length};
        readings.push(activeReading);
        baseStart = text.length;
        lastEnd = text.length;
      }
      if (activeReading) {
        phonetic += value;
        activeReading.soundEnd = phonetic.length;
      }
    } else {
      text += value;
    }
  });
  return {text, phonetic: readings.length ? phonetic + text.slice(lastEnd) : '', readings, fallbackCharacters};
}

function baseOffset(readings: RubyReading[], index: number, end: boolean): number {
  let low = 0;
  let high = readings.length;
  while (low < high) {
    const mid = Math.floor((low + high) / 2);
    if (readings[mid].soundStart <= index) low = mid + 1;
    else high = mid;
  }
  const preceding = readings[low - 1];
  if (preceding && index < preceding.soundEnd) return end ? preceding.end : preceding.start;
  const delta = preceding ? preceding.end - preceding.soundEnd : 0;
  return index + delta + (end ? 1 : 0);
}

export function searchSpans(item: SearchText, pattern: RegExp, limit: number): TextSpan[] {
  const matches = new Map<string, TextSpan>();
  for (const phonetic of [false, true]) {
    const source = phonetic ? item.phonetic : item.text;
    let count = 0;
    for (const match of source.matchAll(pattern)) {
      const first = match.index!;
      const after = first + match[0].length;
      const start = phonetic ? baseOffset(item.readings, first, false) : first;
      const end = phonetic ? baseOffset(item.readings, after - 1, true) : after;
      const key = start + ':' + end;
      if (matches.has(key)) continue;
      matches.set(key, {start, end});
      if (++count >= limit) break;
    }
  }
  return [...matches.values()].sort((a,b) => a.start - b.start || a.end - b.end).slice(0, limit);
}

export function readingOffset(item: SearchText, offset: number) {
  return readerLength(item.text.slice(0, offset)) + item.fallbackCharacters.reduce(
    (sum, item) => sum + (item.offset <= offset ? item.count : 0), 0);
}

// Wrap each text node separately so clearing a search hit restores the original ruby and links.
export function markSearchSpan(root: Element, span: TextSpan, id: string): void {
  let offset = 0;
  const ranges: Range[] = [];
  walkText(root, node => {
    if (node.parentElement?.closest('rt,rp')) return;
    const length = nodeText(node).length;
    const start = Math.max(0, span.start - offset);
    const end = Math.min(length, span.end - offset);
    if (node.nodeType === Node.TEXT_NODE && start < end) {
      const range = root.ownerDocument.createRange();
      range.setStart(node, start);
      range.setEnd(node, end);
      ranges.push(range);
    }
    offset += length;
  });
  for (const range of ranges) {
    const mark = root.ownerDocument.createElement('mark');
    mark.className = 'reader-search-match';
    mark.dataset.readerMarkId = id;
    range.surroundContents(mark);
  }
}
