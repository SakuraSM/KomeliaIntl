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
}

export class KomeliaMotion {
  static readonly selection: number = 160;
  static readonly navigation: number = 200;
  static readonly detail: number = 190;

  static duration(base: number, reducedMotion: boolean): number {
    return reducedMotion ? 0 : base;
  }
}
