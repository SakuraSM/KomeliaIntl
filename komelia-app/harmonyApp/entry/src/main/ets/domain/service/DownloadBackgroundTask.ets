import { WantAgent, wantAgent } from '@kit.AbilityKit';
import { backgroundTaskManager } from '@kit.BackgroundTasksKit';
import { AppContainer } from '../../core/di/AppContainer';

const BUNDLE_NAME = 'io.github.zhengningning.komelia.harmony';

export class DownloadBackgroundTask {
  private activeDownloads: number = 0;
  private running: boolean = false;

  async enter(): Promise<void> {
    this.activeDownloads++;
    if (this.running) return;
    const info: wantAgent.WantAgentInfo = {
      wants: [{ bundleName: BUNDLE_NAME, abilityName: 'EntryAbility' }],
      actionType: wantAgent.OperationType.START_ABILITY,
      requestCode: 20260823,
      wantAgentFlags: [wantAgent.WantAgentFlags.UPDATE_PRESENT_FLAG]
    };
    const agent = await wantAgent.getWantAgent(info) as WantAgent;
    await backgroundTaskManager.startBackgroundRunning(
      AppContainer.requireContext(), backgroundTaskManager.BackgroundMode.DATA_TRANSFER, agent);
    this.running = true;
  }

  async leave(): Promise<void> {
    this.activeDownloads = Math.max(0, this.activeDownloads - 1);
    if (!this.running || this.activeDownloads > 0) return;
    await backgroundTaskManager.stopBackgroundRunning(AppContainer.requireContext());
    this.running = false;
  }
}

export const downloadBackgroundTask = new DownloadBackgroundTask();
