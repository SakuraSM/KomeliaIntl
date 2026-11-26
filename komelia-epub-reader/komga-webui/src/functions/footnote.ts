import {footnoteText, noteSelector, referenceSelector} from '../../../shared/footnote'

interface ReaderFootnotes {
  bookId: string
  getContent: (href: string) => Promise<string>
  open: (link: HTMLAnchorElement) => void
  show: (text: string) => void
  unavailable: () => void
}

export function listenForFootnotes(frame: Window, reader: ReaderFootnotes): () => void {
  let request = 0
  const onClick = async (event: MouseEvent) => {
    // A publication iframe has its own Element constructor.
    const link = (event.target as Element | null)?.closest?.<HTMLAnchorElement>('a[href]')
    if (!link) return
    const target = new URL(link.href)
    const source = new URL(frame.document.baseURI)
    const bookPath = '/api/v1/books/' + encodeURIComponent(reader.bookId) + '/resource/'
    const prefix = source.pathname.indexOf(bookPath)
    if (!target.hash || target.origin !== source.origin || prefix < 0 ||
        !target.pathname.startsWith(source.pathname.slice(0, prefix) + bookPath)) return
    let id: string
    try { id = decodeURIComponent(target.hash.slice(1)) } catch { return }
    target.hash = ''
    source.hash = ''
    const sameResource = source.href === target.href
    const local = sameResource ? frame.document.getElementById(id) : null
    const semanticNote = local?.closest(noteSelector)
    if (!link.matches(referenceSelector) && !semanticNote) return

    event.preventDefault()
    event.stopImmediatePropagation()
    const generation = ++request
    reader.open(link)
    try {
      const note = semanticNote ?? local ?? new DOMParser().parseFromString(
        await reader.getContent(target.href), 'text/html').getElementById(id)
      if (request !== generation) return
      const text = note && footnoteText(note.closest(noteSelector) ?? note)
      if (text) reader.show(text)
      else reader.unavailable()
    } catch {
      if (request === generation) reader.unavailable()
    }
  }
  frame.addEventListener('click', onClick, true)
  return () => { request++; frame.removeEventListener('click', onClick, true) }
}
