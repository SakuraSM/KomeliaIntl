import { window } from '@kit.ArkUI';

export function preferredAppOrientation(): window.Orientation {
  return window.Orientation.AUTO_ROTATION_RESTRICTED;
}
