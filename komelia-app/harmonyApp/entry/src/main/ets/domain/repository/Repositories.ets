import { HomeFeed, KomgaBook, KomgaLibrary, KomgaLibraryCreateRequest, KomgaLibraryUpdateRequest,
  KomgaSeries, KomgaUser, PageResult } from '../../model/KomgaModels';
import { DownloadJobRecord, NamespaceKey, OfflineBookRecord, OfflinePageRecord,
  OfflineLogLevel, OfflineLogPage, ProgressOutboxRecord } from '../../data/local/DatabaseModels';
import { TaskPhase } from '../../core/model/UiState';
import { KomfAppriseRenderResult, KomfAppriseTemplates, KomfDiscordRenderResult, KomfDiscordTemplates,
  KomfJobEvent, KomfJobEventSubscription, KomfMediaServerConnectionResponse, KomfMediaServerLibrary,
  KomfIdentifyRequest, KomfMetadataJob, KomfMetadataJobResponse, KomfMetadataSeriesSearchResult,
  KomfNotificationContext, KomfPage } from '../../model/KomfModels';
import { BookColorCorrectionSettings } from '../../model/ColorCorrectionModels';
import { SeriesSearchOptions } from '../../network/KomgaClient';

export interface SessionCredentials {
  serverUrl: string;
  email: string;
  password: string;
  allowInsecureHttp: boolean;
}

export interface SessionSnapshot {
  namespace: NamespaceKey;
  user: KomgaUser;
  libraries: KomgaLibrary[];
  online: boolean;
  effectiveServerUrl: string;
  usingLan: boolean;
}

export interface OfflineSessionProfile {
  namespace: string;
  serverId: string;
  user: KomgaUser;
}

export interface SessionRepository {
  restore(): Promise<SessionSnapshot | undefined>;
  signIn(credentials: SessionCredentials, persist?: boolean): Promise<SessionSnapshot>;
  offlineProfiles(): Promise<OfflineSessionProfile[]>;
  signInOffline(namespace: string): Promise<SessionSnapshot>;
  deleteOfflineProfile(namespace: string): Promise<void>;
  signOut(): Promise<void>;
}

export interface CatalogRepository {
  libraries(): Promise<KomgaLibrary[]>;
  cachedHomeFeed(): Promise<HomeFeed | undefined>;
  homeFeed(seed?: HomeFeed, onUpdate?: (value: HomeFeed) => void): Promise<HomeFeed>;
  series(query: string, page: number, size: number,
    options?: SeriesSearchOptions): Promise<PageResult<KomgaSeries>>;
  books(query: string, page: number, size: number): Promise<PageResult<KomgaBook>>;
  seriesDetail(id: string): Promise<KomgaSeries>;
  seriesBooks(id: string): Promise<KomgaBook[]>;
  bookDetail(id: string): Promise<KomgaBook>;
}

export interface OfflineRepository {
  saveBook(record: OfflineBookRecord): Promise<void>;
  savePage(record: OfflinePageRecord): Promise<void>;
  getBook(namespace: string, bookId: string): Promise<OfflineBookRecord | undefined>;
  getPage(namespace: string, bookId: string, pageNumber: number): Promise<OfflinePageRecord | undefined>;
  listBooks(namespace: string): Promise<OfflineBookRecord[]>;
  listPages(namespace: string, bookId: string): Promise<OfflinePageRecord[]>;
  deleteBook(namespace: string, bookId: string): Promise<void>;
}

export interface DownloadScheduler {
  enqueue(namespace: string, bookId: string): Promise<string>;
  start(jobId: string, totalParts: number): Promise<void>;
  update(jobId: string, completedParts: number): Promise<void>;
  fail(jobId: string, message: string): Promise<void>;
  complete(jobId: string): Promise<void>;
  pause(jobId: string): Promise<void>;
  resume(jobId: string): Promise<void>;
  cancel(jobId: string): Promise<void>;
  remove(jobId: string): Promise<void>;
  phase(jobId: string): Promise<TaskPhase | undefined>;
  jobs(namespace: string): Promise<DownloadJobRecord[]>;
}

