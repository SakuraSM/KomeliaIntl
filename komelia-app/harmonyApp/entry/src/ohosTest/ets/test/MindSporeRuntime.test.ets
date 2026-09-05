import { Want, common } from '@kit.AbilityKit';
import { image } from '@kit.ImageKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { abilityDelegatorRegistry } from '@kit.TestKit';
import { describe, expect, it } from '@ohos/hypium';
import { AppContainer } from '../../../main/ets/core/di/AppContainer';
import { mindSporePanelDetector } from '../../../main/ets/domain/service/MindSporePanelDetector';
import { mindSporeSuperResolution } from '../../../main/ets/domain/service/MindSporeSuperResolution';
import { PagedReadingDirection } from '../../../main/ets/model/ImageReaderModels';

const DOMAIN: number = 0x0000;
const TAG: string = 'KomeliaMindSporeTest';
const SAMPLE_WIDTH: number = 32;
const SAMPLE_HEIGHT: number = 48;

async function launchApplication(): Promise<void> {
  const delegator = abilityDelegatorRegistry.getAbilityDelegator();
  const want: Want = {
    bundleName: 'io.github.sakurasm.komeliaintl',
    abilityName: 'EntryAbility'
  };
  await delegator.startAbility(want);
  await new Promise<void>((resolve: () => void) => setTimeout(resolve, 500));
  const ability = await delegator.getCurrentTopAbility();
  await AppContainer.initialize(ability.context as common.UIAbilityContext);
}

async function createSyntheticPage(): Promise<image.PixelMap> {
  const rgba = new Uint8Array(SAMPLE_WIDTH * SAMPLE_HEIGHT * 4);
  for (let y = 0; y < SAMPLE_HEIGHT; y += 1) {
    for (let x = 0; x < SAMPLE_WIDTH; x += 1) {
      const offset = (y * SAMPLE_WIDTH + x) * 4;
      rgba[offset] = Math.round(x * 255 / Math.max(1, SAMPLE_WIDTH - 1));
      rgba[offset + 1] = Math.round(y * 255 / Math.max(1, SAMPLE_HEIGHT - 1));
      rgba[offset + 2] = (x + y) % 2 === 0 ? 32 : 224;
      rgba[offset + 3] = 255;
    }
  }
  return image.createPixelMap(rgba.buffer as ArrayBuffer, {
    size: { width: SAMPLE_WIDTH, height: SAMPLE_HEIGHT },
    srcPixelFormat: image.PixelMapFormat.RGBA_8888,
    pixelFormat: image.PixelMapFormat.RGBA_8888,
    editable: false
  });
}

export default function mindSporeRuntimeTest(): void {
  describe('MindSpore bundled model runtime', () => {
    it('loads both model contracts and performs deterministic synthetic inference', 0, async () => {
      await launchApplication();
      await mindSporeSuperResolution.validateModel();
      await mindSporePanelDetector.validateModel();

      const source = await createSyntheticPage();
      let enhanced: image.PixelMap | undefined;
      try {
        const superResolutionStartedAt = Date.now();
        enhanced = await mindSporeSuperResolution.enhance(source);
        const superResolutionElapsed = Date.now() - superResolutionStartedAt;
        const enhancedInfo = enhanced.getImageInfoSync();
        expect(enhancedInfo.size.width).assertEqual(SAMPLE_WIDTH * 2);
        expect(enhancedInfo.size.height).assertEqual(SAMPLE_HEIGHT * 2);

        const detection = await mindSporePanelDetector.detect(source, PagedReadingDirection.RIGHT_TO_LEFT);
        expect(detection.sourceWidth).assertEqual(SAMPLE_WIDTH);
        expect(detection.sourceHeight).assertEqual(SAMPLE_HEIGHT);
        expect(detection.elapsedMillis >= 0).assertTrue();
        hilog.info(DOMAIN, TAG,
          `Synthetic inference passed: super-resolution=${superResolutionElapsed}ms, ` +
          `panel=${detection.elapsedMillis}ms, panelShape=${detection.outputShape.join('x')}, ` +
          `panels=${detection.panels.length}`);
      } finally {
        enhanced?.release();
        source.release();
      }
    });
  });
}
