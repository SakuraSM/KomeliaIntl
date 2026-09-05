import { relationalStore } from '@kit.ArkData';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { TaskPhase } from '../../core/model/UiState';
import { localizedString } from '../../core/i18n/LocalizedResources';
import { networkRouteCandidates, NetworkRouteCandidate } from '../../domain/service/NetworkRouteResolver';
import { DownloadJobRecord, NamespaceKey, OfflineBookRecord, OfflineMediaKind, OfflinePageRecord,
  OfflineLogLevel, OfflineLogPage, OfflineLogRecord, progressOutboxOperation, ProgressOutboxOperation,
  ProgressOutboxRecord } from
  '../local/DatabaseModels';
import { HarmonyDatabase } from '../local/HarmonyDatabase';
import { emptyHomeFeed, HomeFeed, KomgaBook, KomgaLibrary, KomgaLibraryCreateRequest, KomgaLibraryUpdateRequest,
  KomgaSeries, KomgaUser, normalizeHomeFeed, PageResult,
  ReadiumLocator } from '../../model/KomgaModels';
import { BookColorCorrectionSettings, normalizeBookColorCorrection } from '../../model/ColorCorrectionModels';
import { KomfAppriseRenderResult, KomfAppriseTemplates, KomfDiscordRenderResult, KomfDiscordTemplates,
  KomfIdentifyRequest, KomfJobEvent, KomfJobEventSubscription, KomfMetadataJob, KomfNotificationContext } from '../../model/KomfModels';
import { komfClient } from '../../network/KomfClient';
import { komgaClient, SeriesSearchOptions } from '../../network/KomgaClient';
import { AppPreferences } from '../../persistence/AppPreferences';
import { CredentialStore } from '../../persistence/CredentialStore';
import { AdminRepository, BookColorCorrectionRepository, CatalogRepository, DownloadScheduler, KomfRepository,
  OfflineLogRepository, OfflineRepository, OfflineSessionProfile, ProgressOutbox, SessionCredentials, SessionRepository,
  SessionSnapshot } from '../../domain/repository/Repositories';
import { cachedSeriesFilterSupported, filterCachedSeries } from './OfflineSeriesFilter';
import { progressSyncCoordinator, progressSyncKey } from '../../domain/service/ProgressSyncCoordinator';

const LOG_DOMAIN = 0x0000;
const LOG_TAG = 'KomeliaRepositories';

export class DefaultSessionRepository implements SessionRepository {
  async restore(): Promise<SessionSnapshot | undefined> {
    const serverUrl = await AppPreferences.getString('serverUrl');
    const credentialAlias = await AppPreferences.getString('activeCredentialAlias');
    if (credentialAlias.length === 0) return undefined;
    const credential = await CredentialStore.load(credentialAlias);
    if (serverUrl.length === 0 || credential === undefined) return undefined;
    const allowInsecureHttp = await AppPreferences.getBoolean('allowInsecureHttp');
    const lanServerUrl = await AppPreferences.getString('lanServerUrl');
    const lanAutoSwitchEnabled = await AppPreferences.getBoolean('lanAutoSwitchEnabled');
    const allowLanHttp = await AppPreferences.getBoolean('allowLanHttp');
    const candidates = networkRouteCandidates(serverUrl, allowInsecureHttp, lanServerUrl,
      allowLanHttp, lanAutoSwitchEnabled);
    for (let index = 0; index < candidates.length; index++) {
      const candidate: NetworkRouteCandidate = candidates[index];
      try {
        return await this.signInAtOrigin({ serverUrl: candidate.origin, email: credential.email,
          password: credential.password, allowInsecureHttp: candidate.allowInsecureHttp },
          false, serverUrl, candidate.usingLan);
      } catch (_) {
      }
    }
    komgaClient.clear();
    return this.restoreOffline(serverUrl);
  }

  async signIn(credentials: SessionCredentials, persist: boolean = true): Promise<SessionSnapshot> {
    return this.signInAtOrigin(credentials, persist, credentials.serverUrl, false);
  }

