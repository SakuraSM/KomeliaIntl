ALTER TABLE ImageReaderSettings ADD COLUMN default_color_correction TEXT DEFAULT NULL;
ALTER TABLE BookColorCorrection ADD COLUMN mode TEXT NOT NULL DEFAULT 'CUSTOM';
