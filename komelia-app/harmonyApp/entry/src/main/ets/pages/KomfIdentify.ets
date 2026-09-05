import { router } from '@kit.ArkUI';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { KomeliaColors } from '../core/design/KomeliaDesign';
import { localizedString } from '../core/i18n/LocalizedResources';
import { requiredRouteId } from '../core/navigation/RouteParams';
import { komfRepository } from '../data/repository/DefaultRepositories';
import { configureStoredKomf } from '../domain/service/KomfSession';
import { KomfIdentifyRequest, KomfMetadataSeriesSearchResult } from '../model/KomfModels';

interface KomfIdentifyParams {
  seriesId: string;
  libraryId: string;
  seriesName: string;
}

@Entry
@Component
struct KomfIdentify {
  @State searchName: string = '';
  @State results: KomfMetadataSeriesSearchResult[] = [];
  @State selectedKey: string = '';
  @State searching: boolean = false;
  @State submitting: boolean = false;
  @State searched: boolean = false;
  @State message: string = '';
  private seriesId: string = '';
  private libraryId: string = '';

  aboutToAppear(): void {
    const params = router.getParams() as KomfIdentifyParams | undefined;
    this.seriesId = requiredRouteId(params?.seriesId);
    this.libraryId = requiredRouteId(params?.libraryId);
    this.searchName = params?.seriesName?.trim() || '';
  }

  private resultKey(result: KomfMetadataSeriesSearchResult): string {
    return `${result.provider}:${result.resultId}`;
  }

  private selectedResult(): KomfMetadataSeriesSearchResult | undefined {
    return this.results.find((result: KomfMetadataSeriesSearchResult) =>
      this.resultKey(result) === this.selectedKey);
  }

  private openJob(jobId: string): void {
    router.pushUrl({ url: 'pages/KomfJobDetail', params: { jobId: jobId } });
  }

  private async search(): Promise<void> {
    if (this.searchName.trim().length === 0 || this.searching) return;
    this.searching = true;
    this.searched = false;
    this.message = '';
    this.selectedKey = '';
    try {
      await configureStoredKomf();
      this.results = await komfRepository.searchSeries(this.searchName, this.libraryId, this.seriesId);
      this.searched = true;
    } catch (error) {
      this.message = (error as Error).message;
      this.results = [];
    } finally {
      this.searching = false;
    }
  }

  private async autoIdentify(): Promise<void> {
    if (this.submitting || this.libraryId.length === 0 || this.seriesId.length === 0) return;
    this.submitting = true;
    this.message = '';
    try {
      await configureStoredKomf();
      const response = await komfRepository.identifySeries(this.libraryId, this.seriesId);
      this.openJob(response.jobId);
    } catch (error) {
      this.message = (error as Error).message;
    } finally {
      this.submitting = false;
    }
  }

  private async confirmSelection(): Promise<void> {
    const result = this.selectedResult();
    if (result === undefined || this.submitting) return;
    this.submitting = true;
    this.message = '';
    try {
      await configureStoredKomf();
      const request: KomfIdentifyRequest = {
        libraryId: this.libraryId,
        seriesId: this.seriesId,
        provider: result.provider,
        providerSeriesId: result.resultId
      };
      const response = await komfRepository.manuallyIdentifySeries(request);
      this.openJob(response.jobId);
    } catch (error) {
      this.message = (error as Error).message;
    } finally {
      this.submitting = false;
    }
  }

  @Builder
  private resultCard(result: KomfMetadataSeriesSearchResult) {
    Column({ space: 8 }) {
      Row({ space: 10 }) {
        Column({ space: 4 }) {
          Text(result.title).fontSize(16).fontWeight(FontWeight.Medium).width('100%')
            .maxLines(2).textOverflow({ overflow: TextOverflow.Ellipsis })
          Text(result.provider).fontSize(13).fontColor($r('app.color.primary')).width('100%')
          Text(result.resultId).fontSize(12).fontColor($r('app.color.on_surface_variant')).width('100%')
            .maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        Radio({ value: this.resultKey(result), group: 'komf-identify-result' })
          .checked(this.selectedKey === this.resultKey(result))
          .onChange((checked: boolean) => {
            if (checked) this.selectedKey = this.resultKey(result);
          })
      }.width('100%').alignItems(VerticalAlign.Center)
    }
    .padding(14).width('100%').borderRadius(16)
    .backgroundColor(this.selectedKey === this.resultKey(result) ?
      $r('app.color.primary_container') : KomeliaColors.surfaceHigh())
    .border({ width: this.selectedKey === this.resultKey(result) ? 2 : 1,
      color: this.selectedKey === this.resultKey(result) ? $r('app.color.primary') : $r('app.color.outline') })
    .onClick(() => this.selectedKey = this.resultKey(result))
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) {
          Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface'))
        }.width(48).height(48).backgroundColor(Color.Transparent).onClick(() => router.back())
          .accessibilityText($r('app.string.back'))
        Text($r('app.string.komf_identify_page_title')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
      }.height(64).width('100%')

      Scroll() {
        Column({ space: 16 }) {
          Column({ space: 10 }) {
            Text($r('app.string.komf_identify_description')).fontSize(13)
              .fontColor($r('app.color.on_surface_variant')).width('100%')
            Row({ space: 8 }) {
              TextInput({ text: this.searchName, placeholder: $r('app.string.komf_identify_search_hint') })
                .layoutWeight(1).height(48).enterKeyType(EnterKeyType.Search)
                .onChange((value: string) => this.searchName = value)
                .onSubmit(() => this.search())
              Button(this.searching ? $r('app.string.loading') : $r('app.string.search'))
                .height(48).enabled(!this.searching && !this.submitting && this.searchName.trim().length > 0)
                .onClick(() => this.search())
            }.width('100%')
            Button(this.submitting ? $r('app.string.loading') : $r('app.string.komf_auto_identify'))
              .height(48).width('100%').enabled(!this.searching && !this.submitting)
              .onClick(() => this.autoIdentify())
          }.padding(16).width('100%').alignItems(HorizontalAlign.Start)
            .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
            .border({ width: 1, color: $r('app.color.outline') })

          if (this.results.length > 0) {
            Text(localizedString($r('app.string.komf_search_results_count'), this.results.length))
              .fontSize(18).fontWeight(FontWeight.Medium).width('100%')
            ForEach(this.results, (result: KomfMetadataSeriesSearchResult) => {
              this.resultCard(result)
            }, (result: KomfMetadataSeriesSearchResult) => this.resultKey(result))
            Button(this.submitting ? $r('app.string.loading') : $r('app.string.komf_confirm_identify'))
              .height(48).width('100%').enabled(this.selectedKey.length > 0 && !this.submitting)
              .onClick(() => this.confirmSelection())
          } else if (this.searched) {
            Text($r('app.string.komf_no_results')).fontSize(14)
              .fontColor($r('app.color.on_surface_variant')).width('100%').textAlign(TextAlign.Center)
              .padding({ top: 28, bottom: 28 })
          }
          if (this.message.length > 0) {
            Text(this.message).fontSize(13).fontColor($r('app.color.error')).width('100%')
          }
        }.padding({ left: 12, right: 12, bottom: 32 }).width('100%').constraintSize({ maxWidth: 840 })
      }.layoutWeight(1).width('100%').align(Alignment.Top)
    }.width('100%').height('100%').align(Alignment.Top).id('komf_identify_page')
      .justifyContent(FlexAlign.Start).backgroundColor(KomeliaColors.surface())
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      AppNavigationFrame({ content: () => { this.pageContent(); } })
    }.width('100%').height('100%')
  }
}
