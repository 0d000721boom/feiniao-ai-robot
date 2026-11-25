# Large Markdown Chunk Upload Design

## Goal

Add resumable, concurrent chunk upload for large Markdown knowledge-base files.
The backend receives and records chunks, merges them only after every chunk is
present, verifies the complete file MD5, and starts the existing vectorization
pipeline after a successful merge.

## Scope

- Only `.md` and `.markdown` files are accepted.
- The existing `/customer-service/file/check` endpoint remains the upload
  preflight contract.
- Add two endpoints:
  - `POST /customer-service/file/chunk/upload`
  - `POST /customer-service/file/merge`
- Chunks are numbered from `0`.
- The client uses 5 MiB chunks and up to three concurrent chunk requests.
- A completed file keeps the existing asynchronous Markdown vectorization
  behavior.
- This change does not add authentication, object storage, or asynchronous
  merge jobs.

## Architecture

`CustomerServiceImpl` owns upload validation and orchestration.
`AiCustomerServiceFileStorageMapper` persists file-level metadata.
`FileChunkInfoMapper` persists uploaded chunk metadata.

The first chunk creates the file storage record with status `UPLOADING`.
Every successfully persisted chunk is stored on disk and registered with a
unique `(file_md5, chunk_number)` pair. The client queries the existing check
endpoint before uploading and skips chunks already reported by the backend.

The merge endpoint atomically claims the upload by changing its status from
`UPLOADING` to `MERGING`. It verifies that chunks `0` through
`totalChunks - 1` are present, merges them in order, verifies the complete MD5,
and moves the completed file into the configured storage directory.

After the merged file is durable and the storage record is updated to
`PENDING`, the service deletes chunk records and chunk files and publishes the
existing `AiCustomerServiceMdUploadedEvent`. The vectorization listener runs
after the database transaction commits and does not block the merge response.

## Storage Layout

All paths are derived by the server from validated values:

```text
{mdStoragePath}/.chunks/{fileMd5}/{chunkNumber}.part
{mdStoragePath}/.merge-{fileMd5}-{randomUuid}.part
{mdStoragePath}/{randomUuid}-{safeOriginalFileName}
```

The original filename is reduced to its basename before use. The MD5 value must
match 32 hexadecimal characters. Client-provided paths are never accepted.

## Persistence

`t_file_chunk_info` stores:

- `id`
- `file_md5`
- `chunk_number`
- `chunk_path`
- `chunk_size`
- `create_time`

The table requires a unique constraint on
`(file_md5, chunk_number)` and an index on `file_md5`.

`t_ai_customer_service_file_storage` continues to store the original name,
MD5, final path, total size, chunk counts, status, timestamps, and remark.
`file_md5` must be unique so one logical file has one upload record.

The database schema is supplied as an idempotent SQL script because the project
does not use a migration framework.

## API Contract

### Check File

`POST /customer-service/file/check`

```json
{
  "fileMd5": "32-character-md5"
}
```

The response reports whether the file exists, whether chunk upload is needed,
and the indexes of chunks already stored.

### Upload Chunk

`POST /customer-service/file/chunk/upload`

Content type: `multipart/form-data`

Form fields:

- `file`: current chunk bytes
- `fileMd5`: complete file MD5
- `fileName`: original Markdown filename
- `fileSize`: complete file size
- `chunkNumber`: zero-based chunk index
- `totalChunks`: total chunk count

The endpoint returns a successful empty `Response`. Re-uploading an already
recorded chunk is idempotent.

### Merge File

`POST /customer-service/file/merge`

```json
{
  "fileMd5": "32-character-md5"
}
```

The endpoint returns a successful empty `Response` after the final file exists
and the upload record is `PENDING`. The asynchronous vectorization event is
published separately.

## Status Transitions

```text
none -> UPLOADING -> MERGING -> PENDING -> VECTORIZING -> COMPLETED
                    |                 |
                    +-> UPLOADING     +-> FAILED
```

- `MERGING` prevents duplicate concurrent merge requests.
- A missing chunk returns an error and restores `UPLOADING`.
- A merge I/O error removes the partial merged file and restores `UPLOADING`.
- A complete MD5 mismatch removes all chunk records and files, resets the
  upload count, and restores `UPLOADING`.
- A vectorization failure produces `FAILED` and does not require uploading the
  source file again.

## Frontend Flow

1. The selected file is validated as Markdown.
2. A Web Worker calculates the complete MD5 in bounded slices and reports
   progress without blocking the page.
3. The page calls the check endpoint.
4. If upload is required, missing chunks are uploaded with at most three
   requests in flight.
5. Failed chunk requests are retried up to two times.
6. After all chunks succeed, the page calls the merge endpoint.
7. The UI displays MD5 calculation, chunk upload, and merge progress.

The browser uses `spark-md5` and Vite module-worker support. Pure helpers cover
chunk planning, missing-chunk filtering, bounded concurrency, retries, and
overall progress so they can be tested outside the component.

## Error Handling

New business errors cover:

- Invalid MD5
- Invalid Markdown filename
- Invalid chunk number or total count
- Inconsistent file metadata between chunks
- Empty or oversized chunk
- Incomplete chunk set
- Merge already in progress
- Complete-file MD5 mismatch
- Chunk upload or merge I/O failure

Frontend errors keep the current upload context when possible so the user can
resume. Recoverable server failures do not leave the UI permanently busy.

Deleting a Markdown file also removes any remaining chunk records and the
chunk directory. A cleanup failure is logged and does not hide a successful
primary delete or merge operation.

## Configuration

Spring multipart limits must allow a 5 MiB chunk plus request overhead. A 10 MiB
per-request limit is used. The complete file size is not constrained by the
multipart request limit because bytes are sent as separate chunks.

## Testing

Backend tests cover:

- First chunk creates the file record.
- Later chunks are stored in order and recorded.
- Duplicate chunk upload is idempotent.
- Invalid MD5, filename, ranges, and metadata are rejected.
- Missing chunks prevent merge.
- Merge concatenates chunks in numeric order.
- Complete MD5 verification succeeds before the final file is published.
- Successful merge updates `PENDING`, deletes chunks, and publishes after
  commit.
- MD5 mismatch and I/O failure restore a retryable upload state.
- File deletion cleans up remaining chunks.

Frontend tests cover:

- Chunk count and chunk boundaries, including empty and exact-multiple files.
- Filtering already uploaded chunks.
- Bounded concurrency.
- Retry behavior and progress calculation.

Verification includes focused backend tests, the full Maven test suite, frontend
Node tests, and a production Vite build.
