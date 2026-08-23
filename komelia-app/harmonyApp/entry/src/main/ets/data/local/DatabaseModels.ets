import { TaskPhase } from '../../core/model/UiState';

export interface NamespaceKey {
  serverId: string;
  userId: string;
}

export function namespaceOf(key: NamespaceKey): string {
  return `${key.serverId}:${key.userId}`;
}

export enum OfflineMediaKind {
  PAGE_CACHE = 'PAGE_CACHE',
  EPUB_FILE = 'EPUB_FILE'
}

export interface OfflineBookRecord {
  namespace: string;
  bookId: string;
  mediaKind: OfflineMediaKind;
  title: string;
  localPath: string;
  pageCount: number;
  downloadedPages: number;
  sizeBytes: number;
  updatedAt: number;
}

export interface OfflinePageRecord {
  namespace: string;
  bookId: string;
  pageNumber: number;
  localPath: string;
  mediaType: string;
  sizeBytes: number;
  completed: boolean;
}

export interface DownloadJobRecord {
  namespace: string;
  jobId: string;
  bookId: string;
  phase: TaskPhase;
  completedParts: number;
  totalParts: number;
  attempts: number;
  errorMessage: string;
  updatedAt: number;
}

export interface ProgressOutboxRecord {
  namespace: string;
  bookId: string;
  page: number;
  completed: boolean;
  readDate: string;
  attempts: number;
}
