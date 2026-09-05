export function userRoleResource(role: string): ResourceStr {
  if (role === 'ADMIN') return $r('app.string.role_admin');
  if (role === 'USER') return $r('app.string.role_user');
  if (role === 'FILE_DOWNLOAD') return $r('app.string.role_file_download');
  if (role === 'PAGE_STREAMING') return $r('app.string.role_page_streaming');
  if (role === 'KOBO_SYNC') return $r('app.string.role_kobo_sync');
  if (role === 'KOREADER_SYNC') return $r('app.string.role_koreader_sync');
  return role;
}

function twoDigits(value: number): string {
  return value < 10 ? `0${value}` : `${value}`;
}

export function formatDateTime(value: string): string {
  if (value.length === 0) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return `${date.getFullYear()}-${twoDigits(date.getMonth() + 1)}-${twoDigits(date.getDate())} ` +
    `${twoDigits(date.getHours())}:${twoDigits(date.getMinutes())}`;
}
