import { fileIo as fs } from '@kit.CoreFileKit';
import { image } from '@kit.ImageKit';
import { AppContainer } from '../../core/di/AppContainer';
import { PagedReadingDirection } from '../../model/ImageReaderModels';
import { mindSporeImageEnhancer } from './MindSporeImageEnhancer';

const MODEL_RAW_PATH: string = 'models/manga_panel_detector_fp32_1024.ms';
const MODEL_FILE_NAME: string = 'manga_panel_detector_fp32_1024.ms';
const MODEL_INPUT_SIZE: number = 1024;
const MODEL_ROW_SIZE: number = 6;
const PANEL_CLASS_ID: number = 0;
const PANEL_CONFIDENCE: number = 0.25;
const LETTERBOX_VALUE: number = 114 / 255;

export interface PanelRect {
  x: number;
  y: number;
  width: number;
  height: number;
  confidence: number;
}

export interface PanelDetectionResult {
  panels: PanelRect[];
  sourceWidth: number;
  sourceHeight: number;
  outputShape: number[];
  elapsedMillis: number;
}

class LetterboxTensor {
  data: ArrayBuffer;
  scale: number;
  paddingX: number;
  paddingY: number;

  constructor(data: ArrayBuffer, scale: number, paddingX: number, paddingY: number) {
    this.data = data;
    this.scale = scale;
    this.paddingX = paddingX;
    this.paddingY = paddingY;
  }
}

interface PanelRow {
  top: number;
  bottom: number;
  panels: PanelRect[];
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.max(minimum, Math.min(maximum, value));
}

function rowOverlap(row: PanelRow, panel: PanelRect): number {
  const top = Math.max(row.top, panel.y);
  const bottom = Math.min(row.bottom, panel.y + panel.height);
  if (bottom <= top) return 0;
  return (bottom - top) / Math.max(1, Math.min(row.bottom - row.top, panel.height));
}

/** Deterministic manga reading order shared by the model and UI tests. */
export function sortDetectedPanels(panels: PanelRect[], direction: PagedReadingDirection): PanelRect[] {
  const rows: PanelRow[] = [];
  const topFirst = panels.slice().sort((first: PanelRect, second: PanelRect) => {
    if (Math.abs(first.y - second.y) > 1) return first.y - second.y;
    return first.x - second.x;
  });
  topFirst.forEach((panel: PanelRect) => {
    let target: PanelRow | undefined;
    let bestOverlap: number = 0;
    rows.forEach((row: PanelRow) => {
      const overlap = rowOverlap(row, panel);
      if (overlap > bestOverlap) {
        target = row;
        bestOverlap = overlap;
      }
    });
    if (target === undefined || bestOverlap < 0.2) {
      rows.push({ top: panel.y, bottom: panel.y + panel.height, panels: [panel] });
      return;
    }
    target.panels.push(panel);
    target.top = Math.min(target.top, panel.y);
    target.bottom = Math.max(target.bottom, panel.y + panel.height);
  });
  rows.sort((first: PanelRow, second: PanelRow) => first.top - second.top);
  const result: PanelRect[] = [];
  rows.forEach((row: PanelRow) => {
    row.panels.sort((first: PanelRect, second: PanelRect) => {
      const order = first.x - second.x;
      return direction === PagedReadingDirection.RIGHT_TO_LEFT ? -order : order;
    });
    row.panels.forEach((panel: PanelRect) => result.push(panel));
  });
  return result;
}

function intersectionOverUnion(first: PanelRect, second: PanelRect): number {
  const left = Math.max(first.x, second.x);
  const top = Math.max(first.y, second.y);
  const right = Math.min(first.x + first.width, second.x + second.width);
  const bottom = Math.min(first.y + first.height, second.y + second.height);
  if (right <= left || bottom <= top) return 0;
  const intersection = (right - left) * (bottom - top);
  const union = first.width * first.height + second.width * second.height - intersection;
  return union <= 0 ? 0 : intersection / union;
}

