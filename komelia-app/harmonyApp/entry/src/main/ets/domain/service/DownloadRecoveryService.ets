import { TaskPhase } from '../../core/model/UiState';
import { DownloadJobRecord } from '../../data/local/DatabaseModels';
import { downloadScheduler } from '../../data/repository/DefaultRepositories';
import { offlineDownloadService } from './OfflineDownloadService';

export class DownloadRecoveryService {
  private runningJobs: Set<string> = new Set<string>();

  recover(namespace: string): void {
    this.recoverPending(namespace).catch(() => {});
  }

  private async recoverPending(namespace: string): Promise<void> {
    const jobs = await downloadScheduler.jobs(namespace);
    for (let index = 0; index < jobs.length; index++) {
      const job = jobs[index];
      if (job.phase === TaskPhase.QUEUED || job.phase === TaskPhase.RUNNING ||
        job.phase === TaskPhase.RETRYING) {
        await this.resume(namespace, job);
      }
    }
  }

  private async resume(namespace: string, job: DownloadJobRecord): Promise<void> {
    if (this.runningJobs.has(job.jobId)) return;
    this.runningJobs.add(job.jobId);
    try {
      await offlineDownloadService.resume(namespace, job.bookId, job.jobId);
    } catch (_) {
      // OfflineDownloadService persists the paused, cancelled, or failed phase.
    } finally {
      this.runningJobs.delete(job.jobId);
    }
  }
}

export const downloadRecoveryService = new DownloadRecoveryService();
