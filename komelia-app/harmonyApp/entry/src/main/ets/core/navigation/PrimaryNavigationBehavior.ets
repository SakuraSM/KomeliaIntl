export interface PrimaryNavigationDecision {
  updateDestination: boolean;
  returnToRoot: boolean;
}

export function primaryNavigationDecision(currentDestination: number, targetDestination: number,
  nested: boolean): PrimaryNavigationDecision {
  return {
    updateDestination: currentDestination !== targetDestination,
    returnToRoot: nested
  };
}
