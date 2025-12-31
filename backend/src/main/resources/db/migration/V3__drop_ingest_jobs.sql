-- Remove unused ingest_jobs table
-- This table was write-only and never queried, serving no functional purpose

DROP TABLE IF EXISTS ingest_jobs;
