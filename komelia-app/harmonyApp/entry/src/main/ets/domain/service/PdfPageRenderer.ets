import { fileIo as fs } from '@kit.CoreFileKit';
import { pdfService, pdfViewManager } from '@kit.PDFKit';
import { AppContainer } from '../../core/di/AppContainer';
import { localizedString } from '../../core/i18n/LocalizedResources';
import { ReaderRequestToken, SerialTaskGate } from './ReaderRequestToken';

export class PdfPageRenderer {
  private sequence: number = 0;
  private gate: SerialTaskGate = new SerialTaskGate();

  async render(bytes: ArrayBuffer, pageNumber: number, token?: ReaderRequestToken): Promise<PixelMap> {
    await this.gate.enter();
    let path: string = '';
    try {
      token?.throwIfCancelled();
      path = this.temporaryPath(pageNumber);
      const file = fs.openSync(path, fs.OpenMode.WRITE_ONLY | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
      try {
        fs.writeSync(file.fd, bytes);
      } finally {
        fs.closeSync(file);
      }
      token?.throwIfCancelled();
      return await this.renderFileLocked(path, token);
    } finally {
      if (path.length > 0 && fs.accessSync(path)) fs.unlinkSync(path);
      this.gate.leave();
    }
  }

  async renderFile(path: string, token?: ReaderRequestToken, page: number = 0): Promise<PixelMap> {
    await this.gate.enter();
    try {
      token?.throwIfCancelled();
      return await this.renderFileLocked(path, token, page);
    } finally {
      this.gate.leave();
    }
  }

  private async renderFileLocked(path: string, token?: ReaderRequestToken, page: number = 0): Promise<PixelMap> {
    const controller = new pdfViewManager.PdfController();
    let loaded: boolean = false;
    try {
      const result = await controller.loadDocument(path);
      if (result !== pdfService.ParseResult.PARSE_SUCCESS) {
        throw new Error(localizedString($r('app.string.pdf_parse_failed'), result));
      }
      loaded = true;
      token?.throwIfCancelled();
      if (page < 0 || page >= controller.getPageCount()) throw new Error('LOCAL_PAGE_OUT_OF_RANGE');
      const pixelMap = await controller.getPagePixelMap(page, true);
      if (token?.isCancelled()) {
        pixelMap.release();
        throw new Error(localizedString($r('app.string.reader_request_cancelled')));
      }
      return pixelMap;
    } finally {
      if (loaded) controller.releaseDocument();
    }
  }

  private temporaryPath(pageNumber: number): string {
    this.sequence += 1;
    return `${AppContainer.requireContext().tempDir}/komelia-page-${Date.now()}-${pageNumber}-${this.sequence}.pdf`;
  }
}

export const pdfPageRenderer = new PdfPageRenderer();
