import { http } from '@kit.NetworkKit';

export class KomfClient {
  private origin: string = '';

  configure(origin: string): void {
    const normalized = origin.trim().replace(/\/+$/, '');
    if (!normalized.startsWith('https://') && !normalized.startsWith('http://')) {
      throw new Error('Komf 地址必须以 https:// 或 http:// 开头');
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

  async getJobs(): Promise<Object[]> {
    const response = await this.request('/api/jobs', http.RequestMethod.GET);
    return JSON.parse(response.result as string) as Object[];
  }

  async runAllJobs(): Promise<void> {
    await this.request('/api/jobs/all', http.RequestMethod.POST);
  }

  async identifySeries(mediaServer: string, libraryId: string, seriesId: string): Promise<void> {
    await this.request(`/api/${encodeURIComponent(mediaServer)}/metadata/${encodeURIComponent(libraryId)}/series/` +
      `${encodeURIComponent(seriesId)}/identify`, http.RequestMethod.POST);
  }

  async resetSeries(mediaServer: string, libraryId: string, seriesId: string,
    removeComicInfo: boolean): Promise<void> {
    await this.request(`/api/${encodeURIComponent(mediaServer)}/metadata/${encodeURIComponent(libraryId)}/series/` +
      `${encodeURIComponent(seriesId)}/metadata?removeComicInfo=${removeComicInfo}`, http.RequestMethod.DELETE);
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
