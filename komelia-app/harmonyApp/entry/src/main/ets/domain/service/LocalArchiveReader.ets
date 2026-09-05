import { fileIo as fs } from '@kit.CoreFileKit';
import { zlib } from '@kit.BasicServicesKit';
import { LocalZipEntry, LOCAL_ARCHIVE_ENTRY_LIMIT, localCrc32, parseZipDirectory,
  zipDirectoryLocation } from './LocalArchiveIndex';

export async function readLocalRange(path: string, offset: number, size: number): Promise<ArrayBuffer> {
  if (size < 0 || size > LOCAL_ARCHIVE_ENTRY_LIMIT || offset < 0) throw new Error('LOCAL_FILE_READ_LIMIT');
  const file = await fs.open(path, fs.OpenMode.READ_ONLY);
  try {
    fs.lseek(file.fd, offset, fs.WhenceType.SEEK_SET);
    const output = new Uint8Array(size);
    let read = 0;
    while (read < size) {
      const chunk = new ArrayBuffer(size - read);
      const count = await fs.read(file.fd, chunk);
      if (count <= 0) throw new Error('LOCAL_FILE_TRUNCATED');
      output.set(new Uint8Array(chunk, 0, count), read);
      read += count;
    }
    return output.buffer as ArrayBuffer;
  } finally { await fs.close(file); }
}

export class LocalArchiveReader {
  async entries(path: string): Promise<LocalZipEntry[]> {
    const file = await fs.open(path, fs.OpenMode.READ_ONLY);
    let size: number;
    try { size = (await fs.stat(file.fd)).size; } finally { await fs.close(file); }
    const tailSize = Math.min(size, 65557);
    const directory = zipDirectoryLocation(await readLocalRange(path, size - tailSize, tailSize), size);
    return parseZipDirectory(await readLocalRange(path, directory.offset, directory.size),
      directory.count, directory.offset);
  }

  async entry(path: string, name: string): Promise<ArrayBuffer> {
    const entries = await this.entries(path);
    const entry = entries.find((value: LocalZipEntry): boolean => value.name === name);
    if (entry === undefined) throw new Error('LOCAL_ARCHIVE_PAGE_MISSING');
    const header = new DataView(await readLocalRange(path, entry.offset, 30));
    if (header.getUint32(0, true) !== 0x04034b50 || header.getUint16(8, true) !== entry.method ||
      (header.getUint16(6, true) & 0x41) !== 0) throw new Error('LOCAL_INVALID_ZIP');
    const dataOffset = entry.offset + 30 + header.getUint16(26, true) + header.getUint16(28, true);
    const input = await readLocalRange(path, dataOffset, entry.compressedSize);
    let output: ArrayBuffer;
    if (entry.method === 0) {
      output = input;
    } else {
      output = new ArrayBuffer(entry.size);
      const zip = await zlib.createZip();
      const stream: zlib.ZStream = { nextIn: input, availableIn: input.byteLength,
        nextOut: output, availableOut: output.byteLength };
      await zip.inflateInit2(stream, -15);
      try {
        const status = await zip.inflate(stream, zlib.CompressFlushMode.FINISH);
        if (status !== zlib.ReturnStatus.STREAM_END || stream.totalOut !== entry.size) {
          throw new Error('LOCAL_INVALID_ZIP_DATA');
        }
      } finally { await zip.inflateEnd(stream); }
    }
    if (output.byteLength !== entry.size || localCrc32(output) !== entry.crc) throw new Error('LOCAL_INVALID_ZIP_CRC');
    return output;
  }
}

export const localArchiveReader = new LocalArchiveReader();
