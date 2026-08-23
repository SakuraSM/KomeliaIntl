export interface KomfPage<T> {
  content: T[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
}

export interface KomfMetadataJob {
  seriesId: string;
  id: string;
  status: string;
  message: string;
  startedAt?: string;
  finishedAt?: string;
}

export interface KomfMetadataJobResponse { jobId: string; }

export interface KomfMediaServerConnectionResponse {
  connected: boolean;
  message?: string;
}

export interface KomfMediaServerLibrary {
  id: string;
  name: string;
}
