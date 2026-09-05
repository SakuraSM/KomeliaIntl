import { localImageFile, naturalNameCompare } from '../../model/LocalContentModels';

export const LOCAL_ARCHIVE_ENTRY_LIMIT = 32 * 1024 * 1024;
export const LOCAL_ARCHIVE_DIRECTORY_LIMIT = 8 * 1024 * 1024;
export interface ZipDirectoryLocation { offset: number; size: number; count: number; }
export interface LocalZipEntry {
  name: string; method: number; compressedSize: number; size: number; offset: number; crc: number;
}

export function safeArchivePath(name: string): boolean {
  return name.length > 0 && !name.startsWith('/') && !/^[a-z]:/i.test(name) &&
    !name.includes('\\') && !name.includes('\u0000') &&
    !name.split('/').some((part: string): boolean => part === '..' || part === '.');
}

export function zipDirectoryLocation(tail: ArrayBuffer, fileSize: number): ZipDirectoryLocation {
  const view = new DataView(tail);
  for (let offset = tail.byteLength - 22; offset >= 0; offset--) {
    if (view.getUint32(offset, true) !== 0x06054b50) continue;
    if (offset + 22 + view.getUint16(offset + 20, true) !== tail.byteLength) continue;
    const count = view.getUint16(offset + 10, true);
    const size = view.getUint32(offset + 12, true);
    const start = view.getUint32(offset + 16, true);
    if (view.getUint16(offset + 4, true) !== 0 || view.getUint16(offset + 6, true) !== 0 ||
      view.getUint16(offset + 8, true) !== count || count === 0xffff || count > 20000 ||
      size > LOCAL_ARCHIVE_DIRECTORY_LIMIT || start === 0xffffffff ||
      start + size > fileSize - tail.byteLength + offset) throw new Error('LOCAL_UNSUPPORTED_ZIP');
    return { offset: start, size: size, count: count };
  }
  throw new Error('LOCAL_INVALID_ZIP');
}

function decodeZipName(bytes: Uint8Array, utf8: boolean): string {
  let value = '';
  for (let index = 0; index < bytes.length; index++) {
    if (!utf8 && bytes[index] >= 128) throw new Error('LOCAL_UNSUPPORTED_ZIP_NAME_ENCODING');
    value += '%' + bytes[index].toString(16).padStart(2, '0');
  }
  return decodeURIComponent(value);
}

export function parseZipDirectory(bytes: ArrayBuffer, count: number, directoryOffset: number): LocalZipEntry[] {
  const view = new DataView(bytes);
  const entries: LocalZipEntry[] = [];
  const names = new Set<string>();
  let offset = 0;
  for (let index = 0; index < count; index++) {
    if (offset + 46 > bytes.byteLength || view.getUint32(offset, true) !== 0x02014b50) {
      throw new Error('LOCAL_INVALID_ZIP');
    }
    const flags = view.getUint16(offset + 8, true);
    const nameLength = view.getUint16(offset + 28, true);
    const recordSize = 46 + nameLength + view.getUint16(offset + 30, true) + view.getUint16(offset + 32, true);
    if (offset + recordSize > bytes.byteLength) throw new Error('LOCAL_INVALID_ZIP');
    const name = decodeZipName(new Uint8Array(bytes, offset + 46, nameLength), (flags & 0x800) !== 0);
    const method = view.getUint16(offset + 10, true);
    const compressedSize = view.getUint32(offset + 20, true);
    const size = view.getUint32(offset + 24, true);
    const start = view.getUint32(offset + 42, true);
    const mode = view.getUint32(offset + 38, true) >>> 16;
    if (!safeArchivePath(name) || names.has(name) || (mode & 0xf000) === 0xa000 || (flags & 0x41) !== 0 ||
      (method !== 0 && method !== 8) || compressedSize > LOCAL_ARCHIVE_ENTRY_LIMIT ||
      size > LOCAL_ARCHIVE_ENTRY_LIMIT || start + 30 + compressedSize > directoryOffset) {
      throw new Error('LOCAL_UNSAFE_OR_UNSUPPORTED_ZIP');
    }
    names.add(name);
    if (!name.endsWith('/')) entries.push({ name: name, method: method, compressedSize: compressedSize,
      size: size, offset: start, crc: view.getUint32(offset + 16, true) });
    offset += recordSize;
  }
  if (offset !== bytes.byteLength) throw new Error('LOCAL_INVALID_ZIP');
  return entries;
}

export function comicArchivePages(entries: LocalZipEntry[]): string[] {
  return entries.map((entry: LocalZipEntry): string => entry.name)
    .filter((name: string): boolean => !name.startsWith('__MACOSX/') &&
      !name.split('/').some((part: string): boolean => part.startsWith('.')) && localImageFile(name))
    .sort(naturalNameCompare);
}

const CRC_TABLE = new Uint32Array(256);
for (let value = 0; value < 256; value++) {
  let crc = value;
  for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) !== 0 ? 0xedb88320 : 0);
  CRC_TABLE[value] = crc >>> 0;
}

export function localCrc32(bytes: ArrayBuffer): number {
  const values = new Uint8Array(bytes);
  let crc = 0xffffffff;
  for (let index = 0; index < values.length; index++) crc = (crc >>> 8) ^ CRC_TABLE[(crc ^ values[index]) & 255];
  return (crc ^ 0xffffffff) >>> 0;
}
