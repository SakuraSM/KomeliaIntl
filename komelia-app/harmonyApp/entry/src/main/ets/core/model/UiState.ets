export enum LoadPhase {
  IDLE = 'Idle',
  LOADING = 'Loading',
  CONTENT = 'Content',
  EMPTY = 'Empty',
  ERROR = 'Error'
}

export class UiState<T> {
  readonly phase: LoadPhase;
  readonly data?: T;
  readonly message: string;

  constructor(phase: LoadPhase, data?: T, message: string = '') {
    this.phase = phase;
    this.data = data;
    this.message = message;
  }

  static idle<T>(): UiState<T> {
    return new UiState<T>(LoadPhase.IDLE);
  }

  static loading<T>(previous?: T): UiState<T> {
    return new UiState<T>(LoadPhase.LOADING, previous);
  }

  static content<T>(data: T): UiState<T> {
    return new UiState<T>(LoadPhase.CONTENT, data);
  }

  static empty<T>(message: string = ''): UiState<T> {
    return new UiState<T>(LoadPhase.EMPTY, undefined, message);
  }

  static error<T>(message: string, previous?: T): UiState<T> {
    return new UiState<T>(LoadPhase.ERROR, previous, message);
  }
}

export enum TaskPhase {
  QUEUED = 'Queued',
  RUNNING = 'Running',
  PAUSED = 'Paused',
  RETRYING = 'Retrying',
  COMPLETED = 'Completed',
  FAILED = 'Failed',
  CANCELLED = 'Cancelled'
}
