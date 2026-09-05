import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { requiredRouteId } from '../core/navigation/RouteParams';
import { localizedString } from '../core/i18n/LocalizedResources';
import { adminRepository, catalogRepository, komfRepository } from '../data/repository/DefaultRepositories';
import { configureStoredKomf, hasStoredKomfConfiguration } from '../domain/service/KomfSession';
import { KomgaLibrary, KomgaLibraryCreateRequest, KomgaLibraryUpdateRequest } from '../model/KomgaModels';
import { AppState } from '../state/AppState';

interface LibraryEditParams { libraryId?: string; }

@Entry
@Component
struct LibraryEdit {
  @State name: string = '';
  @State root: string = '';
  @State oneshotsDirectory: string = '';
  @State scanDirectoryExclusions: string = '#recycle, @eaDir, @Recycle';
  @State scanInterval: string = 'EVERY_6H';
  @State seriesCover: string = 'FIRST';
  @State scanOnStartup: boolean = false;
  @State scanForceModifiedTime: boolean = false;
  @State scanCbx: boolean = true;
  @State scanPdf: boolean = true;
  @State scanEpub: boolean = true;
  @State emptyTrashAfterScan: boolean = false;
  @State hashFiles: boolean = true;
  @State hashPages: boolean = false;
  @State analyzeDimensions: boolean = true;
  @State repairExtensions: boolean = false;
  @State convertToCbz: boolean = false;
  @State importComicInfoBook: boolean = true;
  @State importComicInfoSeries: boolean = true;
  @State importComicInfoSeriesAppendVolume: boolean = true;
  @State importComicInfoCollection: boolean = true;
  @State importComicInfoReadList: boolean = true;
  @State importEpubBook: boolean = true;
  @State importEpubSeries: boolean = true;
  @State importMylarSeries: boolean = true;
  @State importLocalArtwork: boolean = true;
  @State importBarcodeIsbn: boolean = false;
  @State message: string = '';
  @State saving: boolean = false;
  @State komfRunning: boolean = false;
  @State komfConfigured: boolean = false;
  private libraryId: string = '';

  aboutToAppear(): void {
    const params = router.getParams() as LibraryEditParams | undefined;
    this.libraryId = requiredRouteId(params?.libraryId);
    if (this.libraryId.length === 0) return;
    const value = AppState.getInstance().libraries.find((item: KomgaLibrary) => item.id === this.libraryId);
    if (value === undefined) return;
    this.name = value.name;
    this.root = value.root;
    this.oneshotsDirectory = value.oneshotsDirectory || '';
    this.scanDirectoryExclusions = (value.scanDirectoryExclusions || []).join(', ');
    this.scanInterval = value.scanInterval || 'EVERY_6H';
    this.seriesCover = value.seriesCover || 'FIRST';
    this.scanOnStartup = this.bool(value.scanOnStartup, false);
    this.scanForceModifiedTime = this.bool(value.scanForceModifiedTime, false);
    this.scanCbx = this.bool(value.scanCbx, true);
    this.scanPdf = this.bool(value.scanPdf, true);
    this.scanEpub = this.bool(value.scanEpub, true);
    this.emptyTrashAfterScan = this.bool(value.emptyTrashAfterScan, false);
    this.hashFiles = this.bool(value.hashFiles, true);
    this.hashPages = this.bool(value.hashPages, false);
    this.analyzeDimensions = this.bool(value.analyzeDimensions, true);
    this.repairExtensions = this.bool(value.repairExtensions, false);
    this.convertToCbz = this.bool(value.convertToCbz, false);
    this.importComicInfoBook = this.bool(value.importComicInfoBook, true);
    this.importComicInfoSeries = this.bool(value.importComicInfoSeries, true);
    this.importComicInfoSeriesAppendVolume = this.bool(value.importComicInfoSeriesAppendVolume, true);
    this.importComicInfoCollection = this.bool(value.importComicInfoCollection, true);
    this.importComicInfoReadList = this.bool(value.importComicInfoReadList, true);
    this.importEpubBook = this.bool(value.importEpubBook, true);
    this.importEpubSeries = this.bool(value.importEpubSeries, true);
    this.importMylarSeries = this.bool(value.importMylarSeries, true);
    this.importLocalArtwork = this.bool(value.importLocalArtwork, true);
    this.importBarcodeIsbn = this.bool(value.importBarcodeIsbn, false);
    this.restoreKomfAvailability();
  }

