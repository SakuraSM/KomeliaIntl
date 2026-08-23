import { relationalStore } from '@kit.ArkData';
import { TaskPhase } from '../../core/model/UiState';
import { DownloadJobRecord, OfflineBookRecord, OfflineMediaKind, OfflinePageRecord,
  ProgressOutboxRecord } from '../local/DatabaseModels';
import { HarmonyDatabase } from '../local/HarmonyDatabase';
import { HomeFeed, KomgaBook, KomgaLibrary, KomgaSeries, KomgaUser, PageResult } from '../../model/KomgaModels';
import { komfClient } from '../../network/KomfClient';
import { komgaClient } from '../../network/KomgaClient';
import { AppPreferences } from '../../persistence/AppPreferences';
import { CredentialStore } from '../../persistence/CredentialStore';
import { AdminRepository, CatalogRepository, DownloadScheduler, KomfRepository, OfflineRepository,
  ProgressOutbox, SessionCredentials, SessionRepository, SessionSnapshot } from '../../domain/repository/Repositories';

export class DefaultSessionRepository implements SessionRepository {
  async restore(): Promise<SessionSnapshot | undefined> {
    const serverUrl = await AppPreferences.getString('serverUrl');
    const credentialAlias = await AppPreferences.getString('activeCredentialAlias');
    if (credentialAlias.length === 0) return undefined;
    const credential = await CredentialStore.load(credentialAlias);
    if (serverUrl.length === 0 || credential === undefined) return undefined;
    const allowInsecureHttp = await AppPreferences.getBoolean('allowInsecureHttp');
    try {
      return await this.signIn({
        serverUrl: serverUrl,
        email: credential.email,
        password: credential.password,
        allowInsecureHttp: allowInsecureHttp
      }, false);
    } catch (_) {
      return this.restoreOffline(serverUrl);
    }
  }

  async signIn(credentials: SessionCredentials, persist: boolean = true): Promise<SessionSnapshot> {
    komgaClient.configure(credentials.serverUrl, credentials.email, credentials.password,
      credentials.allowInsecureHttp);
    const values = await Promise.all([komgaClient.login(), komgaClient.getLibraries()]);
    const namespace = { serverId: komgaClient.getServerUrl(), userId: values[0].id };
    const namespaceValue = `${namespace.serverId}:${namespace.userId}`;
    let credentialAlias = await this.findCredentialAlias(namespaceValue);
    if (persist) {
      if (credentialAlias.length === 0) credentialAlias = CredentialStore.createAlias();
      await Promise.all([
        AppPreferences.putString('serverUrl', komgaClient.getServerUrl()),
        AppPreferences.putString('activeNamespace', namespaceValue),
        AppPreferences.putBoolean('allowInsecureHttp', credentials.allowInsecureHttp),
        AppPreferences.putString('activeCredentialAlias', credentialAlias),
        CredentialStore.save(credentialAlias, { email: credentials.email, password: credentials.password })
      ]);
    }
    await HarmonyDatabase.replace('SERVER_PROFILE', {
      'ID': namespace.serverId,
      'ORIGIN': komgaClient.getServerUrl(),
      'DISPLAY_NAME': komgaClient.getServerUrl(),
      'ALLOW_HTTP': credentials.allowInsecureHttp ? 1 : 0,
      'LAST_USER_ID': values[0].id,
      'UPDATED_AT': Date.now()
    });
    await HarmonyDatabase.replace('USER_PROFILE', {
      'NAMESPACE': namespaceValue,
      'ENTITY_ID': values[0].id,
      'PAYLOAD': JSON.stringify(values[0]),
      'CREDENTIAL_ALIAS': credentialAlias,
      'UPDATED_AT': Date.now()
    });
    for (let index = 0; index < values[1].length; index++) {
      await this.cacheCatalog(namespaceValue, 'library', values[1][index].id, values[1][index]);
    }
    return { namespace: namespace, user: values[0], libraries: values[1] };
  }

  async signOut(): Promise<void> {
    komgaClient.clear();
    const credentialAlias = await AppPreferences.getString('activeCredentialAlias');
    if (credentialAlias.length > 0) await CredentialStore.remove(credentialAlias);
    await AppPreferences.putString('activeCredentialAlias', '');
  }

