export interface KomfPage<T> {
  content: T[];
  currentPage: number;
  totalPages: number;
}

export interface KomfMetadataJob {
  seriesId: string;
  id: string;
  status: string;
  message: string;
  startedAt?: string;
  finishedAt?: string;
}

export interface KomfMetadataJobResponse { jobId: string; }

export interface KomfMetadataSeriesSearchResult {
  url: string;
  imageUrl: string;
  title: string;
  provider: string;
  resultId: string;
}

export interface KomfIdentifyRequest {
  libraryId: string;
  seriesId: string;
  provider: string;
  providerSeriesId: string;
}

export interface KomfJobEvent {
  type: string;
  provider?: string;
  totalBooks?: number;
  bookProgress?: number;
  message?: string;
  raw?: string;
}

export interface KomfJobEventSubscription {
  close(): void;
}

export interface KomfMediaServerConnectionResponse {
  success: boolean;
  httpStatusCode?: number;
  errorMessage?: string;
}

export interface KomfMediaServerLibrary {
  id: string;
  name: string;
  roots?: string[];
}

export interface KomfNotificationAuthorContext {
  name: string;
  role: string;
}

export interface KomfNotificationLinkContext {
  label: string;
  url: string;
}

export interface KomfNotificationAlternativeTitleContext {
  label: string;
  title: string;
}

export interface KomfNotificationSeriesMetadataContext {
  status: string;
  title: string;
  titleSort: string;
  alternativeTitles: KomfNotificationAlternativeTitleContext[];
  summary: string;
  readingDirection: string;
  publisher: string;
  alternativePublishers: string[];
  ageRating: number;
  language: string;
  genres: string[];
  tags: string[];
  totalBookCount: number;
  authors: KomfNotificationAuthorContext[];
  releaseYear: number;
  links: KomfNotificationLinkContext[];
}

export interface KomfNotificationBookMetadataContext {
  title: string;
  summary: string;
  number: string;
  numberSort: string;
  releaseDate: string;
  authors: KomfNotificationAuthorContext[];
  tags: string[];
  isbn: string;
  links: KomfNotificationLinkContext[];
}

export interface KomfNotificationBookContext {
  id: string;
  name: string;
  number: number;
  metadata: KomfNotificationBookMetadataContext;
}

export interface KomfNotificationLibraryContext {
  id: string;
  name: string;
}

export interface KomfNotificationSeriesContext {
  id: string;
  name: string;
  bookCount: number;
  metadata: KomfNotificationSeriesMetadataContext;
}

export interface KomfNotificationContext {
  library: KomfNotificationLibraryContext;
  series: KomfNotificationSeriesContext;
  books: KomfNotificationBookContext[];
  mediaServer: string;
}

export interface KomfEmbedFieldTemplate {
  nameTemplate: string;
  valueTemplate: string;
  inline: boolean;
}

export interface KomfDiscordTemplates {
  titleTemplate?: string;
  titleUrlTemplate?: string;
  descriptionTemplate?: string;
  fields: KomfEmbedFieldTemplate[];
  footerTemplate?: string;
}

export interface KomfAppriseTemplates {
  titleTemplate?: string;
  bodyTemplate?: string;
}

export interface KomfDiscordNotificationRequest {
  context: KomfNotificationContext;
  templates: KomfDiscordTemplates;
}

export interface KomfAppriseNotificationRequest {
  context: KomfNotificationContext;
  templates: KomfAppriseTemplates;
}

export interface KomfEmbedField {
  name: string;
  value: string;
  inline: boolean;
}

export interface KomfDiscordRenderResult {
  title?: string;
  titleUrl?: string;
  description?: string;
  fields: KomfEmbedField[];
  footer?: string;
}

export interface KomfAppriseRenderResult {
  title?: string;
  body?: string;
}

export interface KomfNotificationTargetSummary {
  discordCount: number;
  appriseCount: number;
  discordSeriesCover: boolean;
  appriseSeriesCover: boolean;
}

export interface KomfNotificationSettingsSnapshot extends KomfNotificationTargetSummary {
  discordWebhooks: string[];
  appriseUrls: string[];
}

export interface KomfDiscordConfigUpdate {
  webhooks?: Object;
  seriesCover?: boolean;
}

