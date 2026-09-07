const SCROLL_SETTLE_MS = 120
const GESTURE_EXPIRY_MS = 1800
const MIN_GESTURE_DISTANCE = 48
const VERTICAL_AXIS_RATIO = 1.35

interface ChapterScrollNavigation {
  document: Document
  scrollTarget?: EventTarget
  isEnabled: () => boolean
  navigateAtBoundary: (deltaY: number) => boolean
}

/** Only an explicit user gesture may cross a resource boundary, at most once. */
export function setupChapterScrollNavigation(options: ChapterScrollNavigation): () => void {
  const scrollTarget = options.scrollTarget ?? options.document
  let start: {x: number; y: number} | undefined
  let pendingDelta: number | undefined
  let settleTimer: ReturnType<typeof setTimeout> | undefined
  let expiryTimer: ReturnType<typeof setTimeout> | undefined

  function reset(): void {
    start = undefined
    pendingDelta = undefined
    clearTimeout(settleTimer)
    clearTimeout(expiryTimer)
  }

  function tryNavigate(): boolean {
    if (pendingDelta === undefined || !options.isEnabled()) return false
    const delta = pendingDelta
    // Disarm before navigation, which can synchronously load another short chapter.
    pendingDelta = undefined
    if (options.navigateAtBoundary(delta)) {
      reset()
      return true
    }
    pendingDelta = delta
    return false
  }

  function scheduleBoundaryCheck(): void {
    if (pendingDelta === undefined) return
    clearTimeout(settleTimer)
    clearTimeout(expiryTimer)
    settleTimer = setTimeout(tryNavigate, SCROLL_SETTLE_MS)
    expiryTimer = setTimeout(reset, GESTURE_EXPIRY_MS)
  }

  function touchStart(event: TouchEvent): void {
    reset()
    if (!options.isEnabled() || event.touches.length !== 1) return
    const touch = event.touches[0]
    // The iframe moves with the chapter; client coordinates shift during native scrolling.
    start = {x: touch.screenX, y: touch.screenY}
  }

  function touchMove(event: TouchEvent): void {
    if (event.touches.length !== 1) reset()
  }

  function touchEnd(event: TouchEvent): void {
    const origin = start
    start = undefined
    if (!origin || event.changedTouches.length !== 1 || event.touches.length !== 0) return
    const touch = event.changedTouches[0]
    const deltaX = touch.screenX - origin.x
    const deltaY = touch.screenY - origin.y
    if (Math.abs(deltaY) < MIN_GESTURE_DISTANCE || Math.abs(deltaY) < Math.abs(deltaX) * VERTICAL_AXIS_RATIO) return
    pendingDelta = deltaY
    if (tryNavigate()) {
      if (event.cancelable) event.preventDefault()
    } else {
      scheduleBoundaryCheck()
    }
  }

  options.document.addEventListener('touchstart', touchStart, {passive: true})
  options.document.addEventListener('touchmove', touchMove, {passive: true})
  options.document.addEventListener('touchend', touchEnd, {passive: false})
  options.document.addEventListener('touchcancel', reset, {passive: true})
  // The SDK scrolls the outer wrapper, while touches originate inside its iframe.
  scrollTarget.addEventListener('scroll', scheduleBoundaryCheck, {passive: true, capture: true})
  scrollTarget.addEventListener('scrollend', tryNavigate, {passive: true})
  return (): void => {
    reset()
    options.document.removeEventListener('touchstart', touchStart)
    options.document.removeEventListener('touchmove', touchMove)
    options.document.removeEventListener('touchend', touchEnd)
    options.document.removeEventListener('touchcancel', reset)
    scrollTarget.removeEventListener('scroll', scheduleBoundaryCheck, true)
    scrollTarget.removeEventListener('scrollend', tryNavigate)
  }
}
