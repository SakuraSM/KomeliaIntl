import { fileIo as fs } from '@kit.CoreFileKit';
import { image } from '@kit.ImageKit';
import { AppContainer } from '../../core/di/AppContainer';
import { mindSporeImageEnhancer } from './MindSporeImageEnhancer';
import { ReaderRequestToken } from './ReaderRequestToken';

const MODEL_RAW_PATH: string = 'models/mangajanai_illustration_2x_fp32_256.ms';
const MODEL_FILE_NAME: string = 'mangajanai_illustration_2x_fp32_256.ms';
const MODEL_INPUT_SIZE: number = 256;
const MODEL_OUTPUT_SIZE: number = 512;
const MODEL_SCALE: number = 2;
const TILE_MARGIN: number = 16;
const TILE_CORE_SIZE: number = MODEL_INPUT_SIZE - TILE_MARGIN * 2;

export interface SuperResolutionTile {
  coreX: number;
  coreY: number;
  coreWidth: number;
  coreHeight: number;
  inputX: number;
  inputY: number;
}

export function buildSuperResolutionTiles(width: number, height: number): SuperResolutionTile[] {
  const safeWidth = Math.max(1, Math.round(width));
  const safeHeight = Math.max(1, Math.round(height));
  const tiles: SuperResolutionTile[] = [];
  for (let coreY = 0; coreY < safeHeight; coreY += TILE_CORE_SIZE) {
    for (let coreX = 0; coreX < safeWidth; coreX += TILE_CORE_SIZE) {
      tiles.push({
        coreX: coreX,
        coreY: coreY,
        coreWidth: Math.min(TILE_CORE_SIZE, safeWidth - coreX),
        coreHeight: Math.min(TILE_CORE_SIZE, safeHeight - coreY),
        inputX: coreX - TILE_MARGIN,
        inputY: coreY - TILE_MARGIN
      });
    }
  }
  return tiles;
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.max(minimum, Math.min(maximum, value));
}

/** MangaJaNai 2x inference with overlapped tiles and deterministic center-crop stitching. */
export class MindSporeSuperResolution {
  private installedModelPath: string = '';
  private inputIsNchw: boolean = true;

  async validateModel(): Promise<void> {
    const path = await this.ensureModelInstalled();
    const contract = await mindSporeImageEnhancer.inspect(path);
    const shape = contract.inputShape;
    const nchw = shape.length === 4 && shape[0] === 1 && shape[1] === 3 &&
      shape[2] === MODEL_INPUT_SIZE && shape[3] === MODEL_INPUT_SIZE;
    const nhwc = shape.length === 4 && shape[0] === 1 && shape[1] === MODEL_INPUT_SIZE &&
      shape[2] === MODEL_INPUT_SIZE && shape[3] === 3;
    if (!nchw && !nhwc) throw new Error(`SUPER_RESOLUTION_INPUT_SHAPE:${shape.join('x')}`);
    this.inputIsNchw = nchw;
  }

  async isAvailable(): Promise<boolean> {
    try {
      await this.validateModel();
      return true;
    } catch (_) {
      return false;
    }
  }

  async enhance(source: image.PixelMap, token?: ReaderRequestToken): Promise<image.PixelMap> {
    token?.throwIfCancelled();
    await this.validateModel();
    const modelPath = await this.ensureModelInstalled();
    const info = source.getImageInfoSync();
    const width = Math.max(1, info.size.width);
    const height = Math.max(1, info.size.height);
    const sourceBuffer = new ArrayBuffer(source.getPixelBytesNumber());
    await source.readPixelsToBuffer(sourceBuffer);
    token?.throwIfCancelled();
    const sourceRgba = new Uint8Array(sourceBuffer);
    const outputWidth = width * MODEL_SCALE;
    const outputHeight = height * MODEL_SCALE;
    const outputRgba = new Uint8Array(outputWidth * outputHeight * 4);
    const tiles = buildSuperResolutionTiles(width, height);

    for (let index = 0; index < tiles.length; index += 1) {
      token?.throwIfCancelled();
      const tile = tiles[index];
      const input = this.createInputTensor(sourceRgba, width, height, tile.inputX, tile.inputY,
        this.inputIsNchw);
      const result = await mindSporeImageEnhancer.predict(modelPath, [input]);
      token?.throwIfCancelled();
      if (result.outputs.length !== 1) throw new Error('SUPER_RESOLUTION_OUTPUT_COUNT');
      const outputShape = result.outputShapes[0];
      const outputNchw = outputShape.length === 4 && outputShape[0] === 1 && outputShape[1] === 3 &&
        outputShape[2] === MODEL_OUTPUT_SIZE && outputShape[3] === MODEL_OUTPUT_SIZE;
      const outputNhwc = outputShape.length === 4 && outputShape[0] === 1 &&
        outputShape[1] === MODEL_OUTPUT_SIZE && outputShape[2] === MODEL_OUTPUT_SIZE &&
        outputShape[3] === 3;
      if (!outputNchw && !outputNhwc) {
        throw new Error(`SUPER_RESOLUTION_OUTPUT_SHAPE:${outputShape.join('x')}`);
      }
      const values = new Float32Array(result.outputs[0]);
      if (values.length !== 3 * MODEL_OUTPUT_SIZE * MODEL_OUTPUT_SIZE) {
        throw new Error('SUPER_RESOLUTION_OUTPUT_SHAPE');
      }
      this.writeCore(values, outputRgba, outputWidth, tile, outputNchw);
    }

    token?.throwIfCancelled();
    return image.createPixelMap(outputRgba.buffer, {
      size: { width: outputWidth, height: outputHeight },
      srcPixelFormat: image.PixelMapFormat.RGBA_8888,
      pixelFormat: image.PixelMapFormat.RGBA_8888,
      editable: false
    });
  }

