# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RAG Agent Orchestrator is a fully local, multi-agent Retrieval-Augmented Generation (RAG) system built with **Spring Boot 4** (Java 25) backend and **React + Tailwind** (TypeScript) frontend. It uses Ollama for local LLM inference and pgvector for vector storage.

## Development Commands

### Prerequisites
```bash
# Start PostgreSQL with pgvector extension
cd docker && docker compose up -d

# Pull required Ollama models
ollama pull llama3.2
ollama pull nomic-embed-text
```

### Backend (Gradle 9, Java 25)
```bash
cd backend

# Run the application
./gradlew bootRun

# Run tests
./gradlew test

# Ingest documents from CLI
./gradlew ingestFolder --args="../sample_docs"

# Build without running
./gradlew build
```

Backend runs on: http://localhost:8080
Health check: http://localhost:8080/health

### Frontend (Vite, React 18, TypeScript)
```bash
cd frontend

# Install dependencies
npm install

# Run dev server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

Frontend runs on: http://localhost:5173

### Quick Bootstrap (macOS)
```bash
./scripts/bootstrap-mac.sh
```

## Architecture

### Multi-Agent System

The orchestrator implements an explicit agent coordination pattern with these agents:

1. **RouterAgent** - Determines if retrieval is needed based on query analysis
2. **PlannerAgent** - Creates execution plan for query processing
3. **RetrieverAgent** (parallel instances) - Retrieves chunks from pgvector with different top-k values (6, 10, 14)
4. **SynthesizerAgent** - Generates grounded answer from retrieved context
5. **JudgeAgent** - Evaluates answer quality and determines if retry is needed
6. **QueryRewriteAgent** - Rewrites query on failure for another retrieval attempt

### Orchestration Flow

The system uses a judge-retry loop (max 2 retries):

```
Router → Planner → Parallel Retrievers → Synthesizer → Judge
                         ↑                                 ↓
                         ←←←← Query Rewriter ←←←←← (if failed)
```

Key implementation: `MultiAgentOrchestrator.java:150-227` (attempt method)

### Agent Interface

All agents implement `Agent.java` interface:
- `name()` - Unique agent identifier
- `trace(AgentContext)` - Emits SSE events for observability
- `run(AgentContext)` - Executes agent logic, mutates shared context

`AgentContext.java` is the shared mutable state container passed between agents containing:
- `question` (immutable) - Original user query
- `query` (mutable) - Current query version (may be rewritten)
- `needsRetrieval` - Flag for retrieval requirement
- `retrieved` - List of `ChunkHit` objects from vector search
- `draftAnswer` - Synthesized answer
- `judgedPass` - Judge evaluation result
- `citations` - Citation list

### Database Schema

PostgreSQL with pgvector extension. Flyway migrations in `backend/src/main/resources/db/migration/`:

**V1__init.sql**:
- `documents` - Document metadata (source, title, text)
- `chunks` - Text chunks with vector(768) embeddings
- `ingest_jobs` - Track upload jobs
- Uses IVFFlat index for cosine similarity search

**V2__versioning_and_embedding_metadata.sql**:
- `logical_id` (UUID) - Stable document identifier across versions
- `version` (INT) - Monotonic version number
- `is_latest` (BOOLEAN) - Latest version flag
- `embedding_model` (TEXT) - Track which model generated embeddings

### Document Versioning

The system supports re-uploading documents with version tracking:
- Same file → increments version, updates `is_latest`
- Re-embedding: POST `/api/ingest/reembed` with scope "latest" or "all"

### API Endpoints

**Chat**:
- `GET /api/chat/stream?question=...` (SSE)
  - Events: `event: agent` (agent traces), `event: final` (answer + citations)

**Ingestion**:
- `POST /api/ingest/file` (multipart/form-data) → `{jobId}`
- `GET /api/ingest/progress?jobId=...` (SSE) → progress events
- `POST /api/ingest/reembed` → `{"scope": "latest|all", "model": "optional"}`

**Health**:
- `GET /health`

Controllers in `backend/src/main/java/com/ai/agenticrag/api/`

### Key Services

**VectorStoreService** (`backend/src/main/java/com/ai/agenticrag/rag/VectorStoreService.java`):
- `search(query, topK)` - Cosine similarity search
- `hasAnyDocuments()` - Check if corpus exists
- Manages chunk storage and retrieval

**IngestService** (`backend/src/main/java/com/ai/agenticrag/ingest/IngestService.java`):
- Apache Tika for PDF/TXT/MD parsing
- Chunking strategy in `Chunker.java`
- SSE progress streaming via `IngestionJobService.java`

### Configuration

`backend/src/main/resources/application.yml`:

Environment variables:
- `PG_URL` - PostgreSQL JDBC URL (default: localhost:5432/agenticrag)
- `PG_USER` / `PG_PASSWORD` - Database credentials
- `OLLAMA_BASE_URL` - Ollama API endpoint (default: http://localhost:11434)
- `OLLAMA_CHAT_MODEL` - Chat model name (default: llama3.2)
- `OLLAMA_EMBED_MODEL` - Embedding model (default: nomic-embed-text)

### Grounded Synthesis Prompt

The synthesizer uses a strict grounding rule defined in `Agent.java:48-67`:
- Use ONLY information from retrieved context
- No outside knowledge
- Every paragraph must cite chunks with `[chunk:{id}]` format
- Explicit about missing information

## Project Structure

```
backend/src/main/java/com/ai/agenticrag/
├── api/              # Controllers (ChatSseController, IngestController)
├── config/           # Request logging, correlation, startup diagnostics
├── ingest/           # Document ingestion (Tika parsing, chunking)
├── orchestrator/     # Multi-agent system
│   ├── agents/       # Individual agent implementations
│   ├── Agent.java    # Agent interface
│   ├── AgentContext.java
│   └── MultiAgentOrchestrator.java
└── rag/              # Vector store, citations, chunk hits

frontend/src/
└── ui/
    └── App.tsx       # Main React component (SSE client, file upload)
```

## Testing

Backend uses JUnit 5 (JUnit Platform). Run with:
```bash
cd backend
./gradlew test
```

No frontend tests currently configured.

## Dependencies

**Backend**:
- Spring Boot 4.0.1
- Spring AI 2.0.0-M1 (Ollama integration)
- pgvector 0.1.6
- Flyway 10.17.0
- Apache Tika 2.9.2
- PostgreSQL JDBC driver

**Frontend**:
- React 18.3.1
- Vite 6.0.5
- Tailwind CSS 3.4.17
- TypeScript 5.7.2

## Important Notes

- **WebFlux**: Backend uses reactive programming (Reactor). All orchestrator methods return `Mono` or `Flux`.
- **Parallel Retrieval**: Three RetrieverAgent instances run concurrently with different top-k values, results are deduplicated and re-ranked.
- **SSE Streaming**: Both chat and ingestion use Server-Sent Events for real-time updates.
- **Local-Only**: No external API calls - all LLM inference via Ollama, all vectors in PostgreSQL.
