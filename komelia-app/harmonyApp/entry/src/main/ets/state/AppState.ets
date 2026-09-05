import { emptyHomeFeed, HomeFeed, KomgaLibrary, KomgaUser } from '../model/KomgaModels';
import { AppContainer } from '../core/di/AppContainer';
import { localizedString } from '../core/i18n/LocalizedResources';
import { NamespaceKey } from '../data/local/DatabaseModels';
import { catalogRepository, progressOutbox, sessionRepository } from '../data/repository/DefaultRepositories';
import { downloadRecoveryService } from '../domain/service/DownloadRecoveryService';
import { OfflineSessionProfile } from '../domain/repository/Repositories';
import { komgaClient } from '../network/KomgaClient';
import { AppPreferences } from '../persistence/AppPreferences';
import { copyLibraryNavigationRequest, LibraryNavigationRequest } from '../model/LibraryPresentation';
import { AvailableBook, LOCAL_NAMESPACE } from '../model/LocalContentModels';
import { mergeHomeWithAvailable } from '../model/UnifiedContentModels';
import { availableContentRepository } from '../data/repository/AvailableContentRepository';
import { localLibraryService } from '../domain/service/LocalLibraryService';

export enum AppDestination {
  LIBRARY = 0,
  HOME = 1,
  SEARCH = 2,
  SETTINGS = 3,
  LOCAL = 4
}

@Observed
export class AppState {
  private static readonly shared = new AppState();

  isBootstrapping: boolean = true;
  isAuthenticated: boolean = false;
  isLocalMode: boolean = false;
  isOnline: boolean = false;
  effectiveServerUrl: string = '';
  usingLan: boolean = false;
  offlineProfiles: OfflineSessionProfile[] = [];
  isLoading: boolean = false;
  errorMessage: string = '';
  destination: AppDestination = AppDestination.HOME;
  user?: KomgaUser;
  namespace?: NamespaceKey;
  libraries: KomgaLibrary[] = [];
  homeFeed: HomeFeed = emptyHomeFeed();
  availableBooks: AvailableBook[] = [];
  homeLoading: boolean = false;
  homeErrorMessage: string = '';
  reducedMotion: boolean = false;
  private pendingLibraryNavigation?: LibraryNavigationRequest;
  private homeLoadGeneration: number = 0;
  private availableLoadGeneration: number = 0;
  private remoteHomeFeed: HomeFeed = emptyHomeFeed();
  private localObserverStarted: boolean = false;

  static getInstance(): AppState {
    return AppState.shared;
  }

  setDestination(destination: AppDestination): void {
    this.destination = destination;
    AppStorage.setOrCreate('appDestination', destination);
    AppPreferences.putNumber('app.destination', destination).catch(() => {});
  }

  openLibrary(request: LibraryNavigationRequest): void {
    this.pendingLibraryNavigation = copyLibraryNavigationRequest(request);
    this.setDestination(AppDestination.LIBRARY);
  }

  consumeLibraryNavigation(): LibraryNavigationRequest | undefined {
    const request = this.pendingLibraryNavigation;
    this.pendingLibraryNavigation = undefined;
    return request === undefined ? undefined : copyLibraryNavigationRequest(request);
  }

  isAdministrator(): boolean {
    return (this.user?.roles || []).includes('ADMIN');
  }

  canManageServer(): boolean {
    return this.isOnline && this.isAdministrator();
  }

