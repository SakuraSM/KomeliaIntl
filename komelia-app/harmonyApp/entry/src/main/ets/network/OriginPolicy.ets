export enum OriginPolicyError {
  NONE = 'NONE',
  SCHEME_REQUIRED = 'SCHEME_REQUIRED',
  HTTP_CONFIRMATION_REQUIRED = 'HTTP_CONFIRMATION_REQUIRED'
}

export interface OriginPolicyResult {
  normalizedOrigin: string;
  error: OriginPolicyError;
}

export function evaluateOriginPolicy(origin: string, allowInsecureHttp: boolean): OriginPolicyResult {
  const normalized = origin.trim().replace(/\/+$/, '');
  if (!normalized.startsWith('https://') && !normalized.startsWith('http://')) {
    return { normalizedOrigin: normalized, error: OriginPolicyError.SCHEME_REQUIRED };
  }
  if (normalized.startsWith('http://') && !allowInsecureHttp) {
    return { normalizedOrigin: normalized, error: OriginPolicyError.HTTP_CONFIRMATION_REQUIRED };
  }
  return { normalizedOrigin: normalized, error: OriginPolicyError.NONE };
}
