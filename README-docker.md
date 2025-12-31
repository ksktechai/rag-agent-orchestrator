# Docker PostgreSQL Database Management

This guide provides detailed explanations of Docker commands for managing and inspecting the PostgreSQL database used by the RAG Agent Orchestrator.

## Prerequisites

- Docker container `docker-postgres-1` must be running
- PostgreSQL database `agenticrag` must exist
- User `postgres` must have appropriate permissions

## Command Reference

### 1. List All Tables in Public Schema

```bash
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag -c "\d public.*"
```

**Purpose:** Display all tables, views, sequences, and indexes in the `public` schema.

**What it does:**
- Connects to the `agenticrag` database as user `postgres`
- Executes the PostgreSQL `\d` meta-command to describe all objects matching `public.*`
- Shows table names, types (table, sequence, index), owner, and storage information

**When to use:**
- Initial database inspection to understand schema structure
- Verify tables were created correctly by Flyway migrations
- Check for unexpected tables or objects
- Confirm pgvector extension objects are present

**Expected output:**
```
List of relations
Schema | Name            | Type     | Owner
-------+-----------------+----------+----------
public | chunks          | table    | postgres
public | documents       | table    | postgres
public | ingest_jobs     | table    | postgres
public | chunks_pkey     | index    | postgres
public | documents_pkey  | index    | postgres
...
```

---

### 2. Describe Chunks Table Structure

```bash
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag -c "\d public.chunks"
```

**Purpose:** Show detailed schema information for the `chunks` table.

**What it does:**
- Displays column names, data types, and constraints for the `chunks` table
- Shows indexes, foreign keys, and triggers
- Reveals the `vector(768)` embedding column created by pgvector extension

**When to use:**
- Verify chunks table was created correctly
- Check embedding dimension size (should be 768 for nomic-embed-text)
- Inspect indexes for query optimization
- Understand the relationship between chunks and documents

**Expected output:**
```
Table "public.chunks"
Column         | Type         | Collation | Nullable | Default
---------------+--------------+-----------+----------+---------
id             | bigserial    |           | not null |
document_id    | bigint       |           | not null |
chunk_index    | integer      |           | not null |
content        | text         |           | not null |
embedding      | vector(768)  |           | not null |

Indexes:
    "chunks_pkey" PRIMARY KEY, btree (id)
    "chunks_embedding_idx" ivfflat (embedding vector_cosine_ops)

Foreign-key constraints:
    "chunks_document_id_fkey" FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
```

**Key fields:**
- `id` - Unique chunk identifier (auto-increment)
- `document_id` - Foreign key to parent document
- `chunk_index` - Position of chunk within document (0-indexed)
- `content` - Text content of the chunk
- `embedding` - 768-dimensional vector for semantic search

---

### 3. Show Current Database and User

```bash
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag -c "select current_database(), current_user;"
```

**Purpose:** Verify you're connected to the correct database with the expected user.

**What it does:**
- Queries PostgreSQL system functions to show the active database name and authenticated user
- Useful for debugging connection issues or confirming context

**When to use:**
- Sanity check before running destructive operations (like truncate)
- Debugging connection string issues
- Confirming you're in the right database environment

**Expected output:**
```
 current_database | current_user
------------------+--------------
 agenticrag       | postgres
```

---

### 4. Truncate All Data (Dangerous!)

```bash
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "truncate chunks, documents restart identity cascade;"
```

**Purpose:** Delete all data from `chunks` and `documents` tables and reset auto-increment IDs.

**What it does:**
- `TRUNCATE` - Quickly removes all rows from specified tables (faster than `DELETE`)
- `RESTART IDENTITY` - Resets auto-increment sequences (id columns start from 1 again)
- `CASCADE` - Also truncates tables that have foreign key references to these tables

**When to use:**
- Cleaning up test data during development
- Resetting the database to a fresh state
- Before re-ingesting a completely new document corpus