export function suppressDuplicatePanels(panels: PanelRect[], threshold: number = 0.6): PanelRect[] {
  const candidates = panels.slice().sort((first: PanelRect, second: PanelRect) =>
    second.confidence - first.confidence);
  const kept: PanelRect[] = [];
  candidates.forEach((panel: PanelRect) => {
    if (kept.length >= 80) return;
    const duplicate = kept.some((other: PanelRect) => intersectionOverUnion(panel, other) > threshold);
    if (!duplicate) kept.push(panel);
  });
  return kept;
}

export function decodePanelDetections(output: Float32Array, sourceWidth: number, sourceHeight: number,
  scale: number, paddingX: number, paddingY: number, outputShape?: number[]): PanelRect[] {
  const panels: PanelRect[] = [];
  const rowCount = Math.floor(output.length / MODEL_ROW_SIZE);
  const shape: number[] = outputShape === undefined ? [] : outputShape;
  const dimensions = shape.length;
  const channelsFirst = dimensions >= 2 && shape[dimensions - 2] === MODEL_ROW_SIZE &&
    shape[dimensions - 1] !== MODEL_ROW_SIZE;
  const valueAt = (row: number, field: number): number => channelsFirst ?
    output[field * rowCount + row] : output[row * MODEL_ROW_SIZE + field];
  for (let row = 0; row < rowCount; row += 1) {
    const confidence = valueAt(row, 4);
    const classId = Math.round(valueAt(row, 5));
    if (!Number.isFinite(confidence) || confidence < PANEL_CONFIDENCE || classId !== PANEL_CLASS_ID) continue;
    const maximumCoordinate = Math.max(valueAt(row, 0), valueAt(row, 1), valueAt(row, 2), valueAt(row, 3));
    const coordinateScale = maximumCoordinate <= 2 ? MODEL_INPUT_SIZE : 1;
    const left = clamp((valueAt(row, 0) * coordinateScale - paddingX) / scale, 0, sourceWidth);
    const top = clamp((valueAt(row, 1) * coordinateScale - paddingY) / scale, 0, sourceHeight);
    const right = clamp((valueAt(row, 2) * coordinateScale - paddingX) / scale, 0, sourceWidth);
    const bottom = clamp((valueAt(row, 3) * coordinateScale - paddingY) / scale, 0, sourceHeight);
    const width = right - left;
    const height = bottom - top;
    if (width < 8 || height < 8) continue;
    panels.push({ x: left, y: top, width: width, height: height, confidence: confidence });
  }
  return suppressDuplicatePanels(panels);
}

export class MindSporePanelDetector {
  private installedModelPath: string = '';
  private inputIsNchw: boolean = true;

  async validateModel(): Promise<void> {
    const modelPath = await this.ensureModelInstalled();
    const contract = await mindSporeImageEnhancer.inspect(modelPath);
    const shape = contract.inputShape;
    const nchw = shape.length === 4 && shape[0] === 1 && shape[1] === 3 &&
      shape[2] === MODEL_INPUT_SIZE && shape[3] === MODEL_INPUT_SIZE;
    const nhwc = shape.length === 4 && shape[0] === 1 && shape[1] === MODEL_INPUT_SIZE &&
      shape[2] === MODEL_INPUT_SIZE && shape[3] === 3;
    if (!nchw && !nhwc) throw new Error(`PANEL_DETECTOR_INPUT_SHAPE:${shape.join('x')}`);
    this.inputIsNchw = !nhwc;
  }

  async isAvailable(): Promise<boolean> {
    try {
      await this.validateModel();
      return true;
    } catch (_) {
      return false;
    }
  }