  private async signInAtOrigin(credentials: SessionCredentials, persist: boolean,
    stableServerUrl: string, usingLan: boolean): Promise<SessionSnapshot> {
    komgaClient.configure(credentials.serverUrl, credentials.email, credentials.password,
      credentials.allowInsecureHttp);
    const values = await Promise.all([komgaClient.login(), komgaClient.getLibraries()]);
    const normalizedStableUrl = stableServerUrl.trim().replace(/\/+$/, '');
    const namespace: NamespaceKey = { serverId: normalizedStableUrl, userId: values[0].id };
    const namespaceValue = `${namespace.serverId}:${namespace.userId}`;
    let credentialAlias = await this.findCredentialAlias(namespaceValue);
    if (persist) {
      if (credentialAlias.length === 0) credentialAlias = CredentialStore.createAlias();
      await Promise.all([
        AppPreferences.putString('serverUrl', normalizedStableUrl),
        AppPreferences.putString('activeNamespace', namespaceValue),
        AppPreferences.putBoolean('allowInsecureHttp', credentials.allowInsecureHttp),
        AppPreferences.putString('activeCredentialAlias', credentialAlias),
        CredentialStore.save(credentialAlias, { email: credentials.email, password: credentials.password })
      ]);
    }
    await HarmonyDatabase.replace('SERVER_PROFILE', {
      'ID': namespace.serverId,
      'ORIGIN': komgaClient.getServerUrl(),
      'DISPLAY_NAME': namespace.serverId,
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
    return { namespace: namespace, user: values[0], libraries: values[1], online: true,
      effectiveServerUrl: komgaClient.getServerUrl(), usingLan: usingLan };
  }

  async signOut(): Promise<void> {
    komgaClient.clear();
    const credentialAlias = await AppPreferences.getString('activeCredentialAlias');
    if (credentialAlias.length > 0) await CredentialStore.remove(credentialAlias);
    await AppPreferences.putString('activeCredentialAlias', '');
  }

  async offlineProfiles(): Promise<OfflineSessionProfile[]> {
    const result = await HarmonyDatabase.query(
      'SELECT NAMESPACE, PAYLOAD FROM USER_PROFILE ORDER BY UPDATED_AT DESC');
    const profiles: OfflineSessionProfile[] = [];
    try {
      if (result.goToFirstRow()) {
        do {
          const namespace = result.getString(0);
          const user = JSON.parse(result.getString(1)) as KomgaUser;
          const suffix = `:${user.id}`;
          if (!namespace.endsWith(suffix)) continue;
          profiles.push({ namespace: namespace,
            serverId: namespace.substring(0, namespace.length - suffix.length), user: user });
        } while (result.goToNextRow());
      }
      return profiles;
    } finally {
      result.close();
    }
  }

  async signInOffline(namespace: string): Promise<SessionSnapshot> {
    const profiles = await this.offlineProfiles();
    const profile = profiles.find((value: OfflineSessionProfile) => value.namespace === namespace);
    if (profile === undefined) throw new Error(localizedString($r('app.string.offline_profile_missing')));
    const libraries = await this.readCachedLibraries(namespace);
    await Promise.all([
      AppPreferences.putString('activeNamespace', namespace),
      AppPreferences.putString('serverUrl', profile.serverId)
    ]);
    komgaClient.clear();
    return {
      namespace: { serverId: profile.serverId, userId: profile.user.id },
      user: profile.user,
      libraries: libraries,
      online: false,
      effectiveServerUrl: profile.serverId,
      usingLan: false
    };
  }

  async deleteOfflineProfile(namespace: string): Promise<void> {
    const profile = (await this.offlineProfiles()).find(
      (value: OfflineSessionProfile) => value.namespace === namespace);
    if (profile === undefined) return;
    const aliasResult = await HarmonyDatabase.query(
      'SELECT CREDENTIAL_ALIAS FROM USER_PROFILE WHERE NAMESPACE = ? LIMIT 1', [namespace]);
    let credentialAlias = '';
    try {
      if (aliasResult.goToFirstRow()) credentialAlias = aliasResult.getString(0);
    } finally {
      aliasResult.close();
    }
    const jobsResult = await HarmonyDatabase.query(
      'SELECT JOB_ID FROM DOWNLOAD_JOB WHERE NAMESPACE = ?', [namespace]);
    const jobIds: string[] = [];
    try {
      if (jobsResult.goToFirstRow()) {
        do jobIds.push(jobsResult.getString(0)); while (jobsResult.goToNextRow());
      }
    } finally {
      jobsResult.close();
    }
    for (let index = 0; index < jobIds.length; index++) {
      await HarmonyDatabase.execute('DELETE FROM DOWNLOAD_PART WHERE NAMESPACE = ? AND JOB_ID = ?',
        [namespace, jobIds[index]]);
    }
    const tables = ['DOWNLOAD_JOB', 'OFFLINE_PAGE', 'OFFLINE_BOOK', 'PROGRESS_OUTBOX', 'OFFLINE_LOG',
      'BOOK_COLOR_CORRECTION',
      'APP_SETTING', 'HOME_GROUP', 'CATALOG_ENTITY', 'USER_PROFILE'];
    for (let index = 0; index < tables.length; index++) {
      await HarmonyDatabase.execute(`DELETE FROM ${tables[index]} WHERE NAMESPACE = ?`, [namespace]);
    }
    if (credentialAlias.length > 0) await CredentialStore.remove(credentialAlias);
    const remaining = (await this.offlineProfiles()).some(
      (value: OfflineSessionProfile) => value.serverId === profile.serverId);
    if (!remaining) {
      await HarmonyDatabase.execute('DELETE FROM SERVER_PROFILE WHERE ID = ?', [profile.serverId]);
    }
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
      libraries: libraries,
      online: false,
      effectiveServerUrl: serverUrl,
      usingLan: false
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

  async cachedHomeFeed(): Promise<HomeFeed | undefined> {
    const value = await this.readOne<HomeFeed>('homeFeed', 'current');
    return value === undefined ? undefined : normalizeHomeFeed(value);
  }

  async homeFeed(seed?: HomeFeed, onUpdate?: (value: HomeFeed) => void): Promise<HomeFeed> {
    const namespace = await this.activeNamespace();
    try {
      const value = normalizeHomeFeed(await komgaClient.getHomeFeed(seed, onUpdate));
      await this.cacheOne('homeFeed', 'current', value, namespace);
      return value;
    } catch (error) {
      hilog.warn(LOG_DOMAIN, LOG_TAG, `Home feed request failed; using cache: ${(error as Error).name}`);
      const value = await this.readOne<HomeFeed>('homeFeed', 'current', namespace);
      const cached = value === undefined ? emptyHomeFeed() : normalizeHomeFeed(value);
      if (onUpdate !== undefined) onUpdate(cached);
      return cached;
    }
  }

  async series(query: string, page: number, size: number,
    options?: SeriesSearchOptions): Promise<PageResult<KomgaSeries>> {
    try {
      const result = await komgaClient.searchSeries(query, page, size, options);
      await this.cacheMany('series', result.content, (item: KomgaSeries) => item.id);
      return result;
    } catch (_) {
      const cached = await this.readMany<KomgaSeries>('series');
      if (!cachedSeriesFilterSupported(options, cached)) {
        throw new Error(localizedString($r('app.string.offline_series_filter_requires_network')));
      }
      const values = filterCachedSeries(cached, query, options)
        .sort((first: KomgaSeries, second: KomgaSeries) => {
          const comparison = (first.metadata.titleSort || first.metadata.title)
            .localeCompare(second.metadata.titleSort || second.metadata.title);
          return options?.sort === 'metadata.titleSort,desc' ? -comparison : comparison;
        });
      return this.localPage(values, page, size);
    }
  }

  async books(query: string, page: number, size: number): Promise<PageResult<KomgaBook>> {
    try {
      const result = await komgaClient.searchBooks(query, page, size);
      await this.cacheMany('book', result.content, (item: KomgaBook) => item.id);
      result.content = await this.applyPendingProgressList(result.content);
      return result;
    } catch (_) {
      const values = (await this.applyPendingProgressList(await this.readMany<KomgaBook>('book')))
        .filter((item: KomgaBook) =>
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
      if (value === undefined) throw new Error(localizedString($r('app.string.offline_series_missing')));
      return value;
    }
  }

  async seriesBooks(id: string): Promise<KomgaBook[]> {
    try {
      const values = await komgaClient.getSeriesBooks(id);
      await this.cacheMany('book', values, (item: KomgaBook) => item.id);
      await this.cacheOne('seriesBooks', id, values);
      return this.applyPendingProgressList(values);
    } catch (_) {
      const values = await this.readOne<KomgaBook[]>('seriesBooks', id);
      return values === undefined ? [] : this.applyPendingProgressList(values);
    }
  }

  async bookDetail(id: string): Promise<KomgaBook> {
    try {
      const value = await komgaClient.getBook(id);
      await this.cacheOne('book', id, value);
      return this.applyPendingProgress(value);
    } catch (_) {
      const value = await this.readOne<KomgaBook>('book', id);
      if (value === undefined) throw new Error(localizedString($r('app.string.offline_book_missing')));
      return this.applyPendingProgress(value);
    }
  }

  private async applyPendingProgressList(values: KomgaBook[]): Promise<KomgaBook[]> {
    const resolved: KomgaBook[] = [];
    for (let index = 0; index < values.length; index++) {
      resolved.push(await this.applyPendingProgress(values[index]));
    }
    return resolved;
  }

  private async applyPendingProgress(value: KomgaBook): Promise<KomgaBook> {
    const namespace = await this.activeNamespace();
    if (namespace.length === 0) return value;
    const pending = await progressOutbox.pending(namespace, value.id);
    if (pending === undefined) return value;
    value.readProgress = pending.operation === ProgressOutboxOperation.DELETE ? undefined : {
      page: pending.page,
      completed: pending.completed,
      readDate: pending.readDate
    };
    return value;
  }

  private async activeNamespace(): Promise<string> {
    return AppPreferences.getString('activeNamespace');
  }

  private async cacheOne(type: string, id: string, value: Object, capturedNamespace?: string): Promise<void> {
    const namespace = capturedNamespace === undefined ? await this.activeNamespace() : capturedNamespace;
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

  private async readOne<T>(type: string, id: string, capturedNamespace?: string): Promise<T | undefined> {
    const result = await HarmonyDatabase.query(
      'SELECT PAYLOAD FROM CATALOG_ENTITY WHERE NAMESPACE = ? AND ENTITY_TYPE = ? AND ENTITY_ID = ?',
      [capturedNamespace === undefined ? await this.activeNamespace() : capturedNamespace, type, id]);
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
      'OPERATION': record.operation,
      'PAGE': record.page,
      'COMPLETED': record.completed ? 1 : 0,
      'READ_DATE': record.readDate,
      'ATTEMPTS': record.attempts,
      'LOCATOR': record.locatorJson || ''
    });
  }

  async pending(namespace: string, bookId: string): Promise<ProgressOutboxRecord | undefined> {
    const result = await HarmonyDatabase.query(
      'SELECT OPERATION, PAGE, COMPLETED, READ_DATE, ATTEMPTS, LOCATOR FROM PROGRESS_OUTBOX ' +
      'WHERE NAMESPACE = ? AND BOOK_ID = ? LIMIT 1', [namespace, bookId]);
    try {
      if (!result.goToFirstRow()) return undefined;
      return {
        namespace: namespace,
        bookId: bookId,
        operation: progressOutboxOperation(result.getString(result.getColumnIndex('OPERATION'))),
        page: result.getLong(result.getColumnIndex('PAGE')),
        completed: result.getLong(result.getColumnIndex('COMPLETED')) === 1,
        readDate: result.getString(result.getColumnIndex('READ_DATE')),
        attempts: result.getLong(result.getColumnIndex('ATTEMPTS')),
        locatorJson: result.getString(result.getColumnIndex('LOCATOR'))
      };
    } finally {
      result.close();
    }
  }

  async discard(namespace: string, bookId: string): Promise<void> {
    await HarmonyDatabase.execute('DELETE FROM PROGRESS_OUTBOX WHERE NAMESPACE = ? AND BOOK_ID = ?',
      [namespace, bookId]);
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
            operation: progressOutboxOperation(result.getString(result.getColumnIndex('OPERATION'))),
            page: result.getLong(result.getColumnIndex('PAGE')),
            completed: result.getLong(result.getColumnIndex('COMPLETED')) === 1,
            readDate: result.getString(result.getColumnIndex('READ_DATE')),
            attempts: result.getLong(result.getColumnIndex('ATTEMPTS')),
            locatorJson: result.getString(result.getColumnIndex('LOCATOR'))
          });
        } while (result.goToNextRow());
      }
    } finally {
      result.close();
    }
    let flushed = 0;
    for (let index = 0; index < pending.length; index++) {
      const item = pending[index];
      let failed = false;
      await progressSyncCoordinator.run(progressSyncKey(namespace, item.bookId), async (): Promise<void> => {
        const current = await this.pending(namespace, item.bookId);
        if (current === undefined || current.operation !== item.operation || current.readDate !== item.readDate) return;
        try {
          if (item.operation === ProgressOutboxOperation.DELETE) {
            await komgaClient.deleteReadProgress(item.bookId);
          } else if ((item.locatorJson || '').length > 0) {
            await komgaClient.updateReadiumProgression(item.bookId,
              JSON.parse(item.locatorJson || '{}') as ReadiumLocator);
          } else {
            await komgaClient.updateReadProgress(item.bookId, item.page, item.completed);
          }
          await this.discard(namespace, item.bookId);
          try {
            await offlineLogRepository.append(namespace, OfflineLogLevel.INFO,
              localizedString($r('app.string.offline_log_progress_synced')));
          } catch (_) {
          }
          flushed++;
        } catch (error) {
          failed = true;
          await HarmonyDatabase.execute(
            'UPDATE PROGRESS_OUTBOX SET ATTEMPTS = ATTEMPTS + 1 WHERE NAMESPACE = ? AND BOOK_ID = ?',
            [namespace, item.bookId]);
          try {
            await offlineLogRepository.append(namespace, OfflineLogLevel.ERROR,
              localizedString($r('app.string.offline_log_progress_sync_failed'), (error as Error).message));
          } catch (_) {
          }
        }
      });
      if (failed) break;
    }
    return flushed;
  }
}

