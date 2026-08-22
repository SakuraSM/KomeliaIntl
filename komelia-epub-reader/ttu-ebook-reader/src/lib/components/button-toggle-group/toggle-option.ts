/**
 * @license BSD-3-Clause
 * Copyright (c) 2024, ッツ Reader Authors
 * All rights reserved.
 */

export interface ToggleOption<T> {
  id: T;
  text: string;
  style?: Record<string, string>;
  thickBorders?: boolean;
  showIcons?: boolean;
}

export function getOptionsForToggle(translate: (message: string) => string): ToggleOption<boolean>[] {
  return [
    {
      id: false,
      text: translate('Off')
    },
    {
      id: true,
      text: translate('On')
    }
  ];
}
