import { KomgaAuthor } from './KomgaModels';

export const LIBRARY_PAGE_SIZES: number[] = [20, 50, 100];

export interface LibrarySeriesFilter {
  readStatus: string[];
  publicationStatus: string[];
  includeGenres: string[];
  includeTags: string[];
  excludeGenres: string[];
  excludeTags: string[];
  inclusionMode: string;
  exclusionMode: string;
  authors: KomgaAuthor[];
  releaseYears: string[];
  ageRatings: string[];
  publishers: string[];
  languages: string[];
  complete: string;
  oneshot: string;
}

export function emptyLibrarySeriesFilter(): LibrarySeriesFilter {
  return {
    readStatus: [], publicationStatus: [], includeGenres: [], includeTags: [],
    excludeGenres: [], excludeTags: [], inclusionMode: 'ALL', exclusionMode: 'ANY',
    authors: [], releaseYears: [], ageRatings: [], publishers: [], languages: [],
    complete: 'ANY', oneshot: 'ANY'
  };
}

export function copyLibrarySeriesFilter(value: LibrarySeriesFilter): LibrarySeriesFilter {
  return {
    readStatus: value.readStatus.slice(),
    publicationStatus: value.publicationStatus.slice(),
    includeGenres: value.includeGenres.slice(),
    includeTags: value.includeTags.slice(),
    excludeGenres: value.excludeGenres.slice(),
    excludeTags: value.excludeTags.slice(),
    inclusionMode: value.inclusionMode,
    exclusionMode: value.exclusionMode,
    authors: value.authors.map((author: KomgaAuthor): KomgaAuthor => ({ name: author.name, role: author.role })),
    releaseYears: value.releaseYears.slice(),
    ageRatings: value.ageRatings.slice(),
    publishers: value.publishers.slice(),
    languages: value.languages.slice(),
    complete: value.complete,
    oneshot: value.oneshot
  };
}

export function toggleFilterValue(values: string[], value: string): string[] {
  return values.includes(value) ? values.filter((item: string) => item !== value) : values.concat([value]);
}

export function sameAuthorFilter(left: KomgaAuthor, right: KomgaAuthor): boolean {
  return left.name === right.name && left.role === right.role;
}

export function toggleAuthorFilterValue(values: KomgaAuthor[], value: KomgaAuthor): KomgaAuthor[] {
  return values.some((item: KomgaAuthor): boolean => sameAuthorFilter(item, value))
    ? values.filter((item: KomgaAuthor): boolean => !sameAuthorFilter(item, value))
    : values.concat([{ name: value.name, role: value.role }]);
}

export enum LibraryMetadataFilterKind {
  PUBLICATION_STATUS = 0,
  AGE_RATING = 1,
  LANGUAGE = 2,
  PUBLISHER = 3,
  GENRE = 4,
  TAG = 5,
  AUTHOR = 6
}

export interface LibraryNavigationRequest {
  libraryId?: string;
  filter: LibrarySeriesFilter;
}

export function libraryScopeNavigationRequest(libraryId?: string): LibraryNavigationRequest {
  return { libraryId: libraryId, filter: emptyLibrarySeriesFilter() };
}

export function libraryMetadataNavigationRequest(libraryId: string | undefined, kind: LibraryMetadataFilterKind,
  value: string, role: string = ''): LibraryNavigationRequest {
  const filter = emptyLibrarySeriesFilter();
  if (kind === LibraryMetadataFilterKind.PUBLICATION_STATUS) filter.publicationStatus = [value];
  else if (kind === LibraryMetadataFilterKind.AGE_RATING) filter.ageRatings = [value];
  else if (kind === LibraryMetadataFilterKind.LANGUAGE) filter.languages = [value];
  else if (kind === LibraryMetadataFilterKind.PUBLISHER) filter.publishers = [value];
  else if (kind === LibraryMetadataFilterKind.GENRE) filter.includeGenres = [value];
  else if (kind === LibraryMetadataFilterKind.TAG) filter.includeTags = [value];
  else if (kind === LibraryMetadataFilterKind.AUTHOR) filter.authors = [{ name: value, role: role }];
  return { libraryId: libraryId, filter: filter };
}

export function copyLibraryNavigationRequest(value: LibraryNavigationRequest): LibraryNavigationRequest {
  return { libraryId: value.libraryId, filter: copyLibrarySeriesFilter(value.filter) };
}

export function librarySeriesFilterCount(value: LibrarySeriesFilter): number {
  let count = value.readStatus.length + value.publicationStatus.length + value.includeGenres.length +
    value.includeTags.length + value.excludeGenres.length + value.excludeTags.length + value.authors.length +
    value.releaseYears.length + value.ageRatings.length + value.publishers.length + value.languages.length;
  if (value.complete !== 'ANY') count += 1;
  if (value.oneshot !== 'ANY') count += 1;
  return count;
}

export interface LibrarySectionVisibility {
  showSeries: boolean;
  showCollections: boolean;
  showReadLists: boolean;
}

export function librarySectionVisibility(collectionsCount: number,
  readListsCount: number): LibrarySectionVisibility {
  const showCollections = collectionsCount > 0;
  const showReadLists = readListsCount > 0;
  return {
    showSeries: showCollections || showReadLists,
    showCollections: showCollections,
    showReadLists: showReadLists
  };
}

export const SERIES_SORT_VALUES: string[] = [
  'metadata.titleSort,asc',
  'metadata.titleSort,desc',
  'lastModifiedDate,desc',
  'lastModifiedDate,asc',
  'createdDate,desc',
  'createdDate,asc'
];

export function safeLibraryPageSizeIndex(value: number): number {
  const index = LIBRARY_PAGE_SIZES.indexOf(value);
  return index < 0 ? 0 : index;
}

export function safeSeriesSortIndex(value: string): number {
  const index = SERIES_SORT_VALUES.indexOf(value);
  return index < 0 ? 0 : index;
}
