import { http } from '@kit.NetworkKit';
import { rcp } from '@kit.RemoteCommunicationKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { util } from '@kit.ArkTS';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { localizedString } from '../core/i18n/LocalizedResources';
import { AppBuildInfo } from '../core/AppBuildInfo';
import { evaluateOriginPolicy, OriginPolicyError } from './OriginPolicy';
import {
  HomeFeed,
  emptyHomeFeed,
  normalizeHomeFeed,
  KomgaAnnouncementFeed,
  CollectionCreateRequest,
  CollectionUpdateRequest,
  KomgaAuthenticationActivity,
  KomgaAuthor,
  KomgaBook,
  KomgaBookPage,
  KomgaCollection,
  KomgaLibrary,
  KomgaLibraryCreateRequest,
  KomgaLibraryUpdateRequest,
  KomgaReadList,
  KomgaSeries,
  KomgaSettings,
  KomgaSettingsUpdateRequest,
  KomgaUser,
  KomgaUserCreateRequest,
  KomgaPasswordUpdateRequest,
  KomgaUserUpdateRequest,
  BookMetadataUpdateRequest,
  PageResult,
  ReadListCreateRequest,
  ReadListUpdateRequest,
  ReadiumLocator,
  SeriesMetadataUpdateRequest
} from '../model/KomgaModels';

const LOG_DOMAIN = 0x0000;
const LOG_TAG = 'KomeliaKomgaClient';

export class KomgaHttpError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'KomgaHttpError';
    this.status = status;
  }
}

export interface SeriesSearchOperator {
  operator: string;
  value?: string | number | AuthorMatch;
  dateTime?: string;
}

export interface AuthorMatch {
  name: string;
  role: string | null;
}

export interface SeriesSearchCondition {
  type: string;
  allOf?: SeriesSearchCondition[];
  anyOf?: SeriesSearchCondition[];
  libraryId?: SeriesSearchOperator;
  seriesStatus?: SeriesSearchOperator;
  readStatus?: SeriesSearchOperator;
  publisher?: SeriesSearchOperator;
  language?: SeriesSearchOperator;
  genre?: SeriesSearchOperator;
  tag?: SeriesSearchOperator;
  author?: SeriesSearchOperator;
  ageRating?: SeriesSearchOperator;
  releaseDate?: SeriesSearchOperator;
  complete?: SeriesSearchOperator;
  oneShot?: SeriesSearchOperator;
}

export interface SearchRequest {
  fullTextSearch?: string;
  condition?: SeriesSearchCondition;
}

export interface SeriesSearchOptions {
  libraryId?: string;
  sort?: string;
  readStatus?: string[];
  publicationStatus?: string[];
  includeGenres?: string[];
  includeTags?: string[];
  excludeGenres?: string[];
  excludeTags?: string[];
  inclusionMode?: string;
  exclusionMode?: string;
  authors?: KomgaAuthor[];
  releaseYears?: string[];
  ageRatings?: string[];
  publishers?: string[];
  languages?: string[];
  complete?: string;
  oneshot?: string;
}

export interface SeriesFilterReferential {
  genres: string[];
  tags: string[];
  releaseYears: string[];
  ageRatings: string[];
  publishers: string[];
  languages: string[];
}

function equality(value: string | number | AuthorMatch, operator: string = 'is'): SeriesSearchOperator {
  return { operator: operator, value: value };
}

function anyOf(conditions: SeriesSearchCondition[]): SeriesSearchCondition {
  return { type: 'AnyOfSeries', anyOf: conditions };
}

function allOf(conditions: SeriesSearchCondition[]): SeriesSearchCondition {
  return { type: 'AllOfSeries', allOf: conditions };
}

function addAnyOfValues(conditions: SeriesSearchCondition[], values: string[] | undefined,
  factory: (value: string) => SeriesSearchCondition): void {
  if (values === undefined || values.length === 0) return;
  conditions.push(anyOf(values.map((value: string): SeriesSearchCondition => factory(value))));
}

function normalizedYear(value: string): number | undefined {
  const year = Number(value);
  return Number.isInteger(year) && year >= 1 && year <= 9998 ? year : undefined;
}

