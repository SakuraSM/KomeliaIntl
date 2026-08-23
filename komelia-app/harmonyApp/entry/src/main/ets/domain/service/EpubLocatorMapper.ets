import { ReadiumLocator } from '../../model/KomgaModels';

export interface EpubReaderPosition {
  resourceIndex: number;
  domPos: string;
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
    const rawPosition = locator.locations['position'];
    const rawFragment = locator.locations['fragment'];
    const resourceIndex = typeof rawPosition === 'number' ? rawPosition : Number(rawPosition || 0);
    return {
      resourceIndex: Number.isFinite(resourceIndex) ? Math.max(0, resourceIndex) : 0,
      domPos: typeof rawFragment === 'string' ? rawFragment : ''
    };
  }
}

export const epubLocatorMapper = new EpubLocatorMapper();
