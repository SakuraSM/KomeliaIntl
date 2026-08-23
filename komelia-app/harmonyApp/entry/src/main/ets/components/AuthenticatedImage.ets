import { image } from '@kit.ImageKit';
import { komgaClient } from '../network/KomgaClient';

@Component
export struct AuthenticatedImage {
  @Prop endpoint: string = '';
  @Prop fit: ImageFit = ImageFit.Cover;
  @State source: PixelMap | Resource = $r('app.media.app_icon');
  private currentEndpoint: string = '';

  aboutToAppear(): void {
    this.load();
  }

  private async load(): Promise<void> {
    if (this.endpoint.length === 0 || this.currentEndpoint === this.endpoint) {
      return;
    }
    this.currentEndpoint = this.endpoint;
    try {
      const bytes = this.endpoint.startsWith('/api/v1/series/')
        ? await komgaClient.getSeriesThumbnail(this.extractId())
        : await komgaClient.getBookThumbnail(this.extractId());
      const imageSource = image.createImageSource(bytes);
      this.source = await imageSource.createPixelMap({ editable: false });
      imageSource.release();
    } catch (_) {
      this.source = $r('app.media.app_icon');
    }
  }

  private extractId(): string {
    const segments = this.endpoint.split('/');
    const resourceIndex = segments.indexOf('series') >= 0 ? segments.indexOf('series') : segments.indexOf('books');
    return resourceIndex >= 0 && resourceIndex + 1 < segments.length ? segments[resourceIndex + 1] : '';
  }

  build() {
    Image(this.source)
      .width('100%')
      .height('100%')
      .objectFit(this.fit)
      .interpolation(ImageInterpolation.High)
      .draggable(false)
  }
}
