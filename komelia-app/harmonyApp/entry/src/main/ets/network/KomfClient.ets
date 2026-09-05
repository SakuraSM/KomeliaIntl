import { http } from '@kit.NetworkKit';
import { util } from '@kit.ArkTS';
import { localizedString } from '../core/i18n/LocalizedResources';
import { evaluateOriginPolicy, OriginPolicyError } from './OriginPolicy';
import { KomfAppriseNotificationRequest, KomfAppriseRenderResult, KomfAppriseTemplates,
  KomfAppriseConfigUpdate, KomfConfigNotificationUpdateRequest, KomfDiscordConfigUpdate,
  KomfDiscordNotificationRequest, KomfDiscordRenderResult, KomfDiscordTemplates,
  KomfIdentifyRequest, KomfJobEvent, KomfJobEventSubscription, KomfMediaServerConnectionResponse,
  KomfMediaServerLibrary, KomfMetadataJob, KomfMetadataJobResponse, KomfMetadataSeriesSearchResult,
  KomfNotificationContext, KomfNotificationsConfigUpdate, KomfPage } from '../model/KomfModels';

interface KomfJobEventPayload {
  provider?: string;
  totalBooks?: number;
  bookProgress?: number;
  message?: string;
}

export function komfMetadataPath(mediaServer: string): string {
  return `/api/${encodeURIComponent(mediaServer.trim().toLocaleLowerCase())}/metadata`;
}

export function komfSeriesSearchPath(mediaServer: string, name: string,
  libraryId: string = '', seriesId: string = ''): string {
  const query: string[] = [`name=${encodeURIComponent(name.trim())}`];
  if (libraryId.trim().length > 0) query.push(`libraryId=${encodeURIComponent(libraryId.trim())}`);
  if (seriesId.trim().length > 0) query.push(`seriesId=${encodeURIComponent(seriesId.trim())}`);
  return `${komfMetadataPath(mediaServer)}/search?${query.join('&')}`;
}

export function parseKomfSseBlock(block: string): KomfJobEvent | undefined {
  const lines = block.replace(/\r/g, '').split('\n');
  let eventType = '';
  const dataLines: string[] = [];
  for (let index = 0; index < lines.length; index++) {
    const line = lines[index];
    if (line.startsWith('event:')) eventType = line.substring(6).trim();
    else if (line.startsWith('data:')) dataLines.push(line.substring(5).trimStart());
  }
  if (eventType.length === 0) return undefined;
  const raw = dataLines.join('\n');
  if (raw.length === 0) return { type: eventType };
  try {
    const payload = JSON.parse(raw) as KomfJobEventPayload;
    return { type: eventType, provider: payload.provider, totalBooks: payload.totalBooks,
      bookProgress: payload.bookProgress, message: payload.message, raw: raw };
  } catch (_) {
    return { type: eventType, raw: raw };
  }
}

class DefaultKomfJobEventSubscription implements KomfJobEventSubscription {
  private request: http.HttpRequest | undefined;

  constructor(request?: http.HttpRequest) {
    this.request = request;
  }

  close(): void {
    if (this.request === undefined) return;
    this.request.off('dataReceive');
    this.request.off('dataEnd');
    this.request.destroy();
    this.request = undefined;
  }
}

export class KomfClient {
  private origin: string = '';

  configure(origin: string, allowInsecureHttp: boolean = false): void {
    const policy = evaluateOriginPolicy(origin, allowInsecureHttp);
    if (policy.error === OriginPolicyError.SCHEME_REQUIRED) {
      throw new Error(localizedString($r('app.string.komf_url_scheme_validation')));
    }
    if (policy.error === OriginPolicyError.HTTP_CONFIRMATION_REQUIRED) {
      throw new Error(localizedString($r('app.string.komf_http_confirmation_required')));
    }
    this.origin = policy.normalizedOrigin;
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
    await this.request('/api/config', 'PATCH' as http.RequestMethod, config);
  }

  async getJobs(page: number = 1, size: number = 50, status: string = ''): Promise<KomfPage<KomfMetadataJob>> {
    const statusQuery = status.length === 0 ? '' : `&status=${encodeURIComponent(status)}`;
    const response = await this.request(`/api/jobs?page=${page}&pageSize=${size}${statusQuery}`,
      http.RequestMethod.GET);
    return JSON.parse(response.result as string) as KomfPage<KomfMetadataJob>;
  }

  async getJob(jobId: string): Promise<KomfMetadataJob> {
    const response = await this.request(`/api/jobs/${encodeURIComponent(jobId)}`, http.RequestMethod.GET);
    return JSON.parse(response.result as string) as KomfMetadataJob;
  }

