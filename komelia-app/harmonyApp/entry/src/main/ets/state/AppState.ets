import { HomeFeed, KomgaLibrary, KomgaUser } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';
import { AppPreferences } from '../persistence/AppPreferences';
import { CredentialStore } from '../persistence/CredentialStore';

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
  libraries: KomgaLibrary[] = [];
  homeFeed: HomeFeed = { onDeck: [], latest: [], series: [] };

  static getInstance(): AppState {
    return AppState.shared;
  }

  async bootstrap(): Promise<void> {
    this.isBootstrapping = true;
    try {
      const server = await AppPreferences.getString('serverUrl');
      const allowHttp = await AppPreferences.getBoolean('allowInsecureHttp');
      const credential = await CredentialStore.load();
      if (server.length > 0 && credential !== undefined) {
        await this.signIn(server, credential.email, credential.password, allowHttp, false);
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
      komgaClient.configure(server, email, password, allowHttp);
      const values = await Promise.all([komgaClient.login(), komgaClient.getLibraries()]);
      this.user = values[0];
      this.libraries = values[1];
      this.isAuthenticated = true;
      if (persist) {
        await Promise.all([
          AppPreferences.putString('serverUrl', komgaClient.getServerUrl()),
          AppPreferences.putBoolean('allowInsecureHttp', allowHttp),
          CredentialStore.save({ email: email, password: password })
        ]);
      }
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
    this.homeFeed = await komgaClient.getHomeFeed();
  }

  async signOut(): Promise<void> {
    komgaClient.clear();
    await CredentialStore.remove();
    this.user = undefined;
    this.libraries = [];
    this.homeFeed = { onDeck: [], latest: [], series: [] };
    this.isAuthenticated = false;
    this.destination = AppDestination.HOME;
  }
}
