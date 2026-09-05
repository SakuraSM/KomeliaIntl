import { AvailableBook, AvailableBookSource } from './LocalContentModels';
import { HomeFeed, KomgaBook, KomgaSeries, PageResult } from './KomgaModels';

export enum SearchCoverage { COMPLETE = 'COMPLETE', REMOTE_ONLY = 'REMOTE_ONLY', OFFLINE_ONLY = 'OFFLINE_ONLY' }
export interface UnifiedSearchResult<T> { page: PageResult<T>; coverage: SearchCoverage; }

export function mergedSearchPage<T>(remote: T[], remoteTotal: number, local: T[], requestedPage: number,
  pageSize: number, idOf: (value: T) => string, compare: (left: T, right: T) => number): PageResult<T> {
  const ids = new Set<string>();
  const unique: T[] = [];
  remote.concat(local).forEach((value: T): void => {
    if (!ids.has(idOf(value))) { ids.add(idOf(value)); unique.push(value); }
  });
  const remoteIds = new Set(remote.map(idOf));
  const localIds = new Set(local.map(idOf));
  let duplicates = 0;
  localIds.forEach((id: string): void => { if (remoteIds.has(id)) duplicates++; });
  const totalElements = Math.max(0, remoteTotal + localIds.size - duplicates);
  const size = Math.max(1, Math.floor(pageSize));
  const totalPages = Math.max(1, Math.ceil(totalElements / size));
  const page = Math.max(0, Math.min(Math.floor(requestedPage), totalPages - 1));
  const content = unique.sort(compare).slice(page * size, (page + 1) * size);
  return { content: content, totalElements: totalElements, totalPages: totalPages, number: page, size: size,
    first: page === 0, last: page + 1 >= totalPages, empty: content.length === 0 };
}

export function localSeriesFromBooks(books: KomgaBook[]): KomgaSeries[] {
  const ids = Array.from(new Set(books.map((book: KomgaBook): string => book.seriesId)));
  return ids.map((id: string): KomgaSeries => {
    const values = books.filter((book: KomgaBook): boolean => book.seriesId === id);
    const first = values[0];
    const read = values.filter((book: KomgaBook): boolean => book.readProgress?.completed === true).length;
    const progress = values.filter((book: KomgaBook): boolean => book.readProgress !== undefined &&
      book.readProgress !== null && !book.readProgress.completed).length;
    return { id: id, libraryId: first.libraryId, name: first.seriesTitle, booksCount: values.length,
      created: values.map(value => value.created || '').sort()[0],
      lastModified: values.map(value => value.fileLastModified || value.lastModified || '').sort().reverse()[0],
      booksReadCount: read, booksInProgressCount: progress, booksUnreadCount: values.length - read - progress,
      metadata: { title: first.seriesTitle, titleSort: first.seriesTitle, summary: '', publisher: '',
        language: '', status: 'ONGOING', genres: [], tags: [] }, oneshot: false };
  });
}

function mergeHomeBooks(remote: KomgaBook[], local: KomgaBook[], property: string, limit: number = 20): KomgaBook[] {
  return mergedSearchPage(remote, remote.length, local, 0, limit, value => value.id,
    (a: KomgaBook, b: KomgaBook): number => {
      const left = property === 'read' ? a.readProgress?.readDate : a.created;
      const right = property === 'read' ? b.readProgress?.readDate : b.created;
      return (right || '').localeCompare(left || '') || a.id.localeCompare(b.id);
    }).content;
}

function mergeHomeSeries(remote: KomgaSeries[], local: KomgaSeries[], modified: boolean): KomgaSeries[] {
  return mergedSearchPage(remote, remote.length, local, 0, 20, value => value.id,
    (left, right) => ((modified ? right.lastModified : right.created) || '').localeCompare(
      (modified ? left.lastModified : left.created) || '') || left.id.localeCompare(right.id)).content;
}

export function mergeHomeWithAvailable(remote: HomeFeed, available: AvailableBook[]): HomeFeed {
  const books = available.map((value: AvailableBook): KomgaBook => value.book);
  const localBooks = available.filter((value: AvailableBook): boolean => value.source === AvailableBookSource.LOCAL)
    .map((value: AvailableBook): KomgaBook => value.book);
  const localSeries = localSeriesFromBooks(localBooks);
  const onDeck: KomgaBook[] = [];
  const seriesIds = Array.from(new Set(books.map((book: KomgaBook): string => book.seriesId)));
  seriesIds.forEach((id: string): void => {
    const series = books.filter(book => book.seriesId === id).sort((a, b) => a.number - b.number);
    if (series.some(book => book.readProgress !== undefined && book.readProgress !== null && !book.readProgress.completed)) return;
    const completed = series.filter(book => book.readProgress?.completed === true);
    if (completed.length === 0) return;
    const last = completed[completed.length - 1];
    const next = series.find(book => !book.readProgress?.completed && book.number > last.number);
    if (next !== undefined) onDeck.push(next);
  });
  return {
    keepReading: mergeHomeBooks(remote.keepReading, books.filter(book => book.readProgress !== undefined &&
      book.readProgress !== null && !book.readProgress.completed), 'read'),
    onDeck: mergeHomeBooks(remote.onDeck, onDeck, 'created'),
    recentlyRead: mergeHomeBooks(remote.recentlyRead, books.filter(book => book.readProgress?.completed === true), 'read'),
    latestBooks: mergeHomeBooks(remote.latestBooks, books, 'created'),
    recentlyReleased: remote.recentlyReleased,
    recentlyAddedSeries: mergeHomeSeries(remote.recentlyAddedSeries, localSeries, false),
    recentlyUpdatedSeries: mergeHomeSeries(remote.recentlyUpdatedSeries, localSeries, true)
  };
}
