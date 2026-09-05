export type BackgroundTaskTransition = () => Promise<void>;

/**
 * Serializes continuous-task transitions while allowing multiple downloads to
 * share a single system background task.
 */
export class BackgroundTaskLeaseCoordinator {
  private activeLeases: number = 0;
  private running: boolean = false;
  private transition: Promise<void> = Promise.resolve();
  private readonly startTransition: BackgroundTaskTransition;
  private readonly stopTransition: BackgroundTaskTransition;

  constructor(startTransition: BackgroundTaskTransition, stopTransition: BackgroundTaskTransition) {
    this.startTransition = startTransition;
    this.stopTransition = stopTransition;
  }

  enter(): Promise<void> {
    this.activeLeases++;
    return this.enqueue(async (): Promise<void> => {
      if (this.running || this.activeLeases === 0) return;
      await this.startTransition();
      this.running = true;
    });
  }

  leave(): Promise<void> {
    this.activeLeases = Math.max(0, this.activeLeases - 1);
    return this.enqueue(async (): Promise<void> => {
      if (!this.running || this.activeLeases > 0) return;
      try {
        await this.stopTransition();
      } finally {
        // A failed stop must not leave the coordinator permanently believing
        // the task is active; the next lease can request a fresh task.
        this.running = false;
      }
    });
  }

  activeCount(): number {
    return this.activeLeases;
  }

  isRunning(): boolean {
    return this.running;
  }

  private enqueue(operation: BackgroundTaskTransition): Promise<void> {
    const next = this.transition.then(operation, operation);
    this.transition = next.then((): void => {}, (): void => {});
    return next;
  }
}