export interface KomfAppriseConfigUpdate {
  urls?: Object;
  seriesCover?: boolean;
}

export interface KomfNotificationsConfigUpdate {
  discord?: KomfDiscordConfigUpdate;
  apprise?: KomfAppriseConfigUpdate;
}

export interface KomfConfigNotificationUpdateRequest {
  notifications: KomfNotificationsConfigUpdate;
}

interface KomfDiscordConfigView {
  webhooks?: string[];
  seriesCover?: boolean;
}

interface KomfAppriseConfigView {
  urls?: string[];
  seriesCover?: boolean;
}

interface KomfNotificationsConfigView {
  discord?: KomfDiscordConfigView;
  apprise?: KomfAppriseConfigView;
}

interface KomfNotificationConfigView {
  notifications?: KomfNotificationsConfigView;
}

export function komfNotificationTargetSummary(config: Object): KomfNotificationTargetSummary {
  const value = config as KomfNotificationConfigView;
  const discord = value.notifications?.discord;
  const apprise = value.notifications?.apprise;
  return {
    discordCount: discord?.webhooks?.length ?? 0,
    appriseCount: apprise?.urls?.length ?? 0,
    discordSeriesCover: discord?.seriesCover ?? false,
    appriseSeriesCover: apprise?.seriesCover ?? false
  };
}

export function komfNotificationSettings(config: Object): KomfNotificationSettingsSnapshot {
  const value = config as KomfNotificationConfigView;
  const discord = value.notifications?.discord;
  const apprise = value.notifications?.apprise;
  const snapshot: KomfNotificationSettingsSnapshot = {
    discordCount: discord?.webhooks?.length ?? 0,
    appriseCount: apprise?.urls?.length ?? 0,
    discordSeriesCover: discord?.seriesCover ?? false,
    appriseSeriesCover: apprise?.seriesCover ?? false,
    discordWebhooks: (discord?.webhooks ?? []).slice(),
    appriseUrls: (apprise?.urls ?? []).slice()
  };
  return snapshot;
}

export function defaultKomfNotificationContext(): KomfNotificationContext {
  const authors: KomfNotificationAuthorContext[] = [
    { name: 'Author 1', role: 'Writer' },
    { name: 'Author 2', role: 'Artist' }
  ];
  const link: KomfNotificationLinkContext = { label: 'Example link', url: 'https://example.com' };
  const metadata: KomfNotificationSeriesMetadataContext = {
    status: 'ONGOING', title: 'Series title', titleSort: 'Series title', alternativeTitles: [],
    summary: 'Series summary', readingDirection: 'LEFT_TO_RIGHT', publisher: 'Series publisher',
    alternativePublishers: [], ageRating: 18, language: '', genres: ['genre1', 'genre2'],
    tags: ['tag1', 'tag2'], totalBookCount: 2, authors: authors, releaseYear: 1970, links: [link]
  };
  const library: KomfNotificationLibraryContext = { id: '1', name: 'Test library' };
  const series: KomfNotificationSeriesContext = {
    id: '2', name: 'Test series', bookCount: 1, metadata: metadata
  };
  const bookMetadata: KomfNotificationBookMetadataContext = {
    title: 'Book metadata title', summary: 'Book summary', number: '1', numberSort: '1',
    releaseDate: '1970-01-01', authors: authors, tags: ['bookTag1', 'bookTag2'],
    isbn: '9780000000000', links: []
  };
  const book: KomfNotificationBookContext = {
    id: '3', name: 'Test book', number: 1, metadata: bookMetadata
  };
  const context: KomfNotificationContext = {
    library: library, series: series, books: [book], mediaServer: 'KOMGA'
  };
  return context;
}

export interface KomfProcessingSettingsSnapshot {
  libraryType: string;
  aggregate: boolean;
  mergeTags: boolean;
  mergeGenres: boolean;
  bookCovers: boolean;
  seriesCovers: boolean;
  overrideExistingCovers: boolean;
  lockCovers: boolean;
  updateModes: string[];
  seriesTitle: boolean;
  alternativeSeriesTitles: boolean;
  seriesTitleLanguage: string;
  alternativeSeriesTitleLanguages: string[];
  fallbackToAltTitle: boolean;
  orderBooks: boolean;
  readingDirectionValue?: string;
  languageValue?: string;
}

