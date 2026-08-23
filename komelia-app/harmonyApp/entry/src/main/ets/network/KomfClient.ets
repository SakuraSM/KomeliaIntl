import { http } from '@kit.NetworkKit';
import { KomfMediaServerConnectionResponse, KomfMediaServerLibrary, KomfMetadataJob,
  KomfMetadataJobResponse, KomfPage } from '../model/KomfModels';

export class KomfClient {
  private origin: string = '';

  configure(origin: string, allowInsecureHttp: boolean = false): void {
    const normalized = origin.trim().replace(/\/+$/, '');
    if (!normalized.startsWith('https://') && !normalized.startsWith('http://')) {
      throw new Error('Komf 地址必须以 https:// 或 http:// 开头');
    }
    if (normalized.startsWith('http://') && !allowInsecureHttp) {
      throw new Error('Komf HTTP 连接未加密，请确认局域网风险后重试');
    }
    this.origin = normalized;
  }

  async ping(): Promise<boolean> {
    if (this.origin.length === 0) return false;
    try {
      await this.request('/api/config', http.RequestMethod.GET);
      return true;
    } catch (_) {
      return false;
    }
  }

  async getConfig(): Promise<Object> {
    const response = await this.request('/api/config', http.RequestMethod.GET);
    return JSON.parse(response.result as string) as Object;
  }

  async updateConfig(config: Object): Promise<void> {
    await this.request('/api/config', http.RequestMethod.PUT, config);
  }

  async getJobs(page: number = 0, size: number = 50, status: string = ''): Promise<KomfPage<KomfMetadataJob>> {
    const statusQuery = status.length === 0 ? '' : `&status=${encodeURIComponent(status)}`;
    const response = await this.request(`/api/jobs?page=${page}&size=${size}${statusQuery}`, http.RequestMethod.GET);
    return JSON.parse(response.result as string) as KomfPage<KomfMetadataJob>;
  }

  async deleteAllJobs(): Promise<void> {
    await this.request('/api/jobs/all', http.RequestMethod.DELETE);
  }

  async getProviders(): Promise<string[]> {
    const response = await this.request('/api/metadata/providers', http.RequestMethod.GET);
    return JSON.parse(response.result as string) as string[];
  }

  async checkMediaServer(mediaServer: string = 'komga'): Promise<KomfMediaServerConnectionResponse> {
    const response = await this.request(`/api/${encodeURIComponent(mediaServer)}/media-server`, http.RequestMethod.GET);
    return JSON.parse(response.result as string) as KomfMediaServerConnectionResponse;
  }

  async getMediaServerLibraries(mediaServer: string = 'komga'): Promise<KomfMediaServerLibrary[]> {
    const response = await this.request(`/api/${encodeURIComponent(mediaServer)}/media-server/libraries`,
      http.RequestMethod.GET);
    return JSON.parse(response.result as string) as KomfMediaServerLibrary[];
  }

  async identifySeries(mediaServer: string, libraryId: string,
    seriesId: string): Promise<KomfMetadataJobResponse> {
    const response = await this.request(`/api/${encodeURIComponent(mediaServer)}/metadata/match/library/` +
      `${encodeURIComponent(libraryId)}/series/${encodeURIComponent(seriesId)}`, http.RequestMethod.POST);
    return JSON.parse(response.result as string) as KomfMetadataJobResponse;
  }

  async resetSeries(mediaServer: string, libraryId: string, seriesId: string,
    removeComicInfo: boolean): Promise<void> {
    await this.request(`/api/${encodeURIComponent(mediaServer)}/metadata/reset/library/` +
      `${encodeURIComponent(libraryId)}/series/${encodeURIComponent(seriesId)}` +
      `?removeComicInfo=${removeComicInfo}`, http.RequestMethod.POST);
  }

  async getDiscordTemplates(): Promise<Object> {
    const response = await this.request('/api/notifications/discord/templates', http.RequestMethod.GET);
    return JSON.parse(response.result as string) as Object;
  }

  async updateDiscordTemplates(value: Object): Promise<void> {
    await this.request('/api/notifications/discord/templates', http.RequestMethod.PUT, value);
  }

  async getAppriseTemplates(): Promise<Object> {
    const response = await this.request('/api/notifications/apprise/templates', http.RequestMethod.GET);
    return JSON.parse(response.result as string) as Object;
  }

  async updateAppriseTemplates(value: Object): Promise<void> {
    await this.request('/api/notifications/apprise/templates', http.RequestMethod.PUT, value);
  }

  private async request(path: string, method: http.RequestMethod, body?: Object): Promise<http.HttpResponse> {
    if (this.origin.length === 0) throw new Error('尚未配置 Komf');
    const request = http.createHttp();
    try {
      const response = await request.request(`${this.origin}${path}`, {
        method: method,
        header: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
        extraData: body === undefined ? '' : JSON.stringify(body),
        expectDataType: http.HttpDataType.STRING,
        usingCache: false,
        connectTimeout: 30000,
        readTimeout: 120000
      });
      if (response.responseCode < 200 || response.responseCode >= 300) {
        throw new Error(`Komf 请求失败（${response.responseCode}）`);
      }
      return response;
    } finally {
      request.destroy();
    }
  }
}

export const komfClient = new KomfClient();