export function seriesFilterConditions(options?: SeriesSearchOptions): SeriesSearchCondition[] {
  const conditions: SeriesSearchCondition[] = [];
  addAnyOfValues(conditions, options?.publicationStatus, (value: string): SeriesSearchCondition =>
    ({ type: 'SeriesStatus', seriesStatus: equality(value) }));
  addAnyOfValues(conditions, options?.readStatus, (value: string): SeriesSearchCondition =>
    ({ type: 'ReadStatus', readStatus: equality(value) }));
  addAnyOfValues(conditions, options?.publishers, (value: string): SeriesSearchCondition =>
    ({ type: 'Publisher', publisher: equality(value) }));
  addAnyOfValues(conditions, options?.languages, (value: string): SeriesSearchCondition =>
    ({ type: 'Language', language: equality(value) }));

  const included: SeriesSearchCondition[] = [];
  (options?.includeGenres || []).forEach((value: string) =>
    included.push({ type: 'Genre', genre: equality(value) }));
  (options?.includeTags || []).forEach((value: string) =>
    included.push({ type: 'Tag', tag: equality(value) }));
  if (included.length > 0) {
    conditions.push(options?.inclusionMode === 'ANY' ? anyOf(included) : allOf(included));
  }

  const excluded: SeriesSearchCondition[] = [];
  (options?.excludeGenres || []).forEach((value: string) =>
    excluded.push({ type: 'Genre', genre: equality(value, 'isNot') }));
  (options?.excludeTags || []).forEach((value: string) =>
    excluded.push({ type: 'Tag', tag: equality(value, 'isNot') }));
  if (excluded.length > 0) {
    conditions.push(options?.exclusionMode === 'ALL' ? anyOf(excluded) : allOf(excluded));
  }

  (options?.authors || []).forEach((value: KomgaAuthor) => {
    const name = value.name.trim();
    if (name.length > 0) {
      conditions.push({ type: 'Author', author: equality({ name: name, role: value.role.trim() || null }) });
    }
  });

  addAnyOfValues(conditions, options?.ageRatings, (value: string): SeriesSearchCondition => {
    if (value === 'None') return { type: 'AgeRating', ageRating: { operator: 'isNull' } };
    return { type: 'AgeRating', ageRating: equality(Number(value)) };
  });

  const releaseDates: SeriesSearchCondition[] = [];
  (options?.releaseYears || []).forEach((value: string) => {
    const year = normalizedYear(value);
    if (year !== undefined) {
      releaseDates.push(allOf([
        { type: 'ReleaseDate', releaseDate: {
          operator: 'after', dateTime: `${year - 1}-12-31T00:00:00Z`
        } },
        { type: 'ReleaseDate', releaseDate: {
          operator: 'before', dateTime: `${year + 1}-01-01T00:00:00Z`
        } }
      ]));
    }
  });
  if (releaseDates.length > 0) conditions.push(anyOf(releaseDates));

  if (options?.complete === 'YES') {
    conditions.push({ type: 'Complete', complete: { operator: 'isTrue' } });
  } else if (options?.complete === 'NO') {
    conditions.push({ type: 'Complete', complete: { operator: 'isFalse' } });
  }
  if (options?.oneshot === 'YES') {
    conditions.push({ type: 'OneShot', oneShot: { operator: 'isTrue' } });
  } else if (options?.oneshot === 'NO') {
    conditions.push({ type: 'OneShot', oneShot: { operator: 'isFalse' } });
  }
  return conditions;
}

export function buildSeriesSearchRequest(query: string, options?: SeriesSearchOptions): SearchRequest {
  const normalized = query.trim();
  const conditions: SeriesSearchCondition[] = seriesFilterConditions(options);
  const libraryId = options?.libraryId?.trim() || '';
  if (libraryId.length > 0) {
    conditions.push({ type: 'LibraryId', libraryId: { operator: 'is', value: libraryId } });
  }
  const body: SearchRequest = {};
  if (normalized.length > 0) body.fullTextSearch = normalized;
  if (conditions.length > 0) body.condition = { type: 'AllOfSeries', allOf: conditions };
  return body;
}

export function seriesSearchSort(query: string, options?: SeriesSearchOptions): string {
  const requestedSort = options?.sort?.trim() || '';
  if (requestedSort.length > 0) return requestedSort;
  return query.trim().length === 0 ? 'lastModifiedDate,desc' : '';
}

function referentialPath(path: string, libraryId?: string): string {
  const normalized = libraryId?.trim() || '';
  return normalized.length === 0 ? path : `${path}?library_id=${encodeURIComponent(normalized)}`;
}

export interface EqualityOperator {
  operator: string;
  value: string;
}

export interface MediaStatusCondition {
  type: string;
  mediaStatus?: EqualityOperator;
  readStatus?: EqualityOperator;
  releaseDate?: DurationOperator;
}

export interface DurationOperator {
  operator: string;
  duration: string;
}

export interface AllOfBookCondition {
  type: string;
  allOf: MediaStatusCondition[];
}

