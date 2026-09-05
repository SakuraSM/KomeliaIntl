export enum ColorCorrectionType {
  COLOR_CURVES = 'COLOR_CURVES',
  COLOR_LEVELS = 'COLOR_LEVELS'
}

export enum ColorChannel {
  VALUE = 'VALUE',
  RED = 'RED',
  GREEN = 'GREEN',
  BLUE = 'BLUE'
}

export enum CurvePointType {
  SMOOTH = 'SMOOTH',
  CORNER = 'CORNER'
}

export interface CurvePoint {
  x: number;
  y: number;
  type: CurvePointType;
}

export interface ColorCurveChannels {
  color: CurvePoint[];
  red: CurvePoint[];
  green: CurvePoint[];
  blue: CurvePoint[];
}

export interface ColorLevelsConfig {
  lowInput: number;
  highInput: number;
  lowOutput: number;
  highOutput: number;
  gamma: number;
}

export interface ColorLevelChannels {
  color: ColorLevelsConfig;
  red: ColorLevelsConfig;
  green: ColorLevelsConfig;
  blue: ColorLevelsConfig;
}

export interface BookColorCorrectionSettings {
  type: ColorCorrectionType;
  curves: ColorCurveChannels;
  levels: ColorLevelChannels;
}

export interface ColorLookupTables {
  value?: Uint8Array;
  red?: Uint8Array;
  green?: Uint8Array;
  blue?: Uint8Array;
}

export function defaultCurvePoints(): CurvePoint[] {
  return [
    { x: 0, y: 0, type: CurvePointType.SMOOTH },
    { x: 1, y: 1, type: CurvePointType.SMOOTH }
  ];
}

export function defaultCurveChannels(): ColorCurveChannels {
  return {
    color: defaultCurvePoints(),
    red: defaultCurvePoints(),
    green: defaultCurvePoints(),
    blue: defaultCurvePoints()
  };
}

export function defaultLevelsConfig(): ColorLevelsConfig {
  return { lowInput: 0, highInput: 1, lowOutput: 0, highOutput: 1, gamma: 1 };
}

export function defaultLevelChannels(): ColorLevelChannels {
  return {
    color: defaultLevelsConfig(),
    red: defaultLevelsConfig(),
    green: defaultLevelsConfig(),
    blue: defaultLevelsConfig()
  };
}

export function defaultBookColorCorrection(): BookColorCorrectionSettings {
  return {
    type: ColorCorrectionType.COLOR_CURVES,
    curves: defaultCurveChannels(),
    levels: defaultLevelChannels()
  };
}

function finiteInRange(value: number, fallback: number): number {
  if (!Number.isFinite(value)) return fallback;
  return Math.max(0, Math.min(1, value));
}

function validPointType(value: string): CurvePointType {
  return value === CurvePointType.CORNER ? CurvePointType.CORNER : CurvePointType.SMOOTH;
}

function normalizedCurve(points: CurvePoint[]): CurvePoint[] {
  const sorted = points.map((point: CurvePoint): CurvePoint => ({
    x: finiteInRange(point.x, 0),
    y: finiteInRange(point.y, 0),
    type: validPointType(point.type)
  })).sort((first: CurvePoint, second: CurvePoint) => first.x - second.x);
  const result: CurvePoint[] = [];
  for (let index = 0; index < sorted.length; index += 1) {
    const point = sorted[index];
    if (result.length > 0 && Math.abs(result[result.length - 1].x - point.x) < 0.0001) {
      result[result.length - 1] = point;
    } else {
      result.push(point);
    }
  }
  return result.length >= 2 ? result : defaultCurvePoints();
}

function normalizedLevels(value: ColorLevelsConfig): ColorLevelsConfig {
  return {
    lowInput: finiteInRange(value.lowInput, 0),
    highInput: finiteInRange(value.highInput, 1),
    lowOutput: finiteInRange(value.lowOutput, 0),
    highOutput: finiteInRange(value.highOutput, 1),
    gamma: Number.isFinite(value.gamma) ? Math.max(0.1, Math.min(10, value.gamma)) : 1
  };
}

