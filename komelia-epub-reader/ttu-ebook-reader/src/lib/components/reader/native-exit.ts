export function nativeExitErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message) return error.message;
  if (typeof error === 'string' && error) return error;
  if (typeof error === 'object' && error !== null && 'result' in error &&
      typeof error.result === 'string' && error.result) return error.result;
  return fallback;
}
