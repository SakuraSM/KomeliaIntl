import { HomeFeed, KomgaBook, KomgaLibrary, KomgaSeries, KomgaUser, PageResult } from '../../model/KomgaModels';
import { DownloadJobRecord, NamespaceKey, OfflineBookRecord, OfflinePageRecord,
  ProgressOutboxRecord } from '../../data/local/DatabaseModels';
import { TaskPhase } from '../../core/model/UiState';
import { KomfMediaServerConnectionResponse, KomfMediaServerLibrary, KomfMetadataJob,
  KomfMetadataJobResponse, KomfPage } from '../../model/KomfModels';

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
}

export interface SessionRepository {
  restore(): Promise<SessionSnapshot | undefined>;
  signIn(credentials: SessionCredentials, persist?: boolean): Promise<SessionSnapshot>;
  signOut(): Promise<void>;
}

export interface CatalogRepository {
  libraries(): Promise<KomgaLibrary[]>;
  homeFeed(): Promise<HomeFeed>;
  series(query: string, page: number, size: number): Promise<PageResult<KomgaSeries>>;
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
  phase(jobId: string): Promise<TaskPhase | undefined>;
  jobs(namespace: string): Promise<DownloadJobRecord[]>;
}

export interface ProgressOutbox {
  enqueue(record: ProgressOutboxRecord): Promise<void>;
  flush(namespace: string): Promise<number>;
}

export interface AdminRepository {
  addUser(email: string, password: string, roles: string[]): Promise<KomgaUser>;
  updateUserRoles(userId: string, roles: string[]): Promise<void>;
  updateUserPassword(userId: string, password: string): Promise<void>;
  deleteUser(userId: string): Promise<void>;
  addLibrary(name: string, root: string): Promise<KomgaLibrary>;
  updateLibrary(libraryId: string, name: string, root: string): Promise<void>;
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
  mediaServerStatus(): Promise<KomfMediaServerConnectionResponse>;
  mediaServerLibraries(): Promise<KomfMediaServerLibrary[]>;
  jobs(page?: number, size?: number, status?: string): Promise<KomfPage<KomfMetadataJob>>;
  deleteAllJobs(): Promise<void>;
  identifySeries(libraryId: string, seriesId: string): Promise<KomfMetadataJobResponse>;
  resetSeries(libraryId: string, seriesId: string, removeComicInfo: boolean): Promise<void>;
  discordTemplates(): Promise<Object>;
  updateDiscordTemplates(value: Object): Promise<void>;
  appriseTemplates(): Promise<Object>;
  updateAppriseTemplates(value: Object): Promise<void>;
}