  private async restoreOffline(serverUrl: string): Promise<SessionSnapshot | undefined> {
    const namespace = await AppPreferences.getString('activeNamespace');
    if (!namespace.startsWith(`${serverUrl}:`)) return undefined;
    const userResult = await HarmonyDatabase.query(
      'SELECT PAYLOAD FROM USER_PROFILE WHERE NAMESPACE = ? ORDER BY UPDATED_AT DESC LIMIT 1', [namespace]);
    let user: KomgaUser | undefined;
    try {
      if (userResult.goToFirstRow()) user = JSON.parse(userResult.getString(0)) as KomgaUser;
    } finally {
      userResult.close();
    }
    if (user === undefined) return undefined;
    const libraries = await this.readCachedLibraries(namespace);
    return {
      namespace: { serverId: serverUrl, userId: user.id },
      user: user,
      libraries: libraries
    };
  }

  private async readCachedLibraries(namespace: string): Promise<KomgaLibrary[]> {
    const result = await HarmonyDatabase.query(
      'SELECT PAYLOAD FROM CATALOG_ENTITY WHERE NAMESPACE = ? AND ENTITY_TYPE = ? ORDER BY UPDATED_AT DESC',
      [namespace, 'library']);
    const values: KomgaLibrary[] = [];
    try {
      if (result.goToFirstRow()) {
        do values.push(JSON.parse(result.getString(0)) as KomgaLibrary); while (result.goToNextRow());
      }
      return values;
    } finally {
      result.close();
    }
  }

  private async cacheCatalog(namespace: string, type: string, id: string, value: Object): Promise<void> {
    await HarmonyDatabase.replace('CATALOG_ENTITY', {
      'NAMESPACE': namespace,
      'ENTITY_TYPE': type,
      'ENTITY_ID': id,
      'PAYLOAD': JSON.stringify(value),
      'UPDATED_AT': Date.now()
    });
  }

  private async findCredentialAlias(namespace: string): Promise<string> {
    const result = await HarmonyDatabase.query(
      'SELECT CREDENTIAL_ALIAS FROM USER_PROFILE WHERE NAMESPACE = ? ORDER BY UPDATED_AT DESC LIMIT 1', [namespace]);
    try {
      return result.goToFirstRow() ? result.getString(0) : '';
    } finally {
      result.close();
    }
  }
}

export class DefaultCatalogRepository implements CatalogRepository {
  async libraries(): Promise<KomgaLibrary[]> {
    try {
      const values = await komgaClient.getLibraries();
      await this.cacheMany('library', values, (item: KomgaLibrary) => item.id);
      return values;
    } catch (_) {
      return this.readMany<KomgaLibrary>('library');
    }
  }

  async homeFeed(): Promise<HomeFeed> {
    try {
      const value = await komgaClient.getHomeFeed();
      await this.cacheOne('homeFeed', 'current', value);
      return value;
    } catch (_) {
      const value = await this.readOne<HomeFeed>('homeFeed', 'current');
      return value === undefined ? { onDeck: [], latest: [], series: [] } : value;
    }
  }

  async series(query: string, page: number, size: number): Promise<PageResult<KomgaSeries>> {
    try {
      const result = await komgaClient.searchSeries(query, page, size);
      await this.cacheMany('series', result.content, (item: KomgaSeries) => item.id);
      return result;
    } catch (_) {
      const values = (await this.readMany<KomgaSeries>('series')).filter((item: KomgaSeries) =>
        item.metadata.title.toLowerCase().includes(query.toLowerCase()));
      return this.localPage(values, page, size);
    }
  }

  async books(query: string, page: number, size: number): Promise<PageResult<KomgaBook>> {
    try {
      const result = await komgaClient.searchBooks(query, page, size);
      await this.cacheMany('book', result.content, (item: KomgaBook) => item.id);
      return result;
    } catch (_) {
      const values = (await this.readMany<KomgaBook>('book')).filter((item: KomgaBook) =>
        (item.metadata.title || item.name).toLowerCase().includes(query.toLowerCase()));
      return this.localPage(values, page, size);
    }
  }

