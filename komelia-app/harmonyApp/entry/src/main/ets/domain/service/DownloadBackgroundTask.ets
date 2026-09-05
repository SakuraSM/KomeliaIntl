import { WantAgent, wantAgent } from '@kit.AbilityKit';
import { backgroundTaskManager } from '@kit.BackgroundTasksKit';
import { AppContainer } from '../../core/di/AppContainer';
import { BackgroundTaskLeaseCoordinator } from './BackgroundTaskLeaseCoordinator';

const BUNDLE_NAME = 'io.github.sakurasm.komeliaintl';

export class DownloadBackgroundTask {
  private readonly coordinator: BackgroundTaskLeaseCoordinator = new BackgroundTaskLeaseCoordinator(
    (): Promise<void> => this.start(),
    (): Promise<void> => this.stop()
  );

  async enter(): Promise<void> {
    await this.coordinator.enter();
  }

  async leave(): Promise<void> {
    await this.coordinator.leave();
  }

  private async start(): Promise<void> {
    const info: wantAgent.WantAgentInfo = {
      wants: [{ bundleName: BUNDLE_NAME, abilityName: 'EntryAbility' }],
      actionType: wantAgent.OperationType.START_ABILITY,
      requestCode: 20260823,
      wantAgentFlags: [wantAgent.WantAgentFlags.UPDATE_PRESENT_FLAG]
    };
    const agent = await wantAgent.getWantAgent(info) as WantAgent;
    await backgroundTaskManager.startBackgroundRunning(
      AppContainer.requireContext(), backgroundTaskManager.BackgroundMode.DATA_TRANSFER, agent);
  }

  private async stop(): Promise<void> {
    await backgroundTaskManager.stopBackgroundRunning(AppContainer.requireContext());
  }
}

export const downloadBackgroundTask = new DownloadBackgroundTask();
