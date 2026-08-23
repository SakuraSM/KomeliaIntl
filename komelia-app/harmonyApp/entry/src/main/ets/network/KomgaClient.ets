import { http } from '@kit.NetworkKit';
import { util } from '@kit.ArkTS';
import {
  HomeFeed,
  CollectionUpdateRequest,
  KomgaAuthenticationActivity,
  KomgaBook,
  KomgaBookPage,
  KomgaCollection,
  KomgaLibrary,
  KomgaLibraryCreateRequest,
  KomgaLibraryUpdateRequest,
  KomgaReadList,
  KomgaSeries,
  KomgaUser,
  KomgaUserCreateRequest,
  KomgaUserUpdateRequest,
  BookMetadataUpdateRequest,
  PageResult,
  ReadListUpdateRequest,
  ReadiumLocator,
  SeriesMetadataUpdateRequest
} from '../model/KomgaModels';

export class KomgaHttpError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'KomgaHttpError';
    this.status = status;
  }
}

interface SearchRequest {
  fullTextSearch?: string;
}

interface ReadProgressUpdate {
  page: number;
  completed: boolean;
}

export class KomgaClient {
  private serverUrl: string = '';
  private authorization: string = '';

  configure(serverUrl: string, email: string, password: string, allowInsecureHttp: boolean = false): void {
    const normalized = serverUrl.trim().replace(/\/+$/, '');
    if (!normalized.startsWith('https://') && !normalized.startsWith('http://')) {
      throw new Error('服务器地址必须以 https:// 或 http:// 开头');
    }
    if (normalized.startsWith('http://') && !allowInsecureHttp) {
      throw new Error('HTTP 连接未加密，请确认局域网风险后重试');
    }
    const encoded = new util.TextEncoder().encodeInto(`${email}:${password}`);
    this.serverUrl = normalized;
    this.authorization = `Basic ${new util.Base64Helper().encodeToStringSync(encoded)}`;
  }

  clear(): void {
    this.serverUrl = '';
    this.authorization = '';
  }

  isConfigured(): boolean {
    return this.serverUrl.length > 0 && this.authorization.length > 0;
  }

  getServerUrl(): string {
    return this.serverUrl;
  }

  async login(): Promise<KomgaUser> {
    return this.getJson<KomgaUser>('/api/v2/users/me');
  }

  async getLibraries(): Promise<KomgaLibrary[]> {
    return this.getJson<KomgaLibrary[]>('/api/v1/libraries');
  }

  async addLibrary(request: KomgaLibraryCreateRequest): Promise<KomgaLibrary> {
    return this.postJson<KomgaLibrary>('/api/v1/libraries', request);
  }

  async updateLibrary(id: string, request: KomgaLibraryUpdateRequest): Promise<void> {
    await this.sendJson(`/api/v1/libraries/${encodeURIComponent(id)}`, http.RequestMethod.PATCH, request);
  }

  async deleteLibrary(id: string): Promise<void> {
    await this.request(`/api/v1/libraries/${encodeURIComponent(id)}`, http.RequestMethod.DELETE,
      http.HttpDataType.STRING);
  }

  async getCollections(query: string = '', page: number = 0, size: number = 100): Promise<PageResult<KomgaCollection>> {
    const search = query.trim().length > 0 ? `&search=${encodeURIComponent(query.trim())}` : '';
    return this.getJson<PageResult<KomgaCollection>>(
      `/api/v1/collections?page=${page}&size=${size}&sort=name,asc${search}`);
  }

  async getReadLists(query: string = '', page: number = 0, size: number = 100): Promise<PageResult<KomgaReadList>> {
    const search = query.trim().length > 0 ? `&search=${encodeURIComponent(query.trim())}` : '';
    return this.getJson<PageResult<KomgaReadList>>(
      `/api/v1/readlists?page=${page}&size=${size}&sort=name,asc${search}`);
  }