export interface BookConditionSearchRequest {
  condition: AllOfBookCondition;
}

export function mediaStatusBookSearch(statuses: string[]): BookConditionSearchRequest {
  const conditions: MediaStatusCondition[] = [];
  for (let index = 0; index < statuses.length; index += 1) {
    const operator: EqualityOperator = { operator: 'is', value: statuses[index] };
    const condition: MediaStatusCondition = { type: 'MediaStatus', mediaStatus: operator };
    conditions.push(condition);
  }
  const allOf: AllOfBookCondition = { type: 'AllOfBook', allOf: conditions };
  return { condition: allOf };
}

export function readStatusBookSearch(status: string): BookConditionSearchRequest {
  const operator: EqualityOperator = { operator: 'is', value: status };
  const condition: MediaStatusCondition = { type: 'ReadStatus', readStatus: operator };
  const allOf: AllOfBookCondition = { type: 'AllOfBook', allOf: [condition] };
  return { condition: allOf };
}

export function recentReleaseBookSearch(duration: string = 'PT720H'): BookConditionSearchRequest {
  const operator: DurationOperator = { operator: 'isInTheLast', duration: duration };
  const condition: MediaStatusCondition = { type: 'ReleaseDate', releaseDate: operator };
  const allOf: AllOfBookCondition = { type: 'AllOfBook', allOf: [condition] };
  return { condition: allOf };
}

export function allBooksSearch(): BookConditionSearchRequest {
  const allOf: AllOfBookCondition = { type: 'AllOfBook', allOf: [] };
  return { condition: allOf };
}

export interface ReadProgressUpdate {
  page?: number;
  completed?: boolean;
}

export function readProgressUpdateBody(page: number, completed: boolean): ReadProgressUpdate {
  return completed ? { completed: true } : { page: page };
}

interface ActiveFileDownload {
  session: rcp.Session;
  request: rcp.Request;
}

export class KomgaClient {
  private serverUrl: string = '';
  private authorization: string = '';
  private imageCacheRevision: number = 0;
  private activeFileDownloads: Map<string, ActiveFileDownload> = new Map<string, ActiveFileDownload>();

  configure(serverUrl: string, email: string, password: string, allowInsecureHttp: boolean = false): void {
    const policy = evaluateOriginPolicy(serverUrl, allowInsecureHttp);
    if (policy.error === OriginPolicyError.SCHEME_REQUIRED) {
      throw new Error(localizedString($r('app.string.server_url_scheme_validation')));
    }
    if (policy.error === OriginPolicyError.HTTP_CONFIRMATION_REQUIRED) {
      throw new Error(localizedString($r('app.string.http_confirmation_required')));
    }
    const encoded = new util.TextEncoder().encodeInto(`${email}:${password}`);
    const authorization = `Basic ${new util.Base64Helper().encodeToStringSync(encoded)}`;
    if (this.serverUrl !== policy.normalizedOrigin || this.authorization !== authorization) {
      this.imageCacheRevision += 1;
    }
    this.serverUrl = policy.normalizedOrigin;
    this.authorization = authorization;
  }

  clear(): void {
    if (this.serverUrl.length > 0 || this.authorization.length > 0) this.imageCacheRevision += 1;
    this.serverUrl = '';
    this.authorization = '';
  }

  isConfigured(): boolean {
    return this.serverUrl.length > 0 && this.authorization.length > 0;
  }

  getServerUrl(): string {
    return this.serverUrl;
  }

  getImageCacheScope(): number {
    return this.imageCacheRevision;
  }

  async login(): Promise<KomgaUser> {
    return this.getJson<KomgaUser>('/api/v2/users/me');
  }

  async getLibraries(): Promise<KomgaLibrary[]> {
    return this.getJson<KomgaLibrary[]>('/api/v1/libraries');
  }

  async addLibrary(request: KomgaLibraryCreateRequest): Promise<KomgaLibrary> {
    return this.postJson<KomgaLibrary>('/api/v1/libraries', request);
  }

  async updateLibrary(id: string, request: KomgaLibraryUpdateRequest): Promise<void> {
    await this.sendPatchJson(`/api/v1/libraries/${encodeURIComponent(id)}`, request);
  }

  async deleteLibrary(id: string): Promise<void> {
    await this.request(`/api/v1/libraries/${encodeURIComponent(id)}`, http.RequestMethod.DELETE,
      http.HttpDataType.STRING);
  }

