import { TaskPhase } from '../../core/model/UiState';
import { OfflineBookRecord } from '../../data/local/DatabaseModels';

export interface DownloadRuntimeEvent {
  title?: string;
  completedBytes?: number;
  totalBytes?: number;
  bytesPerSecond?: number;
  namespace?: string;
  jobId: string;
  bookId?: string;
  phase?: TaskPhase;
  completedParts?: number;
  totalParts?: number;
  errorMessage?: string;
  record?: OfflineBookRecord;
  removed?: boolean;
}

export type DownloadRuntimeListener = (event: DownloadRuntimeEvent) => void;

class DownloadRuntimeEventHub {
  private listeners: DownloadRuntimeListener[] = [];

  subscribe(listener: DownloadRuntimeListener): void {
    if (!this.listeners.includes(listener)) this.listeners.push(listener);
  }

  unsubscribe(listener: DownloadRuntimeListener): void {
    this.listeners = this.listeners.filter((candidate: DownloadRuntimeListener) => candidate !== listener);
  }

  emit(event: DownloadRuntimeEvent): void {
    const snapshot = this.listeners.slice();
    for (let index = 0; index < snapshot.length; index++) snapshot[index](event);
  }
}

export const downloadRuntimeEvents = new DownloadRuntimeEventHub();
