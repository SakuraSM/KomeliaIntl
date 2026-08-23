import { HomeFeed, KomgaBook, KomgaLibrary, KomgaSeries, KomgaUser, PageResult } from '../../model/KomgaModels';
import { DownloadJobRecord, NamespaceKey, OfflineBookRecord, OfflinePageRecord,
  ProgressOutboxRecord } from '../../data/local/DatabaseModels';
import { TaskPhase } from '../../core/model/UiState';

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
  scanLibrary(libraryId: string, deep: boolean): Promise<void>;
  analyzeLibrary(libraryId: string): Promise<void>;
  emptyLibraryTrash(libraryId: string): Promise<void>;
}

export interface KomfRepository {
  configure(origin: string): void;
  ping(): Promise<boolean>;
  identifySeries(libraryId: string, seriesId: string): Promise<void>;
  resetSeries(libraryId: string, seriesId: string, removeComicInfo: boolean): Promise<void>;
}