  async detect(source: image.PixelMap, direction: PagedReadingDirection): Promise<PanelDetectionResult> {
    await this.validateModel();
    const modelPath = await this.ensureModelInstalled();
    const info = source.getImageInfoSync();
    const tensor = await this.letterbox(source, info.size.width, info.size.height, this.inputIsNchw);
    const inference = await mindSporeImageEnhancer.predict(modelPath, [tensor.data]);
    if (inference.outputs.length !== 1) throw new Error('PANEL_MODEL_OUTPUT_COUNT');
    const outputShape = inference.outputShapes[0];
    const dimensions = outputShape.length;
    const rowsLast = dimensions >= 2 && outputShape[dimensions - 1] === MODEL_ROW_SIZE;
    const fieldsFirst = dimensions >= 2 && outputShape[dimensions - 2] === MODEL_ROW_SIZE;
    if (!rowsLast && !fieldsFirst) throw new Error(`PANEL_MODEL_OUTPUT_SHAPE:${outputShape.join('x')}`);
    const decoded = decodePanelDetections(new Float32Array(inference.outputs[0]),
      info.size.width, info.size.height, tensor.scale, tensor.paddingX, tensor.paddingY, outputShape);
    return {
      panels: sortDetectedPanels(decoded, direction),
      sourceWidth: info.size.width,
      sourceHeight: info.size.height,
      outputShape: outputShape,
      elapsedMillis: inference.elapsedMillis
    };
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

  private async letterbox(source: image.PixelMap, sourceWidth: number,
    sourceHeight: number, nchw: boolean): Promise<LetterboxTensor> {
    const scale = Math.min(MODEL_INPUT_SIZE / sourceWidth, MODEL_INPUT_SIZE / sourceHeight);
    const scaledWidth = Math.max(1, Math.round(sourceWidth * scale));
    const scaledHeight = Math.max(1, Math.round(sourceHeight * scale));
    const sourceBytes = new ArrayBuffer(source.getPixelBytesNumber());
    await source.readPixelsToBuffer(sourceBytes);
    const size: image.Size = { width: sourceWidth, height: sourceHeight };
    const options: image.InitializationOptions = {
      size: size,
      srcPixelFormat: image.PixelMapFormat.RGBA_8888,
      pixelFormat: image.PixelMapFormat.RGBA_8888,
      editable: true
    };
    const scaled = await image.createPixelMap(sourceBytes, options);
    try {
      await scaled.scale(scaledWidth / sourceWidth, scaledHeight / sourceHeight, image.AntiAliasingLevel.LOW);
      const scaledBytes = new ArrayBuffer(scaled.getPixelBytesNumber());
      await scaled.readPixelsToBuffer(scaledBytes);
      const rgba = new Uint8Array(scaledBytes);
      const pixels = MODEL_INPUT_SIZE * MODEL_INPUT_SIZE;
      const values = new Float32Array(pixels * 3);
      values.fill(LETTERBOX_VALUE);
      const paddingX = Math.floor((MODEL_INPUT_SIZE - scaledWidth) / 2);
      const paddingY = Math.floor((MODEL_INPUT_SIZE - scaledHeight) / 2);
      for (let y = 0; y < scaledHeight; y += 1) {
        for (let x = 0; x < scaledWidth; x += 1) {
          const sourceOffset = (y * scaledWidth + x) * 4;
          const targetPixel = (paddingY + y) * MODEL_INPUT_SIZE + paddingX + x;
          if (nchw) {
            values[targetPixel] = rgba[sourceOffset] / 255;
            values[pixels + targetPixel] = rgba[sourceOffset + 1] / 255;
            values[pixels * 2 + targetPixel] = rgba[sourceOffset + 2] / 255;
          } else {
            const targetOffset = targetPixel * 3;
            values[targetOffset] = rgba[sourceOffset] / 255;
            values[targetOffset + 1] = rgba[sourceOffset + 1] / 255;
            values[targetOffset + 2] = rgba[sourceOffset + 2] / 255;
          }
        }
      }
      return new LetterboxTensor(values.buffer as ArrayBuffer, scale, paddingX, paddingY);
    } finally {
      scaled.release();
    }
  }
}

export const mindSporePanelDetector = new MindSporePanelDetector();
