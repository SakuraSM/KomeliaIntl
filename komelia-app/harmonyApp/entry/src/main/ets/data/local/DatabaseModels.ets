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
  title?: string;
  completedBytes?: number;
  totalBytes?: number;
  bytesPerSecond?: number;
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
  operation: ProgressOutboxOperation;
  page: number;
  completed: boolean;
  readDate: string;
  attempts: number;
  locatorJson?: string;
}

export enum ProgressOutboxOperation {
  UPDATE = 'UPDATE',
  DELETE = 'DELETE'
}

export function progressOutboxOperation(value: string): ProgressOutboxOperation {
  return value === ProgressOutboxOperation.DELETE ? ProgressOutboxOperation.DELETE : ProgressOutboxOperation.UPDATE;
}

export enum OfflineLogLevel {
  INFO = 'INFO',
  ERROR = 'ERROR'
}

export interface OfflineLogRecord {
  id: number;
  namespace: string;
  level: OfflineLogLevel;
  message: string;
  timestamp: number;
}

export interface OfflineLogPage {
  content: OfflineLogRecord[];
  page: number;
  totalPages: number;
  totalElements: number;
}
