import { TaskPhase } from '../core/model/UiState';

export interface DownloadByteProgress {
  title: string;
  completedBytes: number;
  totalBytes: number;
  bytesPerSecond: number;
}

export function acceptsDownloadProgress(phase: TaskPhase | undefined): boolean {
  return phase === TaskPhase.RUNNING;
}

export function downloadSpeed(previousBytes: number, bytes: number, elapsedMs: number): number {
  return elapsedMs <= 0 ? 0 : Math.max(0, Math.round((bytes - previousBytes) * 1000 / elapsedMs));
}

export const DOWNLOAD_PROGRESS_SCHEMA: string[] = [
  "ALTER TABLE DOWNLOAD_JOB ADD COLUMN TITLE TEXT NOT NULL DEFAULT ''",
  'ALTER TABLE DOWNLOAD_JOB ADD COLUMN COMPLETED_BYTES INTEGER NOT NULL DEFAULT 0',
  'ALTER TABLE DOWNLOAD_JOB ADD COLUMN TOTAL_BYTES INTEGER NOT NULL DEFAULT 0',
  'ALTER TABLE DOWNLOAD_JOB ADD COLUMN BYTES_PER_SECOND INTEGER NOT NULL DEFAULT 0'
];
