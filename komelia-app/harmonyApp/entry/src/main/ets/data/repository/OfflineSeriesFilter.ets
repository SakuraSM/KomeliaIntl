import { KomgaAuthor, KomgaSeries } from '../../model/KomgaModels';
import { SeriesSearchOptions } from '../../network/KomgaClient';

function hasAny(values: string[], expected: string[] | undefined): boolean {
  if (expected === undefined || expected.length === 0) return true;
  return expected.some((value: string): boolean => values.includes(value));
}

function hasAnyAuthor(values: KomgaAuthor[], expected: KomgaAuthor[] | undefined): boolean {
  if (expected === undefined || expected.length === 0) return true;
  return expected.some((candidate: KomgaAuthor): boolean => values.some((author: KomgaAuthor): boolean =>
    author.name === candidate.name && (candidate.role.length === 0 || author.role === candidate.role)));
}

function inclusionMatches(series: KomgaSeries, options?: SeriesSearchOptions): boolean {
  const checks: boolean[] = [];
  (options?.includeGenres || []).forEach((value: string) => checks.push(series.metadata.genres.includes(value)));
  (options?.includeTags || []).forEach((value: string) => checks.push(series.metadata.tags.includes(value)));
  if (checks.length === 0) return true;
  return options?.inclusionMode === 'ANY'
    ? checks.some((value: boolean): boolean => value)
    : checks.every((value: boolean): boolean => value);
}

function exclusionMatches(series: KomgaSeries, options?: SeriesSearchOptions): boolean {
  const negatedChecks: boolean[] = [];
  (options?.excludeGenres || []).forEach((value: string) => negatedChecks.push(!series.metadata.genres.includes(value)));
  (options?.excludeTags || []).forEach((value: string) => negatedChecks.push(!series.metadata.tags.includes(value)));
  if (negatedChecks.length === 0) return true;
  return options?.exclusionMode === 'ALL'
    ? negatedChecks.some((value: boolean): boolean => value)
    : negatedChecks.every((value: boolean): boolean => value);
}

export function cachedSeriesReadStatus(series: KomgaSeries): string {
  if (series.booksInProgressCount > 0 || (series.booksReadCount > 0 && series.booksUnreadCount > 0)) {
    return 'IN_PROGRESS';
  }
  if (series.booksCount > 0 && series.booksUnreadCount === 0) return 'READ';
  return 'UNREAD';
}

export function cachedSeriesComplete(series: KomgaSeries): boolean {
  const totalBookCount = series.metadata.totalBookCount;
  return totalBookCount !== undefined && totalBookCount !== null && series.booksCount >= totalBookCount;
}

export function cachedSeriesFilterSupported(options?: SeriesSearchOptions,
  values?: KomgaSeries[]): boolean {
  const needsBookMetadata = (options?.authors?.length || 0) > 0 || (options?.releaseYears?.length || 0) > 0;
  if (!needsBookMetadata) return true;
  if (values === undefined) return false;
  return values.every((series: KomgaSeries): boolean => series.booksMetadata !== undefined);
}

export function matchesCachedSeriesFilter(series: KomgaSeries, options?: SeriesSearchOptions): boolean {
  if (options?.libraryId !== undefined && series.libraryId !== options.libraryId) return false;
  if (!hasAny([cachedSeriesReadStatus(series)], options?.readStatus)) return false;
  if (!hasAny([series.metadata.status], options?.publicationStatus)) return false;
  if (!hasAny([series.metadata.publisher], options?.publishers)) return false;
  if (!hasAny([series.metadata.language], options?.languages)) return false;
  if (!hasAnyAuthor(series.booksMetadata?.authors || [], options?.authors)) return false;
  const releaseYear = series.booksMetadata?.releaseDate?.slice(0, 4) || '';
  if (!hasAny(releaseYear.length > 0 ? [releaseYear] : [], options?.releaseYears)) return false;
  if (!inclusionMatches(series, options) || !exclusionMatches(series, options)) return false;

  if (options?.ageRatings !== undefined && options.ageRatings.length > 0) {
    const ageRating = series.metadata.ageRating === undefined || series.metadata.ageRating === null
      ? 'None' : `${series.metadata.ageRating}`;
    if (!options.ageRatings.includes(ageRating)) return false;
  }
  if (options?.oneshot === 'YES' && !series.oneshot) return false;
  if (options?.oneshot === 'NO' && series.oneshot) return false;
  if (options?.complete === 'YES' && !cachedSeriesComplete(series)) return false;
  if (options?.complete === 'NO' && cachedSeriesComplete(series)) return false;
  return true;
}

export function filterCachedSeries(values: KomgaSeries[], query: string,
  options?: SeriesSearchOptions): KomgaSeries[] {
  const normalized = query.trim().toLowerCase();
  return values.filter((item: KomgaSeries): boolean =>
    item.metadata.title.toLowerCase().includes(normalized) && matchesCachedSeriesFilter(item, options));
}