**WARNING:**
- This is a destructive operation that cannot be undone
- All ingested documents and their vector embeddings will be permanently deleted
- The `ingest_jobs` table is also affected due to foreign key cascade
- Always verify database context with command #3 before running

**Expected output:**
```
TRUNCATE TABLE
```

**Recovery:**
After truncating, you'll need to re-ingest all documents using:
```bash
cd backend
./gradlew ingestFolder --args="../sample_docs"
```

---

### 5. Search Chunks by Content

```bash
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "select id, chunk_index, left(content,200) from chunks where content ilike '%Data Center%' order by id limit 20;"
```

**Purpose:** Search for chunks containing specific text (case-insensitive) and preview content.

**What it does:**
- `ILIKE '%Data Center%'` - Case-insensitive pattern matching (unlike `LIKE`)
- `LEFT(content, 200)` - Shows first 200 characters of content (avoids overwhelming output)
- `ORDER BY id` - Sorts results by chunk ID for consistent ordering
- `LIMIT 20` - Restricts output to 20 rows

**When to use:**
- Verify specific content was ingested correctly
- Debug retrieval issues by checking if text exists in the database
- Find which chunks contain particular keywords or phrases
- Investigate why certain queries aren't returning expected results

**Example output:**
```
 id  | chunk_index |                                  left
-----+-------------+-------------------------------------------------------------------------
 142 |           3 | The Data Center Infrastructure team is responsible for maintaining...
 156 |           5 | Our Data Center facilities span across three geographic regions...
 201 |           8 | Data Center power consumption has been optimized through...
```

**Tips:**
- Use `%keyword%` to match anywhere in the text
- Use `keyword%` to match at the start
- Use `%keyword` to match at the end
- Adjust `LEFT(content, N)` to show more or fewer characters

---

### 6. Inspect Documents Table

```bash
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "select * from documents limit 20;"
```

**Purpose:** View the first 20 documents in the database with all metadata.

**What it does:**
- Retrieves all columns from the `documents` table
- Shows document metadata including source, title, version information
- Limited to 20 rows to prevent overwhelming output

**When to use:**
- Check what documents have been ingested
- Verify document versioning is working correctly
- Inspect logical IDs and version numbers
- See which embedding model was used for each document

**Expected columns:**
- `id` - Unique document ID (auto-increment)
- `source` - Document source identifier (e.g., filename, URL)
- `title` - Document title
- `text` - Full document text
- `logical_id` - UUID for tracking document versions
- `version` - Integer version number (1, 2, 3, ...)
- `is_latest` - Boolean flag indicating if this is the latest version
- `embedding_model` - Name of the embedding model used (e.g., "nomic-embed-text")

**Example output:**
```
 id |        source        |       title        | logical_id | version | is_latest | embedding_model
----+---------------------+--------------------+------------+---------+-----------+------------------
  1 | architecture.pdf    | Architecture Guide | uuid-...   |       1 | t         | nomic-embed-text
  2 | requirements.md     | Requirements Doc   | uuid-...   |       1 | t         | nomic-embed-text
```

**Useful variations:**
```bash
# Count total documents
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "select count(*) from documents;"

# Show only latest versions
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "select id, source, title, version from documents where is_latest = true;"

# Group by logical_id to see version history
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "select logical_id, source, count(*) as version_count from documents group by logical_id, source;"
```

---

### 7. Inspect Chunks Table

```bash
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "select * from chunks limit 20;"
```

**Purpose:** View the first 20 chunks with all data including embeddings.

**What it does:**
- Retrieves all columns from the `chunks` table
- Shows chunk content, embeddings, and relationships to parent documents
- Limited to 20 rows to prevent overwhelming output

**When to use:**
- Verify chunks were created correctly during ingestion
- Check embedding vectors are populated (not null)
- Inspect chunk indices and content
- Debug retrieval issues

