import { image } from '@kit.ImageKit';
import { ReaderRequestToken } from './ReaderRequestToken';

export interface ImageCropRegion {
  x: number;
  y: number;
  width: number;
  height: number;
}

interface BorderColor {
  red: number;
  green: number;
  blue: number;
  alpha: number;
}

function bytesPerPixel(pixelFormat: number): number {
  if (pixelFormat === image.PixelMapFormat.RGB_565) return 2;
  if (pixelFormat === image.PixelMapFormat.RGB_888) return 3;
  if (pixelFormat === image.PixelMapFormat.RGBA_8888 ||
    pixelFormat === image.PixelMapFormat.BGRA_8888) return 4;
  return 0;
}

function colorAt(bytes: Uint8Array, offset: number, pixelFormat: number): BorderColor {
  if (pixelFormat === image.PixelMapFormat.RGB_565) {
    const packed = bytes[offset] | (bytes[offset + 1] << 8);
    return {
      red: Math.round(((packed >> 11) & 0x1F) * 255 / 31),
      green: Math.round(((packed >> 5) & 0x3F) * 255 / 63),
      blue: Math.round((packed & 0x1F) * 255 / 31),
      alpha: 255
    };
  }
  if (pixelFormat === image.PixelMapFormat.RGB_888) {
    return { red: bytes[offset], green: bytes[offset + 1], blue: bytes[offset + 2], alpha: 255 };
  }
  if (pixelFormat === image.PixelMapFormat.BGRA_8888) {
    return {
      red: bytes[offset + 2], green: bytes[offset + 1], blue: bytes[offset], alpha: bytes[offset + 3]
    };
  }
  return {
    red: bytes[offset], green: bytes[offset + 1], blue: bytes[offset + 2], alpha: bytes[offset + 3]
  };
}

function colorDistance(first: BorderColor, second: BorderColor): number {
  if (first.alpha <= 16 && second.alpha <= 16) return 0;
  return Math.max(
    Math.abs(first.red - second.red),
    Math.abs(first.green - second.green),
    Math.abs(first.blue - second.blue),
    Math.abs(first.alpha - second.alpha)
  );
}

function dominantCornerColor(bytes: Uint8Array, width: number, height: number, stride: number,
  pixelFormat: number, channels: number, threshold: number): BorderColor {
  const offsets = [
    0,
    Math.max(0, width - 1) * channels,
    Math.max(0, height - 1) * stride,
    Math.max(0, height - 1) * stride + Math.max(0, width - 1) * channels
  ];
  const corners = offsets.map((offset: number) => colorAt(bytes, offset, pixelFormat));
  let selected = 0;
  let selectedMatches = -1;
  for (let candidate = 0; candidate < corners.length; candidate += 1) {
    let matches = 0;
    for (let other = 0; other < corners.length; other += 1) {
      if (colorDistance(corners[candidate], corners[other]) <= threshold) matches += 1;
    }
    if (matches > selectedMatches) {
      selected = candidate;
      selectedMatches = matches;
    }
  }
  return corners[selected];
}

export function findImageContentRegion(bytes: Uint8Array, width: number, height: number, stride: number,
  pixelFormat: number, threshold: number = 50): ImageCropRegion | undefined {
  const channels = bytesPerPixel(pixelFormat);
  if (channels === 0 || width < 3 || height < 3 || stride < width * channels ||
    bytes.length < stride * height) return undefined;

  const background = dominantCornerColor(bytes, width, height, stride, pixelFormat, channels, threshold);
  let left = width;
  let top = height;
  let right = -1;
  let bottom = -1;
  for (let y = 0; y < height; y += 1) {
    const rowOffset = y * stride;
    for (let x = 0; x < width; x += 1) {
      const current = colorAt(bytes, rowOffset + x * channels, pixelFormat);
      if (colorDistance(current, background) <= threshold) continue;
      left = Math.min(left, x);
      top = Math.min(top, y);
      right = Math.max(right, x);
      bottom = Math.max(bottom, y);
    }
  }
  if (right < left || bottom < top) return undefined;

  const safetyMargin = 2;
  left = Math.max(0, left - safetyMargin);
  top = Math.max(0, top - safetyMargin);
  right = Math.min(width - 1, right + safetyMargin);
  bottom = Math.min(height - 1, bottom + safetyMargin);
  const croppedWidth = right - left + 1;
  const croppedHeight = bottom - top + 1;
  const removedPixels = left + top + (width - 1 - right) + (height - 1 - bottom);
  const minimumWidth = Math.min(16, Math.max(3, Math.round(width * 0.12)));
  const minimumHeight = Math.min(16, Math.max(3, Math.round(height * 0.12)));
  if (removedPixels < 4 || croppedWidth < minimumWidth || croppedHeight < minimumHeight ||
    croppedWidth * croppedHeight < width * height * 0.15) return undefined;
  return { x: left, y: top, width: croppedWidth, height: croppedHeight };
}

export class ImageBorderCropper {
  async crop(pixelMap: PixelMap, token?: ReaderRequestToken): Promise<boolean> {
    try {
      token?.throwIfCancelled();
      const info = pixelMap.getImageInfoSync();
      const buffer = new ArrayBuffer(pixelMap.getPixelBytesNumber());
      await pixelMap.readPixelsToBuffer(buffer);
      token?.throwIfCancelled();
      const stride = info.stride > 0 ? info.stride : pixelMap.getBytesNumberPerRow();
      const region = findImageContentRegion(new Uint8Array(buffer), info.size.width, info.size.height,
        stride, info.pixelFormat);
      if (region === undefined) return false;
      await pixelMap.crop({ x: region.x, y: region.y, size: { width: region.width, height: region.height } });
      token?.throwIfCancelled();
      return true;
    } catch (_) {
      return false;
    }
  }
}

export const imageBorderCropper = new ImageBorderCropper();