export interface ProgressOutbox {
  enqueue(record: ProgressOutboxRecord): Promise<void>;
  pending(namespace: string, bookId: string): Promise<ProgressOutboxRecord | undefined>;
  discard(namespace: string, bookId: string): Promise<void>;
  flush(namespace: string): Promise<number>;
}

export interface BookColorCorrectionRepository {
  get(namespace: string, bookId: string): Promise<BookColorCorrectionSettings | undefined>;
  save(namespace: string, bookId: string, value: BookColorCorrectionSettings): Promise<void>;
  delete(namespace: string, bookId: string): Promise<void>;
}

export interface OfflineLogRepository {
  append(namespace: string, level: OfflineLogLevel, message: string): Promise<void>;
  page(namespace: string, level: OfflineLogLevel, page: number, size: number): Promise<OfflineLogPage>;
  deleteAll(namespace: string): Promise<void>;
}

export interface AdminRepository {
  addUser(email: string, password: string, roles: string[]): Promise<KomgaUser>;
  updateUserRoles(userId: string, roles: string[]): Promise<void>;
  updateUserPassword(userId: string, password: string): Promise<void>;
  deleteUser(userId: string): Promise<void>;
  addLibrary(request: KomgaLibraryCreateRequest): Promise<KomgaLibrary>;
  updateLibrary(libraryId: string, request: KomgaLibraryUpdateRequest): Promise<void>;
  deleteLibrary(libraryId: string): Promise<void>;
  scanLibrary(libraryId: string, deep: boolean): Promise<void>;
  analyzeLibrary(libraryId: string): Promise<void>;
  emptyLibraryTrash(libraryId: string): Promise<void>;
  emptyTaskQueue(): Promise<number>;
}

export interface KomfRepository {
  configure(origin: string, allowInsecureHttp?: boolean): void;
  ping(): Promise<boolean>;
  config(): Promise<Object>;
  updateConfig(value: Object): Promise<void>;
  providers(): Promise<string[]>;
  mediaServerStatus(mediaServer?: string): Promise<KomfMediaServerConnectionResponse>;
  mediaServerLibraries(mediaServer?: string): Promise<KomfMediaServerLibrary[]>;
  jobs(page?: number, size?: number, status?: string): Promise<KomfPage<KomfMetadataJob>>;
  job(jobId: string): Promise<KomfMetadataJob>;
  observeJobEvents(jobId: string, onEvent: (event: KomfJobEvent) => void,
    onComplete: () => void, onError: (error: Error) => void): KomfJobEventSubscription;
  deleteAllJobs(): Promise<void>;
  identifySeries(libraryId: string, seriesId: string): Promise<KomfMetadataJobResponse>;
  searchSeries(name: string, libraryId: string, seriesId: string): Promise<KomfMetadataSeriesSearchResult[]>;
  manuallyIdentifySeries(request: KomfIdentifyRequest): Promise<KomfMetadataJobResponse>;
  identifyLibrary(libraryId: string): Promise<KomfMetadataJobResponse>;
  resetSeries(libraryId: string, seriesId: string, removeComicInfo: boolean): Promise<void>;
  resetLibrary(libraryId: string, removeComicInfo: boolean): Promise<void>;
  discordTemplates(): Promise<KomfDiscordTemplates>;
  updateDiscordTemplates(value: KomfDiscordTemplates): Promise<void>;
  renderDiscord(context: KomfNotificationContext, templates: KomfDiscordTemplates): Promise<KomfDiscordRenderResult>;
  sendDiscord(context: KomfNotificationContext, templates: KomfDiscordTemplates): Promise<void>;
  appriseTemplates(): Promise<KomfAppriseTemplates>;
  updateAppriseTemplates(value: KomfAppriseTemplates): Promise<void>;
  renderApprise(context: KomfNotificationContext, templates: KomfAppriseTemplates): Promise<KomfAppriseRenderResult>;
  sendApprise(context: KomfNotificationContext, templates: KomfAppriseTemplates): Promise<void>;
  updateDiscordWebhook(index: number, value?: string): Promise<void>;
  updateDiscordSeriesCover(value: boolean): Promise<void>;
  updateAppriseUrl(index: number, value?: string): Promise<void>;
  updateAppriseSeriesCover(value: boolean): Promise<void>;
}
