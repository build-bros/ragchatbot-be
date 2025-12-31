# RAG Chatbot Backend

## Setup

1. **Set up environment variables**:
   ```bash
   # Copy the example file
   cp .env.example .env
   
   # Edit .env and set your GCP_PROJECT_ID
   # Required: GCP_PROJECT_ID=your-actual-gcp-project-id
   ```

2. **Authenticate with GCP**:
   ```bash
   gcloud auth application-default login
   ```

3. **Load environment variables and run**:
   ```bash
   # Load environment variables
   export $(grep -v '^#' .env | xargs)
   
   # Build and run
   ./gradlew build
   ./gradlew bootRun
   ```

Server runs on http://localhost:8080

## Required Configuration

- **GCP_PROJECT_ID**: Your Google Cloud Project ID (REQUIRED)
- **GCP_BIGQUERY_DATASET**: BigQuery dataset (defaults to `bigquery-public-data`)
- **GCP_BIGQUERY_SCHEMA**: BigQuery schema (defaults to `ncaa_basketball`)
- **GCP_VERTEX_AI_LOCATION**: Vertex AI location (defaults to `us-central1`)
- **GCP_VERTEX_AI_MODEL**: Vertex AI model (defaults to `gemini-2.5-pro`)
- **CORS_ALLOWED_ORIGINS**: CORS allowed origins (defaults to `http://localhost:4200`)