  async getCollections(query: string = '', page: number = 0, size: number = 100): Promise<PageResult<KomgaCollection>> {
    const search = query.trim().length > 0 ? `&search=${encodeURIComponent(query.trim())}` : '';
    return this.getJson<PageResult<KomgaCollection>>(
      `/api/v1/collections?page=${page}&size=${size}&sort=name,asc${search}`);
  }

  async getCollection(id: string): Promise<KomgaCollection> {
    return this.getJson<KomgaCollection>(`/api/v1/collections/${encodeURIComponent(id)}`);
  }

  async createCollection(request: CollectionCreateRequest): Promise<KomgaCollection> {
    return this.postJson<KomgaCollection>('/api/v1/collections', request);
  }

  async getReadLists(query: string = '', page: number = 0, size: number = 100): Promise<PageResult<KomgaReadList>> {
    const search = query.trim().length > 0 ? `&search=${encodeURIComponent(query.trim())}` : '';
    return this.getJson<PageResult<KomgaReadList>>(
      `/api/v1/readlists?page=${page}&size=${size}&sort=name,asc${search}`);
  }


  async getReadList(id: string): Promise<KomgaReadList> {
    return this.getJson<KomgaReadList>(`/api/v1/readlists/${encodeURIComponent(id)}`);
  }

  async createReadList(request: ReadListCreateRequest): Promise<KomgaReadList> {
    return this.postJson<KomgaReadList>('/api/v1/readlists', request);
  }

  async getCollectionSeries(id: string, page: number = 0, size: number = 100): Promise<PageResult<KomgaSeries>> {
    return this.getJson<PageResult<KomgaSeries>>(
      `/api/v1/collections/${encodeURIComponent(id)}/series?page=${page}&size=${size}`);
  }

  async getReadListBooks(id: string, page: number = 0, size: number = 100): Promise<PageResult<KomgaBook>> {
    return this.getJson<PageResult<KomgaBook>>(
      `/api/v1/readlists/${encodeURIComponent(id)}/books?page=${page}&size=${size}`);
  }

  async updateCollection(id: string, request: CollectionUpdateRequest): Promise<void> {
    await this.sendPatchJson(`/api/v1/collections/${encodeURIComponent(id)}`, request);
  }

  async updateReadList(id: string, request: ReadListUpdateRequest): Promise<void> {
    await this.sendPatchJson(`/api/v1/readlists/${encodeURIComponent(id)}`, request);
  }

  async deleteCollection(id: string): Promise<void> {
    await this.request(`/api/v1/collections/${encodeURIComponent(id)}`, http.RequestMethod.DELETE,
      http.HttpDataType.STRING);
  }

  async deleteReadList(id: string): Promise<void> {
    await this.request(`/api/v1/readlists/${encodeURIComponent(id)}`, http.RequestMethod.DELETE,
      http.HttpDataType.STRING);
  }

  async getHomeFeed(seed?: HomeFeed, onUpdate?: (value: HomeFeed) => void): Promise<HomeFeed> {
    const feed = seed === undefined ? emptyHomeFeed() : normalizeHomeFeed(seed);
    let completedSections: number = 0;
    let firstErrorMessage: string = '';
    const publish = (): void => {
      completedSections += 1;
      if (onUpdate !== undefined) onUpdate(normalizeHomeFeed(feed));
    };
    const captureError = (error: Error): void => {
      if (firstErrorMessage.length === 0) firstErrorMessage = error.message;
    };
    const requests: Promise<void>[] = [
      this.postJson<PageResult<KomgaBook>>(
        '/api/v1/books/list?page=0&size=20&sort=readProgress.readDate,desc',
        readStatusBookSearch('IN_PROGRESS'))
        .then((value: PageResult<KomgaBook>): void => { feed.keepReading = value.content; publish(); })
        .catch((error: Error): void => captureError(error)),
      this.getJson<PageResult<KomgaBook>>('/api/v1/books/ondeck?page=0&size=20')
        .then((value: PageResult<KomgaBook>): void => { feed.onDeck = value.content; publish(); })
        .catch((error: Error): void => captureError(error)),
      this.postJson<PageResult<KomgaBook>>(
        '/api/v1/books/list?page=0&size=20&sort=metadata.releaseDate,desc', recentReleaseBookSearch())
        .then((value: PageResult<KomgaBook>): void => { feed.recentlyReleased = value.content; publish(); })
        .catch((error: Error): void => captureError(error)),
      this.postJson<PageResult<KomgaBook>>(
        '/api/v1/books/list?page=0&size=20&sort=createdDate,desc', allBooksSearch())
        .then((value: PageResult<KomgaBook>): void => { feed.latestBooks = value.content; publish(); })
        .catch((error: Error): void => captureError(error)),
      this.postJson<PageResult<KomgaBook>>(
        '/api/v1/books/list?page=0&size=20&sort=readProgress.readDate,desc', readStatusBookSearch('READ'))
        .then((value: PageResult<KomgaBook>): void => { feed.recentlyRead = value.content; publish(); })
        .catch((error: Error): void => captureError(error)),
      this.getJson<PageResult<KomgaSeries>>('/api/v1/series/new?oneshot=false&page=0&size=20')
        .then((value: PageResult<KomgaSeries>): void => { feed.recentlyAddedSeries = value.content; publish(); })
        .catch((error: Error): void => captureError(error)),
      this.getJson<PageResult<KomgaSeries>>('/api/v1/series/updated?oneshot=false&page=0&size=20')
        .then((value: PageResult<KomgaSeries>): void => { feed.recentlyUpdatedSeries = value.content; publish(); })
        .catch((error: Error): void => captureError(error))
    ];
    await Promise.all(requests);
    if (completedSections === 0) {
      throw new Error(firstErrorMessage.length > 0 ? firstErrorMessage : 'HOME_FEED_REQUEST_FAILED');
    }
    hilog.info(LOG_DOMAIN, LOG_TAG,
      `Home feed updated progressively: completed=${completedSections}, ` +
        `keepReading=${feed.keepReading.length}, onDeck=${feed.onDeck.length}, ` +
        `released=${feed.recentlyReleased.length}, latest=${feed.latestBooks.length}, ` +
        `series=${feed.recentlyAddedSeries.length}, updatedSeries=${feed.recentlyUpdatedSeries.length}, ` +
        `recentlyRead=${feed.recentlyRead.length}`);
    return normalizeHomeFeed(feed);
  }

