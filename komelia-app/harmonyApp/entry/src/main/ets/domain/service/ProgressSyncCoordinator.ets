export class ProgressSyncCoordinator {
  private tails: Map<string, Promise<void>> = new Map<string, Promise<void>>();

  run(key: string, operation: () => Promise<void>): Promise<void> {
    const previous = this.tails.get(key) || Promise.resolve();
    const result = previous.then(operation, operation);
    const settled = result.catch((): void => {});
    this.tails.set(key, settled);
    settled.then((): void => {
      if (this.tails.get(key) === settled) this.tails.delete(key);
    });
    return result;
  }
}

export function progressSyncKey(namespace: string, bookId: string): string {
  return `${namespace}\n${bookId}`;
}

export const progressSyncCoordinator = new ProgressSyncCoordinator();
