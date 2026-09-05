export interface KomgaUser {
  id: string;
  email: string;
  roles: string[];
  sharedAllLibraries: boolean;
  sharedLibrariesIds: string[];
  labelsAllow?: string[];
  labelsExclude?: string[];
}

export interface KomgaUserCreateRequest {
  email: string;
  password: string;
  roles: string[];
}

export interface KomgaUserUpdateRequest {
  roles?: string[];
  labelsAllow?: string[];
  labelsExclude?: string[];
  sharedLibraries?: Record<string, Object>;
}

export interface KomgaPasswordUpdateRequest { password: string; }

export interface NumberSettingMultiSource {
  configurationSource?: number | null;
  databaseSource?: number | null;
  effectiveValue: number;
}

export interface StringSettingMultiSource {
  configurationSource?: string | null;
  databaseSource?: string | null;
  effectiveValue: string;
}

export interface KomgaSettings {
  deleteEmptyCollections: boolean;
  deleteEmptyReadLists: boolean;
  rememberMeDurationDays: number;
  thumbnailSize: string;
  taskPoolSize: number;
  serverPort: NumberSettingMultiSource;
  serverContextPath: StringSettingMultiSource;
}

export interface KomgaSettingsUpdateRequest {
  deleteEmptyCollections?: boolean;
  deleteEmptyReadLists?: boolean;
  rememberMeDurationDays?: number;
  renewRememberMeKey?: boolean;
  thumbnailSize?: string;
  taskPoolSize?: number;
  serverPort?: number | null;
  serverContextPath?: string | null;
}

export interface KomgaLibraryCreateRequest {
  name: string;
  root: string;
  importComicInfoBook: boolean;
  importComicInfoSeries: boolean;
  importComicInfoSeriesAppendVolume: boolean;
  importComicInfoCollection: boolean;
  importComicInfoReadList: boolean;
  importEpubBook: boolean;
  importEpubSeries: boolean;
  importMylarSeries: boolean;
  importLocalArtwork: boolean;
  importBarcodeIsbn: boolean;
  scanForceModifiedTime: boolean;
  repairExtensions: boolean;
  convertToCbz: boolean;
  emptyTrashAfterScan: boolean;
  seriesCover: string;
  hashFiles: boolean;
  hashPages: boolean;
  analyzeDimensions: boolean;
  scanOnStartup: boolean;
  scanCbx: boolean;
  scanPdf: boolean;
  scanEpub: boolean;
  scanInterval: string;
  oneshotsDirectory: string;
  scanDirectoryExclusions: string[];
}

export interface KomgaLibraryUpdateRequest {
  name?: string;
  root?: string;
  importComicInfoBook?: boolean;
  importComicInfoSeries?: boolean;
  importComicInfoSeriesAppendVolume?: boolean;
  importComicInfoCollection?: boolean;
  importComicInfoReadList?: boolean;
  importEpubBook?: boolean;
  importEpubSeries?: boolean;
  importMylarSeries?: boolean;
  importLocalArtwork?: boolean;
  importBarcodeIsbn?: boolean;
  scanForceModifiedTime?: boolean;
  repairExtensions?: boolean;
  convertToCbz?: boolean;
  emptyTrashAfterScan?: boolean;
  seriesCover?: string;
  hashFiles?: boolean;
  hashPages?: boolean;
  analyzeDimensions?: boolean;
  scanOnStartup?: boolean;
  scanCbx?: boolean;
  scanPdf?: boolean;
  scanEpub?: boolean;
  scanInterval?: string;
  oneshotsDirectory?: string;
  scanDirectoryExclusions?: string[];
}

export interface KomgaAuthenticationActivity {
  userId: string;
  email: string;
  ip: string;
  userAgent: string;
  success: boolean;
  error: string;
  dateTime: string;
  source: string;
}

