import { fileIo as fs, fileShare, fileUri, picker } from '@kit.CoreFileKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { pdfService } from '@kit.PDFKit';
import { AppContainer } from '../../core/di/AppContainer';
import { LocalBookInspection, LocalLibraryKind, LocalLibraryRecord, LocalSourceFile,
  localFileSupported, localImageFile, localStableId } from '../../model/LocalContentModels';
import { LocalSourceReader } from './LocalLibraryIndexer';
import { localArchiveReader } from './LocalArchiveReader';
import { comicArchivePages } from './LocalArchiveIndex';

const FOLDER_SELECTION = 'SystemCapability.FileManagement.UserFileService.FolderSelection';
const PERSISTENT_ACCESS = 'SystemCapability.FileManagement.AppFileService.FolderAuthorization';

export function localSourcePath(uri: string): string {
  return uri.startsWith('file://') ? new fileUri.FileUri(uri).path : uri;
}

export class LocalFilePlatform implements LocalSourceReader {
  supportsFolders(): boolean { return canIUse(FOLDER_SELECTION) && canIUse(PERSISTENT_ACCESS); }

  async select(folder: boolean): Promise<LocalLibraryRecord[]> {
    if (folder && !this.supportsFolders()) throw new Error('LOCAL_FOLDER_UNSUPPORTED');
    const view = new picker.DocumentViewPicker(AppContainer.requireContext());
    const options = new picker.DocumentSelectOptions();
    options.maxSelectNumber = folder ? 1 : 100;
    if (folder) options.selectMode = picker.DocumentSelectMode.FOLDER;
    else options.fileSuffixFilters = ['Books|.cbz,.zip,.epub,.pdf,.jpg,.jpeg,.png,.webp,.gif,.bmp'];
    const uris = await view.select(options);
    const libraries: LocalLibraryRecord[] = [];
    for (let index = 0; index < uris.length; index++) {
      const uri = uris[index];
      const name = new fileUri.FileUri(uri).name;
      if (!folder && !localFileSupported(name)) throw new Error('LOCAL_FILE_UNSUPPORTED');
      const id = 'local-library-' + localStableId(uri);
      const managed = !canIUse(PERSISTENT_ACCESS);
      let rootUri = uri;
      if (managed) {
        // Picker explicitly imports files on devices without durable folder grants; never modifies the source URI.
        const directory = `${AppContainer.requireContext().filesDir}/local-imports`;
        if (!fs.accessSync(directory)) await fs.mkdir(directory, true);
        const suffix = name.substring(name.lastIndexOf('.')).toLowerCase();
        const target = `${directory}/${id}${suffix}`;
        // Re-import does not overwrite a file an active reader may still be using.
        if (!fs.accessSync(target)) {
          const input = await fs.open(uri, fs.OpenMode.READ_ONLY);
          try {
            await fs.copyFile(input.fd, `${target}.partial`);
            await fs.rename(`${target}.partial`, target);
          } catch (error) {
            if (fs.accessSync(`${target}.partial`)) await fs.unlink(`${target}.partial`);
            throw error as Error;
          } finally { await fs.close(input); }
        }
        rootUri = target;
      } else {
        await fileShare.persistPermission([{ uri: uri, operationMode: fileShare.OperationMode.READ_MODE }]);
        await fileShare.activatePermission([{ uri: uri, operationMode: fileShare.OperationMode.READ_MODE }]);
      }
      libraries.push({ id: id, name: name, rootUri: rootUri,
        kind: folder ? LocalLibraryKind.DIRECTORY : LocalLibraryKind.FILE, managedCopy: managed,
        fileName: folder ? '' : name, scanOnStartup: true, scanIntervalHours: 1, lastScannedAt: 0, excludedPaths: [] });
    }
    return libraries;
  }

  async activate(library: LocalLibraryRecord): Promise<void> {
    if (!library.managedCopy) {
      await fileShare.activatePermission([{ uri: library.rootUri, operationMode: fileShare.OperationMode.READ_MODE }]);
    }
  }