export function normalizeBookColorCorrection(value: BookColorCorrectionSettings): BookColorCorrectionSettings {
  return {
    type: value.type === ColorCorrectionType.COLOR_LEVELS ?
      ColorCorrectionType.COLOR_LEVELS : ColorCorrectionType.COLOR_CURVES,
    curves: {
      color: normalizedCurve(value.curves.color),
      red: normalizedCurve(value.curves.red),
      green: normalizedCurve(value.curves.green),
      blue: normalizedCurve(value.curves.blue)
    },
    levels: {
      color: normalizedLevels(value.levels.color),
      red: normalizedLevels(value.levels.red),
      green: normalizedLevels(value.levels.green),
      blue: normalizedLevels(value.levels.blue)
    }
  };
}

function curveIsDefault(points: CurvePoint[]): boolean {
  const normalized = normalizedCurve(points);
  return normalized.length === 2 && normalized[0].x === 0 && normalized[0].y === 0 &&
    normalized[1].x === 1 && normalized[1].y === 1;
}

function levelsAreDefault(value: ColorLevelsConfig): boolean {
  const levels = normalizedLevels(value);
  return levels.lowInput === 0 && levels.highInput === 1 && levels.lowOutput === 0 &&
    levels.highOutput === 1 && levels.gamma === 1;
}

export function colorCorrectionIsActive(value: BookColorCorrectionSettings): boolean {
  if (value.type === ColorCorrectionType.COLOR_LEVELS) {
    return !levelsAreDefault(value.levels.color) || !levelsAreDefault(value.levels.red) ||
      !levelsAreDefault(value.levels.green) || !levelsAreDefault(value.levels.blue);
  }
  return !curveIsDefault(value.curves.color) || !curveIsDefault(value.curves.red) ||
    !curveIsDefault(value.curves.green) || !curveIsDefault(value.curves.blue);
}

export function createLevelsLookupTable(value: ColorLevelsConfig): Uint8Array | undefined {
  const levels = normalizedLevels(value);
  if (levelsAreDefault(levels)) return undefined;
  const result = new Uint8Array(256);
  const inverseGamma = 1 / levels.gamma;
  for (let index = 0; index < result.length; index += 1) {
    const input = index / 255;
    let output = levels.highInput !== levels.lowInput ?
      (input - levels.lowInput) / (levels.highInput - levels.lowInput) : input - levels.lowInput;
    output = Math.max(0, Math.min(1, output));
    if (inverseGamma !== 1 && output > 0) output = Math.pow(output, inverseGamma);
    if (levels.highOutput >= levels.lowOutput) {
      output = output * (levels.highOutput - levels.lowOutput) + levels.lowOutput;
    } else {
      output = levels.lowOutput - output * (levels.lowOutput - levels.highOutput);
    }
    result[index] = Math.round(Math.max(0, Math.min(1, output)) * 255);
  }
  return result;
}

interface CurveWindow {
  start: CurvePoint;
  end: CurvePoint;
  previous?: CurvePoint;
  next?: CurvePoint;
}