export interface KomgaLibrary {
  id: string;
  name: string;
  root: string;
  importComicInfoBook?: boolean;
  importComicInfoSeries?: boolean;
  importComicInfoSeriesAppendVolume?: boolean;
  importComicInfoCollection?: boolean;
  importComicInfoReadList?: boolean;
  importEpubBook?: boolean;
  importEpubSeries?: boolean;
  importMylarSeries?: boolean;
  importLocalArtwork?: boolean;
  importBarcodeIsbn?: boolean;
  scanForceModifiedTime?: boolean;
  repairExtensions?: boolean;
  convertToCbz?: boolean;
  emptyTrashAfterScan?: boolean;
  seriesCover?: string;
  hashFiles?: boolean;
  hashPages?: boolean;
  hashKoreader?: boolean;
  analyzeDimensions?: boolean;
  scanOnStartup?: boolean;
  scanCbx?: boolean;
  scanPdf?: boolean;
  scanEpub?: boolean;
  scanInterval?: string;
  oneshotsDirectory?: string;
  scanDirectoryExclusions?: string[];
  unavailable: boolean;
}

export interface KomgaWebLink {
  label: string;
  url: string;
}

export interface KomgaAlternativeTitle {
  label: string;
  title: string;
}

export interface KomgaAuthor {
  name: string;
  role: string;
}

export interface SeriesBooksMetadata {
  created?: string;
  lastModified?: string;
  releaseDate?: string;
  summary: string;
  summaryNumber: string;
  authors: KomgaAuthor[];
  tags: string[];
}

export interface SeriesMetadata {
  title: string;
  titleLock?: boolean;
  titleSort: string;
  titleSortLock?: boolean;
  summary: string;
  summaryLock?: boolean;
  publisher: string;
  publisherLock?: boolean;
  language: string;
  languageLock?: boolean;
  status: string;
  statusLock?: boolean;
  readingDirection?: string;
  readingDirectionLock?: boolean;
  ageRating?: number | null;
  ageRatingLock?: boolean;
  genres: string[];
  genresLock?: boolean;
  tags: string[];
  tagsLock?: boolean;
  totalBookCount?: number | null;
  totalBookCountLock?: boolean;
  sharingLabels?: string[];
  sharingLabelsLock?: boolean;
  links?: KomgaWebLink[];
  linksLock?: boolean;
  alternateTitles?: KomgaAlternativeTitle[];
  alternateTitlesLock?: boolean;
}