  async seriesDetail(id: string): Promise<KomgaSeries> {
    try {
      const value = await komgaClient.getSeries(id);
      await this.cacheOne('series', id, value);
      return value;
    } catch (_) {
      const value = await this.readOne<KomgaSeries>('series', id);
      if (value === undefined) throw new Error('离线缓存中没有该系列');
      return value;
    }
  }

  async seriesBooks(id: string): Promise<KomgaBook[]> {
    try {
      const values = await komgaClient.getSeriesBooks(id);
      await this.cacheMany('book', values, (item: KomgaBook) => item.id);
      await this.cacheOne('seriesBooks', id, values);
      return values;
    } catch (_) {
      const values = await this.readOne<KomgaBook[]>('seriesBooks', id);
      return values === undefined ? [] : values;
    }
  }

  async bookDetail(id: string): Promise<KomgaBook> {
    try {
      const value = await komgaClient.getBook(id);
      await this.cacheOne('book', id, value);
      return value;
    } catch (_) {
      const value = await this.readOne<KomgaBook>('book', id);
      if (value === undefined) throw new Error('离线缓存中没有该书籍');
      return value;
    }
  }

  private async activeNamespace(): Promise<string> {
    return AppPreferences.getString('activeNamespace');
  }

  private async cacheOne(type: string, id: string, value: Object): Promise<void> {
    const namespace = await this.activeNamespace();
    if (namespace.length === 0) return;
    await HarmonyDatabase.replace('CATALOG_ENTITY', {
      'NAMESPACE': namespace, 'ENTITY_TYPE': type, 'ENTITY_ID': id,
      'PAYLOAD': JSON.stringify(value), 'UPDATED_AT': Date.now()
    });
  }

  private async cacheMany<T>(type: string, values: T[], idOf: (value: T) => string): Promise<void> {
    for (let index = 0; index < values.length; index++) {
      await this.cacheOne(type, idOf(values[index]), values[index] as Object);
    }
  }

  private async readOne<T>(type: string, id: string): Promise<T | undefined> {
    const result = await HarmonyDatabase.query(
      'SELECT PAYLOAD FROM CATALOG_ENTITY WHERE NAMESPACE = ? AND ENTITY_TYPE = ? AND ENTITY_ID = ?',
      [await this.activeNamespace(), type, id]);
    try {
      return result.goToFirstRow() ? JSON.parse(result.getString(0)) as T : undefined;
    } finally {
      result.close();
    }
  }

  private async readMany<T>(type: string): Promise<T[]> {
    const result = await HarmonyDatabase.query(
      'SELECT PAYLOAD FROM CATALOG_ENTITY WHERE NAMESPACE = ? AND ENTITY_TYPE = ? ORDER BY UPDATED_AT DESC',
      [await this.activeNamespace(), type]);
    const values: T[] = [];
    try {
      if (result.goToFirstRow()) {
        do values.push(JSON.parse(result.getString(0)) as T); while (result.goToNextRow());
      }
      return values;
    } finally {
      result.close();
    }
  }

  private localPage<T>(values: T[], page: number, size: number): PageResult<T> {
    const from = Math.max(0, page * size);
    return {
      content: values.slice(from, from + size),
      number: page,
      size: size,
      totalElements: values.length,
      totalPages: Math.ceil(values.length / size),
      first: page === 0,
      last: from + size >= values.length,
      empty: values.length === 0
    };
  }
}

export class RdbOfflineRepository implements OfflineRepository {
  async saveBook(record: OfflineBookRecord): Promise<void> {
    await HarmonyDatabase.replace('OFFLINE_BOOK', {
      'NAMESPACE': record.namespace,
      'BOOK_ID': record.bookId,
      'MEDIA_KIND': record.mediaKind,
      'TITLE': record.title,
      'LOCAL_PATH': record.localPath,
      'PAGE_COUNT': record.pageCount,
      'DOWNLOADED_PAGES': record.downloadedPages,
      'SIZE_BYTES': record.sizeBytes,
      'UPDATED_AT': record.updatedAt
    });
  }

