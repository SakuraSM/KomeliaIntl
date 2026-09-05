import { AppPreferences } from '../persistence/AppPreferences';

export enum ImageReaderType {
  PAGED = 'PAGED',
  PANELS = 'PANELS',
  CONTINUOUS = 'CONTINUOUS'
}

export enum PagedReadingDirection {
  LEFT_TO_RIGHT = 'LEFT_TO_RIGHT',
  RIGHT_TO_LEFT = 'RIGHT_TO_LEFT'
}

export enum ContinuousReadingDirection {
  TOP_TO_BOTTOM = 'TOP_TO_BOTTOM',
  LEFT_TO_RIGHT = 'LEFT_TO_RIGHT',
  RIGHT_TO_LEFT = 'RIGHT_TO_LEFT'
}

export enum PageDisplayLayout {
  SINGLE_PAGE = 'SINGLE_PAGE',
  DOUBLE_PAGES = 'DOUBLE_PAGES',
  DOUBLE_PAGES_NO_COVER = 'DOUBLE_PAGES_NO_COVER'
}

export enum LayoutScaleType {
  SCREEN = 'SCREEN',
  FIT_WIDTH = 'FIT_WIDTH',
  FIT_HEIGHT = 'FIT_HEIGHT',
  ORIGINAL = 'ORIGINAL'
}

export enum ReaderFlashColor {
  BLACK = 'BLACK',
  WHITE = 'WHITE',
  WHITE_AND_BLACK = 'WHITE_AND_BLACK'
}

export interface ImageReaderSettingsValue {
  readerType: ImageReaderType;
  pagedDirection: PagedReadingDirection;
  continuousDirection: ContinuousReadingDirection;
  pageLayout: PageDisplayLayout;
  scaleType: LayoutScaleType;
  layoutOffset: boolean;
  sidePadding: number;
  pageSpacing: number;
  stretchSmallImages: boolean;
  cropBorders: boolean;
  colorCorrection: boolean;
  aiEnabled: boolean;
  flashEnabled: boolean;
  flashDuration: number;
  flashEveryNPages: number;
  flashColor: ReaderFlashColor;
}

export function defaultImageReaderSettings(): ImageReaderSettingsValue {
  return {
    readerType: ImageReaderType.PAGED,
    pagedDirection: PagedReadingDirection.LEFT_TO_RIGHT,
    continuousDirection: ContinuousReadingDirection.TOP_TO_BOTTOM,
    pageLayout: PageDisplayLayout.SINGLE_PAGE,
    scaleType: LayoutScaleType.SCREEN,
    layoutOffset: false,
    sidePadding: 0,
    pageSpacing: 0,
    stretchSmallImages: true,
    cropBorders: false,
    colorCorrection: false,
    aiEnabled: false,
    flashEnabled: false,
    flashDuration: 100,
    flashEveryNPages: 1,
    flashColor: ReaderFlashColor.BLACK
  };
}

export class ImageReaderPreferences {
  static async load(): Promise<ImageReaderSettingsValue> {
    const legacyMode = Number(await AppPreferences.getString('image.mode', '0'));
    const legacyDirection = Number(await AppPreferences.getString('image.direction', '0'));
    const defaultReaderType = legacyMode === 1 ? ImageReaderType.CONTINUOUS :
      legacyMode === 3 ? ImageReaderType.PANELS : ImageReaderType.PAGED;
    const defaultLayout = legacyMode === 2 ? PageDisplayLayout.DOUBLE_PAGES :
      PageDisplayLayout.SINGLE_PAGE;
    const defaultPagedDirection = legacyDirection === 1 ? PagedReadingDirection.RIGHT_TO_LEFT :
      PagedReadingDirection.LEFT_TO_RIGHT;
    const defaultContinuousDirection = legacyDirection === 1 ? ContinuousReadingDirection.RIGHT_TO_LEFT :
      legacyDirection === 0 ? ContinuousReadingDirection.LEFT_TO_RIGHT :
        ContinuousReadingDirection.TOP_TO_BOTTOM;
    return {
      readerType: ImageReaderPreferences.readerType(await AppPreferences.getString('image.readerType'),
        defaultReaderType),
      pagedDirection: ImageReaderPreferences.pagedDirection(
        await AppPreferences.getString('image.pagedDirection'),
        defaultPagedDirection),
      continuousDirection: ImageReaderPreferences.continuousDirection(
        await AppPreferences.getString('image.continuousDirection'), defaultContinuousDirection),
      pageLayout: ImageReaderPreferences.pageLayout(await AppPreferences.getString('image.pageLayout'),
        defaultLayout),
      scaleType: ImageReaderPreferences.scaleType(await AppPreferences.getString('image.scaleType'),
        LayoutScaleType.SCREEN),
      layoutOffset: await AppPreferences.getBoolean('image.layoutOffset'),
      sidePadding: ImageReaderPreferences.numberInRange(
        await AppPreferences.getString('image.sidePadding', '0'), 0, 0.4, 0),
      pageSpacing: ImageReaderPreferences.numberInRange(
        await AppPreferences.getString('image.pageSpacing', '0'), 0, 500, 0),
      stretchSmallImages: await AppPreferences.getBoolean('image.stretchSmallImages', true),
      cropBorders: await AppPreferences.getBoolean('image.cropBorders'),
      colorCorrection: await AppPreferences.getBoolean('image.colorCorrection'),
      aiEnabled: await AppPreferences.getBoolean('image.aiEnabled'),
      flashEnabled: await AppPreferences.getBoolean('image.flashEnabled'),
      flashDuration: ImageReaderPreferences.numberInRange(
        await AppPreferences.getString('image.flashDuration', '100'), 100, 1500, 100),
      flashEveryNPages: ImageReaderPreferences.numberInRange(
        await AppPreferences.getString('image.flashEveryNPages', '1'), 1, 10, 1),
      flashColor: ImageReaderPreferences.flashColor(await AppPreferences.getString('image.flashColor'),
        ReaderFlashColor.BLACK)
    };
  }

