/**
 * @license BSD-3-Clause
 * Copyright (c) 2024, ッツ Reader Authors
 * All rights reserved.
 */

export const READER_ANNOTATION_TYPE_HIGHLIGHT = 'highlight' as const;
export const DEFAULT_READER_HIGHLIGHT_COLOR = '#facc15';

export type ReaderAnnotationType = typeof READER_ANNOTATION_TYPE_HIGHLIGHT;

export interface ReaderAnnotation {
  id: string;
  type: ReaderAnnotationType;
  bookId: string;
  chapterReference: string;
  chapterIndex: number;
  startCharacter: number;
  endCharacter: number;
  text: string;
  color: string;
  createdAt: number;
  updatedAt: number;
}

export type ReaderAnnotationsByBook = Record<string, ReaderAnnotation[]>;
