import { HomeFeed, KomgaLibrary, KomgaUser } from '../model/KomgaModels';
import { AppContainer } from '../core/di/AppContainer';
import { NamespaceKey } from '../data/local/DatabaseModels';
import { catalogRepository, progressOutbox, sessionRepository } from '../data/repository/DefaultRepositories';
import { komgaClient } from '../network/KomgaClient';
import { AppPreferences } from '../persistence/AppPreferences';

export enum AppDestination {
  LIBRARY = 0,
  HOME = 1,
  SEARCH = 2,
  SETTINGS = 3
}

@Observed
export class AppState {
  private static readonly shared = new AppState();

  isBootstrapping: boolean = true;
  isAuthenticated: boolean = false;
  isLoading: boolean = false;
  errorMessage: string = '';
  destination: AppDestination = AppDestination.HOME;
  user?: KomgaUser;
  namespace?: NamespaceKey;
  libraries: KomgaLibrary[] = [];
  homeFeed: HomeFeed = { onDeck: [], latest: [], series: [] };
  reducedMotion: boolean = false;

  static getInstance(): AppState {
    return AppState.shared;
  }

  async bootstrap(): Promise<void> {
    this.isBootstrapping = true;
    try {
      await AppContainer.ready();
      this.reducedMotion = await AppPreferences.getBoolean('reducedMotion');
      const snapshot = await sessionRepository.restore();
      if (snapshot !== undefined) {
        this.namespace = snapshot.namespace;
        this.user = snapshot.user;
        this.libraries = snapshot.libraries;
        this.isAuthenticated = true;
        await this.refreshHome();
        progressOutbox.flush(`${snapshot.namespace.serverId}:${snapshot.namespace.userId}`);
      }
    } catch (error) {
      this.errorMessage = `自动登录失败：${(error as Error).message}`;
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
      this.user = snapshot.user;
      this.libraries = snapshot.libraries;
      this.isAuthenticated = true;
      await this.refreshHome();
    } catch (error) {
      komgaClient.clear();
      this.isAuthenticated = false;
      this.errorMessage = (error as Error).message;
      throw error;
    } finally {
      this.isLoading = false;
    }
  }

  async refreshHome(): Promise<void> {
    this.homeFeed = await catalogRepository.homeFeed();
  }

  async signOut(): Promise<void> {
    await sessionRepository.signOut();
    this.user = undefined;
    this.namespace = undefined;
    this.libraries = [];
    this.homeFeed = { onDeck: [], latest: [], series: [] };
    this.isAuthenticated = false;
    this.destination = AppDestination.HOME;
  }
}
