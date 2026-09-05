import { image } from '@kit.ImageKit';
import { BookColorCorrectionSettings, ColorLookupTables, createColorLookupTables } from
  '../../model/ColorCorrectionModels';
import { ReaderRequestToken } from './ReaderRequestToken';

interface PixelLayout {
  stride: number;
  redOffset: number;
  greenOffset: number;
  blueOffset: number;
}

function pixelLayout(pixelFormat: number): PixelLayout | undefined {
  if (pixelFormat === image.PixelMapFormat.RGB_888) {
    return { stride: 3, redOffset: 0, greenOffset: 1, blueOffset: 2 };
  }
  if (pixelFormat === image.PixelMapFormat.BGRA_8888) {
    return { stride: 4, redOffset: 2, greenOffset: 1, blueOffset: 0 };
  }
  if (pixelFormat === image.PixelMapFormat.RGBA_8888) {
    return { stride: 4, redOffset: 0, greenOffset: 1, blueOffset: 2 };
  }
  return undefined;
}

function lookup(value: number, table?: Uint8Array): number {
  return table === undefined ? value : table[value];
}

function tablesAreEmpty(tables: ColorLookupTables): boolean {
  return tables.value === undefined && tables.red === undefined && tables.green === undefined &&
    tables.blue === undefined;
}

export function mapImageRows(bytes: Uint8Array, width: number, height: number, bytesPerRow: number,
  layout: PixelLayout, tables: ColorLookupTables): void {
  for (let y = 0; y < height; y += 1) {
    const row = y * bytesPerRow;
    for (let x = 0; x < width; x += 1) {
      const offset = row + x * layout.stride;
      let red = bytes[offset + layout.redOffset];
      let green = bytes[offset + layout.greenOffset];
      let blue = bytes[offset + layout.blueOffset];
      red = lookup(red, tables.value);
      green = lookup(green, tables.value);
      blue = lookup(blue, tables.value);
      bytes[offset + layout.redOffset] = lookup(red, tables.red);
      bytes[offset + layout.greenOffset] = lookup(green, tables.green);
      bytes[offset + layout.blueOffset] = lookup(blue, tables.blue);
    }
  }
}

export class ImageColorProcessor {
  async apply(pixelMap: PixelMap, value: BookColorCorrectionSettings,
    token?: ReaderRequestToken): Promise<boolean> {
    const tables = createColorLookupTables(value);
    if (tablesAreEmpty(tables)) return false;
    try {
      token?.throwIfCancelled();
      const info = pixelMap.getImageInfoSync();
      const layout = pixelLayout(info.pixelFormat);
      if (layout === undefined) return false;
      const buffer = new ArrayBuffer(pixelMap.getPixelBytesNumber());
      await pixelMap.readPixelsToBuffer(buffer);
      token?.throwIfCancelled();
      const bytesPerRow = info.stride > 0 ? info.stride : pixelMap.getBytesNumberPerRow();
      mapImageRows(new Uint8Array(buffer), info.size.width, info.size.height, bytesPerRow, layout, tables);
      token?.throwIfCancelled();
      await pixelMap.writeBufferToPixels(buffer);
      token?.throwIfCancelled();
      return true;
    } catch (_) {
      return false;
    }
  }
}

export const imageColorProcessor = new ImageColorProcessor();
