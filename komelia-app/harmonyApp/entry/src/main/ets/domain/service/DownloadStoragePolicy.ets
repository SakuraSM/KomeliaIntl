const DOWNLOAD_OVERHEAD_MULTIPLIER: number = 1.25;
const DOWNLOAD_RESERVE_BYTES: number = 64 * 1024 * 1024;

export function requiredDownloadBytes(expectedSizeBytes: number): number {
  const expected = Number.isFinite(expectedSizeBytes) && expectedSizeBytes > 0 ? expectedSizeBytes : 0;
  return Math.ceil(expected * DOWNLOAD_OVERHEAD_MULTIPLIER) + DOWNLOAD_RESERVE_BYTES;
}

export function hasDownloadSpace(freeSizeBytes: number, expectedSizeBytes: number): boolean {
  if (!Number.isFinite(freeSizeBytes) || freeSizeBytes < 0) return true;
  return freeSizeBytes >= requiredDownloadBytes(expectedSizeBytes);
}

export function bytesToWholeMiB(sizeBytes: number): number {
  return Math.max(0, Math.ceil(sizeBytes / (1024 * 1024)));
}
