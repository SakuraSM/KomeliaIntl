const FOCUSABLE_SELECTORS = ['a[href]', 'button:not([disabled])', 'input:not([disabled])',
  'textarea:not([disabled])', 'select:not([disabled])', 'summary', '[tabindex]:not([tabindex="-1"])'].join(',');

export function focusableDialogElements(container: HTMLElement | undefined): HTMLElement[] {
  if (!container) return [];
  return Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTORS))
    .filter(element => element.getClientRects().length > 0);
}

export function trapDialogFocus(container: HTMLElement | undefined, event: KeyboardEvent): void {
  if (!container || event.key !== 'Tab') return;
  const elements = focusableDialogElements(container);
  const first = elements[0];
  const last = elements[elements.length - 1];
  if (!first || !last) { event.preventDefault(); container.focus(); return; }
  if (event.shiftKey && (document.activeElement === first || document.activeElement === container)) {
    event.preventDefault(); last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault(); first.focus();
  }
}