  private createInputTensor(source: Uint8Array, width: number, height: number,
    inputX: number, inputY: number, nchw: boolean): ArrayBuffer {
    const pixels = MODEL_INPUT_SIZE * MODEL_INPUT_SIZE;
    const values = new Float32Array(pixels * 3);
    for (let y = 0; y < MODEL_INPUT_SIZE; y += 1) {
      const sourceY = clamp(inputY + y, 0, height - 1);
      for (let x = 0; x < MODEL_INPUT_SIZE; x += 1) {
        const sourceX = clamp(inputX + x, 0, width - 1);
        const sourceOffset = (sourceY * width + sourceX) * 4;
        const pixel = y * MODEL_INPUT_SIZE + x;
        if (nchw) {
          values[pixel] = source[sourceOffset] / 255;
          values[pixels + pixel] = source[sourceOffset + 1] / 255;
          values[pixels * 2 + pixel] = source[sourceOffset + 2] / 255;
        } else {
          const targetOffset = pixel * 3;
          values[targetOffset] = source[sourceOffset] / 255;
          values[targetOffset + 1] = source[sourceOffset + 1] / 255;
          values[targetOffset + 2] = source[sourceOffset + 2] / 255;
        }
      }
    }
    return values.buffer as ArrayBuffer;
  }

  private writeCore(values: Float32Array, output: Uint8Array, outputWidth: number,
    tile: SuperResolutionTile, nchw: boolean): void {
    const plane = MODEL_OUTPUT_SIZE * MODEL_OUTPUT_SIZE;
    const startX = tile.coreX * MODEL_SCALE;
    const startY = tile.coreY * MODEL_SCALE;
    const coreWidth = tile.coreWidth * MODEL_SCALE;
    const coreHeight = tile.coreHeight * MODEL_SCALE;
    const sourceStartX = (tile.coreX - tile.inputX) * MODEL_SCALE;
    const sourceStartY = (tile.coreY - tile.inputY) * MODEL_SCALE;
    for (let y = 0; y < coreHeight; y += 1) {
      for (let x = 0; x < coreWidth; x += 1) {
        const sourcePixel = (sourceStartY + y) * MODEL_OUTPUT_SIZE + sourceStartX + x;
        const targetPixel = (startY + y) * outputWidth + startX + x;
        for (let channel = 0; channel < 3; channel += 1) {
          const sourceOffset = nchw ? channel * plane + sourcePixel : sourcePixel * 3 + channel;
          output[targetPixel * 4 + channel] = Math.round(
            clamp(values[sourceOffset], 0, 1) * 255);
        }
        output[targetPixel * 4 + 3] = 255;
      }
    }
  }

  private async ensureModelInstalled(): Promise<string> {
    if (this.installedModelPath.length > 0 && fs.accessSync(this.installedModelPath)) {
      return this.installedModelPath;
    }
    const context = AppContainer.requireContext();
    const directory = `${context.filesDir}/models`;
    if (!fs.accessSync(directory)) fs.mkdirSync(directory, true);
    const target = `${directory}/${MODEL_FILE_NAME}`;
    const content = await context.resourceManager.getRawFileContent(MODEL_RAW_PATH);
    if (!fs.accessSync(target) || fs.statSync(target).size !== content.byteLength) {
      const temporary = `${target}.partial`;
      const file = fs.openSync(temporary, fs.OpenMode.WRITE_ONLY | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
      try {
        fs.writeSync(file.fd, content.buffer);
      } finally {
        fs.closeSync(file);
      }
      if (fs.accessSync(target)) fs.unlinkSync(target);
      fs.renameSync(temporary, target);
    }
    this.installedModelPath = target;
    return target;
  }
}

export const mindSporeSuperResolution = new MindSporeSuperResolution();