  async getCollectionSeries(id: string, page: number = 0, size: number = 100): Promise<PageResult<KomgaSeries>> {
    return this.getJson<PageResult<KomgaSeries>>(
      `/api/v1/collections/${encodeURIComponent(id)}/series?page=${page}&size=${size}`);
  }

  async getReadListBooks(id: string, page: number = 0, size: number = 100): Promise<PageResult<KomgaBook>> {
    return this.getJson<PageResult<KomgaBook>>(
      `/api/v1/readlists/${encodeURIComponent(id)}/books?page=${page}&size=${size}`);
  }

  async updateCollection(id: string, request: CollectionUpdateRequest): Promise<void> {
    await this.sendJson(`/api/v1/collections/${encodeURIComponent(id)}`, http.RequestMethod.PATCH, request);
  }

  async updateReadList(id: string, request: ReadListUpdateRequest): Promise<void> {
    await this.sendJson(`/api/v1/readlists/${encodeURIComponent(id)}`, http.RequestMethod.PATCH, request);
  }

  async deleteCollection(id: string): Promise<void> {
    await this.request(`/api/v1/collections/${encodeURIComponent(id)}`, http.RequestMethod.DELETE,
      http.HttpDataType.STRING);
  }

  async deleteReadList(id: string): Promise<void> {
    await this.request(`/api/v1/readlists/${encodeURIComponent(id)}`, http.RequestMethod.DELETE,
      http.HttpDataType.STRING);
  }

  async getHomeFeed(): Promise<HomeFeed> {
    const values = await Promise.all([
      this.getJson<PageResult<KomgaBook>>('/api/v1/books/ondeck?page=0&size=12&sort=readProgress.readDate,desc'),
      this.getJson<PageResult<KomgaBook>>('/api/v1/books/latest?page=0&size=12&sort=created,desc'),
      this.searchSeries('', 0, 12)
    ]);
    return {
      onDeck: values[0].content,
      latest: values[1].content,
      series: values[2].content
    };
  }

  async searchSeries(query: string, page: number = 0, size: number = 30): Promise<PageResult<KomgaSeries>> {
    const body: SearchRequest = query.trim().length > 0 ? { fullTextSearch: query.trim() } : {};
    return this.postJson<PageResult<KomgaSeries>>(
      `/api/v1/series/list?page=${page}&size=${size}&sort=metadata.titleSort,asc`, body);
  }

  async searchBooks(query: string, page: number = 0, size: number = 30): Promise<PageResult<KomgaBook>> {
    const body: SearchRequest = query.trim().length > 0 ? { fullTextSearch: query.trim() } : {};
    return this.postJson<PageResult<KomgaBook>>(
      `/api/v1/books/list?page=${page}&size=${size}&sort=metadata.title,asc`, body);
  }

  async getSeries(id: string): Promise<KomgaSeries> {
    return this.getJson<KomgaSeries>(`/api/v1/series/${encodeURIComponent(id)}`);
  }

  async getSeriesBooks(id: string): Promise<KomgaBook[]> {
    const page = await this.getJson<PageResult<KomgaBook>>(
      `/api/v1/series/${encodeURIComponent(id)}/books?page=0&size=500&sort=metadata.numberSort,asc`);
    return page.content;
  }

  async getBook(id: string): Promise<KomgaBook> {
    return this.getJson<KomgaBook>(`/api/v1/books/${encodeURIComponent(id)}`);
  }

  async getBookPages(id: string): Promise<KomgaBookPage[]> {
    return this.getJson<KomgaBookPage[]>(`/api/v1/books/${encodeURIComponent(id)}/pages`);
  }

  async getSeriesThumbnail(id: string): Promise<ArrayBuffer> {
    return this.getBytes(`/api/v1/series/${encodeURIComponent(id)}/thumbnail`);
  }

  async getBookThumbnail(id: string): Promise<ArrayBuffer> {
    return this.getBytes(`/api/v1/books/${encodeURIComponent(id)}/thumbnail`);
  }

