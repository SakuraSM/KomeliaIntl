export type SeriesPublicationStatusKey = 'ended' | 'ongoing' | 'abandoned' | 'hiatus' | 'unknown';

export const SERIES_PUBLICATION_STATUS_VALUES: string[] = ['ENDED', 'ONGOING', 'ABANDONED', 'HIATUS'];

export function seriesPublicationStatusKey(status: string): SeriesPublicationStatusKey {
  switch (status.trim().toUpperCase()) {
    case 'ENDED':
      return 'ended';
    case 'ONGOING':
      return 'ongoing';
    case 'ABANDONED':
      return 'abandoned';
    case 'HIATUS':
      return 'hiatus';
    default:
      return 'unknown';
  }
}

export function seriesPublicationStatusResource(status: string): ResourceStr {
  switch (seriesPublicationStatusKey(status)) {
    case 'ended':
      return $r('app.string.series_status_ended');
    case 'ongoing':
      return $r('app.string.series_status_ongoing');
    case 'abandoned':
      return $r('app.string.series_status_abandoned');
    case 'hiatus':
      return $r('app.string.series_status_hiatus');
    default:
      return status;
  }
}

export function seriesPublicationStatusValues(currentStatus: string): string[] {
  const normalized = currentStatus.trim().toUpperCase();
  if (normalized.length > 0 && !SERIES_PUBLICATION_STATUS_VALUES.includes(normalized)) {
    return [normalized].concat(SERIES_PUBLICATION_STATUS_VALUES);
  }
  return SERIES_PUBLICATION_STATUS_VALUES.slice();
}

export interface SeriesActionAvailability {
  analyze: boolean;
  refreshMetadata: boolean;
  addToCollection: boolean;
  markAsRead: boolean;
  markAsUnread: boolean;
  edit: boolean;
  komf: boolean;
}

export function seriesActionAvailability(isOnline: boolean, canManageServer: boolean,
  booksCount: number, booksReadCount: number, booksUnreadCount: number,
  komfConfigured: boolean = false): SeriesActionAvailability {
  const canManageOnline = isOnline && canManageServer;
  return {
    analyze: canManageOnline,
    refreshMetadata: canManageOnline,
    addToCollection: canManageOnline,
    markAsRead: booksReadCount !== booksCount,
    markAsUnread: booksUnreadCount !== booksCount,
    edit: canManageOnline,
    komf: canManageOnline && komfConfigured
  };
}
