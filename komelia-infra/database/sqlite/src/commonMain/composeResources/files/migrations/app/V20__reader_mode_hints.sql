ALTER TABLE ImageReaderSettings ADD COLUMN reader_mode_hints_enabled INTEGER NOT NULL DEFAULT 1;
ALTER TABLE ImageReaderSettings ADD COLUMN last_reader_mode_hint_signature TEXT;
