import { ReadiumLocator } from '../../model/KomgaModels';

export interface EpubReaderPosition {
  resourceIndex: number;
  domPos: string;
}

export interface EpubProgressConflict {
  hasLocalPosition: boolean;
  localReadDate: string;
  hasPendingUpload: boolean;
  hasServerPosition: boolean;
  serverReadDate: string;
}

/**
 * Local unsynchronized progress always wins. Otherwise the most recently read
 * position wins, allowing another device to advance the server position.
 */
export function shouldUseLocalEpubPosition(conflict: EpubProgressConflict): boolean {
  if (!conflict.hasLocalPosition) return false;
  if (conflict.hasPendingUpload) return true;
  if (!conflict.hasServerPosition) return true;
  const localTime = Date.parse(conflict.localReadDate);
  const serverTime = Date.parse(conflict.serverReadDate);
  if (!Number.isFinite(localTime)) return false;
  if (!Number.isFinite(serverTime)) return true;
  return localTime >= serverTime;
}

export class EpubLocatorMapper {
  toLocator(resourceIndex: number, domPos: string, href: string = '', title: string = ''): ReadiumLocator {
    return {
      href: href,
      type: 'application/xhtml+xml',
      title: title,
      locations: {
        position: resourceIndex,
        fragment: domPos
      }
    };
  }

  fromLocator(locator: ReadiumLocator | undefined): EpubReaderPosition | undefined {
    if (locator === undefined || locator.locations === undefined) return undefined;
    const rawPosition = locator.locations.position;
    const rawFragment = locator.locations.fragment;
    const resourceIndex = typeof rawPosition === 'number' ? rawPosition : Number(rawPosition || 0);
    return {
      resourceIndex: Number.isFinite(resourceIndex) ? Math.max(0, resourceIndex) : 0,
      domPos: typeof rawFragment === 'string' ? rawFragment : ''
    };
  }
}

export const epubLocatorMapper = new EpubLocatorMapper();