export class RdbOfflineLogRepository implements OfflineLogRepository {
  async append(namespace: string, level: OfflineLogLevel, message: string): Promise<void> {
    await HarmonyDatabase.execute(
      'INSERT INTO OFFLINE_LOG (NAMESPACE, LEVEL, MESSAGE, TIMESTAMP) VALUES (?, ?, ?, ?)',
      [namespace, level, message, Date.now()]);
  }

  async page(namespace: string, level: OfflineLogLevel, page: number,
    size: number): Promise<OfflineLogPage> {
    const safePage = Math.max(0, page);
    const safeSize = Math.max(1, size);
    const countResult = await HarmonyDatabase.query(
      'SELECT COUNT(*) FROM OFFLINE_LOG WHERE NAMESPACE = ? AND LEVEL = ?', [namespace, level]);
    let totalElements = 0;
    try {
      if (countResult.goToFirstRow()) totalElements = countResult.getLong(0);
    } finally {
      countResult.close();
    }
    const result = await HarmonyDatabase.query(
      'SELECT ID, NAMESPACE, LEVEL, MESSAGE, TIMESTAMP FROM OFFLINE_LOG WHERE NAMESPACE = ? AND LEVEL = ? ORDER BY TIMESTAMP DESC LIMIT ? OFFSET ?',
      [namespace, level, safeSize, safePage * safeSize]);
    const content: OfflineLogRecord[] = [];
    try {
      if (result.goToFirstRow()) {
        do {
          content.push({
            id: result.getLong(0),
            namespace: result.getString(1),
            level: result.getString(2) as OfflineLogLevel,
            message: result.getString(3),
            timestamp: result.getLong(4)
          });
        } while (result.goToNextRow());
      }
    } finally {
      result.close();
    }
    return {
      content: content,
      page: safePage,
      totalPages: Math.ceil(totalElements / safeSize),
      totalElements: totalElements
    };
  }

