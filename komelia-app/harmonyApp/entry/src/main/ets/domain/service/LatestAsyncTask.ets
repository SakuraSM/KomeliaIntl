export class LatestAsyncTask<T> {
  private pending: T | undefined = undefined;
  private running: boolean = false;
  private waiters: Array<() => void> = [];
  private worker: (value: T) => Promise<void>;

  constructor(worker: (value: T) => Promise<void>) {
    this.worker = worker;
  }

  submit(value: T): void {
    this.pending = value;
    if (this.running) return;
    this.running = true;
    this.drain();
  }

  isIdle(): boolean {
    return !this.running && this.pending === undefined;
  }

  flush(): Promise<void> {
    if (this.isIdle()) return Promise.resolve();
    return new Promise<void>((resolve: () => void) => this.waiters.push(resolve));
  }

  private async drain(): Promise<void> {
    while (this.pending !== undefined) {
      const next = this.pending;
      this.pending = undefined;
      try {
        await this.worker(next);
      } catch (_) {
      }
    }
    this.running = false;
    const completed = this.waiters;
    this.waiters = [];
    completed.forEach((resolve: () => void) => resolve());
  }
}
