const CHAPTER_TIMEOUT_MS = 25_000

interface PublicationContentRequest {
  href: string
  bookId: string
  load: (href: string) => Promise<string>
  onFailure: (error: unknown) => void
  timeoutMs?: number
}

export async function loadPublicationContent({
  href,
  bookId,
  load,
  onFailure,
  timeoutMs = CHAPTER_TIMEOUT_MS,
}: PublicationContentRequest): Promise<string> {
  // The navigator also asks for optional popover content through this API.
  const resourcePath = `/api/v1/books/${encodeURIComponent(bookId)}/resource/`
  const path = href.split(/[?#]/, 1)[0]
  if (!path.includes(resourcePath) || !/\.x?html?$/i.test(path)) return load(href)

  let timer: ReturnType<typeof setTimeout> | undefined
  try {
    const timeout = new Promise<never>((_, reject) => {
      timer = setTimeout(() => reject(new Error('EPUB chapter load timed out')), timeoutMs)
    })
    const content = await Promise.race([load(href), timeout])
    if (!content) throw new Error('EPUB chapter content is unavailable')
    return content
  } catch (error) {
    onFailure(error)
    // The library handles fetch fallback for missing content but doesn't catch a rejected getContent promise.
    return ''
  } finally {
    clearTimeout(timer)
  }
}