  async savePage(record: OfflinePageRecord): Promise<void> {
    await HarmonyDatabase.replace('OFFLINE_PAGE', {
      'NAMESPACE': record.namespace,
      'BOOK_ID': record.bookId,
      'PAGE_NUMBER': record.pageNumber,
      'LOCAL_PATH': record.localPath,
      'MEDIA_TYPE': record.mediaType,
      'SIZE_BYTES': record.sizeBytes,
      'COMPLETED': record.completed ? 1 : 0
    });
  }

  async getBook(namespace: string, bookId: string): Promise<OfflineBookRecord | undefined> {
    const values = await this.readBooks(
      'SELECT * FROM OFFLINE_BOOK WHERE NAMESPACE = ? AND BOOK_ID = ?', [namespace, bookId]);
    return values.length === 0 ? undefined : values[0];
  }

  async getPage(namespace: string, bookId: string, pageNumber: number): Promise<OfflinePageRecord | undefined> {
    const result = await HarmonyDatabase.query(
      'SELECT * FROM OFFLINE_PAGE WHERE NAMESPACE = ? AND BOOK_ID = ? AND PAGE_NUMBER = ?',
      [namespace, bookId, pageNumber]);
    try {
      if (!result.goToFirstRow()) return undefined;
      return this.readPage(result);
    } finally {
      result.close();
    }
  }

  listBooks(namespace: string): Promise<OfflineBookRecord[]> {
    return this.readBooks('SELECT * FROM OFFLINE_BOOK WHERE NAMESPACE = ? ORDER BY UPDATED_AT DESC', [namespace]);
  }

  async listPages(namespace: string, bookId: string): Promise<OfflinePageRecord[]> {
    const result = await HarmonyDatabase.query(
      'SELECT * FROM OFFLINE_PAGE WHERE NAMESPACE = ? AND BOOK_ID = ? ORDER BY PAGE_NUMBER',
      [namespace, bookId]);
    const values: OfflinePageRecord[] = [];
    try {
      if (result.goToFirstRow()) {
        do {
          values.push(this.readPage(result));
        } while (result.goToNextRow());
      }
      return values;
    } finally {
      result.close();
    }
  }

  private readPage(result: relationalStore.ResultSet): OfflinePageRecord {
    return {
      namespace: result.getString(result.getColumnIndex('NAMESPACE')),
      bookId: result.getString(result.getColumnIndex('BOOK_ID')),
      pageNumber: result.getLong(result.getColumnIndex('PAGE_NUMBER')),
      localPath: result.getString(result.getColumnIndex('LOCAL_PATH')),
      mediaType: result.getString(result.getColumnIndex('MEDIA_TYPE')),
      sizeBytes: result.getLong(result.getColumnIndex('SIZE_BYTES')),
      completed: result.getLong(result.getColumnIndex('COMPLETED')) === 1
    };
  }

  async deleteBook(namespace: string, bookId: string): Promise<void> {
    await HarmonyDatabase.execute('DELETE FROM OFFLINE_PAGE WHERE NAMESPACE = ? AND BOOK_ID = ?',
      [namespace, bookId]);
    await HarmonyDatabase.execute('DELETE FROM OFFLINE_BOOK WHERE NAMESPACE = ? AND BOOK_ID = ?',
      [namespace, bookId]);
  }

  private async readBooks(sql: string, args: relationalStore.ValueType[]): Promise<OfflineBookRecord[]> {
    const result = await HarmonyDatabase.query(sql, args);
    const values: OfflineBookRecord[] = [];
    try {
      if (result.goToFirstRow()) {
        do {
          values.push({
            namespace: result.getString(result.getColumnIndex('NAMESPACE')),
            bookId: result.getString(result.getColumnIndex('BOOK_ID')),
            mediaKind: result.getString(result.getColumnIndex('MEDIA_KIND')) as OfflineMediaKind,
            title: result.getString(result.getColumnIndex('TITLE')),
            localPath: result.getString(result.getColumnIndex('LOCAL_PATH')),
            pageCount: result.getLong(result.getColumnIndex('PAGE_COUNT')),
            downloadedPages: result.getLong(result.getColumnIndex('DOWNLOADED_PAGES')),
            sizeBytes: result.getLong(result.getColumnIndex('SIZE_BYTES')),
            updatedAt: result.getLong(result.getColumnIndex('UPDATED_AT'))
          });
        } while (result.goToNextRow());
      }
      return values;
    } finally {
      result.close();
    }
  }
}

