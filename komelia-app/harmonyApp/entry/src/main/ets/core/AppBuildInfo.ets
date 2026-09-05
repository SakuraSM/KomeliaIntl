/**
 * Runtime build metadata shared by native HarmonyOS surfaces and network clients.
 * Keep this public SemVer aligned with gradle/libs.versions.toml. The HarmonyOS
 * package version in AppScope/app.json5 uses a numeric AppGallery mapping.
 */
export class AppBuildInfo {
  static readonly versionName: string = '0.25.0';
  static readonly userAgent: string = `Komelia-Harmony/${AppBuildInfo.versionName}`;
}