  async deleteAll(namespace: string): Promise<void> {
    await HarmonyDatabase.execute('DELETE FROM OFFLINE_LOG WHERE NAMESPACE = ?', [namespace]);
  }
}

export class RdbDownloadScheduler implements DownloadScheduler {
  async updateBytes(jobId: string, title: string, completedBytes: number, totalBytes: number,
    bytesPerSecond: number): Promise<void> {
    await HarmonyDatabase.execute(
      'UPDATE DOWNLOAD_JOB SET TITLE = ?, COMPLETED_BYTES = ?, TOTAL_BYTES = ?, BYTES_PER_SECOND = ?, ' +
      'UPDATED_AT = ? WHERE JOB_ID = ? AND PHASE = ?',
      [title, completedBytes, totalBytes, bytesPerSecond, Date.now(), jobId, TaskPhase.RUNNING]);
  }
  async enqueue(namespace: string, bookId: string): Promise<string> {
    const existing = await HarmonyDatabase.query(
      'SELECT JOB_ID, PHASE FROM DOWNLOAD_JOB WHERE NAMESPACE = ? AND BOOK_ID = ? ORDER BY UPDATED_AT DESC LIMIT 1',
      [namespace, bookId]);
    try {
      if (existing.goToFirstRow()) {
        const jobId = existing.getString(existing.getColumnIndex('JOB_ID'));
        const phase = existing.getString(existing.getColumnIndex('PHASE')) as TaskPhase;
        if (phase === TaskPhase.FAILED || phase === TaskPhase.CANCELLED) {
          await HarmonyDatabase.execute(
            'UPDATE DOWNLOAD_JOB SET PHASE = ?, COMPLETED_PARTS = 0, TOTAL_PARTS = 0, ERROR_MESSAGE = ?, ' +
            'UPDATED_AT = ? WHERE NAMESPACE = ? AND JOB_ID = ?',
            [TaskPhase.QUEUED, '', Date.now(), namespace, jobId]);
        }
        return jobId;
      }
    } finally {
      existing.close();
    }
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
      'UPDATE DOWNLOAD_JOB SET PHASE = ?, TOTAL_PARTS = ?, ERROR_MESSAGE = ?, BYTES_PER_SECOND = 0, ' +
      'UPDATED_AT = ? WHERE JOB_ID = ? AND PHASE IN (?, ?, ?)',
      [TaskPhase.RUNNING, totalParts, '', Date.now(), jobId, TaskPhase.QUEUED, TaskPhase.RUNNING, TaskPhase.RETRYING]);
  }

  async update(jobId: string, completedParts: number): Promise<void> {
    await HarmonyDatabase.execute(
      'UPDATE DOWNLOAD_JOB SET COMPLETED_PARTS = ?, UPDATED_AT = ? WHERE JOB_ID = ? AND PHASE = ?',
      [completedParts, Date.now(), jobId, TaskPhase.RUNNING]);
  }

  async fail(jobId: string, message: string): Promise<void> {
    await HarmonyDatabase.execute(
      'UPDATE DOWNLOAD_JOB SET PHASE = ?, ATTEMPTS = ATTEMPTS + 1, ERROR_MESSAGE = ?, BYTES_PER_SECOND = 0, UPDATED_AT = ? ' +
      'WHERE JOB_ID = ? AND PHASE IN (?, ?, ?)',
      [TaskPhase.FAILED, message, Date.now(), jobId, TaskPhase.RUNNING, TaskPhase.QUEUED, TaskPhase.RETRYING]);
  }

  async complete(jobId: string): Promise<void> {
    await HarmonyDatabase.execute(
      'UPDATE DOWNLOAD_JOB SET PHASE = ?, COMPLETED_PARTS = TOTAL_PARTS, BYTES_PER_SECOND = 0, UPDATED_AT = ? ' +
      'WHERE JOB_ID = ? AND PHASE = ?', [TaskPhase.COMPLETED, Date.now(), jobId, TaskPhase.RUNNING]);
  }

  resume(jobId: string): Promise<void> {
    return this.setPhase(jobId, TaskPhase.QUEUED);
  }

  cancel(jobId: string): Promise<void> {
    return this.setPhase(jobId, TaskPhase.CANCELLED);
  }

  async remove(jobId: string): Promise<void> {
    await HarmonyDatabase.execute('DELETE FROM DOWNLOAD_PART WHERE JOB_ID = ?', [jobId]);
    await HarmonyDatabase.execute('DELETE FROM DOWNLOAD_JOB WHERE JOB_ID = ?', [jobId]);
  }

  async jobs(namespace: string): Promise<DownloadJobRecord[]> {
    const result = await HarmonyDatabase.query(
      'SELECT * FROM DOWNLOAD_JOB WHERE NAMESPACE = ? ORDER BY UPDATED_AT DESC', [namespace]);
    const values: DownloadJobRecord[] = [];
    try {
      if (result.goToFirstRow()) {
        do {
          values.push({
            title: result.getString(result.getColumnIndex('TITLE')),
            completedBytes: result.getLong(result.getColumnIndex('COMPLETED_BYTES')),
            totalBytes: result.getLong(result.getColumnIndex('TOTAL_BYTES')),
            bytesPerSecond: result.getLong(result.getColumnIndex('BYTES_PER_SECOND')),
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
    await HarmonyDatabase.execute('UPDATE DOWNLOAD_JOB SET PHASE = ?, BYTES_PER_SECOND = 0, UPDATED_AT = ? WHERE JOB_ID = ?',
      [phase, Date.now(), jobId]);
  }
}

export class RdbBookColorCorrectionRepository implements BookColorCorrectionRepository {
  async get(namespace: string, bookId: string): Promise<BookColorCorrectionSettings | undefined> {
    const result = await HarmonyDatabase.query(
      'SELECT PAYLOAD FROM BOOK_COLOR_CORRECTION WHERE NAMESPACE = ? AND BOOK_ID = ? LIMIT 1',
      [namespace, bookId]);
    try {
      if (!result.goToFirstRow()) return undefined;
      return normalizeBookColorCorrection(JSON.parse(result.getString(0)) as BookColorCorrectionSettings);
    } catch (_) {
      return undefined;
    } finally {
      result.close();
    }
  }

  async save(namespace: string, bookId: string, value: BookColorCorrectionSettings): Promise<void> {
    const normalized = normalizeBookColorCorrection(value);
    await HarmonyDatabase.replace('BOOK_COLOR_CORRECTION', {
      'NAMESPACE': namespace,
      'BOOK_ID': bookId,
      'PAYLOAD': JSON.stringify(normalized),
      'UPDATED_AT': Date.now()
    });
  }

  async delete(namespace: string, bookId: string): Promise<void> {
    await HarmonyDatabase.execute(
      'DELETE FROM BOOK_COLOR_CORRECTION WHERE NAMESPACE = ? AND BOOK_ID = ?', [namespace, bookId]);
  }
}

export class DefaultAdminRepository implements AdminRepository {
  addUser(email: string, password: string, roles: string[]): Promise<KomgaUser> {
    return komgaClient.addUser({ email: email, password: password, roles: roles });
  }

  updateUserRoles(userId: string, roles: string[]): Promise<void> {
    return komgaClient.updateUser(userId, { roles: roles });
  }

  updateUserPassword(userId: string, password: string): Promise<void> {
    return komgaClient.updateUserPassword(userId, password);
  }

  deleteUser(userId: string): Promise<void> {
    return komgaClient.deleteUser(userId);
  }

  addLibrary(request: KomgaLibraryCreateRequest): Promise<KomgaLibrary> {
    return komgaClient.addLibrary(request);
  }

  updateLibrary(libraryId: string, request: KomgaLibraryUpdateRequest): Promise<void> {
    return komgaClient.updateLibrary(libraryId, request);
  }

  deleteLibrary(libraryId: string): Promise<void> {
    return komgaClient.deleteLibrary(libraryId);
  }

  scanLibrary(libraryId: string, deep: boolean): Promise<void> {
    return komgaClient.runLibraryAction(libraryId, 'scan', deep);
  }

  analyzeLibrary(libraryId: string): Promise<void> {
    return komgaClient.runLibraryAction(libraryId, 'analyze');
  }

  emptyLibraryTrash(libraryId: string): Promise<void> {
    return komgaClient.runLibraryAction(libraryId, 'empty-trash');
  }

  emptyTaskQueue(): Promise<number> {
    return komgaClient.emptyTaskQueue();
  }
}

export class DefaultKomfRepository implements KomfRepository {
  configure(origin: string, allowInsecureHttp: boolean = false): void {
    komfClient.configure(origin, allowInsecureHttp);
  }

  ping(): Promise<boolean> {
    return komfClient.ping();
  }

  config(): Promise<Object> { return komfClient.getConfig(); }

  updateConfig(value: Object): Promise<void> { return komfClient.updateConfig(value); }

  providers(): Promise<string[]> { return komfClient.getProviders(); }

  mediaServerStatus(mediaServer: string = 'komga') { return komfClient.checkMediaServer(mediaServer); }

  mediaServerLibraries(mediaServer: string = 'komga') {
    return komfClient.getMediaServerLibraries(mediaServer);
  }

  jobs(page: number = 1, size: number = 50, status: string = '') {
    return komfClient.getJobs(page, size, status);
  }

  job(jobId: string): Promise<KomfMetadataJob> { return komfClient.getJob(jobId); }

  observeJobEvents(jobId: string, onEvent: (event: KomfJobEvent) => void,
    onComplete: () => void, onError: (error: Error) => void): KomfJobEventSubscription {
    return komfClient.observeJobEvents(jobId, onEvent, onComplete, onError);
  }

  deleteAllJobs(): Promise<void> { return komfClient.deleteAllJobs(); }

  identifySeries(libraryId: string, seriesId: string) {
    return komfClient.identifySeries('komga', libraryId, seriesId);
  }

  searchSeries(name: string, libraryId: string, seriesId: string) {
    return komfClient.searchSeries('komga', name, libraryId, seriesId);
  }

  manuallyIdentifySeries(request: KomfIdentifyRequest) {
    return komfClient.manuallyIdentifySeries('komga', request);
  }

  identifyLibrary(libraryId: string) {
    return komfClient.identifyLibrary('komga', libraryId);
  }

  resetSeries(libraryId: string, seriesId: string, removeComicInfo: boolean): Promise<void> {
    return komfClient.resetSeries('komga', libraryId, seriesId, removeComicInfo);
  }

  resetLibrary(libraryId: string, removeComicInfo: boolean): Promise<void> {
    return komfClient.resetLibrary('komga', libraryId, removeComicInfo);
  }

  discordTemplates(): Promise<KomfDiscordTemplates> { return komfClient.getDiscordTemplates(); }

  updateDiscordTemplates(value: KomfDiscordTemplates): Promise<void> {
    return komfClient.updateDiscordTemplates(value);
  }

  renderDiscord(context: KomfNotificationContext,
    templates: KomfDiscordTemplates): Promise<KomfDiscordRenderResult> {
    return komfClient.renderDiscord(context, templates);
  }

  sendDiscord(context: KomfNotificationContext, templates: KomfDiscordTemplates): Promise<void> {
    return komfClient.sendDiscord(context, templates);
  }

  appriseTemplates(): Promise<KomfAppriseTemplates> { return komfClient.getAppriseTemplates(); }

  updateAppriseTemplates(value: KomfAppriseTemplates): Promise<void> {
    return komfClient.updateAppriseTemplates(value);
  }

  renderApprise(context: KomfNotificationContext,
    templates: KomfAppriseTemplates): Promise<KomfAppriseRenderResult> {
    return komfClient.renderApprise(context, templates);
  }

  sendApprise(context: KomfNotificationContext, templates: KomfAppriseTemplates): Promise<void> {
    return komfClient.sendApprise(context, templates);
  }

  updateDiscordWebhook(index: number, value?: string): Promise<void> {
    return komfClient.updateDiscordWebhook(index, value);
  }

  updateDiscordSeriesCover(value: boolean): Promise<void> {
    return komfClient.updateDiscordSeriesCover(value);
  }

  updateAppriseUrl(index: number, value?: string): Promise<void> {
    return komfClient.updateAppriseUrl(index, value);
  }

  updateAppriseSeriesCover(value: boolean): Promise<void> {
    return komfClient.updateAppriseSeriesCover(value);
  }
}

export const sessionRepository = new DefaultSessionRepository();
export const catalogRepository = new DefaultCatalogRepository();
export const offlineRepository = new RdbOfflineRepository();
export const downloadScheduler = new RdbDownloadScheduler();
export const progressOutbox = new RdbProgressOutbox();
export const offlineLogRepository = new RdbOfflineLogRepository();
export const bookColorCorrectionRepository = new RdbBookColorCorrectionRepository();
export const adminRepository = new DefaultAdminRepository();
export const komfRepository = new DefaultKomfRepository();
