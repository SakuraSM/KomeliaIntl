export function requiredRouteId(value: string | undefined): string {
  return value?.trim() || '';
}

export function optionalRouteText(value: string | undefined): string {
  return value || '';
}