interface KomfPostProcessingConfigView {
  seriesTitle?: boolean;
  alternativeSeriesTitles?: boolean;
  seriesTitleLanguage?: string;
  alternativeSeriesTitleLanguages?: string[];
  fallbackToAltTitle?: boolean;
  orderBooks?: boolean;
  readingDirectionValue?: string;
  languageValue?: string;
}

interface KomfProcessingConfigView {
  libraryType?: string;
  aggregate?: boolean;
  mergeTags?: boolean;
  mergeGenres?: boolean;
  bookCovers?: boolean;
  seriesCovers?: boolean;
  overrideExistingCovers?: boolean;
  lockCovers?: boolean;
  updateModes?: string[];
  postProcessing?: KomfPostProcessingConfigView;
}

interface KomfMetadataUpdateConfigView {
  default?: KomfProcessingConfigView;
  library?: Record<string, KomfProcessingConfigView>;
}

interface KomfServerProcessingConfigView {
  metadataUpdate?: KomfMetadataUpdateConfigView;
}

interface KomfProcessingRootConfigView {
  komga?: KomfServerProcessingConfigView;
  kavita?: KomfServerProcessingConfigView;
}

export type KomfMediaServerId = 'komga' | 'kavita';

export function normalizeKomfMediaServer(value: string): KomfMediaServerId {
  return value.trim().toLowerCase() === 'kavita' ? 'kavita' : 'komga';
}

export interface KomfPostProcessingConfigUpdate {
  seriesTitle: boolean;
  alternativeSeriesTitles: boolean;
  seriesTitleLanguage: string;
  alternativeSeriesTitleLanguages: string[];
  fallbackToAltTitle: boolean;
  orderBooks: boolean;
  readingDirectionValue: string | null;
  languageValue: string | null;
}

export interface KomfProcessingConfigUpdate {
  libraryType: string;
  aggregate: boolean;
  mergeTags: boolean;
  mergeGenres: boolean;
  bookCovers: boolean;
  seriesCovers: boolean;
  overrideExistingCovers: boolean;
  lockCovers: boolean;
  updateModes: string[];
  postProcessing: KomfPostProcessingConfigUpdate;
}

export interface KomfMetadataUpdateConfigUpdate {
  default?: KomfProcessingConfigUpdate;
  library?: Record<string, KomfProcessingConfigUpdate | null>;
}

export interface KomfServerProcessingConfigUpdate {
  metadataUpdate: KomfMetadataUpdateConfigUpdate;
}

export interface KomfProcessingConfigUpdateRequest {
  komga?: KomfServerProcessingConfigUpdate;
  kavita?: KomfServerProcessingConfigUpdate;
}

export function komfProcessingSettings(config: Object, mediaServer: KomfMediaServerId = 'komga',
  libraryId: string = ''): KomfProcessingSettingsSnapshot {
  const value = config as KomfProcessingRootConfigView;
  const server = mediaServer === 'kavita' ? value.kavita : value.komga;
  const processing = libraryId.length > 0 ? server?.metadataUpdate?.library?.[libraryId] :
    server?.metadataUpdate?.default;
  const post = processing?.postProcessing;
  const snapshot: KomfProcessingSettingsSnapshot = {
    libraryType: processing?.libraryType ?? 'MANGA',
    aggregate: processing?.aggregate ?? false,
    mergeTags: processing?.mergeTags ?? false,
    mergeGenres: processing?.mergeGenres ?? false,
    bookCovers: processing?.bookCovers ?? false,
    seriesCovers: processing?.seriesCovers ?? false,
    overrideExistingCovers: processing?.overrideExistingCovers ?? false,
    lockCovers: processing?.lockCovers ?? false,
    updateModes: (processing?.updateModes ?? ['API']).slice(),
    seriesTitle: post?.seriesTitle ?? false,
    alternativeSeriesTitles: post?.alternativeSeriesTitles ?? false,
    seriesTitleLanguage: post?.seriesTitleLanguage ?? '',
    alternativeSeriesTitleLanguages: (post?.alternativeSeriesTitleLanguages ?? []).slice(),
    fallbackToAltTitle: post?.fallbackToAltTitle ?? false,
    orderBooks: post?.orderBooks ?? false,
    readingDirectionValue: post?.readingDirectionValue,
    languageValue: post?.languageValue
  };
  return snapshot;
}

