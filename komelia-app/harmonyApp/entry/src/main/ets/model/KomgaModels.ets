export interface KomgaUser {
  id: string;
  email: string;
  roles: string[];
  sharedAllLibraries: boolean;
  sharedLibrariesIds: string[];
}

export interface KomgaLibrary {
  id: string;
  name: string;
  root: string;
  unavailable: boolean;
}

export interface SeriesMetadata {
  title: string;
  titleSort: string;
  summary: string;
  publisher: string;
  language: string;
  status: string;
  genres: string[];
  tags: string[];
}

export interface KomgaSeries {
  id: string;
  libraryId: string;
  name: string;
  booksCount: number;
  booksReadCount: number;
  booksUnreadCount: number;
  booksInProgressCount: number;
  metadata: SeriesMetadata;
  oneshot: boolean;
}

export interface BookMedia {
  status: string;
  mediaType: string;
  pagesCount: number;
  comment: string;
  epubDivinaCompatible: boolean;
  epubIsKepub: boolean;
}

export interface KomgaAuthor {
  name: string;
  role: string;
}

export interface BookMetadata {
  title: string;
  summary: string;
  number: string;
  numberSort: number;
  releaseDate?: string;
  authors: KomgaAuthor[];
  tags: string[];
  isbn: string;
}

export interface ReadProgress {
  page: number;
  completed: boolean;
  readDate: string;
}

export interface KomgaBook {
  id: string;
  seriesId: string;
  seriesTitle: string;
  libraryId: string;
  name: string;
  number: number;
  sizeBytes: number;
  size: string;
  media: BookMedia;
  metadata: BookMetadata;
  readProgress?: ReadProgress;
  oneshot: boolean;
}

export interface KomgaBookPage {
  number: number;
  fileName: string;
  mediaType: string;
  width?: number;
  height?: number;
  sizeBytes?: number;
  size?: string;
}

export interface PageResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  last: boolean;
  first: boolean;
  number: number;
  size: number;
  empty: boolean;
}

export interface HomeFeed {
  onDeck: KomgaBook[];
  latest: KomgaBook[];
  series: KomgaSeries[];
}

export function emptyPage<T>(): PageResult<T> {
  return {
    content: [],
    totalElements: 0,
    totalPages: 0,
    last: true,
    first: true,
    number: 0,
    size: 0,
    empty: true
  };
}