  async release(library: LocalLibraryRecord): Promise<void> {
    if (!library.managedCopy) {
      await fileShare.revokePermission([{ uri: library.rootUri, operationMode: fileShare.OperationMode.READ_MODE }]);
    } else {
      const root = `${AppContainer.requireContext().filesDir}/local-imports/`;
      const path = library.rootUri;
      // Only the exact private import file owned by this library can be removed, never a granted user source.
      if (path.startsWith(root + library.id + '.') && !path.substring(root.length).includes('/') &&
        fs.accessSync(path)) await fs.unlink(path);
    }
  }

  async list(library: LocalLibraryRecord): Promise<LocalSourceFile[]> {
    await this.activate(library);
    const root = localSourcePath(library.rootUri);
    if (library.kind === LocalLibraryKind.FILE) {
      try {
        const file = await fs.open(library.rootUri, fs.OpenMode.READ_ONLY);
        try {
          const stat = await fs.stat(file.fd);
          return [{ relativePath: library.fileName || library.name, sourceUri: library.rootUri,
            displayName: library.fileName || library.name, sizeBytes: stat.size, modifiedAt: stat.mtime * 1000 }];
        } finally { await fs.close(file); }
      } catch (error) {
        if ((error as BusinessError).code === 13900002) return [];
        throw error as Error;
      }
    }
    const result: LocalSourceFile[] = [];
    await this.walk(root, '', result, 0);
    return result;
  }

  private async walk(root: string, relative: string, result: LocalSourceFile[], depth: number): Promise<void> {
    if (depth > 32 || result.length > 20000) throw new Error('LOCAL_LIBRARY_SCAN_LIMIT');
    const path = relative.length === 0 ? root : `${root}/${relative}`;
    const names = await fs.listFile(path);
    for (let index = 0; index < names.length; index++) {
      const name = names[index];
      if (name === '.' || name === '..' || name.includes('/') || name.includes('\\')) continue;
      const childRelative = relative.length === 0 ? name : `${relative}/${name}`;
      const child = `${root}/${childRelative}`;
      const stat = await fs.lstat(child);
      if (stat.isSymbolicLink()) continue;
      if (stat.isDirectory()) await this.walk(root, childRelative, result, depth + 1);
      else if (stat.isFile() && localFileSupported(name)) {
        result.push({ relativePath: childRelative, sourceUri: fileUri.getUriFromPath(child), displayName: name,
          sizeBytes: stat.size, modifiedAt: stat.mtime * 1000 });
      }
    }
  }

  async inspect(file: LocalSourceFile): Promise<LocalBookInspection> {
    if (file.sizeBytes <= 0) throw new Error('LOCAL_EMPTY_PUBLICATION');
    if (/\.pdf$/i.test(file.displayName)) {
      const document = new pdfService.PdfDocument();
      try {
        const result = document.loadDocument(localSourcePath(file.sourceUri));
        if (result !== pdfService.ParseResult.PARSE_SUCCESS) throw new Error('LOCAL_PDF_PARSE_FAILED');
        return { mediaType: 'application/pdf', pageCount: document.getPageCount(), pageEntries: [] };
      } finally { document.releaseDocument(); }
    }
    if (localImageFile(file.displayName)) return { mediaType: 'image/*', pageCount: 1, pageEntries: [] };
    const entries = await localArchiveReader.entries(file.sourceUri);
    if (/\.epub$/i.test(file.displayName)) {
      if (!entries.some(entry => entry.name === 'META-INF/container.xml')) throw new Error('LOCAL_EPUB_PARSE_FAILED');
      return { mediaType: 'application/epub+zip', pageCount: 1, pageEntries: [] };
    }
    const pages = comicArchivePages(entries);
    if (pages.length === 0) throw new Error('LOCAL_EMPTY_PUBLICATION');
    return { mediaType: 'application/zip', pageCount: pages.length, pageEntries: pages };
  }
}

export const localFilePlatform = new LocalFilePlatform();
