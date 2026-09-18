export const SiblingStatus = {
  Loading: 'loading', Ready: 'ready', Empty: 'empty', Error: 'error',
} as const

export type SiblingDirection = 'previous' | 'next'
interface BookIdentity { id: string }
export type SiblingState<Book extends BookIdentity> =
  | {status: typeof SiblingStatus.Loading | typeof SiblingStatus.Empty}
  | {status: typeof SiblingStatus.Ready; book: Book}
  | {status: typeof SiblingStatus.Error; error: unknown}
export interface BookSiblings<Book extends BookIdentity> {
  bookId: string
  generation: number
  previous: SiblingState<Book>
  next: SiblingState<Book>
}
interface SiblingCallbacks<Book extends BookIdentity> {
  load: (bookId: string, direction: SiblingDirection) => Promise<Book | null>
  changed: (state: BookSiblings<Book>) => void
  open: (book: Book) => void
  close: () => void
  failed: (direction: SiblingDirection) => void
}

/** Owns only directory lookups and navigation intent, never reader/progress state. */
export class BookSiblingState<Book extends BookIdentity> {
  private state: BookSiblings<Book> = this.emptyState('', 0)
  private isDisposed = false
  private navigationGeneration: number | undefined
  private requests: Partial<Record<SiblingDirection, Promise<void>>> = {}

  constructor(private readonly callbacks: SiblingCallbacks<Book>) {}

  reset(bookId: string): void {
    if (this.isDisposed) return
    this.requests = {}
    this.navigationGeneration = undefined
    this.state = this.emptyState(bookId, this.state.generation + 1)
    this.callbacks.changed(this.state)
    void this.load('previous')
    void this.load('next')
  }

  async navigate(direction: SiblingDirection): Promise<void> {
    if (this.isDisposed || this.navigationGeneration !== undefined) return
    if (this.state[direction].status === SiblingStatus.Loading) return
    const generation = this.state.generation
    this.navigationGeneration = generation
    try {
      if (this.state[direction].status === SiblingStatus.Error) await this.load(direction)
      if (!this.isCurrent(generation)) return
      const sibling = this.state[direction]
      if (sibling.status === SiblingStatus.Ready) this.callbacks.open(sibling.book)
      else if (sibling.status === SiblingStatus.Empty) this.callbacks.close()
      else if (sibling.status === SiblingStatus.Error) this.callbacks.failed(direction)
    } finally {
      if (this.navigationGeneration === generation) this.navigationGeneration = undefined
    }
  }

  dispose(): void {
    this.isDisposed = true
    this.requests = {}
    this.navigationGeneration = undefined
  }

  private load(direction: SiblingDirection): Promise<void> {
    const pending = this.requests[direction]
    if (pending) return pending
    const {bookId, generation} = this.state
    this.publish(direction, {status: SiblingStatus.Loading})
    // Defer invocation so even a synchronous bridge throw is a recorded failure.
    const request = Promise.resolve().then(() => this.callbacks.load(bookId, direction))
      .then(book => {
        if (!this.isCurrent(generation)) return
        if (book?.id === bookId) throw new Error('A book cannot be its own sibling')
        this.publish(direction, book === null
          ? {status: SiblingStatus.Empty}
          : {status: SiblingStatus.Ready, book})
      }).catch((error: unknown) => {
        if (this.isCurrent(generation)) this.publish(direction, {status: SiblingStatus.Error, error})
      }).finally(() => {
        if (this.isCurrent(generation)) delete this.requests[direction]
      })
    this.requests[direction] = request
    return request
  }

  private isCurrent(generation: number): boolean {
    return !this.isDisposed && this.state.generation === generation
  }

  private publish(direction: SiblingDirection, sibling: SiblingState<Book>): void {
    this.state = {...this.state, [direction]: sibling}
    this.callbacks.changed(this.state)
  }

  private emptyState(bookId: string, generation: number): BookSiblings<Book> {
    return {bookId, generation,
      previous: {status: SiblingStatus.Loading}, next: {status: SiblingStatus.Loading}}
  }
}