  observeJobEvents(jobId: string, onEvent: (event: KomfJobEvent) => void,
    onComplete: () => void, onError: (error: Error) => void): KomfJobEventSubscription {
    if (this.origin.length === 0) {
      onError(new Error(localizedString($r('app.string.komf_not_configured'))));
      return new DefaultKomfJobEventSubscription();
    }
    const request = http.createHttp();
    const subscription = new DefaultKomfJobEventSubscription(request);
    const decoder = util.TextDecoder.create('utf-8', { ignoreBOM: true });
    let pending = '';
    const emitBlocks = (flush: boolean): void => {
      const normalized = pending.replace(/\r\n/g, '\n');
      const blocks = normalized.split('\n\n');
      const limit = flush ? blocks.length : blocks.length - 1;
      for (let index = 0; index < limit; index++) {
        const event = parseKomfSseBlock(blocks[index]);
        if (event !== undefined) onEvent(event);
      }
      pending = flush ? '' : blocks[blocks.length - 1];
    };
    request.on('dataReceive', (data: ArrayBuffer) => {
      pending += decoder.decodeToString(new Uint8Array(data), { stream: true });
      emitBlocks(false);
    });
    request.on('dataEnd', () => {
      pending += decoder.decodeToString(new Uint8Array(0), { stream: false });
      emitBlocks(true);
    });
    request.requestInStream(`${this.origin}/api/jobs/${encodeURIComponent(jobId)}/events`, {
      method: http.RequestMethod.GET,
      header: { 'Accept': 'text/event-stream', 'Cache-Control': 'no-cache' },
      usingCache: false,
      connectTimeout: 30000,
      readTimeout: 120000
    }).then((responseCode: number) => {
      if (responseCode < 200 || responseCode >= 300) {
        onError(new Error(localizedString($r('app.string.komf_request_failed'), responseCode)));
      } else {
        onComplete();
      }
      subscription.close();
    }).catch((error: Error) => {
      onError(error);
      subscription.close();
    });
    return subscription;
  }

  async deleteAllJobs(): Promise<void> {
    await this.request('/api/jobs/all', http.RequestMethod.DELETE);
  }

  async getProviders(): Promise<string[]> {
    const response = await this.request('/api/metadata/providers', http.RequestMethod.GET);
    return JSON.parse(response.result as string) as string[];
  }

  async checkMediaServer(mediaServer: string = 'komga'): Promise<KomfMediaServerConnectionResponse> {
    const response = await this.request(`/api/${encodeURIComponent(mediaServer)}/media-server/connected`,
      http.RequestMethod.GET);
    return JSON.parse(response.result as string) as KomfMediaServerConnectionResponse;
  }

  async getMediaServerLibraries(mediaServer: string = 'komga'): Promise<KomfMediaServerLibrary[]> {
    const response = await this.request(`/api/${encodeURIComponent(mediaServer)}/media-server/libraries`,
      http.RequestMethod.GET);
    return JSON.parse(response.result as string) as KomfMediaServerLibrary[];
  }

  async identifySeries(mediaServer: string, libraryId: string,
    seriesId: string): Promise<KomfMetadataJobResponse> {
    const response = await this.request(`${komfMetadataPath(mediaServer)}/match/library/` +
      `${encodeURIComponent(libraryId)}/series/${encodeURIComponent(seriesId)}`, http.RequestMethod.POST);
    return JSON.parse(response.result as string) as KomfMetadataJobResponse;
  }

  async searchSeries(mediaServer: string, name: string, libraryId: string = '',
    seriesId: string = ''): Promise<KomfMetadataSeriesSearchResult[]> {
    const response = await this.request(komfSeriesSearchPath(mediaServer, name, libraryId, seriesId),
      http.RequestMethod.GET);
    return JSON.parse(response.result as string) as KomfMetadataSeriesSearchResult[];
  }

  async manuallyIdentifySeries(mediaServer: string,
    identifyRequest: KomfIdentifyRequest): Promise<KomfMetadataJobResponse> {
    const response = await this.request(`${komfMetadataPath(mediaServer)}/identify`, http.RequestMethod.POST,
      identifyRequest);
    return JSON.parse(response.result as string) as KomfMetadataJobResponse;
  }

  async identifyLibrary(mediaServer: string, libraryId: string): Promise<KomfMetadataJobResponse> {
    const response = await this.request(`${komfMetadataPath(mediaServer)}/match/library/` +
      encodeURIComponent(libraryId), http.RequestMethod.POST);
    return JSON.parse(response.result as string) as KomfMetadataJobResponse;
  }