  async searchSeries(query: string, page: number = 0, size: number = 30,
    options?: SeriesSearchOptions): Promise<PageResult<KomgaSeries>> {
    const body = buildSeriesSearchRequest(query, options);
    const sortValue = seriesSearchSort(query, options);
    const sort = sortValue.length > 0 ? `&sort=${encodeURIComponent(sortValue)}` : '';
    return this.postJson<PageResult<KomgaSeries>>(
      `/api/v1/series/list?page=${page}&size=${size}${sort}`, body);
  }

  async getSeriesFilterReferential(libraryId?: string): Promise<SeriesFilterReferential> {
    const values = await Promise.all([
      this.getJson<string[]>(referentialPath('/api/v1/genres', libraryId)),
      this.getJson<string[]>(referentialPath('/api/v1/tags', libraryId)),
      this.getJson<string[]>(referentialPath('/api/v1/series/release-dates', libraryId)),
      this.getJson<Array<number | null>>(referentialPath('/api/v1/age-ratings', libraryId)),
      this.getJson<string[]>(referentialPath('/api/v1/publishers', libraryId)),
      this.getJson<string[]>(referentialPath('/api/v1/languages', libraryId))
    ]);
    return {
      genres: values[0] as string[],
      tags: values[1] as string[],
      releaseYears: (values[2] as string[]).map((value: string): string => value.slice(0, 4)),
      ageRatings: (values[3] as Array<number | null>)
        .map((value: number | null): string => value === null ? 'None' : `${value}`),
      publishers: values[4] as string[],
      languages: values[5] as string[]
    };
  }

  async searchAuthors(query: string, libraryId?: string): Promise<KomgaAuthor[]> {
    const parameters: string[] = [
      `search=${encodeURIComponent(query.trim())}`,
      'page=0',
      'size=50',
      'sort=name,asc'
    ];
    const normalizedLibrary = libraryId?.trim() || '';
    if (normalizedLibrary.length > 0) {
      parameters.push(`library_id=${encodeURIComponent(normalizedLibrary)}`);
    }
    const result = await this.getJson<PageResult<KomgaAuthor>>(`/api/v2/authors?${parameters.join('&')}`);
    return result.content;
  }

  async searchBooks(query: string, page: number = 0, size: number = 30,
    sortOverride?: string): Promise<PageResult<KomgaBook>> {
    const normalized = query.trim();
    const body: SearchRequest = normalized.length > 0 ? { fullTextSearch: normalized } : {};
    const sort = sortOverride !== undefined ? `&sort=${encodeURIComponent(sortOverride)}` :
      normalized.length === 0 ? '&sort=lastModifiedDate,desc' : '';
    return this.postJson<PageResult<KomgaBook>>(
      `/api/v1/books/list?page=${page}&size=${size}${sort}`, body);
  }