  static async save(value: ImageReaderSettingsValue): Promise<void> {
    const legacyMode = value.readerType === ImageReaderType.CONTINUOUS ? 1 :
      value.readerType === ImageReaderType.PANELS ? 3 :
        value.pageLayout === PageDisplayLayout.SINGLE_PAGE ? 0 : 2;
    const legacyDirection = value.readerType === ImageReaderType.CONTINUOUS
      ? value.continuousDirection === ContinuousReadingDirection.RIGHT_TO_LEFT ? 1 :
        value.continuousDirection === ContinuousReadingDirection.TOP_TO_BOTTOM ? 2 : 0
      : value.pagedDirection === PagedReadingDirection.RIGHT_TO_LEFT ? 1 : 0;
    await Promise.all([
      AppPreferences.putString('image.readerType', value.readerType),
      AppPreferences.putString('image.pagedDirection', value.pagedDirection),
      AppPreferences.putString('image.continuousDirection', value.continuousDirection),
      AppPreferences.putString('image.pageLayout', value.pageLayout),
      AppPreferences.putString('image.scaleType', value.scaleType),
      AppPreferences.putBoolean('image.layoutOffset', value.layoutOffset),
      AppPreferences.putString('image.sidePadding', `${value.sidePadding}`),
      AppPreferences.putString('image.pageSpacing', `${Math.round(value.pageSpacing)}`),
      AppPreferences.putBoolean('image.stretchSmallImages', value.stretchSmallImages),
      AppPreferences.putBoolean('image.cropBorders', value.cropBorders),
      AppPreferences.putBoolean('image.colorCorrection', value.colorCorrection),
      AppPreferences.putBoolean('image.aiEnabled', value.aiEnabled),
      AppPreferences.putBoolean('image.flashEnabled', value.flashEnabled),
      AppPreferences.putString('image.flashDuration', `${Math.round(value.flashDuration)}`),
      AppPreferences.putString('image.flashEveryNPages', `${Math.round(value.flashEveryNPages)}`),
      AppPreferences.putString('image.flashColor', value.flashColor),
      AppPreferences.putString('image.mode', `${legacyMode}`),
      AppPreferences.putString('image.direction', `${legacyDirection}`)
    ]);
  }

  private static readerType(value: string, fallback: ImageReaderType): ImageReaderType {
    if (value === ImageReaderType.PAGED || value === ImageReaderType.PANELS ||
      value === ImageReaderType.CONTINUOUS) return value as ImageReaderType;
    return fallback;
  }

  private static pagedDirection(value: string, fallback: PagedReadingDirection): PagedReadingDirection {
    if (value === PagedReadingDirection.LEFT_TO_RIGHT || value === PagedReadingDirection.RIGHT_TO_LEFT) {
      return value as PagedReadingDirection;
    }
    return fallback;
  }

  private static continuousDirection(value: string,
    fallback: ContinuousReadingDirection): ContinuousReadingDirection {
    if (value === ContinuousReadingDirection.TOP_TO_BOTTOM ||
      value === ContinuousReadingDirection.LEFT_TO_RIGHT ||
      value === ContinuousReadingDirection.RIGHT_TO_LEFT) return value as ContinuousReadingDirection;
    return fallback;
  }

  private static pageLayout(value: string, fallback: PageDisplayLayout): PageDisplayLayout {
    if (value === PageDisplayLayout.SINGLE_PAGE || value === PageDisplayLayout.DOUBLE_PAGES ||
      value === PageDisplayLayout.DOUBLE_PAGES_NO_COVER) return value as PageDisplayLayout;
    return fallback;
  }

  private static scaleType(value: string, fallback: LayoutScaleType): LayoutScaleType {
    if (value === LayoutScaleType.SCREEN || value === LayoutScaleType.FIT_WIDTH ||
      value === LayoutScaleType.FIT_HEIGHT || value === LayoutScaleType.ORIGINAL) {
      return value as LayoutScaleType;
    }
    return fallback;
  }

  private static flashColor(value: string, fallback: ReaderFlashColor): ReaderFlashColor {
    if (value === ReaderFlashColor.BLACK || value === ReaderFlashColor.WHITE ||
      value === ReaderFlashColor.WHITE_AND_BLACK) return value as ReaderFlashColor;
    return fallback;
  }

  private static numberInRange(value: string, minimum: number, maximum: number, fallback: number): number {
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) return fallback;
    return Math.max(minimum, Math.min(maximum, parsed));
  }
}
