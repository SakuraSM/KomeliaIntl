export class ReaderRequestToken {
  private cancelledState: boolean = false;

  cancel(): void {
    this.cancelledState = true;
  }

  isCancelled(): boolean {
    return this.cancelledState;
  }

  throwIfCancelled(): void {
    if (this.cancelledState) {
      throw new Error('READER_REQUEST_CANCELLED');
    }
  }
}

export class SerialTaskGate {
  private busy: boolean = false;
  private waiters: Array<() => void> = [];

  async enter(): Promise<void> {
    if (!this.busy) {
      this.busy = true;
      return;
    }
    await new Promise<void>((resolve: () => void) => {
      this.waiters.push(resolve);
    });
  }

  leave(): void {
    const next = this.waiters.shift();
    if (next !== undefined) {
      next();
      return;
    }
    this.busy = false;
  }
}

export class ConcurrentTaskGate {
  private readonly limit: number;
  private active: number = 0;
  private waiters: Array<() => void> = [];

  constructor(limit: number) {
    this.limit = Math.max(1, limit);
  }

  async enter(): Promise<void> {
    if (this.active < this.limit) {
      this.active++;
      return;
    }
    await new Promise<void>((resolve: () => void) => {
      this.waiters.push(resolve);
    });
  }

  leave(): void {
    const next = this.waiters.shift();
    if (next !== undefined) {
      next();
      return;
    }
    this.active = Math.max(0, this.active - 1);
  }
}
