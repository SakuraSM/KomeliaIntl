export enum WindowClass {
  COMPACT = 'Compact',
  MEDIUM = 'Medium',
  EXPANDED = 'Expanded',
  FULL = 'Full'
}

export enum AppThemeMode {
  LIGHT = 'LIGHT',
  DARK = 'DARK',
  DARKER = 'DARKER'
}

export enum AppLanguage {
  SYSTEM = 'SYSTEM',
  EN = 'EN',
  ZH_CN = 'ZH_CN'
}

export enum CoverCaptionVariant {
  TITLE_ONLY = 'TitleOnly',
  TITLE_WITH_SUPPORTING = 'TitleWithSupporting'
}

export class KomeliaCoverCaption {
  static height(variant: CoverCaptionVariant, compact: boolean): number {
    if (variant === CoverCaptionVariant.TITLE_WITH_SUPPORTING) return compact ? 68 : 72;
    return compact ? 48 : 52;
  }

  static titleFontSize(compact: boolean): number {
    return compact ? 13 : 14;
  }

  static titleLineHeight(compact: boolean): number {
    return compact ? 18 : 20;
  }

  static supportingLineHeight(compact: boolean): number {
    return compact ? 16 : 18;
  }
}

export class KomeliaDetailCover {
  static readonly aspectRatio: number = 0.703;

  static width(windowClass: WindowClass): number {
    if (windowClass === WindowClass.COMPACT || windowClass === WindowClass.MEDIUM) return 116;
    return 220;
  }

  static height(windowClass: WindowClass): number {
    return Math.round(KomeliaDetailCover.width(windowClass) / KomeliaDetailCover.aspectRatio);
  }
}

/** Android DetailMetadataRow and NoPaddingChip geometry. */
export class KomeliaDetailMetadata {
  static labelWidth(windowClass: WindowClass): number {
    return windowClass === WindowClass.COMPACT || windowClass === WindowClass.MEDIUM ? 96 : 120;
  }

  static readonly rowFontSize: number = 14;
  static readonly rowLineHeight: number = 20;
  static readonly chipFontSize: number = 12;
  static readonly chipLineHeight: number = 16;
  static readonly chipHorizontalPadding: number = 10;
  static readonly chipVerticalPadding: number = 5;
  static readonly chipRadius: number = 12;
  static readonly chipSpacing: number = 5;
}

/** Compact settings controls measured against the Android Compose reference. */
export class KomeliaSettingsField {
  static readonly width: number = 250;
  static readonly height: number = 62;
  static readonly selectHeight: number = 32;
}

/** Compact About-page geometry measured against the Android Compose reference. */
export class KomeliaAboutLayout {
  static readonly repositoryRowHeight: number = 58;
  static readonly sectionSpacing: number = 20;
  static readonly cardVerticalPadding: number = 10;
}

export class KomeliaColors {
  static readonly oledSurface: string = '#08080C';
  static readonly oledSurfaceContainer: string = '#12111A';
  static readonly oledSurfaceContainerHigh: string = '#191824';
  static readonly oledSurfaceHigh: string = '#000000';

  private static isOled(oledMode?: boolean): boolean {
    if (oledMode !== undefined) return oledMode;
    return AppStorage.get<boolean>('oledMode') === true;
  }

  static surface(oledMode?: boolean): ResourceColor {
    return KomeliaColors.isOled(oledMode) ? KomeliaColors.oledSurface : $r('app.color.surface');
  }

  static surfaceContainer(oledMode?: boolean): ResourceColor {
    return KomeliaColors.isOled(oledMode) ? KomeliaColors.oledSurfaceContainer :
      $r('app.color.surface_container');
  }

  static surfaceContainerHigh(oledMode?: boolean): ResourceColor {
    return KomeliaColors.isOled(oledMode) ? KomeliaColors.oledSurfaceContainerHigh :
      $r('app.color.surface_container_high');
  }

  static surfaceHigh(oledMode?: boolean): ResourceColor {
    return KomeliaColors.isOled(oledMode) ? KomeliaColors.oledSurfaceHigh : $r('app.color.surface_high');
  }
}

export interface KomeliaLayoutSpec {
  pageHorizontalPadding: number;
  pageVerticalPadding: number;
  gridSpacing: number;
  sectionSpacing: number;
  itemSpacing: number;
  controlSpacing: number;
  cardContentPadding: number;
  dialogContentPadding: number;
  gridBottomPadding: number;
  minimumTouchTarget: number;
  contentMaxWidth: number;
}

