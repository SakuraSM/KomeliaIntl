interface NativeExitHandlers {
  waitForRequest: () => Promise<boolean>;
  leave: () => Promise<void>;
  onError: (error: unknown) => void;
}

export function watchNativeExit({waitForRequest, leave, onError}: NativeExitHandlers): () => void {
  let isActive = true;
  const listen = async (): Promise<void> => {
    while (isActive) {
      const shouldLeave = await waitForRequest();
      if (!isActive || !shouldLeave) return;
      await leave();
    }
  };
  void listen().catch(error => { if (isActive) onError(error); });
  return () => { isActive = false; };
}

export function nativeExitErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message) return error.message;
  if (typeof error === 'string' && error) return error;
  if (typeof error === 'object' && error !== null && 'result' in error &&
      typeof error.result === 'string' && error.result) return error.result;
  return fallback;
}