  async getBookPage(id: string, page: number): Promise<ArrayBuffer> {
    return this.getBytes(`/api/v1/books/${encodeURIComponent(id)}/pages/${page}`);
  }

  async getBookFile(id: string): Promise<ArrayBuffer> {
    return this.getBytes(`/api/v1/books/${encodeURIComponent(id)}/file`, 120000);
  }

  async updateReadProgress(id: string, page: number, completed: boolean): Promise<void> {
    const body: ReadProgressUpdate = { page: page, completed: completed };
    await this.request(`/api/v1/books/${encodeURIComponent(id)}/read-progress`, http.RequestMethod.PUT,
      http.HttpDataType.STRING, JSON.stringify(body));
  }

  async deleteReadProgress(id: string): Promise<void> {
    await this.request(`/api/v1/books/${encodeURIComponent(id)}/read-progress`, http.RequestMethod.DELETE,
      http.HttpDataType.STRING);
  }

  async getReadiumProgression(id: string): Promise<ReadiumLocator> {
    return this.getJson<ReadiumLocator>(`/api/v1/books/${encodeURIComponent(id)}/progression`);
  }

  async updateReadiumProgression(id: string, locator: ReadiumLocator): Promise<void> {
    await this.sendJson(`/api/v1/books/${encodeURIComponent(id)}/progression`, http.RequestMethod.PUT, locator);
  }

  async getUsers(): Promise<KomgaUser[]> {
    return this.getJson<KomgaUser[]>('/api/v2/users');
  }

  async addUser(request: KomgaUserCreateRequest): Promise<KomgaUser> {
    return this.postJson<KomgaUser>('/api/v2/users', request);
  }

  async updateUser(id: string, request: KomgaUserUpdateRequest): Promise<void> {
    await this.sendJson(`/api/v2/users/${encodeURIComponent(id)}`, http.RequestMethod.PATCH, request);
  }

  async updateUserPassword(id: string, password: string): Promise<void> {
    await this.sendJson(`/api/v2/users/${encodeURIComponent(id)}/password`, http.RequestMethod.PATCH,
      { password: password });
  }

  async deleteUser(id: string): Promise<void> {
    await this.request(`/api/v2/users/${encodeURIComponent(id)}`, http.RequestMethod.DELETE,
      http.HttpDataType.STRING);
  }

  async getAuthenticationActivity(forMe: boolean, page: number = 0,
    size: number = 50): Promise<PageResult<KomgaAuthenticationActivity>> {
    const root = forMe ? '/api/v2/users/me/authentication-activity' : '/api/v2/users/authentication-activity';
    return this.getJson<PageResult<KomgaAuthenticationActivity>>(`${root}?page=${page}&size=${size}&sort=dateTime,desc`);
  }

  async updateSeriesMetadata(id: string, metadata: SeriesMetadataUpdateRequest): Promise<void> {
    await this.sendJson(`/api/v1/series/${encodeURIComponent(id)}/metadata`, http.RequestMethod.PATCH, metadata);
  }

  async updateBookMetadata(id: string, metadata: BookMetadataUpdateRequest): Promise<void> {
    await this.sendJson(`/api/v1/books/${encodeURIComponent(id)}/metadata`, http.RequestMethod.PATCH, metadata);
  }

  async runLibraryAction(id: string, action: string, deep: boolean = false): Promise<void> {
    const allowed = ['scan', 'analyze', 'metadata/refresh', 'empty-trash'];
    if (!allowed.includes(action)) throw new Error('不支持的书库操作');
    const query = action === 'scan' ? `?deep=${deep}` : '';
    await this.request(`/api/v1/libraries/${encodeURIComponent(id)}/${action}${query}`,
      http.RequestMethod.POST, http.HttpDataType.STRING);
  }

