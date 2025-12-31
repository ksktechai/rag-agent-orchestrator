#!/usr/bin/env bash
set -euo pipefail

(cd docker && docker compose up -d)
ollama pull nomic-embed-text || true
ollama pull llama3.2 || true

echo "Backend:"
echo "  cd backend && ./gradlew bootRun"
echo "Ingest docs:"
echo "  cd backend && ./gradlew ingestFolder --args=../sample_docs"
echo "Frontend:"
echo "  cd frontend && npm install && npm run dev"
