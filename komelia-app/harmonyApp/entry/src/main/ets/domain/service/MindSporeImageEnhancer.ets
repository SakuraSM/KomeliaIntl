import { fileIo as fs } from '@kit.CoreFileKit';
import { deviceInfo } from '@kit.BasicServicesKit';
import { image } from '@kit.ImageKit';
import { mindSporeLite } from '@kit.MindSporeLiteKit';
import { localizedString } from '../../core/i18n/LocalizedResources';

export enum MindSporeExecutionTarget {
  NNRT = 'nnrt',
  CPU = 'cpu'
}

export interface MindSporeInferenceResult {
  outputs: ArrayBuffer[];
  outputShapes: number[][];
  outputFormats: number[];
  outputTypes: number[];
  target: MindSporeExecutionTarget;
  elapsedMillis: number;
}

export interface MindSporeModelContract {
  inputShape: number[];
  inputFormat: number;
  inputType: number;
}

export function shouldPreferNnrt(productModel: string): boolean {
  return productModel.trim().toLowerCase() !== 'emulator';
}

/**
 * Pure ArkTS inference gateway. Image normalization and tiling stay outside this class so
 * converted super-resolution and panel models can declare their own fixed tensor contract.
 */
export class MindSporeImageEnhancer {
  private models: Map<string, mindSporeLite.Model> = new Map<string, mindSporeLite.Model>();
  private loadingModels: Map<string, Promise<mindSporeLite.Model>> =
    new Map<string, Promise<mindSporeLite.Model>>();
  private failedTargets: Set<string> = new Set<string>();

  async isModelAvailable(modelPath: string): Promise<boolean> {
    return modelPath.length > 0 && fs.accessSync(modelPath);
  }

  async predict(modelPath: string, inputs: ArrayBuffer[]): Promise<MindSporeInferenceResult> {
    if (!await this.isModelAvailable(modelPath)) {
      throw new Error(localizedString($r('app.string.mindspore_model_unavailable')));
    }
    const nnrtKey = this.modelKey(modelPath, MindSporeExecutionTarget.NNRT);
    if (this.shouldTryNnrt() && !this.failedTargets.has(nnrtKey)) {
      try {
        return await this.predictWithTarget(modelPath, inputs, MindSporeExecutionTarget.NNRT);
      } catch (_) {
        this.failedTargets.add(nnrtKey);
      }
    }
    return this.predictWithTarget(modelPath, inputs, MindSporeExecutionTarget.CPU);
  }

  async inspect(modelPath: string): Promise<MindSporeModelContract> {
    if (!await this.isModelAvailable(modelPath)) {
      throw new Error(localizedString($r('app.string.mindspore_model_unavailable')));
    }
    const model = await this.loadModel(modelPath, MindSporeExecutionTarget.CPU);
    const inputs = model.getInputs();
    if (inputs.length !== 1) throw new Error(localizedString($r('app.string.single_input_model_only')));
    return {
      inputShape: inputs[0].shape,
      inputFormat: inputs[0].format,
      inputType: inputs[0].dtype
    };
  }

  async enhancePixelMap(modelPath: string, source: image.PixelMap): Promise<image.PixelMap> {
    const nnrtKey = this.modelKey(modelPath, MindSporeExecutionTarget.NNRT);
    if (this.shouldTryNnrt() && !this.failedTargets.has(nnrtKey)) {
      try {
        return await this.enhanceWithTarget(modelPath, source, MindSporeExecutionTarget.NNRT);
      } catch (_) {
        this.failedTargets.add(nnrtKey);
      }
    }
    return this.enhanceWithTarget(modelPath, source, MindSporeExecutionTarget.CPU);
  }