  async bootstrap(): Promise<void> {
    this.isBootstrapping = true;
    try {
      await AppContainer.ready();
      this.reducedMotion = await AppPreferences.getBoolean('reducedMotion');
      if (!this.localObserverStarted) {
        this.localObserverStarted = true;
        localLibraryService.observe(() => this.refreshAvailableContent().catch(() => {}));
      }
      const savedDestination = await AppPreferences.getNumber('app.destination', AppDestination.HOME);
      if (await AppPreferences.getBoolean('localMode')) {
        await this.useLocalMode();
        this.restoreDestination(savedDestination);
        localLibraryService.scanAll(false, true).catch(() => {});
        return;
      }
      const snapshot = await sessionRepository.restore();
      if (snapshot !== undefined) {
        this.namespace = snapshot.namespace;
        await AppPreferences.putString('activeNamespace', `${snapshot.namespace.serverId}:${snapshot.namespace.userId}`);
        this.user = snapshot.user;
        this.libraries = snapshot.libraries;
        this.homeFeed = await this.restoreCachedHomeFeed();
        this.isOnline = snapshot.online;
        this.effectiveServerUrl = snapshot.effectiveServerUrl;
        this.usingLan = snapshot.usingLan;
        this.isAuthenticated = true;
        this.restoreDestination(savedDestination);
        if (snapshot.online) {
          this.refreshHome();
          const namespace = `${snapshot.namespace.serverId}:${snapshot.namespace.userId}`;
          progressOutbox.flush(namespace).catch(() => {});
          downloadRecoveryService.recover(namespace);
        }
      }
      if (!this.isAuthenticated) this.offlineProfiles = await sessionRepository.offlineProfiles();
      localLibraryService.scanAll(false, true).catch(() => {});
    } catch (error) {
      this.errorMessage = localizedString($r('app.string.auto_login_failed'), (error as Error).message);
    } finally {
      this.isBootstrapping = false;
    }
  }

  async signIn(server: string, email: string, password: string, allowHttp: boolean,
    persist: boolean = true): Promise<void> {
    this.isLoading = true;
    this.errorMessage = '';
    try {
      const snapshot = await sessionRepository.signIn({
        serverUrl: server,
        email: email,
        password: password,
        allowInsecureHttp: allowHttp
      }, persist);
      this.namespace = snapshot.namespace;
      this.isLocalMode = false;
      await AppPreferences.putBoolean('localMode', false);
      await AppPreferences.putString('activeNamespace', `${snapshot.namespace.serverId}:${snapshot.namespace.userId}`);
      this.user = snapshot.user;
      this.libraries = snapshot.libraries;
      this.homeFeed = await this.restoreCachedHomeFeed();
      this.isOnline = true;
      this.effectiveServerUrl = snapshot.effectiveServerUrl;
      this.usingLan = snapshot.usingLan;
      this.setDestination(AppDestination.HOME);
      this.isAuthenticated = true;
      this.refreshHome();
      const namespace = `${snapshot.namespace.serverId}:${snapshot.namespace.userId}`;
      progressOutbox.flush(namespace).catch(() => {});
      downloadRecoveryService.recover(namespace);
    } catch (error) {
      komgaClient.clear();
      this.namespace = undefined;
      this.user = undefined;
      this.libraries = [];
      this.homeFeed = emptyHomeFeed();
      this.isAuthenticated = false;
      this.isOnline = false;
      this.effectiveServerUrl = '';
      this.usingLan = false;
      this.errorMessage = (error as Error).message;
      throw new Error((error as Error).message);
    } finally {
      this.isLoading = false;
    }
  }

  async refreshHome(): Promise<void> {
    const generation = ++this.homeLoadGeneration;
    this.homeLoading = true;
    this.homeErrorMessage = '';
    try {
      await this.refreshAvailableContent();
      if (this.isLocalMode) return;
      const homeFeed = await catalogRepository.homeFeed(this.remoteHomeFeed, (partial: HomeFeed): void => {
        if (generation === this.homeLoadGeneration && this.isAuthenticated) {
          this.remoteHomeFeed = partial;
          this.homeFeed = mergeHomeWithAvailable(partial, this.availableBooks);
        }
      });
      if (generation === this.homeLoadGeneration && this.isAuthenticated) {
        this.remoteHomeFeed = homeFeed;
        this.homeFeed = mergeHomeWithAvailable(homeFeed, this.availableBooks);
      }
    } catch (error) {
      if (generation === this.homeLoadGeneration) this.homeErrorMessage = (error as Error).message;
    } finally {
      if (generation === this.homeLoadGeneration) this.homeLoading = false;
    }
  }

  private async restoreCachedHomeFeed(): Promise<HomeFeed> {
    try {
      this.remoteHomeFeed = (await catalogRepository.cachedHomeFeed()) ?? emptyHomeFeed();
      const key = this.namespace;
      this.availableBooks = await availableContentRepository.books(key === undefined ? '' : `${key.serverId}:${key.userId}`);
      return mergeHomeWithAvailable(this.remoteHomeFeed, this.availableBooks);
    } catch (_) {
      return emptyHomeFeed();
    }
  }