  async getBooksByMediaStatus(statuses: string[], page: number = 0,
    size: number = 20): Promise<PageResult<KomgaBook>> {
    const body = mediaStatusBookSearch(statuses);
    return this.postJson<PageResult<KomgaBook>>(
      `/api/v1/books/list?page=${page}&size=${size}&sort=name,asc`, body);
  }

  async getAnnouncements(): Promise<KomgaAnnouncementFeed> {
    return this.getJson<KomgaAnnouncementFeed>('/api/v1/announcements');
  }

  async markAnnouncementsRead(ids: string[]): Promise<void> {
    await this.sendJson('/api/v1/announcements', http.RequestMethod.PUT, ids);
  }

  async getSeries(id: string): Promise<KomgaSeries> {
    return this.getJson<KomgaSeries>(`/api/v1/series/${encodeURIComponent(id)}`);
  }

  async getSeriesBooks(id: string): Promise<KomgaBook[]> {
    const page = await this.getJson<PageResult<KomgaBook>>(
      `/api/v1/series/${encodeURIComponent(id)}/books?page=0&size=500&sort=metadata.numberSort,asc`);
    return page.content;
  }

  async getBook(id: string): Promise<KomgaBook> {
    return this.getJson<KomgaBook>(`/api/v1/books/${encodeURIComponent(id)}`);
  }

  async getBookPages(id: string): Promise<KomgaBookPage[]> {
    return this.getJson<KomgaBookPage[]>(`/api/v1/books/${encodeURIComponent(id)}/pages`);
  }

  async getSeriesThumbnail(id: string): Promise<ArrayBuffer> {
    return this.getBytes(`/api/v1/series/${encodeURIComponent(id)}/thumbnail`);
  }

  async getBookThumbnail(id: string): Promise<ArrayBuffer> {
    return this.getBytes(`/api/v1/books/${encodeURIComponent(id)}/thumbnail`);
  }

  async getBookPage(id: string, page: number): Promise<ArrayBuffer> {
    return this.getBytes(`/api/v1/books/${encodeURIComponent(id)}/pages/${page}`);
  }

  async getBookFile(id: string): Promise<ArrayBuffer> {
    return this.getBytes(`/api/v1/books/${encodeURIComponent(id)}/file`, 120000);
  }

  async downloadBookFile(id: string, target: string, operationId: string = '',
    onProgress?: (total: number, transferred: number) => void): Promise<void> {
    await this.downloadToFile(`/api/v1/books/${encodeURIComponent(id)}/file`, target, 300000, operationId, true, onProgress);
  }

  async downloadBookPage(id: string, page: number, target: string, operationId: string = '',
    onProgress?: (total: number, transferred: number) => void): Promise<void> {
    await this.downloadToFile(`/api/v1/books/${encodeURIComponent(id)}/pages/${page}`, target, 120000,
      operationId, false, onProgress);
  }

  cancelFileDownload(operationId: string): void {
    const active = this.activeFileDownloads.get(operationId);
    if (active === undefined) return;
    try {
      active.session.cancel(active.request);
    } catch (_) {
    }
  }

  async updateReadProgress(id: string, page: number, completed: boolean): Promise<void> {
    // Komga accepts either a page update or a completed update. Sending both
    // fields (especially completed=false) is rejected as an invalid parameter.
    const body = readProgressUpdateBody(page, completed);
    await this.sendPatchJson(`/api/v1/books/${encodeURIComponent(id)}/read-progress`, body);
  }

  async deleteReadProgress(id: string): Promise<void> {
    await this.request(`/api/v1/books/${encodeURIComponent(id)}/read-progress`, http.RequestMethod.DELETE,
      http.HttpDataType.STRING);
  }

  async getReadiumProgression(id: string): Promise<ReadiumLocator> {
    return this.getJson<ReadiumLocator>(`/api/v1/books/${encodeURIComponent(id)}/progression`);
  }

  async updateReadiumProgression(id: string, locator: ReadiumLocator): Promise<void> {
    await this.sendJson(`/api/v1/books/${encodeURIComponent(id)}/progression`, http.RequestMethod.PUT, locator);
  }

  async getUsers(): Promise<KomgaUser[]> {
    return this.getJson<KomgaUser[]>('/api/v2/users');
  }

  async addUser(request: KomgaUserCreateRequest): Promise<KomgaUser> {
    return this.postJson<KomgaUser>('/api/v2/users', request);
  }

  async updateUser(id: string, request: KomgaUserUpdateRequest): Promise<void> {
    await this.sendPatchJson(`/api/v2/users/${encodeURIComponent(id)}`, request);
  }

