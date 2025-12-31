-- Document versioning + embedding metadata tracking
CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE documents
  ADD COLUMN IF NOT EXISTS logical_id UUID,
  ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS is_latest BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE documents
SET logical_id = COALESCE(logical_id, gen_random_uuid())
WHERE logical_id IS NULL;

ALTER TABLE chunks
  ADD COLUMN IF NOT EXISTS embedding_model TEXT;

UPDATE chunks
SET embedding_model = COALESCE(embedding_model, 'nomic-embed-text')
WHERE embedding_model IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS documents_logical_version_uq ON documents(logical_id, version);
CREATE INDEX IF NOT EXISTS documents_latest_idx ON documents(is_latest);
