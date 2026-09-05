export interface NetworkRouteCandidate {
  origin: string;
  allowInsecureHttp: boolean;
  usingLan: boolean;
}

export function normalizeServerOrigin(value: string): string {
  return value.trim().replace(/\/+$/, '');
}

export function networkRouteCandidates(primaryOrigin: string, allowPrimaryHttp: boolean,
  lanOrigin: string, allowLanHttp: boolean, lanAutoSwitchEnabled: boolean): NetworkRouteCandidate[] {
  const primary = normalizeServerOrigin(primaryOrigin);
  const lan = normalizeServerOrigin(lanOrigin);
  const result: NetworkRouteCandidate[] = [];
  if (lanAutoSwitchEnabled && lan.length > 0 && lan !== primary) {
    result.push({ origin: lan, allowInsecureHttp: allowLanHttp, usingLan: true });
  }
  result.push({ origin: primary, allowInsecureHttp: allowPrimaryHttp, usingLan: false });
  return result;
}