  private async restoreKomfAvailability(): Promise<void> {
    this.komfConfigured = await hasStoredKomfConfiguration();
  }

  private bool(value: boolean | undefined, fallback: boolean): boolean {
    return value === undefined ? fallback : value;
  }

  private list(value: string): string[] {
    return value.split(/[,，、]/).map((item: string) => item.trim())
      .filter((item: string) => item.length > 0);
  }

  private request(): KomgaLibraryCreateRequest {
    return {
      name: this.name.trim(), root: this.root.trim(),
      importComicInfoBook: this.importComicInfoBook,
      importComicInfoSeries: this.importComicInfoSeries,
      importComicInfoSeriesAppendVolume: this.importComicInfoSeriesAppendVolume,
      importComicInfoCollection: this.importComicInfoCollection,
      importComicInfoReadList: this.importComicInfoReadList,
      importEpubBook: this.importEpubBook, importEpubSeries: this.importEpubSeries,
      importMylarSeries: this.importMylarSeries, importLocalArtwork: this.importLocalArtwork,
      importBarcodeIsbn: this.importBarcodeIsbn, scanForceModifiedTime: this.scanForceModifiedTime,
      repairExtensions: this.repairExtensions, convertToCbz: this.convertToCbz,
      emptyTrashAfterScan: this.emptyTrashAfterScan, seriesCover: this.seriesCover,
      hashFiles: this.hashFiles, hashPages: this.hashPages, analyzeDimensions: this.analyzeDimensions,
      scanOnStartup: this.scanOnStartup, scanCbx: this.scanCbx, scanPdf: this.scanPdf,
      scanEpub: this.scanEpub, scanInterval: this.scanInterval,
      oneshotsDirectory: this.oneshotsDirectory.trim(),
      scanDirectoryExclusions: this.list(this.scanDirectoryExclusions)
    };
  }

  private updateRequest(): KomgaLibraryUpdateRequest {
    return {
      name: this.name.trim(), root: this.root.trim(),
      importComicInfoBook: this.importComicInfoBook,
      importComicInfoSeries: this.importComicInfoSeries,
      importComicInfoSeriesAppendVolume: this.importComicInfoSeriesAppendVolume,
      importComicInfoCollection: this.importComicInfoCollection,
      importComicInfoReadList: this.importComicInfoReadList,
      importEpubBook: this.importEpubBook, importEpubSeries: this.importEpubSeries,
      importMylarSeries: this.importMylarSeries, importLocalArtwork: this.importLocalArtwork,
      importBarcodeIsbn: this.importBarcodeIsbn, scanForceModifiedTime: this.scanForceModifiedTime,
      repairExtensions: this.repairExtensions, convertToCbz: this.convertToCbz,
      emptyTrashAfterScan: this.emptyTrashAfterScan, seriesCover: this.seriesCover,
      hashFiles: this.hashFiles, hashPages: this.hashPages, analyzeDimensions: this.analyzeDimensions,
      scanOnStartup: this.scanOnStartup, scanCbx: this.scanCbx, scanPdf: this.scanPdf,
      scanEpub: this.scanEpub, scanInterval: this.scanInterval,
      oneshotsDirectory: this.oneshotsDirectory.trim(),
      scanDirectoryExclusions: this.list(this.scanDirectoryExclusions)
    };
  }

  private async save(): Promise<void> {
    if (this.name.trim().length === 0 || this.root.trim().length === 0) {
      this.message = localizedString($r('app.string.library_name_root_required'));
      return;
    }
    this.saving = true;
    this.message = '';
    try {
      if (this.libraryId.length === 0) await adminRepository.addLibrary(this.request());
      else await adminRepository.updateLibrary(this.libraryId, this.updateRequest());
      AppState.getInstance().libraries = await catalogRepository.libraries();
      this.message = localizedString($r('app.string.library_saved'));
    } catch (error) {
      this.message = (error as Error).message;
    } finally {
      this.saving = false;
    }
  }