  async updateUserPassword(id: string, password: string): Promise<void> {
    const request: KomgaPasswordUpdateRequest = { password: password };
    await this.sendPatchJson(`/api/v2/users/${encodeURIComponent(id)}/password`, request);
  }

  async deleteUser(id: string): Promise<void> {
    await this.request(`/api/v2/users/${encodeURIComponent(id)}`, http.RequestMethod.DELETE,
      http.HttpDataType.STRING);
  }

  async getAuthenticationActivity(forMe: boolean, page: number = 0,
    size: number = 50): Promise<PageResult<KomgaAuthenticationActivity>> {
    const root = forMe ? '/api/v2/users/me/authentication-activity' : '/api/v2/users/authentication-activity';
    return this.getJson<PageResult<KomgaAuthenticationActivity>>(`${root}?page=${page}&size=${size}&sort=dateTime,desc`);
  }

  async getSettings(): Promise<KomgaSettings> {
    return this.getJson<KomgaSettings>('/api/v1/settings');
  }

  async updateSettings(request: KomgaSettingsUpdateRequest): Promise<void> {
    await this.sendPatchJson('/api/v1/settings', request);
  }

  async updateSeriesMetadata(id: string, metadata: SeriesMetadataUpdateRequest): Promise<void> {
    await this.sendPatchJson(`/api/v1/series/${encodeURIComponent(id)}/metadata`, metadata);
  }

  async updateBookMetadata(id: string, metadata: BookMetadataUpdateRequest): Promise<void> {
    await this.sendPatchJson(`/api/v1/books/${encodeURIComponent(id)}/metadata`, metadata);
  }

  async runLibraryAction(id: string, action: string, deep: boolean = false): Promise<void> {
    const allowed = ['scan', 'analyze', 'metadata/refresh', 'empty-trash'];
    if (!allowed.includes(action)) throw new Error(localizedString($r('app.string.unsupported_library_action')));
    const query = action === 'scan' ? `?deep=${deep}` : '';
    await this.request(`/api/v1/libraries/${encodeURIComponent(id)}/${action}${query}`,
      http.RequestMethod.POST, http.HttpDataType.STRING);
  }

  async runSeriesAction(id: string, action: string): Promise<void> {
    const allowed = ['analyze', 'metadata/refresh'];
    if (!allowed.includes(action)) throw new Error(localizedString($r('app.string.unsupported_series_action')));
    await this.request(`/api/v1/series/${encodeURIComponent(id)}/${action}`,
      http.RequestMethod.POST, http.HttpDataType.STRING);
  }

  async setSeriesReadStatus(id: string, completed: boolean): Promise<void> {
    if (completed) {
      await this.request(`/api/v1/series/${encodeURIComponent(id)}/read-progress`, http.RequestMethod.PUT,
        http.HttpDataType.STRING);
    } else {
      await this.request(`/api/v1/series/${encodeURIComponent(id)}/read-progress`, http.RequestMethod.DELETE,
        http.HttpDataType.STRING);
    }
  }

  async deleteSeriesFiles(id: string): Promise<void> {
    await this.request(`/api/v1/series/${encodeURIComponent(id)}/file`, http.RequestMethod.DELETE,
      http.HttpDataType.STRING);
  }

  async runBookAction(id: string, action: string): Promise<void> {
    const allowed = ['analyze', 'metadata/refresh'];
    if (!allowed.includes(action)) throw new Error(localizedString($r('app.string.unsupported_book_action')));
    await this.request(`/api/v1/books/${encodeURIComponent(id)}/${action}`,
      http.RequestMethod.POST, http.HttpDataType.STRING);
  }

  async deleteBookFile(id: string): Promise<void> {
    await this.request(`/api/v1/books/${encodeURIComponent(id)}/file`, http.RequestMethod.DELETE,
      http.HttpDataType.STRING);
  }

  async emptyTaskQueue(): Promise<number> {
    const response = await this.request('/api/v1/tasks', http.RequestMethod.DELETE, http.HttpDataType.STRING);
    if ((response.result as string).length === 0) return 0;
    return Number(response.result as string);
  }

  private async getJson<T>(path: string): Promise<T> {
    const response = await this.request(path, http.RequestMethod.GET, http.HttpDataType.STRING);
    return JSON.parse(response.result as string) as T;
  }

  private async postJson<T>(path: string, body: Object): Promise<T> {
    const response = await this.request(path, http.RequestMethod.POST, http.HttpDataType.STRING,
      JSON.stringify(body));
    return JSON.parse(response.result as string) as T;
  }

  private async sendJson(path: string, method: http.RequestMethod, body: Object): Promise<void> {
    await this.request(path, method, http.HttpDataType.STRING, JSON.stringify(body));
  }

