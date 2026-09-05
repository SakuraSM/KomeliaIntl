export function seriesDetailPage(oneshot: boolean, seriesId: string = ''): string {
  if (seriesId.startsWith('local-series-')) return 'pages/LocalSeriesDetail';
  return oneshot ? 'pages/OneshotDetail' : 'pages/SeriesDetail';
}

export function bookDetailPage(oneshot: boolean, bookId: string = ''): string {
  if (bookId.startsWith('local-book-')) return 'pages/LocalBookDetail';
  return oneshot ? 'pages/OneshotDetail' : 'pages/BookDetail';
}
