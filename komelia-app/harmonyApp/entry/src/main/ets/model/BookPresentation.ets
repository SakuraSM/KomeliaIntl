import { KomgaAuthor, KomgaBook } from './KomgaModels';

const AUTHOR_ROLE_ORDER: string[] = [
  'writer', 'penciller', 'inker', 'colorist', 'letterer', 'cover', 'editor', 'translator'
];

export interface BookAuthorGroup {
  role: string;
  authors: KomgaAuthor[];
}

export interface BookReadingSummary {
  percentage: number;
  pagesLeft: number;
}

export function isBookCompleted(book: KomgaBook): boolean {
  return book.readProgress?.completed === true;
}

export function bookReadingSummary(book: KomgaBook): BookReadingSummary | undefined {
  const progress = book.readProgress;
  if (progress === undefined || progress === null || progress.completed) return undefined;
  const pageCount = Math.max(1, book.media.pagesCount);
  const currentPage = Math.min(Math.max(0, progress.page), pageCount);
  return {
    percentage: Math.min(100, Math.max(0, Math.round(currentPage / pageCount * 100))),
    pagesLeft: Math.max(0, pageCount - currentPage)
  };
}

export function bookMediaTypeLabel(mediaType: string): string {
  const normalized = mediaType.trim().toLowerCase();
  if (normalized === 'application/epub+zip') return 'EPUB';
  if (normalized === 'application/pdf') return 'PDF';
  if (normalized === 'application/zip' || normalized === 'application/x-cbz') return 'CBZ / ZIP';
  if (normalized === 'application/vnd.comicbook-rar' || normalized === 'application/x-rar-compressed') {
    return 'CBR / RAR';
  }
  return mediaType.trim();
}

export function isLocalDownloadOutdated(fileLastModified: string | undefined, downloadedAt: number): boolean {
  if (fileLastModified === undefined || fileLastModified.length === 0 || downloadedAt <= 0) return false;
  const remoteModifiedAt = new Date(fileLastModified).getTime();
  if (Number.isNaN(remoteModifiedAt)) return false;
  return remoteModifiedAt > downloadedAt;
}

export function authorRoleResource(role: string): ResourceStr {
  const normalized = role.trim().toLowerCase();
  if (normalized === 'writer') return $r('app.string.writer');
  if (normalized === 'penciller') return $r('app.string.penciller');
  if (normalized === 'inker') return $r('app.string.inker');
  if (normalized === 'colorist') return $r('app.string.colorist');
  if (normalized === 'letterer') return $r('app.string.letterer');
  if (normalized === 'cover') return $r('app.string.cover_artist');
  if (normalized === 'editor') return $r('app.string.editor');
  if (normalized === 'translator') return $r('app.string.translator');
  return role.trim().length > 0 ? role.trim() : $r('app.string.author_roles');
}

export function groupBookAuthors(authors: KomgaAuthor[]): BookAuthorGroup[] {
  const groups: BookAuthorGroup[] = [];
  for (const author of authors) {
    const name = author.name.trim();
    const role = author.role.trim().toLowerCase();
    if (name.length === 0) continue;
    const normalizedAuthor: KomgaAuthor = { name: name, role: role };
    const groupIndex = groups.findIndex((group: BookAuthorGroup): boolean => group.role === role);
    if (groupIndex < 0) {
      groups.push({ role: role, authors: [normalizedAuthor] });
    } else if (!groups[groupIndex].authors.some((value: KomgaAuthor): boolean => value.name === name)) {
      groups[groupIndex].authors.push(normalizedAuthor);
    }
  }
  return groups.sort((left: BookAuthorGroup, right: BookAuthorGroup): number => {
    const leftIndex = AUTHOR_ROLE_ORDER.indexOf(left.role);
    const rightIndex = AUTHOR_ROLE_ORDER.indexOf(right.role);
    const leftRank = leftIndex < 0 ? AUTHOR_ROLE_ORDER.length : leftIndex;
    const rightRank = rightIndex < 0 ? AUTHOR_ROLE_ORDER.length : rightIndex;
    if (leftRank !== rightRank) return leftRank - rightRank;
    return left.role.localeCompare(right.role);
  });
}