  private confirmDelete(): void {
    this.getUIContext().showAlertDialog({
      title: $r('app.string.delete_library'),
      message: $r('app.string.delete_library_confirmation'),
      buttons: [
        { value: $r('app.string.cancel'), action: () => {} },
        { value: $r('app.string.delete'), style: DialogButtonStyle.HIGHLIGHT,
          fontColor: $r('app.color.error'), action: () => {
          adminRepository.deleteLibrary(this.libraryId).then(async () => {
            AppState.getInstance().libraries = await catalogRepository.libraries();
            router.back();
          }).catch((error: Error) => this.message = error.message);
        } }
      ]
    });
  }

  private confirmKomfIdentify(): void {
    this.getUIContext().showAlertDialog({
      title: $r('app.string.komf_library_auto_identify'),
      message: $r('app.string.komf_library_auto_identify_confirmation'),
      autoCancel: true,
      buttons: [
        { value: $r('app.string.cancel'), action: () => {} },
        { value: $r('app.string.confirm'), style: DialogButtonStyle.HIGHLIGHT, action: () => {
          this.identifyLibrary().catch((error: Error) => this.message = error.message);
        } }
      ]
    });
  }

  private async identifyLibrary(): Promise<void> {
    if (this.libraryId.length === 0 || this.komfRunning) return;
    this.komfRunning = true;
    this.message = '';
    try {
      await configureStoredKomf();
      const response = await komfRepository.identifyLibrary(this.libraryId);
      router.pushUrl({ url: 'pages/KomfJobDetail', params: { jobId: response.jobId } });
    } catch (error) {
      this.message = (error as Error).message;
    } finally {
      this.komfRunning = false;
    }
  }

  private openKomfReset(): void {
    if (this.libraryId.length === 0) return;
    router.pushUrl({ url: 'pages/KomfResetMetadata', params: {
      scope: 'library', libraryId: this.libraryId, displayName: this.name
    } });
  }

  private scanIntervalOptions(): SelectOption[] {
    return [
      { value: localizedString($r('app.string.scan_disabled')) },
      { value: localizedString($r('app.string.scan_hourly')) },
      { value: localizedString($r('app.string.scan_every_6h')) },
      { value: localizedString($r('app.string.scan_every_12h')) },
      { value: localizedString($r('app.string.scan_daily')) },
      { value: localizedString($r('app.string.scan_weekly')) }
    ];
  }

  private scanIntervalIndex(): number {
    const values = ['DISABLED', 'HOURLY', 'EVERY_6H', 'EVERY_12H', 'DAILY', 'WEEKLY'];
    const index = values.indexOf(this.scanInterval);
    return index < 0 ? 2 : index;
  }

  private coverOptions(): SelectOption[] {
    return [
      { value: localizedString($r('app.string.series_cover_first')) },
      { value: localizedString($r('app.string.series_cover_first_unread_first')) },
      { value: localizedString($r('app.string.series_cover_first_unread_last')) },
      { value: localizedString($r('app.string.series_cover_last')) }
    ];
  }

  private coverIndex(): number {
    const values = ['FIRST', 'FIRST_UNREAD_OR_FIRST', 'FIRST_UNREAD_OR_LAST', 'LAST'];
    const index = values.indexOf(this.seriesCover);
    return index < 0 ? 0 : index;
  }

  @Builder
  private sectionTitle(title: ResourceStr, description: ResourceStr) {
    Column({ space: 3 }) {
      Text(title).fontSize(17).fontWeight(FontWeight.Bold).width('100%')
      Text(description).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
    }.alignItems(HorizontalAlign.Start).width('100%')
  }

  @Builder
  private switchRow(title: ResourceStr, value: boolean, onChange: (value: boolean) => void) {
    Row({ space: 12 }) {
      Text(title).fontSize(15).layoutWeight(1)
      Toggle({ type: ToggleType.Switch, isOn: value }).onChange(onChange)
    }.width('100%').constraintSize({ minHeight: 48 })
  }