  async reconnectConfiguredNetwork(): Promise<boolean> {
    this.isLoading = true;
    try {
      const snapshot = await sessionRepository.restore();
      if (snapshot === undefined) return false;
      this.namespace = snapshot.namespace;
      this.isLocalMode = false;
      await AppPreferences.putBoolean('localMode', false);
      await AppPreferences.putString('activeNamespace', `${snapshot.namespace.serverId}:${snapshot.namespace.userId}`);
      this.user = snapshot.user;
      this.libraries = snapshot.libraries;
      this.homeFeed = await this.restoreCachedHomeFeed();
      this.isOnline = snapshot.online;
      this.effectiveServerUrl = snapshot.effectiveServerUrl;
      this.usingLan = snapshot.usingLan;
      if (snapshot.online) this.refreshHome();
      return snapshot.online;
    } finally {
      this.isLoading = false;
    }
  }

  async loadOfflineProfiles(): Promise<void> {
    this.offlineProfiles = await sessionRepository.offlineProfiles();
  }

  async signInOffline(namespace: string): Promise<void> {
    this.isLoading = true;
    this.errorMessage = '';
    try {
      const snapshot = await sessionRepository.signInOffline(namespace);
      this.namespace = snapshot.namespace;
      this.isLocalMode = false;
      await AppPreferences.putBoolean('localMode', false);
      this.user = snapshot.user;
      this.libraries = snapshot.libraries;
      this.isOnline = false;
      this.effectiveServerUrl = snapshot.effectiveServerUrl;
      this.usingLan = false;
      this.setDestination(AppDestination.HOME);
      this.homeFeed = await this.restoreCachedHomeFeed();
      this.isAuthenticated = true;
    } catch (error) {
      this.errorMessage = (error as Error).message;
      throw new Error((error as Error).message);
    } finally {
      this.isLoading = false;
    }
  }

  async signOut(): Promise<void> {
    if (!this.isLocalMode) await sessionRepository.signOut();
    else komgaClient.clear();
    await AppPreferences.putBoolean('localMode', false);
    this.isLocalMode = false;
    this.user = undefined;
    this.namespace = undefined;
    this.libraries = [];
    this.homeFeed = emptyHomeFeed();
    this.remoteHomeFeed = emptyHomeFeed();
    this.availableBooks = [];
    this.availableLoadGeneration++;
    this.homeLoadGeneration += 1;
    this.homeLoading = false;
    this.homeErrorMessage = '';
    this.isAuthenticated = false;
    this.isOnline = false;
    this.effectiveServerUrl = '';
    this.usingLan = false;
    this.pendingLibraryNavigation = undefined;
    this.setDestination(AppDestination.HOME);
    this.offlineProfiles = await sessionRepository.offlineProfiles();
  }

  async useLocalMode(): Promise<void> {
    await AppContainer.ready();
    this.homeLoadGeneration++;
    this.availableLoadGeneration++;
    komgaClient.clear();
    await AppPreferences.putBoolean('localMode', true);
    await AppPreferences.putString('activeNamespace', LOCAL_NAMESPACE);
    this.namespace = { serverId: 'local-device', userId: 'root' };
    this.user = undefined;
    this.libraries = [];
    this.isOnline = false;
    this.isLocalMode = true;
    this.isAuthenticated = true;
    this.effectiveServerUrl = '';
    this.usingLan = false;
    this.remoteHomeFeed = emptyHomeFeed();
    this.homeErrorMessage = '';
    this.homeLoading = false;
    this.setDestination(AppDestination.LOCAL);
    await this.refreshAvailableContent();
  }

  private restoreDestination(value: number): void {
    const valid = Number.isInteger(value) && value >= AppDestination.LIBRARY && value <= AppDestination.LOCAL;
    this.setDestination(this.isLocalMode && value === AppDestination.LIBRARY ? AppDestination.LOCAL :
      valid ? value as AppDestination : AppDestination.HOME);
  }

  async refreshAvailableContent(): Promise<void> {
    const generation = ++this.availableLoadGeneration;
    const key = this.namespace;
    const namespace = key === undefined ? '' : `${key.serverId}:${key.userId}`;
    const available = await availableContentRepository.books(namespace);
    if (generation !== this.availableLoadGeneration || !this.isAuthenticated) return;
    this.availableBooks = available;
    this.homeFeed = mergeHomeWithAvailable(this.remoteHomeFeed, available);
    AppStorage.setOrCreate('localContentRevision', Date.now());
  }
}