export function komfDefaultProcessingSettings(config: Object): KomfProcessingSettingsSnapshot {
  return komfProcessingSettings(config, 'komga');
}

export function komfProcessingUpdate(settings: KomfProcessingSettingsSnapshot,
  mediaServer: KomfMediaServerId = 'komga', libraryId: string = ''): KomfProcessingConfigUpdateRequest {
  const post: KomfPostProcessingConfigUpdate = {
    seriesTitle: settings.seriesTitle,
    alternativeSeriesTitles: settings.alternativeSeriesTitles,
    seriesTitleLanguage: settings.seriesTitleLanguage,
    alternativeSeriesTitleLanguages: settings.alternativeSeriesTitleLanguages,
    fallbackToAltTitle: settings.fallbackToAltTitle,
    orderBooks: settings.orderBooks,
    readingDirectionValue: settings.readingDirectionValue ?? null,
    languageValue: settings.languageValue === undefined || settings.languageValue.length === 0 ?
      null : settings.languageValue
  };
  const processing: KomfProcessingConfigUpdate = {
    libraryType: settings.libraryType,
    aggregate: settings.aggregate,
    mergeTags: settings.mergeTags,
    mergeGenres: settings.mergeGenres,
    bookCovers: settings.bookCovers,
    seriesCovers: settings.seriesCovers,
    overrideExistingCovers: settings.overrideExistingCovers,
    lockCovers: settings.lockCovers,
    updateModes: settings.updateModes,
    postProcessing: post
  };
  let metadataUpdate: KomfMetadataUpdateConfigUpdate;
  if (libraryId.length > 0) {
    const library: Record<string, KomfProcessingConfigUpdate> = {};
    library[libraryId] = processing;
    metadataUpdate = { library: library };
  } else {
    metadataUpdate = { default: processing };
  }
  const server: KomfServerProcessingConfigUpdate = { metadataUpdate: metadataUpdate };
  return mediaServer === 'kavita' ? { kavita: server } : { komga: server };
}

export function komfDefaultProcessingUpdate(settings: KomfProcessingSettingsSnapshot):
  KomfProcessingConfigUpdateRequest {
  return komfProcessingUpdate(settings, 'komga');
}

export function komfRemoveLibraryProcessingUpdate(mediaServer: KomfMediaServerId,
  libraryId: string): KomfProcessingConfigUpdateRequest {
  const library: Record<string, KomfProcessingConfigUpdate | null> = {};
  library[libraryId] = null;
  const server: KomfServerProcessingConfigUpdate = { metadataUpdate: { library: library } };
  return mediaServer === 'kavita' ? { kavita: server } : { komga: server };
}

export interface KomfProviderSummary {
  id: string;
  enabled: boolean;
  priority: number;
}

export interface KomfProviderGlobalSettings {
  nameMatchingMode: string;
  malClientId: string;
  comicVineClientId: string;
}

interface KomfProviderConfigView {
  enabled?: boolean;
  priority?: number;
}

interface KomfProvidersConfigView {
  mangaUpdates?: KomfProviderConfigView;
  mal?: KomfProviderConfigView;
  nautiljon?: KomfProviderConfigView;
  aniList?: KomfProviderConfigView;
  yenPress?: KomfProviderConfigView;
  kodansha?: KomfProviderConfigView;
  viz?: KomfProviderConfigView;
  bookWalker?: KomfProviderConfigView;
  mangaDex?: KomfProviderConfigView;
  bangumi?: KomfProviderConfigView;
  comicVine?: KomfProviderConfigView;
  hentag?: KomfProviderConfigView;
  mangaBaka?: KomfProviderConfigView;
  webtoons?: KomfProviderConfigView;
}

interface KomfMetadataProvidersConfigView {
  nameMatchingMode?: string;
  malClientId?: string;
  comicVineClientId?: string;
  defaultProviders?: KomfProvidersConfigView;
}

interface KomfProviderRootConfigView {
  metadataProviders?: KomfMetadataProvidersConfigView;
}

