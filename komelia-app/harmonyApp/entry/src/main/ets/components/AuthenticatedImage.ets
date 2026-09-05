import { image } from '@kit.ImageKit';
import { KomeliaColors } from '../core/design/KomeliaDesign';
import { ConcurrentTaskGate } from '../domain/service/ReaderRequestToken';
import { RecentFailureBackoff, RetainedAsyncLruCache } from '../domain/service/RetainedAsyncLruCache';
import { komgaClient } from '../network/KomgaClient';
import { isLocalBook } from '../model/LocalContentModels';
import { localPublicationReader } from '../domain/service/LocalPublicationReader';

const coverLoadGate = new ConcurrentTaskGate(6);
const coverImageCache = new RetainedAsyncLruCache<PixelMap>(96, (pixelMap: PixelMap): void => {
  pixelMap.release();
}, 48 * 1024 * 1024, (pixelMap: PixelMap): number => pixelMap.getPixelBytesNumber());
const coverFailureBackoff = new RecentFailureBackoff(30000, 96);

@Component
export struct AuthenticatedImage {
  @Prop @Watch('onEndpointChanged') endpoint: string = '';
  @Prop fit: ImageFit = ImageFit.Cover;
  @State source: PixelMap | undefined = undefined;
  private currentEndpoint: string = '';
  private currentCacheKey: string = '';
  private ownedCacheKey: string = '';
  private pixelMap?: PixelMap;
  private requestGeneration: number = 0;
  private visible: boolean = false;
  private targetWidthPixels: number = 0;
  private targetHeightPixels: number = 0;

  aboutToAppear(): void {
    this.visible = true;
    this.load();
  }

  private onEndpointChanged(): void { if (this.visible) this.load(); }

  aboutToDisappear(): void {
    this.visible = false;
    this.requestGeneration++;
    this.currentEndpoint = '';
    this.currentCacheKey = '';
    this.releaseOwnedImage();
  }

  private async load(): Promise<void> {
    const cacheKey = `${komgaClient.getImageCacheScope()}:${this.endpoint}:` +
      `${this.targetWidthPixels}x${this.targetHeightPixels}`;
    if (this.endpoint.length === 0 || this.targetWidthPixels <= 0 || this.targetHeightPixels <= 0 ||
      (this.currentEndpoint === this.endpoint && this.currentCacheKey === cacheKey)) {
      return;
    }
    const keepCurrentImage = this.currentEndpoint === this.endpoint && this.source !== undefined;
    if (!keepCurrentImage) {
      this.releaseOwnedImage();
      this.source = undefined;
    }
    this.currentEndpoint = this.endpoint;
    this.currentCacheKey = cacheKey;
    const generation = ++this.requestGeneration;
    const endpoint = this.endpoint;
    const width = this.targetWidthPixels;
    const height = this.targetHeightPixels;

    const cached = coverImageCache.acquireCached(cacheKey);
    if (cached !== undefined) {
      this.ownedCacheKey = cacheKey;
      this.pixelMap = cached;
      this.source = cached;
      return;
    }
    if (!coverFailureBackoff.shouldRetry(cacheKey)) return;

    try {
      if (!this.visible || generation !== this.requestGeneration) return;
      const pixelMap = await coverImageCache.acquire(cacheKey, async (): Promise<PixelMap> => {
        await coverLoadGate.enter();
        try {
          return await this.downloadPixelMap(width, height, endpoint);
        } finally {
          coverLoadGate.leave();
        }
      });
      if (!this.visible || generation !== this.requestGeneration) {
        coverImageCache.release(cacheKey, pixelMap);
        return;
      }
      this.releaseOwnedImage();
      this.ownedCacheKey = cacheKey;
      this.pixelMap = pixelMap;
      this.source = pixelMap;
      coverFailureBackoff.clear(cacheKey);
    } catch (_) {
      coverFailureBackoff.markFailure(cacheKey);
      if (!keepCurrentImage && this.visible && generation === this.requestGeneration) {
        this.releaseOwnedImage();
        this.source = undefined;
      }
    }
  }

  private async downloadPixelMap(targetWidth: number, targetHeight: number, endpoint: string): Promise<PixelMap> {
    const id = this.extractId(endpoint);
    if (isLocalBook(id)) {
      return localPublicationReader.thumbnail(id, targetWidth, targetHeight);
    }
    if (id.startsWith('local-series-')) {
      return localPublicationReader.seriesThumbnail(id, targetWidth, targetHeight);
    }
    const bytes = endpoint.startsWith('/api/v1/series/')
      ? await komgaClient.getSeriesThumbnail(id)
      : await komgaClient.getBookThumbnail(id);
    const imageSource = image.createImageSource(bytes);
    try {
      const info = await imageSource.getImageInfo();
      const widthScale = targetWidth / Math.max(1, info.size.width);
      const heightScale = targetHeight / Math.max(1, info.size.height);
      const requestedScale = this.fit === ImageFit.Contain ? Math.min(widthScale, heightScale) :
        Math.max(widthScale, heightScale);
      const scale = Math.min(1, Math.max(0.01, requestedScale));
      return await imageSource.createPixelMap({
        editable: false,
        desiredPixelFormat: image.PixelMapFormat.RGBA_8888,
        desiredSize: {
          width: Math.max(1, Math.round(info.size.width * scale)),
          height: Math.max(1, Math.round(info.size.height * scale))
        }
      });
    } finally {
      imageSource.release();
    }
  }

  private releaseOwnedImage(): void {
    if (this.pixelMap !== undefined && this.ownedCacheKey.length > 0) {
      coverImageCache.release(this.ownedCacheKey, this.pixelMap);
    }
    this.pixelMap = undefined;
    this.ownedCacheKey = '';
  }

  private extractId(endpoint: string): string {
    const segments = endpoint.split('/');
    const resourceIndex = segments.indexOf('series') >= 0 ? segments.indexOf('series') : segments.indexOf('books');
    return resourceIndex >= 0 && resourceIndex + 1 < segments.length ? segments[resourceIndex + 1] : '';
  }

  private updateTargetSize(widthVp: number, heightVp: number): void {
    const width = Math.max(64, Math.ceil(vp2px(widthVp) / 64) * 64);
    const height = Math.max(64, Math.ceil(vp2px(heightVp) / 64) * 64);
    if (width === this.targetWidthPixels && height === this.targetHeightPixels) return;
    this.targetWidthPixels = width;
    this.targetHeightPixels = height;
    this.currentCacheKey = '';
    this.load();
  }

  build() {
    Stack() {
      Row()
        .width('100%')
        .height('100%')
        .backgroundColor(KomeliaColors.surfaceContainer())
      if (this.source !== undefined) {
        Image(this.source)
          .width('100%')
          .height('100%')
          .syncLoad(true)
          .objectFit(this.fit)
          .interpolation(ImageInterpolation.Medium)
          .draggable(false)
      }
    }
    .width('100%')
    .height('100%')
    .onAreaChange((_, area) => this.updateTargetSize(Number(area.width), Number(area.height)))
  }
}