  async resetSeries(mediaServer: string, libraryId: string, seriesId: string,
    removeComicInfo: boolean): Promise<void> {
    await this.request(`${komfMetadataPath(mediaServer)}/reset/library/` +
      `${encodeURIComponent(libraryId)}/series/${encodeURIComponent(seriesId)}` +
      `?removeComicInfo=${removeComicInfo}`, http.RequestMethod.POST);
  }

  async resetLibrary(mediaServer: string, libraryId: string, removeComicInfo: boolean): Promise<void> {
    await this.request(`${komfMetadataPath(mediaServer)}/reset/library/` + encodeURIComponent(libraryId) +
      `?removeComicInfo=${removeComicInfo}`, http.RequestMethod.POST);
  }

  async getDiscordTemplates(): Promise<KomfDiscordTemplates> {
    const response = await this.request('/api/notifications/discord/templates', http.RequestMethod.GET);
    return JSON.parse(response.result as string) as KomfDiscordTemplates;
  }

  async updateDiscordTemplates(value: KomfDiscordTemplates): Promise<void> {
    await this.request('/api/notifications/discord/templates', http.RequestMethod.POST, value);
  }

  async renderDiscord(context: KomfNotificationContext,
    templates: KomfDiscordTemplates): Promise<KomfDiscordRenderResult> {
    const request: KomfDiscordNotificationRequest = { context: context, templates: templates };
    const response = await this.request('/api/notifications/discord/render', http.RequestMethod.POST,
      request);
    return JSON.parse(response.result as string) as KomfDiscordRenderResult;
  }

  async sendDiscord(context: KomfNotificationContext, templates: KomfDiscordTemplates): Promise<void> {
    const request: KomfDiscordNotificationRequest = { context: context, templates: templates };
    await this.request('/api/notifications/discord/send', http.RequestMethod.POST,
      request);
  }

  async getAppriseTemplates(): Promise<KomfAppriseTemplates> {
    const response = await this.request('/api/notifications/apprise/templates', http.RequestMethod.GET);
    return JSON.parse(response.result as string) as KomfAppriseTemplates;
  }

  async updateAppriseTemplates(value: KomfAppriseTemplates): Promise<void> {
    await this.request('/api/notifications/apprise/templates', http.RequestMethod.POST, value);
  }

  async renderApprise(context: KomfNotificationContext,
    templates: KomfAppriseTemplates): Promise<KomfAppriseRenderResult> {
    const request: KomfAppriseNotificationRequest = { context: context, templates: templates };
    const response = await this.request('/api/notifications/apprise/render', http.RequestMethod.POST,
      request);
    return JSON.parse(response.result as string) as KomfAppriseRenderResult;
  }

  async sendApprise(context: KomfNotificationContext, templates: KomfAppriseTemplates): Promise<void> {
    const request: KomfAppriseNotificationRequest = { context: context, templates: templates };
    await this.request('/api/notifications/apprise/send', http.RequestMethod.POST,
      request);
  }

  async updateDiscordWebhook(index: number, value?: string): Promise<void> {
    const patchValue = value === undefined ? 'null' : JSON.stringify(value);
    const request = JSON.parse(`{"notifications":{"discord":{"webhooks":{"${index}":${patchValue}}}}}`) as
      KomfConfigNotificationUpdateRequest;
    await this.updateConfig(request);
  }

  async updateDiscordSeriesCover(value: boolean): Promise<void> {
    const discord: KomfDiscordConfigUpdate = { seriesCover: value };
    const notifications: KomfNotificationsConfigUpdate = { discord: discord };
    const request: KomfConfigNotificationUpdateRequest = { notifications: notifications };
    await this.updateConfig(request);
  }

  async updateAppriseUrl(index: number, value?: string): Promise<void> {
    const patchValue = value === undefined ? 'null' : JSON.stringify(value);
    const request = JSON.parse(`{"notifications":{"apprise":{"urls":{"${index}":${patchValue}}}}}`) as
      KomfConfigNotificationUpdateRequest;
    await this.updateConfig(request);
  }

  async updateAppriseSeriesCover(value: boolean): Promise<void> {
    const apprise: KomfAppriseConfigUpdate = { seriesCover: value };
    const notifications: KomfNotificationsConfigUpdate = { apprise: apprise };
    const request: KomfConfigNotificationUpdateRequest = { notifications: notifications };
    await this.updateConfig(request);
  }

  private async request(path: string, method: http.RequestMethod, body?: Object): Promise<http.HttpResponse> {
    if (this.origin.length === 0) throw new Error(localizedString($r('app.string.komf_not_configured')));
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
        throw new Error(localizedString($r('app.string.komf_request_failed'), response.responseCode));
      }
      return response;
    } finally {
      request.destroy();
    }
  }
}

export const komfClient = new KomfClient();
