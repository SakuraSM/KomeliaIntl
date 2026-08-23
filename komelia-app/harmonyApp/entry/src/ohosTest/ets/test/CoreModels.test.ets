import { describe, expect, it } from '@ohos/hypium';
import { KomeliaLayout, KomeliaMotion, WindowClass } from '../../../main/ets/core/design/KomeliaDesign';
import { epubLocatorMapper } from '../../../main/ets/domain/service/EpubLocatorMapper';
import { KomfClient } from '../../../main/ets/network/KomfClient';

export default function coreModelsTest(): void {
  describe('responsive layout and motion', () => {
    it('maps all supported breakpoints', 0, () => {
      expect(KomeliaLayout.windowClass(360)).assertEqual(WindowClass.COMPACT);
      expect(KomeliaLayout.windowClass(600)).assertEqual(WindowClass.MEDIUM);
      expect(KomeliaLayout.windowClass(840)).assertEqual(WindowClass.EXPANDED);
      expect(KomeliaLayout.windowClass(1200)).assertEqual(WindowClass.FULL);
    });

    it('removes motion when requested', 0, () => {
      expect(KomeliaMotion.duration(KomeliaMotion.navigation, true)).assertEqual(0);
      expect(KomeliaMotion.duration(KomeliaMotion.navigation, false)).assertEqual(200);
    });
  });

  describe('EPUB locator mapping', () => {
    it('round trips Reader Kit position', 0, () => {
      const locator = epubLocatorMapper.toLocator(7, '#point(/1/4)', 'chapter-8.xhtml', '第八章');
      const position = epubLocatorMapper.fromLocator(locator);
      expect(position?.resourceIndex).assertEqual(7);
      expect(position?.domPos).assertEqual('#point(/1/4)');
    });
  });

  describe('Komf connection policy', () => {
    it('requires explicit HTTP confirmation', 0, () => {
      const client = new KomfClient();
      expect(() => client.configure('http://192.168.1.10:8085')).assertThrowError();
      client.configure('http://192.168.1.10:8085', true);
    });
  });
}