export class RdbProgressOutbox implements ProgressOutbox {
  async enqueue(record: ProgressOutboxRecord): Promise<void> {
    await HarmonyDatabase.replace('PROGRESS_OUTBOX', {
      'NAMESPACE': record.namespace,
      'BOOK_ID': record.bookId,
      'PAGE': record.page,
      'COMPLETED': record.completed ? 1 : 0,
      'READ_DATE': record.readDate,
      'ATTEMPTS': record.attempts
    });
  }

  async flush(namespace: string): Promise<number> {
    const result = await HarmonyDatabase.query(
      'SELECT * FROM PROGRESS_OUTBOX WHERE NAMESPACE = ? ORDER BY READ_DATE', [namespace]);
    const pending: ProgressOutboxRecord[] = [];
    try {
      if (result.goToFirstRow()) {
        do {
          pending.push({
            namespace: namespace,
            bookId: result.getString(result.getColumnIndex('BOOK_ID')),
            page: result.getLong(result.getColumnIndex('PAGE')),
            completed: result.getLong(result.getColumnIndex('COMPLETED')) === 1,
            readDate: result.getString(result.getColumnIndex('READ_DATE')),
            attempts: result.getLong(result.getColumnIndex('ATTEMPTS'))
          });
        } while (result.goToNextRow());
      }
    } finally {
      result.close();
    }
    let flushed = 0;
    for (let index = 0; index < pending.length; index++) {
      const item = pending[index];
      try {
        await komgaClient.updateReadProgress(item.bookId, item.page, item.completed);
        await HarmonyDatabase.execute(
          'DELETE FROM PROGRESS_OUTBOX WHERE NAMESPACE = ? AND BOOK_ID = ?', [namespace, item.bookId]);
        flushed++;
      } catch (_) {
        await HarmonyDatabase.execute(
          'UPDATE PROGRESS_OUTBOX SET ATTEMPTS = ATTEMPTS + 1 WHERE NAMESPACE = ? AND BOOK_ID = ?',
          [namespace, item.bookId]);
        break;
      }
    }
    return flushed;
  }
}

export class RdbDownloadScheduler implements DownloadScheduler {
  async enqueue(namespace: string, bookId: string): Promise<string> {
    const jobId = `${bookId}-${Date.now()}`;
    await HarmonyDatabase.replace('DOWNLOAD_JOB', {
      'NAMESPACE': namespace,
      'JOB_ID': jobId,
      'BOOK_ID': bookId,
      'PHASE': TaskPhase.QUEUED,
      'COMPLETED_PARTS': 0,
      'TOTAL_PARTS': 0,
      'ATTEMPTS': 0,
      'ERROR_MESSAGE': '',
      'UPDATED_AT': Date.now()
    });
    return jobId;
  }

  pause(jobId: string): Promise<void> {
    return this.setPhase(jobId, TaskPhase.PAUSED);
  }

  async start(jobId: string, totalParts: number): Promise<void> {
    await HarmonyDatabase.execute(
      'UPDATE DOWNLOAD_JOB SET PHASE = ?, TOTAL_PARTS = ?, ERROR_MESSAGE = ?, UPDATED_AT = ? WHERE JOB_ID = ?',
      [TaskPhase.RUNNING, totalParts, '', Date.now(), jobId]);
  }

  async update(jobId: string, completedParts: number): Promise<void> {
    await HarmonyDatabase.execute(
      'UPDATE DOWNLOAD_JOB SET COMPLETED_PARTS = ?, UPDATED_AT = ? WHERE JOB_ID = ?',
      [completedParts, Date.now(), jobId]);
  }

  async fail(jobId: string, message: string): Promise<void> {
    await HarmonyDatabase.execute(
      'UPDATE DOWNLOAD_JOB SET PHASE = ?, ATTEMPTS = ATTEMPTS + 1, ERROR_MESSAGE = ?, UPDATED_AT = ? ' +
      'WHERE JOB_ID = ?', [TaskPhase.FAILED, message, Date.now(), jobId]);
  }

