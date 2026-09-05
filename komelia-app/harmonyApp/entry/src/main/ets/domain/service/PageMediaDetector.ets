import { fileIo as fs } from '@kit.CoreFileKit';

export function isPdfMediaType(mediaType: string): boolean {
  return mediaType.toLowerCase().includes('pdf');
}

export function isPdfBytes(bytes: ArrayBuffer): boolean {
  if (bytes.byteLength < 4) return false;
  const header = new Uint8Array(bytes, 0, 4);
  return header[0] === 0x25 && header[1] === 0x50 && header[2] === 0x44 && header[3] === 0x46;
}

export function isPdfFile(path: string): boolean {
  if (path.length === 0 || !fs.accessSync(path)) return false;
  let file: fs.File | undefined = undefined;
  try {
    file = fs.openSync(path, fs.OpenMode.READ_ONLY);
    const header = new ArrayBuffer(4);
    const read = fs.readSync(file.fd, header);
    return read >= 4 && isPdfBytes(header);
  } catch (_) {
    return false;
  } finally {
    if (file !== undefined) fs.closeSync(file);
  }
}