export function normalizeKomfProviderId(value: string): string {
  return value.trim().toLocaleUpperCase().replace(/[- ]/g, '_');
}

export function komfProviderJsonKey(providerId: string): string {
  const id = normalizeKomfProviderId(providerId);
  if (id === 'MANGA_UPDATES') return 'mangaUpdates';
  if (id === 'MAL') return 'mal';
  if (id === 'NAUTILJON') return 'nautiljon';
  if (id === 'ANILIST') return 'aniList';
  if (id === 'YEN_PRESS') return 'yenPress';
  if (id === 'KODANSHA') return 'kodansha';
  if (id === 'VIZ') return 'viz';
  if (id === 'BOOK_WALKER') return 'bookWalker';
  if (id === 'MANGADEX') return 'mangaDex';
  if (id === 'BANGUMI') return 'bangumi';
  if (id === 'COMIC_VINE') return 'comicVine';
  if (id === 'HENTAG') return 'hentag';
  if (id === 'MANGA_BAKA') return 'mangaBaka';
  if (id === 'WEBTOONS') return 'webtoons';
  return '';
}

function komfProviderConfig(value: KomfProvidersConfigView | undefined,
  providerId: string): KomfProviderConfigView | undefined {
  const id = normalizeKomfProviderId(providerId);
  if (id === 'MANGA_UPDATES') return value?.mangaUpdates;
  if (id === 'MAL') return value?.mal;
  if (id === 'NAUTILJON') return value?.nautiljon;
  if (id === 'ANILIST') return value?.aniList;
  if (id === 'YEN_PRESS') return value?.yenPress;
  if (id === 'KODANSHA') return value?.kodansha;
  if (id === 'VIZ') return value?.viz;
  if (id === 'BOOK_WALKER') return value?.bookWalker;
  if (id === 'MANGADEX') return value?.mangaDex;
  if (id === 'BANGUMI') return value?.bangumi;
  if (id === 'COMIC_VINE') return value?.comicVine;
  if (id === 'HENTAG') return value?.hentag;
  if (id === 'MANGA_BAKA') return value?.mangaBaka;
  if (id === 'WEBTOONS') return value?.webtoons;
  return undefined;
}

export function komfDefaultProviders(config: Object, available: string[]): KomfProviderSummary[] {
  const root = config as KomfProviderRootConfigView;
  const providers = root.metadataProviders?.defaultProviders;
  const values: KomfProviderSummary[] = [];
  for (let index = 0; index < available.length; index += 1) {
    const id = normalizeKomfProviderId(available[index]);
    if (komfProviderJsonKey(id).length === 0) continue;
    const provider = komfProviderConfig(providers, id);
    values.push({ id: id, enabled: provider?.enabled ?? false, priority: provider?.priority ?? 999 });
  }
  return values.sort((left: KomfProviderSummary, right: KomfProviderSummary) => {
    if (left.enabled !== right.enabled) return left.enabled ? -1 : 1;
    if (left.priority !== right.priority) return left.priority - right.priority;
    return left.id.localeCompare(right.id);
  });
}

export function komfProviderGlobalSettings(config: Object): KomfProviderGlobalSettings {
  const root = config as KomfProviderRootConfigView;
  const providers = root.metadataProviders;
  return {
    nameMatchingMode: providers?.nameMatchingMode ?? 'CLOSEST_MATCH',
    malClientId: providers?.malClientId ?? '',
    comicVineClientId: providers?.comicVineClientId ?? ''
  };
}

export function komfDefaultProviderPatch(providerId: string, enabled: boolean, priority: number): Object {
  const key = komfProviderJsonKey(providerId);
  if (key.length === 0) return JSON.parse('{}') as Object;
  return JSON.parse(`{"metadataProviders":{"defaultProviders":{"${key}":` +
    `{"enabled":${enabled},"priority":${priority}}}}}`) as Object;
}

export function komfProviderGlobalPatch(nameMatchingMode: string, malClientId: string,
  comicVineClientId: string): Object {
  return JSON.parse('{"metadataProviders":{"nameMatchingMode":' + JSON.stringify(nameMatchingMode) +
    ',"malClientId":' + JSON.stringify(malClientId) + ',"comicVineClientId":' +
    JSON.stringify(comicVineClientId) + '}}') as Object;
}
