import { fileIo as fs } from '@kit.CoreFileKit';
import { mindSporeLite } from '@kit.MindSporeLiteKit';

export enum MindSporeExecutionTarget {
  NNRT = 'nnrt',
  CPU = 'cpu'
}

export interface MindSporeInferenceResult {
  outputs: ArrayBuffer[];
  target: MindSporeExecutionTarget;
  elapsedMillis: number;
}

/**
 * Pure ArkTS inference gateway. Image normalization and tiling stay outside this class so
 * converted super-resolution and panel models can declare their own fixed tensor contract.
 */
export class MindSporeImageEnhancer {
  async isModelAvailable(modelPath: string): Promise<boolean> {
    return modelPath.length > 0 && fs.accessSync(modelPath);
  }

  async predict(modelPath: string, inputs: ArrayBuffer[]): Promise<MindSporeInferenceResult> {
    if (!await this.isModelAvailable(modelPath)) throw new Error('MindSpore 模型尚未安装');
    try {
      return await this.predictWithTarget(modelPath, inputs, MindSporeExecutionTarget.NNRT);
    } catch (_) {
      return this.predictWithTarget(modelPath, inputs, MindSporeExecutionTarget.CPU);
    }
  }

  private async predictWithTarget(modelPath: string, inputs: ArrayBuffer[],
    target: MindSporeExecutionTarget): Promise<MindSporeInferenceResult> {
    const context: mindSporeLite.Context = {};
    context.target = [target];
    if (target === MindSporeExecutionTarget.CPU) {
      context.cpu = {};
      context.cpu.threadNum = 2;
      context.cpu.threadAffinityMode = 1;
      context.cpu.precisionMode = 'enforce_fp32';
    }
    const startedAt = Date.now();
    const model = await mindSporeLite.loadModelFromFile(modelPath, context);
    const tensors = model.getInputs();
    if (tensors.length !== inputs.length) {
      throw new Error(`模型输入数量不匹配：需要 ${tensors.length}，实际 ${inputs.length}`);
    }
    for (let index = 0; index < inputs.length; index++) tensors[index].setData(inputs[index]);
    const outputTensors = await model.predict(tensors);
    const outputs: ArrayBuffer[] = [];
    for (let index = 0; index < outputTensors.length; index++) outputs.push(outputTensors[index].getData());
    return { outputs: outputs, target: target, elapsedMillis: Date.now() - startedAt };
  }
}

export const mindSporeImageEnhancer = new MindSporeImageEnhancer();