  async runSeriesAction(id: string, action: string): Promise<void> {
    const allowed = ['analyze', 'metadata/refresh'];
    if (!allowed.includes(action)) throw new Error('不支持的系列操作');
    await this.request(`/api/v1/series/${encodeURIComponent(id)}/${action}`,
      http.RequestMethod.POST, http.HttpDataType.STRING);
  }

  async setSeriesReadStatus(id: string, completed: boolean): Promise<void> {
    if (completed) {
      await this.request(`/api/v1/series/${encodeURIComponent(id)}/read-progress`, http.RequestMethod.PUT,
        http.HttpDataType.STRING);
    } else {
      await this.request(`/api/v1/series/${encodeURIComponent(id)}/read-progress`, http.RequestMethod.DELETE,
        http.HttpDataType.STRING);
    }
  }

  async deleteSeriesFiles(id: string): Promise<void> {
    await this.request(`/api/v1/series/${encodeURIComponent(id)}/file`, http.RequestMethod.DELETE,
      http.HttpDataType.STRING);
  }

  async runBookAction(id: string, action: string): Promise<void> {
    const allowed = ['analyze', 'metadata/refresh'];
    if (!allowed.includes(action)) throw new Error('不支持的书籍操作');
    await this.request(`/api/v1/books/${encodeURIComponent(id)}/${action}`,
      http.RequestMethod.POST, http.HttpDataType.STRING);
  }

  async deleteBookFile(id: string): Promise<void> {
    await this.request(`/api/v1/books/${encodeURIComponent(id)}/file`, http.RequestMethod.DELETE,
      http.HttpDataType.STRING);
  }

  async emptyTaskQueue(): Promise<number> {
    const response = await this.request('/api/v1/tasks', http.RequestMethod.DELETE, http.HttpDataType.STRING);
    if ((response.result as string).length === 0) return 0;
    return Number(response.result as string);
  }

  private async getJson<T>(path: string): Promise<T> {
    const response = await this.request(path, http.RequestMethod.GET, http.HttpDataType.STRING);
    return JSON.parse(response.result as string) as T;
  }

  private async postJson<T>(path: string, body: Object): Promise<T> {
    const response = await this.request(path, http.RequestMethod.POST, http.HttpDataType.STRING,
      JSON.stringify(body));
    return JSON.parse(response.result as string) as T;
  }

  private async sendJson(path: string, method: http.RequestMethod, body: Object): Promise<void> {
    await this.request(path, method, http.HttpDataType.STRING, JSON.stringify(body));
  }

  private async getBytes(path: string, timeout: number = 60000): Promise<ArrayBuffer> {
    const response = await this.request(path, http.RequestMethod.GET, http.HttpDataType.ARRAY_BUFFER,
      undefined, timeout);
    return response.result as ArrayBuffer;
  }

  private async request(path: string, method: http.RequestMethod, dataType: http.HttpDataType,
    body?: string, timeout: number = 30000): Promise<http.HttpResponse> {
    if (!this.isConfigured()) {
      throw new Error('尚未登录 Komga');
    }
    const request = http.createHttp();
    try {
      const response = await request.request(`${this.serverUrl}${path}`, {
        method: method,
        header: {
          'Authorization': this.authorization,
          'Accept': dataType === http.HttpDataType.STRING ? 'application/json' : '*/*',
          'Content-Type': 'application/json',
          'User-Agent': 'Komelia-Harmony/0.18.13'
        },
        // ArkTS' strict request option type does not accept an optional value here.
        extraData: body ?? '',
        expectDataType: dataType,
        usingCache: false,
        connectTimeout: timeout,
        readTimeout: timeout
      });
      if (response.responseCode < 200 || response.responseCode >= 300) {
        throw new KomgaHttpError(response.responseCode, `Komga 请求失败（${response.responseCode}）`);
      }
      return response;
    } finally {
      request.destroy();
    }
  }
}

export const komgaClient = new KomgaClient();