  @Builder
  private field(label: ResourceStr, value: string, placeholder: ResourceStr,
    onChange: (value: string) => void) {
    Column({ space: 6 }) {
      Text(label).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
      TextInput({ text: value, placeholder: placeholder }).height(48).width('100%').onChange(onChange)
    }.alignItems(HorizontalAlign.Start).width('100%')
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48).fontColor($r('app.color.on_surface'))
          .backgroundColor(Color.Transparent).onClick(() => router.back()).accessibilityText($r('app.string.back'))
        Text(this.libraryId.length === 0 ? $r('app.string.add_library') : $r('app.string.edit_library'))
          .fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        Button(this.saving ? $r('app.string.saving') : $r('app.string.save_changes'))
          .height(44).enabled(!this.saving).onClick(() => this.save())
      }.height(64).width('100%')
      Scroll() {
        Column({ space: 16 }) {
          Column({ space: 10 }) {
            this.sectionTitle($r('app.string.library_general'), $r('app.string.library_general_description'))
            this.field($r('app.string.library_name'), this.name, '', (value: string) => this.name = value)
            this.field($r('app.string.server_root'), this.root, '/media/books', (value: string) => this.root = value)
            this.field($r('app.string.oneshots_directory'), this.oneshotsDirectory, '',
              (value: string) => this.oneshotsDirectory = value)
          }.padding(16).width('100%').alignItems(HorizontalAlign.Start)
            .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
            .border({ width: 1, color: $r('app.color.outline') })
          Column({ space: 10 }) {
            this.sectionTitle($r('app.string.library_scanner'), $r('app.string.library_scanner_description'))
            Column({ space: 6 }) {
              Text($r('app.string.scan_interval')).fontSize(13)
                .fontColor($r('app.color.on_surface_variant')).width('100%')
              Select(this.scanIntervalOptions()).selected(this.scanIntervalIndex())
                .value(this.scanIntervalOptions()[this.scanIntervalIndex()].value).height(48).width('100%')
                .onSelect((index: number) => {
                  this.scanInterval = ['DISABLED', 'HOURLY', 'EVERY_6H', 'EVERY_12H', 'DAILY', 'WEEKLY'][index];
                })
            }.width('100%')
            this.field($r('app.string.scan_directory_exclusions'), this.scanDirectoryExclusions,
              $r('app.string.scan_directory_exclusions_hint'),
              (value: string) => this.scanDirectoryExclusions = value)
            this.switchRow($r('app.string.scan_on_startup'), this.scanOnStartup,
              (value: boolean) => this.scanOnStartup = value)
            this.switchRow($r('app.string.scan_force_modified_time'), this.scanForceModifiedTime,
              (value: boolean) => this.scanForceModifiedTime = value)
            this.switchRow($r('app.string.scan_cbx'), this.scanCbx, (value: boolean) => this.scanCbx = value)
            this.switchRow($r('app.string.scan_pdf'), this.scanPdf, (value: boolean) => this.scanPdf = value)
            this.switchRow($r('app.string.scan_epub'), this.scanEpub, (value: boolean) => this.scanEpub = value)
            this.switchRow($r('app.string.empty_trash_after_scan'), this.emptyTrashAfterScan,
              (value: boolean) => this.emptyTrashAfterScan = value)
          }.padding(16).width('100%').alignItems(HorizontalAlign.Start)
            .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
            .border({ width: 1, color: $r('app.color.outline') })
          Column({ space: 10 }) {
            this.sectionTitle($r('app.string.library_processing'), $r('app.string.library_processing_description'))
            Column({ space: 6 }) {
              Text($r('app.string.series_cover_policy')).fontSize(13)
                .fontColor($r('app.color.on_surface_variant')).width('100%')
              Select(this.coverOptions()).selected(this.coverIndex()).value(this.coverOptions()[this.coverIndex()].value)
                .height(48).width('100%').onSelect((index: number) => {
                this.seriesCover = ['FIRST', 'FIRST_UNREAD_OR_FIRST', 'FIRST_UNREAD_OR_LAST', 'LAST'][index];
              })
            }.width('100%')
            this.switchRow($r('app.string.hash_files'), this.hashFiles, (value: boolean) => this.hashFiles = value)
            this.switchRow($r('app.string.hash_pages'), this.hashPages, (value: boolean) => this.hashPages = value)
            this.switchRow($r('app.string.analyze_dimensions'), this.analyzeDimensions,
              (value: boolean) => this.analyzeDimensions = value)
            this.switchRow($r('app.string.repair_extensions'), this.repairExtensions,
              (value: boolean) => this.repairExtensions = value)
            this.switchRow($r('app.string.convert_to_cbz'), this.convertToCbz,
              (value: boolean) => this.convertToCbz = value)
          }.padding(16).width('100%').alignItems(HorizontalAlign.Start)
            .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
            .border({ width: 1, color: $r('app.color.outline') })
          Column({ space: 10 }) {
            this.sectionTitle($r('app.string.library_metadata_import'),
              $r('app.string.library_metadata_import_description'))
            this.switchRow($r('app.string.import_comicinfo_book'), this.importComicInfoBook,
              (value: boolean) => this.importComicInfoBook = value)
            this.switchRow($r('app.string.import_comicinfo_series'), this.importComicInfoSeries,
              (value: boolean) => this.importComicInfoSeries = value)
            this.switchRow($r('app.string.append_comicinfo_volume'), this.importComicInfoSeriesAppendVolume,
              (value: boolean) => this.importComicInfoSeriesAppendVolume = value)
            this.switchRow($r('app.string.import_comicinfo_collection'), this.importComicInfoCollection,
              (value: boolean) => this.importComicInfoCollection = value)
            this.switchRow($r('app.string.import_comicinfo_readlist'), this.importComicInfoReadList,
              (value: boolean) => this.importComicInfoReadList = value)
            this.switchRow($r('app.string.import_epub_book'), this.importEpubBook,
              (value: boolean) => this.importEpubBook = value)
            this.switchRow($r('app.string.import_epub_series'), this.importEpubSeries,
              (value: boolean) => this.importEpubSeries = value)
            this.switchRow($r('app.string.import_mylar_series'), this.importMylarSeries,
              (value: boolean) => this.importMylarSeries = value)
            this.switchRow($r('app.string.import_local_artwork'), this.importLocalArtwork,
              (value: boolean) => this.importLocalArtwork = value)
            this.switchRow($r('app.string.import_barcode_isbn'), this.importBarcodeIsbn,
              (value: boolean) => this.importBarcodeIsbn = value)
          }.padding(16).width('100%').alignItems(HorizontalAlign.Start)
            .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
            .border({ width: 1, color: $r('app.color.outline') })
          if (this.libraryId.length > 0 && this.komfConfigured) {
            Column({ space: 10 }) {
              this.sectionTitle($r('app.string.komf_library_maintenance'),
                $r('app.string.komf_library_maintenance_description'))
              Button(this.komfRunning ? $r('app.string.loading') :
                $r('app.string.komf_library_auto_identify')).height(48).width('100%')
                .enabled(!this.komfRunning).onClick(() => this.confirmKomfIdentify())
              Button($r('app.string.komf_library_reset')).height(48).width('100%')
                .enabled(!this.komfRunning).fontColor($r('app.color.error'))
                .backgroundColor(KomeliaColors.surface()).onClick(() => this.openKomfReset())
            }.padding(16).width('100%').alignItems(HorizontalAlign.Start)
              .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
              .border({ width: 1, color: $r('app.color.outline') })
            Button($r('app.string.delete_library')).height(48).width('100%').fontColor($r('app.color.error'))
              .backgroundColor(KomeliaColors.surfaceHigh()).onClick(() => this.confirmDelete())
          }
          if (this.message.length > 0) {
            Text(this.message).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
          }
        }.padding({ left: 12, right: 12, bottom: 32 }).width('100%').constraintSize({ maxWidth: 840 })
      }.layoutWeight(1).width('100%').align(Alignment.Top)
    }.width('100%').height('100%').align(Alignment.Top)
      .justifyContent(FlexAlign.Start).backgroundColor(KomeliaColors.surface())
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      AppNavigationFrame({ content: () => { this.pageContent(); } })
    }
    .width('100%')
    .height('100%')
  }
}