**Expected columns:**
- `id` - Unique chunk ID
- `document_id` - Foreign key to parent document
- `chunk_index` - Position within document (0, 1, 2, ...)
- `content` - Text content of the chunk
- `embedding` - 768-dimensional vector (displayed as array)

**Example output:**
```
 id | document_id | chunk_index |                content                 |              embedding
----+-------------+-------------+----------------------------------------+--------------------------------------
  1 |           1 |           0 | This is the first chunk of text...     | [0.123, -0.456, 0.789, ...]
  2 |           1 |           1 | This is the second chunk...            | [0.234, -0.567, 0.890, ...]
```

**Note:** The `embedding` column will show a large array of 768 floating-point numbers. To avoid clutter, you may want to exclude it:

```bash
# Exclude embedding column for readability
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "select id, document_id, chunk_index, left(content, 100) as content_preview from chunks limit 20;"

# Count chunks per document
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "select document_id, count(*) as chunk_count from chunks group by document_id order by document_id;"

# Show chunks for a specific document
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "select id, chunk_index, left(content, 100) from chunks where document_id = 1 order by chunk_index;"
```

---

## Common Workflows

### Debugging Ingestion

1. Check if documents were created:
```bash
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "select count(*) from documents;"
```

2. Check if chunks were created:
```bash
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "select count(*) from chunks;"
```

3. Verify embeddings are populated:
```bash
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "select count(*) from chunks where embedding is null;"
```
(Should return 0)

### Debugging Retrieval

1. Search for expected content:
```bash
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "select id, left(content, 150) from chunks where content ilike '%your search term%' limit 10;"
```

2. Check vector similarity (manual test):
```bash
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "select id, chunk_index, content from chunks order by embedding <=> '[0.1, 0.2, ...]'::vector limit 5;"
```
(Replace `[0.1, 0.2, ...]` with actual embedding vector)

### Document Versioning

1. Find all versions of a document:
```bash
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "select id, version, is_latest, source from documents where logical_id = 'uuid-here' order by version;"
```

2. Count documents by version:
```bash
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "select version, count(*) from documents group by version order by version;"
```

### Database Health Check

```bash
# Check table sizes
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "select
        schemaname,
        tablename,
        pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
      from pg_tables
      where schemaname = 'public'
      order by pg_total_relation_size(schemaname||'.'||tablename) desc;"

# Check index usage
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
  -c "select * from pg_stat_user_indexes where schemaname = 'public';"
```

---

## Safety Tips

1. **Always verify database context** before running destructive commands:
   ```bash
   docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
     -c "select current_database(), current_user;"
   ```

2. **Use transactions for data modifications**:
   ```bash
   docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
     -c "BEGIN; DELETE FROM chunks WHERE id = 1; ROLLBACK;"
   ```
   (Replace `ROLLBACK` with `COMMIT` to apply changes)

3. **Backup before truncating**:
   ```bash
   docker exec -it docker-postgres-1 pg_dump -U postgres agenticrag > backup.sql
   ```

4. **Use `LIMIT` when querying large tables** to avoid overwhelming your terminal.

5. **Check row counts** before truncating:
   ```bash
   docker exec -it docker-postgres-1 psql -U postgres -d agenticrag \
     -c "select
           (select count(*) from documents) as doc_count,
           (select count(*) from chunks) as chunk_count;"
   ```

---

## Interactive psql Session

For more interactive exploration, you can start a `psql` session:

```bash
docker exec -it docker-postgres-1 psql -U postgres -d agenticrag
```

Once inside, you can run commands without the `-c` flag:

```sql
-- List tables
\dt

-- Describe chunks table
\d chunks

-- Run queries
SELECT COUNT(*) FROM documents;

-- Exit
\q
```

---

## Related Documentation

- See [README.md](README.md) for application architecture and setup
- See [CLAUDE.md](CLAUDE.md) for development commands and project structure
- See [backend/src/main/resources/db/migration/](backend/src/main/resources/db/migration/) for Flyway migration SQL scripts
