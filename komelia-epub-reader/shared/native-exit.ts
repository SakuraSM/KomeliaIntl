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