function sampleCurveWindow(samples: Float32Array, window: CurveWindow): void {
  const dx = window.end.x - window.start.x;
  if (dx <= 0) return;
  const dy = window.end.y - window.start.y;
  const x0 = window.start.x;
  const y0 = window.start.y;
  const x3 = window.end.x;
  const y3 = window.end.y;
  let y1 = 0;
  let y2 = 0;
  if (window.previous === undefined && window.next === undefined) {
    y1 = y0 + dy / 3;
    y2 = y0 + dy * 2 / 3;
  } else if (window.previous === undefined && window.next !== undefined) {
    const denominator = window.next.x - x0;
    const slope = denominator === 0 ? 0 : (window.next.y - y0) / denominator;
    y2 = y3 - slope * dx / 3;
    y1 = y0 + (y2 - y0) / 2;
  } else if (window.previous !== undefined && window.next === undefined) {
    const denominator = x3 - window.previous.x;
    const slope = denominator === 0 ? 0 : (y3 - window.previous.y) / denominator;
    y1 = y0 + slope * dx / 3;
    y2 = y3 + (y1 - y3) / 2;
  } else {
    const previous = window.previous as CurvePoint;
    const next = window.next as CurvePoint;
    const firstDenominator = window.end.x - previous.x;
    const secondDenominator = next.x - window.start.x;
    const firstSlope = firstDenominator === 0 ? 0 : (window.end.y - previous.y) / firstDenominator;
    const secondSlope = secondDenominator === 0 ? 0 : (next.y - window.start.y) / secondDenominator;
    y1 = window.start.y + firstSlope * dx / 3;
    y2 = window.end.y - secondSlope * dx / 3;
  }
  const segmentSamples = Math.round(dx * 255);
  for (let offset = 0; offset <= segmentSamples; offset += 1) {
    const t = offset / dx / 255;
    const inverse = 1 - t;
    const y = y0 * inverse * inverse * inverse + 3 * y1 * inverse * inverse * t +
      3 * y2 * inverse * t * t + y3 * t * t * t;
    const sampleIndex = offset + Math.round(x0 * 255);
    if (sampleIndex < samples.length) samples[sampleIndex] = Math.max(0, Math.min(1, y));
  }
}

export function createCurveLookupTable(value: CurvePoint[]): Uint8Array | undefined {
  const points = normalizedCurve(value);
  if (curveIsDefault(points)) return undefined;
  const samples = new Float32Array(256);
  const firstBoundary = Math.round(points[0].x * 255);
  for (let index = 0; index < firstBoundary; index += 1) samples[index] = points[0].y;
  const last = points[points.length - 1];
  const lastBoundary = Math.round(last.x * 255);
  for (let index = lastBoundary; index < samples.length; index += 1) samples[index] = last.y;

  let start = points[0];
  let previous: CurvePoint | undefined = undefined;
  for (let index = 1; index < points.length; index += 1) {
    const end = points[index];
    if (start.type === CurvePointType.CORNER) previous = start;
    const next = end.type === CurvePointType.CORNER ? end : points[index + 1];
    sampleCurveWindow(samples, { start: start, end: end, previous: previous, next: next });
    previous = start;
    start = end;
  }
  for (let index = 0; index < points.length; index += 1) {
    samples[Math.round(points[index].x * 255)] = points[index].y;
  }
  const result = new Uint8Array(256);
  for (let index = 0; index < result.length; index += 1) {
    result[index] = Math.round(samples[index] * 255);
  }
  return result;
}

export function createColorLookupTables(value: BookColorCorrectionSettings): ColorLookupTables {
  const normalized = normalizeBookColorCorrection(value);
  if (normalized.type === ColorCorrectionType.COLOR_LEVELS) {
    return {
      value: createLevelsLookupTable(normalized.levels.color),
      red: createLevelsLookupTable(normalized.levels.red),
      green: createLevelsLookupTable(normalized.levels.green),
      blue: createLevelsLookupTable(normalized.levels.blue)
    };
  }
  return {
    value: createCurveLookupTable(normalized.curves.color),
    red: createCurveLookupTable(normalized.curves.red),
    green: createCurveLookupTable(normalized.curves.green),
    blue: createCurveLookupTable(normalized.curves.blue)
  };
}

export function mapRgbBytes(bytes: Uint8Array, redOffset: number, greenOffset: number, blueOffset: number,
  pixelStride: number, tables: ColorLookupTables): void {
  for (let offset = 0; offset + pixelStride <= bytes.length; offset += pixelStride) {
    let red = bytes[offset + redOffset];
    let green = bytes[offset + greenOffset];
    let blue = bytes[offset + blueOffset];
    if (tables.value !== undefined) {
      red = tables.value[red];
      green = tables.value[green];
      blue = tables.value[blue];
    }
    bytes[offset + redOffset] = tables.red === undefined ? red : tables.red[red];
    bytes[offset + greenOffset] = tables.green === undefined ? green : tables.green[green];
    bytes[offset + blueOffset] = tables.blue === undefined ? blue : tables.blue[blue];
  }
}
