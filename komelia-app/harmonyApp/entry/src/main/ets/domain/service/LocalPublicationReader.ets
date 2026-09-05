import { image } from '@kit.ImageKit';
import { LocalBookRecord } from '../../model/LocalContentModels';
import { localLibraryService } from './LocalLibraryService';
import { localArchiveReader } from './LocalArchiveReader';
import { localImageFile } from '../../model/LocalContentModels';
import { localSourcePath } from './LocalFilePlatform';
import { pdfPageRenderer } from './PdfPageRenderer';
import { ReaderRequestToken } from './ReaderRequestToken';
import { localLibraryRepository } from '../../data/repository/LocalLibraryRepository';

export class LocalPublicationReader {
  async seriesThumbnail(seriesId: string, width: number, height: number): Promise<PixelMap> {
    const book = (await localLibraryRepository.books()).filter(value => value.seriesId === seriesId)
      .sort((left, right) => left.number - right.number)[0];
    if (book === undefined) throw new Error('LOCAL_BOOK_MISSING');
    return this.thumbnail(book.id, width, height);
  }
  async page(bookId: string, page: number, token?: ReaderRequestToken, editable: boolean = false): Promise<PixelMap> {
    const book = await localLibraryService.readableBook(bookId);
    token?.throwIfCancelled();
    if (!Number.isInteger(page) || page < 0 || page >= book.pageCount) throw new Error('LOCAL_PAGE_OUT_OF_RANGE');
    if (book.mediaType === 'application/pdf') return pdfPageRenderer.renderFile(localSourcePath(book.sourceUri), token, page);
    return this.image(book, book.pageEntries[page] || '', editable, token);
  }

  async thumbnail(bookId: string, width: number, height: number): Promise<PixelMap> {
    const book = await localLibraryService.readableBook(bookId);
    if (book.mediaType === 'application/pdf') return pdfPageRenderer.renderFile(localSourcePath(book.sourceUri));
    let entry = book.pageEntries[0] || '';
    if (book.mediaType === 'application/epub+zip') {
      const images = (await localArchiveReader.entries(book.sourceUri)).filter(value => localImageFile(value.name));
      entry = (images.find(value => /cover/i.test(value.name)) || images[0])?.name || '';
      if (entry.length === 0) throw new Error('LOCAL_EPUB_COVER_MISSING');
    }
    return this.image(book, entry, false, undefined, width, height);
  }

  private async image(book: LocalBookRecord, entry: string, editable: boolean, token?: ReaderRequestToken,
    targetWidth: number = 2048, targetHeight: number = 2048): Promise<PixelMap> {
    const source = entry.length > 0 ? image.createImageSource(await localArchiveReader.entry(book.sourceUri, entry)) :
      image.createImageSource(localSourcePath(book.sourceUri));
    try {
      token?.throwIfCancelled();
      const info = await source.getImageInfo();
      const scale = Math.min(1, Math.max(targetWidth / Math.max(1, info.size.width),
        targetHeight / Math.max(1, info.size.height)), 2048 / Math.max(info.size.width, info.size.height));
      const pixels = await source.createPixelMap({ editable: editable, desiredPixelFormat: image.PixelMapFormat.RGBA_8888,
        desiredSize: { width: Math.max(1, Math.round(info.size.width * scale)),
          height: Math.max(1, Math.round(info.size.height * scale)) } });
      if (token?.isCancelled()) { pixels.release(); token.throwIfCancelled(); }
      return pixels;
    } finally { source.release(); }
  }
}

export const localPublicationReader = new LocalPublicationReader();