  async complete(jobId: string): Promise<void> {
    await HarmonyDatabase.execute(
      'UPDATE DOWNLOAD_JOB SET PHASE = ?, COMPLETED_PARTS = TOTAL_PARTS, UPDATED_AT = ? WHERE JOB_ID = ?',
      [TaskPhase.COMPLETED, Date.now(), jobId]);
  }

  resume(jobId: string): Promise<void> {
    return this.setPhase(jobId, TaskPhase.QUEUED);
  }

  cancel(jobId: string): Promise<void> {
    return this.setPhase(jobId, TaskPhase.CANCELLED);
  }

  async jobs(namespace: string): Promise<DownloadJobRecord[]> {
    const result = await HarmonyDatabase.query(
      'SELECT * FROM DOWNLOAD_JOB WHERE NAMESPACE = ? ORDER BY UPDATED_AT DESC', [namespace]);
    const values: DownloadJobRecord[] = [];
    try {
      if (result.goToFirstRow()) {
        do {
          values.push({
            namespace: result.getString(result.getColumnIndex('NAMESPACE')),
            jobId: result.getString(result.getColumnIndex('JOB_ID')),
            bookId: result.getString(result.getColumnIndex('BOOK_ID')),
            phase: result.getString(result.getColumnIndex('PHASE')) as TaskPhase,
            completedParts: result.getLong(result.getColumnIndex('COMPLETED_PARTS')),
            totalParts: result.getLong(result.getColumnIndex('TOTAL_PARTS')),
            attempts: result.getLong(result.getColumnIndex('ATTEMPTS')),
            errorMessage: result.getString(result.getColumnIndex('ERROR_MESSAGE')),
            updatedAt: result.getLong(result.getColumnIndex('UPDATED_AT'))
          });
        } while (result.goToNextRow());
      }
      return values;
    } finally {
      result.close();
    }
  }

  async phase(jobId: string): Promise<TaskPhase | undefined> {
    const result = await HarmonyDatabase.query('SELECT PHASE FROM DOWNLOAD_JOB WHERE JOB_ID = ?', [jobId]);
    try {
      return result.goToFirstRow() ? result.getString(0) as TaskPhase : undefined;
    } finally {
      result.close();
    }
  }

  private async setPhase(jobId: string, phase: TaskPhase): Promise<void> {
    await HarmonyDatabase.execute('UPDATE DOWNLOAD_JOB SET PHASE = ?, UPDATED_AT = ? WHERE JOB_ID = ?',
      [phase, Date.now(), jobId]);
  }
}

export class DefaultAdminRepository implements AdminRepository {
  scanLibrary(libraryId: string, deep: boolean): Promise<void> {
    return komgaClient.runLibraryAction(libraryId, 'scan', deep);
  }

  analyzeLibrary(libraryId: string): Promise<void> {
    return komgaClient.runLibraryAction(libraryId, 'analyze');
  }

  emptyLibraryTrash(libraryId: string): Promise<void> {
    return komgaClient.runLibraryAction(libraryId, 'empty-trash');
  }
}

export class DefaultKomfRepository implements KomfRepository {
  configure(origin: string): void {
    komfClient.configure(origin);
  }

  ping(): Promise<boolean> {
    return komfClient.ping();
  }

  identifySeries(libraryId: string, seriesId: string): Promise<void> {
    return komfClient.identifySeries('komga', libraryId, seriesId);
  }

  resetSeries(libraryId: string, seriesId: string, removeComicInfo: boolean): Promise<void> {
    return komfClient.resetSeries('komga', libraryId, seriesId, removeComicInfo);
  }
}

export const sessionRepository = new DefaultSessionRepository();
export const catalogRepository = new DefaultCatalogRepository();
export const offlineRepository = new RdbOfflineRepository();
export const downloadScheduler = new RdbDownloadScheduler();
export const progressOutbox = new RdbProgressOutbox();
export const adminRepository = new DefaultAdminRepository();
export const komfRepository = new DefaultKomfRepository();