export interface KomgaSeries {
  created?: string;
  lastModified?: string;
  id: string;
  libraryId: string;
  name: string;
  booksCount: number;
  booksReadCount: number;
  booksUnreadCount: number;
  booksInProgressCount: number;
  metadata: SeriesMetadata;
  booksMetadata?: SeriesBooksMetadata;
  deleted?: boolean;
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

export interface BookMetadata {
  title: string;
  titleLock?: boolean;
  summary: string;
  summaryLock?: boolean;
  number: string;
  numberLock?: boolean;
  numberSort: number;
  numberSortLock?: boolean;
  releaseDate?: string;
  releaseDateLock?: boolean;
  authors: KomgaAuthor[];
  authorsLock?: boolean;
  tags: string[];
  tagsLock?: boolean;
  isbn: string;
  isbnLock?: boolean;
  links?: KomgaWebLink[];
  linksLock?: boolean;
}

export interface SeriesMetadataUpdateRequest {
  title?: string;
  titleLock?: boolean;
  titleSort?: string;
  titleSortLock?: boolean;
  summary?: string;
  summaryLock?: boolean;
  publisher?: string;
  publisherLock?: boolean;
  language?: string;
  languageLock?: boolean;
  status?: string;
  statusLock?: boolean;
  readingDirection?: string;
  readingDirectionLock?: boolean;
  ageRating?: number | null;
  ageRatingLock?: boolean;
  genres?: string[];
  genresLock?: boolean;
  tags?: string[];
  tagsLock?: boolean;
  totalBookCount?: number | null;
  totalBookCountLock?: boolean;
  sharingLabels?: string[];
  sharingLabelsLock?: boolean;
  links?: KomgaWebLink[];
  linksLock?: boolean;
  alternateTitles?: KomgaAlternativeTitle[];
  alternateTitlesLock?: boolean;
}

export interface BookMetadataUpdateRequest {
  title?: string;
  titleLock?: boolean;
  summary?: string;
  summaryLock?: boolean;
  number?: string;
  numberLock?: boolean;
  numberSort?: number;
  numberSortLock?: boolean;
  releaseDate?: string;
  releaseDateLock?: boolean;
  authors?: KomgaAuthor[];
  authorsLock?: boolean;
  tags?: string[];
  tagsLock?: boolean;
  isbn?: string;
  isbnLock?: boolean;
  links?: KomgaWebLink[];
  linksLock?: boolean;
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
  url?: string;
  number: number;
  created?: string;
  lastModified?: string;
  fileLastModified?: string;
  sizeBytes: number;
  size: string;
  media: BookMedia;
  metadata: BookMetadata;
  readProgress?: ReadProgress | null;
  deleted?: boolean;
  fileHash?: string;
  remoteFileUnavailable?: boolean;
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
  keepReading: KomgaBook[];
  onDeck: KomgaBook[];
  recentlyReleased: KomgaBook[];
  latestBooks: KomgaBook[];
  recentlyAddedSeries: KomgaSeries[];
  recentlyUpdatedSeries: KomgaSeries[];
  recentlyRead: KomgaBook[];
  latest?: KomgaBook[];
  series?: KomgaSeries[];
}

export function emptyHomeFeed(): HomeFeed {
  return {
    keepReading: [],
    onDeck: [],
    recentlyReleased: [],
    latestBooks: [],
    recentlyAddedSeries: [],
    recentlyUpdatedSeries: [],
    recentlyRead: []
  };
}

export function normalizeHomeFeed(value?: HomeFeed): HomeFeed {
  if (value === undefined) return emptyHomeFeed();
  return {
    keepReading: value.keepReading || [],
    onDeck: value.onDeck || [],
    recentlyReleased: value.recentlyReleased || [],
    latestBooks: value.latestBooks || value.latest || [],
    recentlyAddedSeries: value.recentlyAddedSeries || value.series || [],
    recentlyUpdatedSeries: value.recentlyUpdatedSeries || [],
    recentlyRead: value.recentlyRead || []
  };
}

export function hasHomeFeedItems(value: HomeFeed): boolean {
  return value.keepReading.length > 0 || value.onDeck.length > 0 || value.recentlyReleased.length > 0 ||
    value.latestBooks.length > 0 || value.recentlyAddedSeries.length > 0 ||
    value.recentlyUpdatedSeries.length > 0 || value.recentlyRead.length > 0;
}

export interface KomgaAnnouncementAuthor {
  name: string;
  url: string;
}

export interface KomgaAnnouncementExtension {
  read: boolean;
}

export interface KomgaAnnouncement {
  id: string;
  url?: string | null;
  title?: string | null;
  summary?: string | null;
  contentHtml?: string | null;
  dateModified?: string | null;
  author?: KomgaAnnouncementAuthor | null;
  tags: string[];
  _komga?: KomgaAnnouncementExtension | null;
}

export interface KomgaAnnouncementFeed {
  version: string;
  title: string;
  home_page_url?: string | null;
  description?: string | null;
  items: KomgaAnnouncement[];
}

export interface KomgaCollection {
  id: string;
  name: string;
  ordered: boolean;
  seriesIds: string[];
  createdDate: string;
  lastModifiedDate: string;
  filtered: boolean;
}

export interface KomgaReadList {
  id: string;
  name: string;
  summary: string;
  ordered: boolean;
  bookIds: string[];
  createdDate: string;
  lastModifiedDate: string;
  filtered: boolean;
}

export interface CollectionUpdateRequest {
  name: string;
  ordered: boolean;
  seriesIds: string[];
}

export interface CollectionCreateRequest {
  name: string;
  ordered: boolean;
  seriesIds: string[];
}

export interface ReadListUpdateRequest {
  name: string;
  summary: string;
  ordered: boolean;
  bookIds: string[];
}

export interface ReadListCreateRequest {
  name: string;
  summary: string;
  ordered: boolean;
  bookIds: string[];
}

export interface KomgaTaskCount {
  count: number;
}

export interface ReadiumLocator {
  href?: string;
  type?: string;
  title?: string;
  locations?: ReadiumLocations;
  text?: Record<string, string>;
}

export interface ReadiumLocations {
  position?: number;
  fragment?: string;
  progression?: number;
  totalProgression?: number;
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
