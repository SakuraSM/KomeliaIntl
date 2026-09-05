import { RetainedAsyncLruCache } from './RetainedAsyncLruCache';
import { PanelDetectionResult } from './MindSporePanelDetector';
import { PagedReadingDirection } from '../../model/ImageReaderModels';

export interface ReaderPageImageCacheKeyOptions {
  sourceRevision?: number;
  scope: number;
  namespace: string;
  bookId: string;
  page: number;
  cropBorders: boolean;
  colorCorrection: boolean;
  colorCorrectionRevision: number;
  aiEnabled: boolean;
}

export function readerPageByteCacheKey(scope: number, namespace: string, bookId: string,
  page: number): string {
  return `${scope}\n${namespace}\n${bookId}\n${page}`;
}

export function readerPageImageCacheKey(options: ReaderPageImageCacheKeyOptions): string {
  return `${readerPageByteCacheKey(options.scope, options.namespace, options.bookId, options.page)}\n` +
    `${options.cropBorders ? 1 : 0}\n` +
    `${options.colorCorrection ? options.colorCorrectionRevision : 0}\n${options.aiEnabled ? 1 : 0}` +
    (options.sourceRevision === undefined ? '' : `\nsource:${options.sourceRevision}`);
}

export function readerPrefetchPageNumbers(pageNumbers: number[], currentIndex: number,
  before: number = 1, after: number = 2): number[] {
  const result: number[] = [];
  for (let offset = 1; offset <= Math.max(before, after); offset += 1) {
    const next = currentIndex + offset;
    if (offset <= after && next >= 0 && next < pageNumbers.length) result.push(pageNumbers[next]);
    const previous = currentIndex - offset;
    if (offset <= before && previous >= 0 && previous < pageNumbers.length) result.push(pageNumbers[previous]);
  }
  return result;
}

export function panelDetectionCacheKey(imageCacheKey: string, direction: PagedReadingDirection): string {
  return `${imageCacheKey}\npanels\n${direction}`;
}

// Keep enough compressed pages for the active spread and its immediate neighbours.
export const readerPageByteCache = new RetainedAsyncLruCache<ArrayBuffer>(12, (): void => {},
  48 * 1024 * 1024, (bytes: ArrayBuffer): number => bytes.byteLength);

// PixelMaps are substantially larger than compressed pages, so retain a tighter working set.
export const readerPageImageCache = new RetainedAsyncLruCache<PixelMap>(6, (pixelMap: PixelMap): void => {
  pixelMap.release();
}, 64 * 1024 * 1024, (pixelMap: PixelMap): number => pixelMap.getPixelBytesNumber());

// Panel rectangles are immutable and tiny compared with their source PixelMaps.
export const panelDetectionCache = new RetainedAsyncLruCache<PanelDetectionResult>(32, (): void => {});
