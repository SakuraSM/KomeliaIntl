export type AsyncValueLoader<T> = () => Promise<T>;
export type ValueDisposer<T> = (value: T) => void;
export type ValueWeigher<T> = (value: T) => number;

interface RetainedCacheEntry<T> {
  value: T;
  retainCount: number;
  lastAccess: number;
  weight: number;
}

interface PendingCacheEntry<T> {
  promise: Promise<T>;
  retainCount: number;
}

export class RetainedAsyncLruCache<T> {
  private readonly capacity: number;
  private readonly disposeValue: ValueDisposer<T>;
  private readonly maximumWeight: number;
  private readonly weighValue?: ValueWeigher<T>;
  private readonly entries: Map<string, RetainedCacheEntry<T>> = new Map<string, RetainedCacheEntry<T>>();
  private readonly pending: Map<string, PendingCacheEntry<T>> = new Map<string, PendingCacheEntry<T>>();
  private accessSequence: number = 0;
  private retainedWeight: number = 0;

  constructor(capacity: number, disposeValue: ValueDisposer<T>, maximumWeight: number = Number.MAX_VALUE,
    weighValue?: ValueWeigher<T>) {
    this.capacity = Math.max(1, capacity);
    this.disposeValue = disposeValue;
    this.maximumWeight = Math.max(1, maximumWeight);
    this.weighValue = weighValue;
  }

  size(): number {
    return this.entries.size;
  }

  weight(): number {
    return this.retainedWeight;
  }

  acquireCached(key: string): T | undefined {
    const entry = this.entries.get(key);
    if (entry === undefined) return undefined;
    entry.retainCount += 1;
    entry.lastAccess = this.nextAccess();
    return entry.value;
  }

  async acquire(key: string, loader: AsyncValueLoader<T>): Promise<T> {
    const cached = this.acquireCached(key);
    if (cached !== undefined) return cached;

    const existingRequest = this.pending.get(key);
    if (existingRequest !== undefined) {
      existingRequest.retainCount += 1;
      return existingRequest.promise;
    }

    const request = this.loadAndStore(key, loader);
    this.pending.set(key, { promise: request, retainCount: 1 });
    return request;
  }

  release(key: string, value: T): void {
    const entry = this.entries.get(key);
    if (entry === undefined || entry.value !== value) return;
    entry.retainCount = Math.max(0, entry.retainCount - 1);
    entry.lastAccess = this.nextAccess();
    this.trim();
  }

  private async loadAndStore(key: string, loader: AsyncValueLoader<T>): Promise<T> {
    try {
      const value = await loader();
      const request = this.pending.get(key);
      if (request === undefined) {
        this.disposeValue(value);
        throw new Error('Cache request was invalidated');
      }
      const weight = this.valueWeight(value);
      this.entries.set(key, {
        value: value,
        retainCount: request.retainCount,
        lastAccess: this.nextAccess(),
        weight: weight
      });
      this.retainedWeight += weight;
      this.trim();
      return value;
    } finally {
      this.pending.delete(key);
    }
  }

  private nextAccess(): number {
    this.accessSequence += 1;
    return this.accessSequence;
  }

  private valueWeight(value: T): number {
    if (this.weighValue === undefined) return 1;
    return Math.max(0, Math.round(this.weighValue(value)));
  }

  private trim(): void {
    while (this.entries.size > this.capacity || this.retainedWeight > this.maximumWeight) {
      let candidateKey: string | undefined = undefined;
      let candidateAccess = Number.MAX_VALUE;
      this.entries.forEach((entry: RetainedCacheEntry<T>, key: string): void => {
        if (entry.retainCount === 0 && entry.lastAccess < candidateAccess) {
          candidateKey = key;
          candidateAccess = entry.lastAccess;
        }
      });
      if (candidateKey === undefined) return;
      const candidate = this.entries.get(candidateKey);
      this.entries.delete(candidateKey);
      if (candidate !== undefined) {
        this.retainedWeight = Math.max(0, this.retainedWeight - candidate.weight);
        this.disposeValue(candidate.value);
      }
    }
  }
}

export class RecentFailureBackoff {
  private readonly cooldownMillis: number;
  private readonly capacity: number;
  private readonly failures: Map<string, number> = new Map<string, number>();

  constructor(cooldownMillis: number, capacity: number) {
    this.cooldownMillis = Math.max(0, cooldownMillis);
    this.capacity = Math.max(1, capacity);
  }

  shouldRetry(key: string, now: number = Date.now()): boolean {
    const failedAt = this.failures.get(key);
    if (failedAt === undefined) return true;
    if (now - failedAt >= this.cooldownMillis) {
      this.failures.delete(key);
      return true;
    }
    return false;
  }

  markFailure(key: string, now: number = Date.now()): void {
    if (!this.failures.has(key) && this.failures.size >= this.capacity) {
      const oldestKey = this.oldestKey();
      if (oldestKey !== undefined) this.failures.delete(oldestKey);
    }
    this.failures.set(key, now);
  }

  clear(key: string): void {
    this.failures.delete(key);
  }

  private oldestKey(): string | undefined {
    let result: string | undefined = undefined;
    let oldest = Number.MAX_VALUE;
    this.failures.forEach((failedAt: number, key: string): void => {
      if (failedAt < oldest) {
        oldest = failedAt;
        result = key;
      }
    });
    return result;
  }
}