  private async enhanceWithTarget(modelPath: string, source: image.PixelMap,
    target: MindSporeExecutionTarget): Promise<image.PixelMap> {
    const model = await this.loadModel(modelPath, target);
    const inputs = model.getInputs();
    if (inputs.length !== 1 || inputs[0].shape.length !== 4) {
      throw new Error(localizedString($r('app.string.four_dimensional_model_required')));
    }
    const inputShape = inputs[0].shape;
    const nchw = inputs[0].format === mindSporeLite.Format.NCHW;
    const inputHeight = nchw ? inputShape[2] : inputShape[1];
    const inputWidth = nchw ? inputShape[3] : inputShape[2];
    const info = source.getImageInfoSync();
    if (info.size.width !== inputWidth || info.size.height !== inputHeight) {
      throw new Error(localizedString($r('app.string.model_input_size_mismatch'),
        inputWidth, inputHeight, info.size.width, info.size.height));
    }
    const rgba = new ArrayBuffer(inputWidth * inputHeight * 4);
    await source.readPixelsToBuffer(rgba);
    inputs[0].setData(this.toFloatTensor(new Uint8Array(rgba), inputWidth, inputHeight, nchw));
    const outputs = await model.predict(inputs);
    if (outputs.length === 0 || outputs[0].shape.length !== 4) {
      throw new Error(localizedString($r('app.string.model_output_invalid')));
    }
    const outputShape = outputs[0].shape;
    const outputNchw = outputs[0].format === mindSporeLite.Format.NCHW;
    const outputHeight = outputNchw ? outputShape[2] : outputShape[1];
    const outputWidth = outputNchw ? outputShape[3] : outputShape[2];
    const outputRgba = this.fromFloatTensor(new Float32Array(outputs[0].getData()),
      outputWidth, outputHeight, outputNchw);
    return image.createPixelMap(outputRgba.buffer, {
      size: { width: outputWidth, height: outputHeight },
      srcPixelFormat: image.PixelMapFormat.RGBA_8888,
      pixelFormat: image.PixelMapFormat.RGBA_8888,
      editable: false
    });
  }

  private toFloatTensor(rgba: Uint8Array, width: number, height: number, nchw: boolean): ArrayBuffer {
    const pixels = width * height;
    const values = new Float32Array(pixels * 3);
    for (let pixel = 0; pixel < pixels; pixel++) {
      for (let channel = 0; channel < 3; channel++) {
        const target = nchw ? channel * pixels + pixel : pixel * 3 + channel;
        values[target] = rgba[pixel * 4 + channel] / 255.0;
      }
    }
    return values.buffer;
  }

  private fromFloatTensor(values: Float32Array, width: number, height: number,
    nchw: boolean): Uint8Array {
    const pixels = width * height;
    const rgba = new Uint8Array(pixels * 4);
    for (let pixel = 0; pixel < pixels; pixel++) {
      for (let channel = 0; channel < 3; channel++) {
        const source = nchw ? channel * pixels + pixel : pixel * 3 + channel;
        rgba[pixel * 4 + channel] = Math.round(Math.max(0, Math.min(1, values[source])) * 255);
      }
      rgba[pixel * 4 + 3] = 255;
    }
    return rgba;
  }

  private async predictWithTarget(modelPath: string, inputs: ArrayBuffer[],
    target: MindSporeExecutionTarget): Promise<MindSporeInferenceResult> {
    const startedAt = Date.now();
    const model = await this.loadModel(modelPath, target);
    const tensors = model.getInputs();
    if (tensors.length !== inputs.length) {
      throw new Error(localizedString($r('app.string.model_input_count_mismatch'), tensors.length, inputs.length));
    }
    for (let index = 0; index < inputs.length; index++) tensors[index].setData(inputs[index]);
    const outputTensors = await model.predict(tensors);
    const outputs: ArrayBuffer[] = [];
    const outputShapes: number[][] = [];
    const outputFormats: number[] = [];
    const outputTypes: number[] = [];
    for (let index = 0; index < outputTensors.length; index++) {
      outputs.push(outputTensors[index].getData());
      outputShapes.push(outputTensors[index].shape);
      outputFormats.push(outputTensors[index].format);
      outputTypes.push(outputTensors[index].dtype);
    }
    return {
      outputs: outputs,
      outputShapes: outputShapes,
      outputFormats: outputFormats,
      outputTypes: outputTypes,
      target: target,
      elapsedMillis: Date.now() - startedAt
    };
  }

  private modelKey(modelPath: string, target: MindSporeExecutionTarget): string {
    return `${target}:${modelPath}`;
  }

  private shouldTryNnrt(): boolean {
    return shouldPreferNnrt(deviceInfo.productModel);
  }

  private async loadModel(modelPath: string, target: MindSporeExecutionTarget): Promise<mindSporeLite.Model> {
    const key = this.modelKey(modelPath, target);
    const cached = this.models.get(key);
    if (cached !== undefined) return cached;
    const pending = this.loadingModels.get(key);
    if (pending !== undefined) return pending;
    const context: mindSporeLite.Context = { target: [target] };
    if (target === MindSporeExecutionTarget.CPU) {
      context.cpu = { threadNum: 2, threadAffinityMode: 1, precisionMode: 'enforce_fp32' };
    }
    const loading = mindSporeLite.loadModelFromFile(modelPath, context);
    this.loadingModels.set(key, loading);
    try {
      const model = await loading;
      this.models.set(key, model);
      return model;
    } finally {
      this.loadingModels.delete(key);
    }
  }
}

export const mindSporeImageEnhancer = new MindSporeImageEnhancer();