export class KomeliaLayout {
  static readonly spaceXs: number = 4;
  static readonly spaceSm: number = 8;
  static readonly spaceMd: number = 12;
  static readonly spaceLg: number = 16;
  static readonly spaceXl: number = 20;
  static readonly section: number = 24;
  static readonly compactTouch: number = 48;
  static readonly desktopTouch: number = 40;
  static readonly cardRadius: number = 16;
  static readonly dialogRadius: number = 24;
  static readonly contentMaxWidth: number = 1200;

  static windowClass(width: number): WindowClass {
    if (width >= 1200) return WindowClass.FULL;
    if (width >= 840) return WindowClass.EXPANDED;
    if (width >= 600) return WindowClass.MEDIUM;
    return WindowClass.COMPACT;
  }

  static pageHorizontal(windowClass: WindowClass): number {
    if (windowClass === WindowClass.COMPACT) return 12;
    if (windowClass === WindowClass.MEDIUM) return 16;
    return 24;
  }

  static pageVertical(windowClass: WindowClass): number {
    if (windowClass === WindowClass.COMPACT) return 12;
    if (windowClass === WindowClass.MEDIUM) return 16;
    if (windowClass === WindowClass.EXPANDED) return 20;
    return 24;
  }

  static spec(windowClass: WindowClass, desktopPointer: boolean = false): KomeliaLayoutSpec {
    const minimumTouchTarget = desktopPointer ? KomeliaLayout.desktopTouch : KomeliaLayout.compactTouch;
    if (windowClass === WindowClass.COMPACT) {
      return {
        pageHorizontalPadding: 12, pageVerticalPadding: 12, gridSpacing: 12, sectionSpacing: 16,
        itemSpacing: 12, controlSpacing: 8, cardContentPadding: 12, dialogContentPadding: 16,
        gridBottomPadding: 16, minimumTouchTarget: minimumTouchTarget, contentMaxWidth: 600
      };
    }
    if (windowClass === WindowClass.MEDIUM) {
      return {
        pageHorizontalPadding: 16, pageVerticalPadding: 16, gridSpacing: 16, sectionSpacing: 20,
        itemSpacing: 12, controlSpacing: 8, cardContentPadding: 16, dialogContentPadding: 20,
        gridBottomPadding: 20, minimumTouchTarget: minimumTouchTarget, contentMaxWidth: 840
      };
    }
    if (windowClass === WindowClass.EXPANDED) {
      return {
        pageHorizontalPadding: 24, pageVerticalPadding: 20, gridSpacing: 16, sectionSpacing: 24,
        itemSpacing: 12, controlSpacing: 8, cardContentPadding: 16, dialogContentPadding: 24,
        gridBottomPadding: 24, minimumTouchTarget: minimumTouchTarget, contentMaxWidth: 1200
      };
    }
    return {
      pageHorizontalPadding: 24, pageVerticalPadding: 24, gridSpacing: 16, sectionSpacing: 24,
      itemSpacing: 12, controlSpacing: 8, cardContentPadding: 16, dialogContentPadding: 24,
      gridBottomPadding: 24, minimumTouchTarget: minimumTouchTarget, contentMaxWidth: 1200
    };
  }
}

export class KomeliaGrid {
  static readonly minimumCardWidth: number = 150;
  static readonly maximumCardWidth: number = 350;
  static readonly defaultCardWidth: number = 150;

  static normalizedCardWidth(value: number): number {
    if (!Number.isFinite(value)) return KomeliaGrid.defaultCardWidth;
    return Math.min(KomeliaGrid.maximumCardWidth, Math.max(KomeliaGrid.minimumCardWidth,
      Math.round(value / 10) * 10));
  }

  /** Mirrors Android mobile grids: three fixed columns through Medium, adaptive afterwards. */
  static columnCount(availableWidth: number, windowClass: WindowClass, cardWidth: number): number {
    if (windowClass === WindowClass.COMPACT || windowClass === WindowClass.MEDIUM) return 3;
    const layout = KomeliaLayout.spec(windowClass);
    const boundedWidth = Math.min(Math.max(0, availableWidth), layout.contentMaxWidth);
    const contentWidth = Math.max(0, boundedWidth - layout.pageHorizontalPadding * 2);
    const minimum = KomeliaGrid.normalizedCardWidth(cardWidth);
    return Math.max(1, Math.floor((contentWidth + layout.gridSpacing) / (minimum + layout.gridSpacing)));
  }

  static template(columnCount: number): string {
    const safeCount = Math.max(1, Math.floor(columnCount));
    let value = '1fr';
    for (let index = 1; index < safeCount; index += 1) value += ' 1fr';
    return value;
  }
}

export class KomeliaMotion {
  static readonly selection: number = 160;
  static readonly navigation: number = 200;
  static readonly detail: number = 190;

  static duration(base: number, reducedMotion: boolean): number {
    return reducedMotion ? 0 : base;
  }
}