  private async sendPatchJson(path: string, body: Object): Promise<void> {
    if (!this.isConfigured()) {
      throw new Error(localizedString($r('app.string.komga_not_signed_in')));
    }
    const session = rcp.createSession();
    try {
      const request = new rcp.Request(`${this.serverUrl}${path}`, 'PATCH', {
        'authorization': this.authorization,
        'accept': 'application/json',
        'content-type': 'application/json',
        'user-agent': AppBuildInfo.userAgent
      }, JSON.stringify(body));
      const response = await session.fetch(request);
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw new KomgaHttpError(response.statusCode,
          localizedString($r('app.string.komga_request_failed'), response.statusCode));
      }
    } finally {
      session.close();
    }
  }

  private async downloadToFile(path: string, target: string, timeout: number, operationId: string,
    resumable: boolean = false, onProgress?: (total: number, transferred: number) => void): Promise<void> {
    if (!this.isConfigured()) {
      throw new Error(localizedString($r('app.string.komga_not_signed_in')));
    }
    const resumeFrom = resumable && fs.accessSync(target) ? fs.statSync(target).size : 0;
    const session = rcp.createSession();
    let appendFile: fs.File | undefined = undefined;
    let restartFromBeginning = false;
    try {
      const request = new rcp.Request(`${this.serverUrl}${path}`, 'GET', {
        'authorization': this.authorization,
        'accept': '*/*',
        'user-agent': AppBuildInfo.userAgent
      });
      if (resumeFrom > 0) {
        appendFile = fs.openSync(target, fs.OpenMode.WRITE_ONLY | fs.OpenMode.APPEND);
        request.transferRange = { from: resumeFrom };
        request.destination = { kind: 'file', file: appendFile };
      } else {
        request.destination = { kind: 'file', file: target, keepLocal: true };
      }
      request.configuration = {
        tracing: { httpEventsHandler: { onDownloadProgress: (total: number, transferred: number): void => {
          if (onProgress !== undefined) onProgress(total > 0 ? resumeFrom + total : 0, resumeFrom + transferred);
        } } },
        transfer: { timeout: { connectMs: 30000, transferMs: timeout, inactivityMs: 30000 } }
      };
      if (operationId.length > 0) {
        this.activeFileDownloads.set(operationId, { session: session, request: request });
      }
      const response = await session.fetch(request);
      if (resumeFrom > 0 && response.statusCode === 200) {
        restartFromBeginning = true;
      } else if (resumeFrom > 0 && response.statusCode === 416) {
        return;
      }
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw new KomgaHttpError(response.statusCode,
          localizedString($r('app.string.komga_request_failed'), response.statusCode));
      }
    } finally {
      if (operationId.length > 0) this.activeFileDownloads.delete(operationId);
      if (appendFile !== undefined) fs.closeSync(appendFile);
      session.close();
    }
    if (restartFromBeginning) {
      if (fs.accessSync(target)) fs.unlinkSync(target);
      await this.downloadToFile(path, target, timeout, operationId, false, onProgress);
    }
  }

  private async getBytes(path: string, timeout: number = 60000): Promise<ArrayBuffer> {
    const response = await this.request(path, http.RequestMethod.GET, http.HttpDataType.ARRAY_BUFFER,
      undefined, timeout);
    return response.result as ArrayBuffer;
  }

  private async request(path: string, method: http.RequestMethod, dataType: http.HttpDataType,
    body?: string, timeout: number = 30000): Promise<http.HttpResponse> {
    if (!this.isConfigured()) {
      throw new Error(localizedString($r('app.string.komga_not_signed_in')));
    }
    const request = http.createHttp();
    try {
      const response = await request.request(`${this.serverUrl}${path}`, {
        method: method,
        header: {
          'Authorization': this.authorization,
          'Accept': dataType === http.HttpDataType.STRING ? 'application/json' : '*/*',
          'Content-Type': 'application/json',
          'User-Agent': AppBuildInfo.userAgent
        },
        // ArkTS' strict request option type does not accept an optional value here.
        extraData: body ?? '',
        expectDataType: dataType,
        usingCache: method === http.RequestMethod.GET,
        connectTimeout: timeout,
        readTimeout: timeout
      });
      if (response.responseCode < 200 || response.responseCode >= 300) {
        throw new KomgaHttpError(response.responseCode,
          localizedString($r('app.string.komga_request_failed'), response.responseCode));
      }
      return response;
    } finally {
      request.destroy();
    }
  }
}

export const komgaClient = new KomgaClient();
