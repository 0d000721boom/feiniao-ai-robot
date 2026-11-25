# Large Markdown Chunk Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add resumable 5 MiB chunk upload, verified merge, cleanup, and asynchronous vectorization for large Markdown knowledge-base files, including the Vue uploader.

**Architecture:** `CustomerServiceImpl` orchestrates upload state while a focused `LocalFileChunkStorage` component owns safe local paths and streaming file operations. MyBatis-Plus mappers persist file and chunk metadata. A Web Worker calculates MD5, and the Vue page uploads missing chunks with bounded concurrency before calling merge.

**Tech Stack:** Java 21, Spring Boot 4.1.2 MVC, MyBatis-Plus 3.5.16, PostgreSQL, JUnit 5, Mockito, Vue 3, Vite 6, Node test runner, axios, spark-md5.

**Spec:** `docs/superpowers/specs/2026-11-26-large-file-chunk-upload-design.md`

## Global Constraints

- Only `.md` and `.markdown` files are accepted.
- Chunk numbering starts at `0`; chunks are 5 MiB and at most three upload concurrently.
- Spring multipart file and request limits are 10 MiB.
- Client MD5 values must contain exactly 32 hexadecimal characters.
- All server-generated paths must remain below `customer-service.md-storage-path`.
- A completed merge verifies the complete MD5 before publishing the final path.
- Vectorization is triggered only after the merge transaction commits.
- Re-uploading an already persisted chunk is idempotent.
- The user's existing working-tree changes must be preserved. Stage only files named by the current task.

Use this command prefix in PowerShell because Maven is not on `PATH`:

```powershell
$mvnCommand = (Get-ChildItem "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.10.0-bin\*\apache-maven-3.10.0\bin\mvn.cmd" | Select-Object -First 1).FullName
```

---

### Task 1: Persistence and Upload Status Foundation

**Files:**
- Create: `docs/sql/2026-11-26-large-file-chunk-upload.sql`
- Modify: `src/main/java/com/feiniao/airobot/domain/dos/FileChunkInfoDO.java`
- Modify: `src/main/java/com/feiniao/airobot/enums/AiCustomerServiceFileStatusEnum.java`
- Modify: `src/main/java/com/feiniao/airobot/enums/ResponseCodeEnum.java`
- Test: `src/test/java/com/feiniao/airobot/enums/FileUploadStatusTest.java`

**Interfaces:**
- Produces: `AiCustomerServiceFileStatusEnum.MERGING` with code `5`.
- Produces: upload error codes `FILE_MD5_INVALID`, `FILE_NAME_INVALID`, `CHUNK_PARAM_INVALID`, `CHUNK_METADATA_MISMATCH`, `CHUNK_INCOMPLETE`, `FILE_MERGE_IN_PROGRESS`, `FILE_MD5_MISMATCH`, and `FILE_UPLOAD_RECORD_NOT_FOUND`.
- Produces: `FileChunkInfoDO.createTime`.

- [ ] **Step 1: Write the failing status test**

```java
package com.feiniao.airobot.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileUploadStatusTest {

    @Test
    void mergingStatusUsesAStableCode() {
        assertEquals(5, AiCustomerServiceFileStatusEnum.MERGING.getCode());
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

```powershell
& $mvnCommand -q -Dtest=FileUploadStatusTest test
```

Expected: compilation failure because `MERGING` does not exist.

- [ ] **Step 3: Add the schema, status, and error codes**

Create the SQL script:

```sql
CREATE TABLE IF NOT EXISTS t_file_chunk_info (
    id BIGSERIAL PRIMARY KEY,
    file_md5 VARCHAR(32) NOT NULL,
    chunk_number INTEGER NOT NULL,
    chunk_path TEXT NOT NULL,
    chunk_size BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_file_chunk_file_md5_number UNIQUE (file_md5, chunk_number)
);

CREATE INDEX IF NOT EXISTS idx_file_chunk_file_md5
    ON t_file_chunk_info (file_md5);

CREATE UNIQUE INDEX IF NOT EXISTS uk_file_storage_file_md5
    ON t_ai_customer_service_file_storage (file_md5);
```

Add `createTime` to `FileChunkInfoDO`:

```java
private LocalDateTime createTime;
```

Add the enum value after `FAILED`:

```java
MERGING(5, "合并中");
```

Add error codes after `MARKDOWN_FILE_CANT_DELETE`:

```java
FILE_MD5_INVALID("20006", "文件 MD5 不合法"),
FILE_NAME_INVALID("20007", "文件名称不合法，仅支持 Markdown 文件"),
CHUNK_PARAM_INVALID("20008", "文件分片参数不合法"),
CHUNK_METADATA_MISMATCH("20009", "文件分片元数据不一致"),
CHUNK_INCOMPLETE("20010", "文件分片不完整，请继续上传"),
FILE_MERGE_IN_PROGRESS("20011", "文件正在合并，请稍后重试"),
FILE_MD5_MISMATCH("20012", "合并后的文件 MD5 校验失败"),
FILE_UPLOAD_RECORD_NOT_FOUND("20013", "文件上传记录不存在"),
```

- [ ] **Step 4: Run the focused test**

```powershell
& $mvnCommand -q -Dtest=FileUploadStatusTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the task**

```powershell
git add docs/sql/2026-11-26-large-file-chunk-upload.sql `
  src/main/java/com/feiniao/airobot/domain/dos/FileChunkInfoDO.java `
  src/main/java/com/feiniao/airobot/enums/AiCustomerServiceFileStatusEnum.java `
  src/main/java/com/feiniao/airobot/enums/ResponseCodeEnum.java `
  src/test/java/com/feiniao/airobot/enums/FileUploadStatusTest.java
git commit -m "feat: add chunk upload persistence foundation"
```

### Task 2: Safe Local Chunk Storage and Merge

**Files:**
- Create: `src/main/java/com/feiniao/airobot/service/impl/LocalFileChunkStorage.java`
- Test: `src/test/java/com/feiniao/airobot/service/impl/LocalFileChunkStorageTest.java`

**Interfaces:**
- Produces constructor: `LocalFileChunkStorage(String storageRoot)`
- Produces: `void saveChunk(String fileMd5, int chunkNumber, MultipartFile chunk)`
- Produces: `Path mergeChunks(String fileMd5, String originalFileName, int totalChunks)`
- Produces: `boolean chunkExists(String fileMd5, int chunkNumber)`
- Produces: `void deleteChunks(String fileMd5)`
- Produces: `void deleteFile(String filePath)`

- [ ] **Step 1: Write failing storage tests**

```java
package com.feiniao.airobot.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.feiniao.airobot.enums.ResponseCodeEnum;
import com.feiniao.airobot.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileChunkStorageTest {

    @TempDir
    Path storageRoot;

    @Test
    void mergeWritesChunksInNumericOrderAndVerifiesMd5() throws Exception {
        LocalFileChunkStorage storage = new LocalFileChunkStorage(storageRoot.toString());
        byte[] first = "first\n".getBytes();
        byte[] second = "second\n".getBytes();
        String md5 = DigestUtil.md5Hex("first\nsecond\n".getBytes());
        storage.saveChunk(md5, 0, new MockMultipartFile("file", first));
        storage.saveChunk(md5, 1, new MockMultipartFile("file", second));

        Path merged = storage.mergeChunks(md5, "../faq.md", 2);

        assertTrue(Files.exists(merged));
        assertTrue(merged.startsWith(storageRoot.toAbsolutePath().normalize()));
        assertTrue(merged.getFileName().toString().endsWith("-faq.md"));
        assertEquals("first\nsecond\n", Files.readString(merged));
    }

    @Test
    void md5MismatchDoesNotPublishMergedFile() throws Exception {
        LocalFileChunkStorage storage = new LocalFileChunkStorage(storageRoot.toString());
        String wrongMd5 = "00000000000000000000000000000000";
        storage.saveChunk(wrongMd5, 0, new MockMultipartFile("file", "content".getBytes()));

        BizException error = assertThrows(BizException.class,
                () -> storage.mergeChunks(wrongMd5, "faq.md", 1));

        assertEquals(ResponseCodeEnum.FILE_MD5_MISMATCH.getErrorCode(), error.getErrorCode());
        try (var files = Files.list(storageRoot)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith("-faq.md")));
        }
    }

    @Test
    void invalidMd5CannotEscapeTheStorageRoot() {
        LocalFileChunkStorage storage = new LocalFileChunkStorage(storageRoot.toString());

        BizException error = assertThrows(BizException.class,
                () -> storage.saveChunk("../../outside", 0,
                        new MockMultipartFile("file", "x".getBytes())));

        assertEquals(ResponseCodeEnum.FILE_MD5_INVALID.getErrorCode(), error.getErrorCode());
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail**

```powershell
& $mvnCommand -q -Dtest=LocalFileChunkStorageTest test
```

Expected: compilation failure because `LocalFileChunkStorage` does not exist.

- [ ] **Step 3: Implement the storage component**

Use `^[0-9a-fA-F]{32}$` to validate MD5. Store chunks at
`storageRoot/.chunks/{md5}/{chunkNumber}.part`.

`saveChunk` must create directories and copy the multipart input to the chunk
path. `mergeChunks` must:

1. Open a buffer of 64 KiB.
2. Resolve every `0..totalChunks-1` chunk and reject missing/non-regular files
   with `CHUNK_INCOMPLETE`.
3. Create a temporary file under `storageRoot`.
4. Copy each chunk into the output while updating an MD5 digest.
5. Compare the digest case-insensitively with `fileMd5`.
6. Delete the temporary file and throw `FILE_MD5_MISMATCH` on mismatch.
7. Generate `{uuid}-{FilenameUtils.getName(originalFileName)}` and move the
   verified temporary file to that final path with `ATOMIC_MOVE`, falling back
   to a normal move only when the filesystem does not support atomic moves.

`deleteChunks` must recursively delete only the validated chunk directory.
`chunkExists` must return true only for a regular file at the validated chunk
path.
`deleteFile` must normalize the path and refuse paths outside `storageRoot`.

- [ ] **Step 4: Run the storage tests**

```powershell
& $mvnCommand -q -Dtest=LocalFileChunkStorageTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the task**

```powershell
git add src/main/java/com/feiniao/airobot/service/impl/LocalFileChunkStorage.java `
  src/test/java/com/feiniao/airobot/service/impl/LocalFileChunkStorageTest.java
git commit -m "feat: add safe local chunk storage"
```

### Task 3: Chunk Upload and Resume Behavior

**Files:**
- Create: `src/main/java/com/feiniao/airobot/model/vo/customerService/UploadChunkReqVO.java`
- Modify: `src/main/java/com/feiniao/airobot/domain/mapper/FileChunkInfoMapper.java`
- Modify: `src/main/java/com/feiniao/airobot/service/CustomerService.java`
- Modify: `src/main/java/com/feiniao/airobot/service/impl/CustomerServiceImpl.java`
- Test: `src/test/java/com/feiniao/airobot/service/CustomerServiceImplTest.java`

**Interfaces:**
- Produces: `Response<?> uploadChunk(UploadChunkReqVO request)`
- Produces mapper methods:
  - `FileChunkInfoDO selectByFileMd5AndChunkNumber(String fileMd5, Integer chunkNumber)`
  - `int deleteByFileMd5(String fileMd5)`
- `UploadChunkReqVO` exposes `fileMd5`, `fileName`, `fileSize`,
  `chunkNumber`, `totalChunks`, and `chunk`.

- [ ] **Step 1: Write failing upload tests**

```java
@Test
void firstChunkCreatesUploadingRecordAndStoresChunk() {
    String md5 = "0123456789abcdef0123456789abcdef";
    when(storageMapper.selectByMd5(md5)).thenReturn(null);
    when(chunkMapper.selectByFileMd5AndChunkNumber(md5, 0)).thenReturn(null);
    doAnswer(invocation -> {
        AiCustomerServiceFileStorageDO row = invocation.getArgument(0);
        row.setId(41L);
        return 1;
    }).when(storageMapper).insert(any(AiCustomerServiceFileStorageDO.class));

    Response<?> response = service.uploadChunk(UploadChunkReqVO.builder()
            .fileMd5(md5)
            .fileName("guide.md")
            .fileSize(11L)
            .chunkNumber(0)
            .totalChunks(2)
            .chunk(new MockMultipartFile("file", "hello".getBytes()))
            .build());

    assertTrue(response.isSuccess());
    verify(chunkStorage).saveChunk(eq(md5), eq(0), any(MultipartFile.class));
    verify(chunkMapper).insert(any(FileChunkInfoDO.class));
}

@Test
void duplicateChunkUploadIsIdempotent() {
    String md5 = "0123456789abcdef0123456789abcdef";
    when(storageMapper.selectByMd5(md5)).thenReturn(uploadingStorage(md5));
    when(chunkMapper.selectByFileMd5AndChunkNumber(md5, 0))
            .thenReturn(FileChunkInfoDO.builder().chunkNumber(0).build());
    when(chunkStorage.chunkExists(md5, 0)).thenReturn(true);

    Response<?> response = service.uploadChunk(uploadRequest(md5, 0));

    assertTrue(response.isSuccess());
    verify(chunkStorage, never()).saveChunk(any(), anyInt(), any());
    verify(chunkMapper, never()).insert(any(FileChunkInfoDO.class));
}
```

The `chunkStorage` mock is added to `CustomerServiceImplTest` alongside the
existing mapper mocks. Add helper methods in the test class:

```java
private UploadChunkReqVO uploadRequest(String md5, int chunkNumber) {
    return UploadChunkReqVO.builder()
            .fileMd5(md5)
            .fileName("guide.md")
            .fileSize(11L)
            .chunkNumber(chunkNumber)
            .totalChunks(2)
            .chunk(new MockMultipartFile("file", "hello".getBytes()))
            .build();
}

private AiCustomerServiceFileStorageDO uploadingStorage(String md5) {
    return AiCustomerServiceFileStorageDO.builder()
            .id(41L)
            .fileMd5(md5)
            .fileName("guide.md")
            .fileSize(11L)
            .totalChunks(2)
            .uploadedChunks(1)
            .status(AiCustomerServiceFileStatusEnum.UPLOADING.getCode())
            .build();
}
```

- [ ] **Step 2: Run the upload tests and verify they fail**

```powershell
& $mvnCommand -q -Dtest=CustomerServiceImplTest test
```

Expected: compilation failure because `uploadChunk`, `UploadChunkReqVO`, and
the mapper methods do not exist.

- [ ] **Step 3: Implement upload validation and persistence**

Validate in this order:

1. Request and multipart chunk are non-null and non-empty.
2. MD5 matches `^[0-9a-fA-F]{32}$`.
3. Safe basename ends with `.md` or `.markdown`.
4. `fileSize >= 0`, `totalChunks > 0`, and
   `0 <= chunkNumber < totalChunks`.
5. Chunk byte length is at most 5 MiB.

If an existing chunk row has a matching file on disk, return success without
writing again. If the database row exists but its file is missing, delete the
stale row and continue with a fresh upload.

If the storage record is absent, insert it with `UPLOADING`, the request
metadata, zero uploaded chunks, and current timestamps. If present, require
`UPLOADING` and matching filename, file size, and total chunk count.

Call `chunkStorage.saveChunk`, insert a `FileChunkInfoDO`, and update the file
storage row with the actual number of chunk rows and a new `updateTime`.
Delete the just-written chunk if database insertion fails.

- [ ] **Step 4: Run the focused service tests**

```powershell
& $mvnCommand -q -Dtest=CustomerServiceImplTest test
```

Expected: PASS after removing the obsolete `uploadFile` and
`uploadMarkdownFile` test references that no longer match the service contract.

- [ ] **Step 5: Commit the task**

```powershell
git add src/main/java/com/feiniao/airobot/model/vo/customerService/UploadChunkReqVO.java `
  src/main/java/com/feiniao/airobot/domain/mapper/FileChunkInfoMapper.java `
  src/main/java/com/feiniao/airobot/service/CustomerService.java `
  src/main/java/com/feiniao/airobot/service/impl/CustomerServiceImpl.java `
  src/test/java/com/feiniao/airobot/service/CustomerServiceImplTest.java
git commit -m "feat: support resumable chunk upload"
```

### Task 4: Verified Merge and After-Commit Vectorization

**Files:**
- Create: `src/main/java/com/feiniao/airobot/model/vo/customerService/MergeFileReqVO.java`
- Modify: `src/main/java/com/feiniao/airobot/service/CustomerService.java`
- Modify: `src/main/java/com/feiniao/airobot/service/impl/CustomerServiceImpl.java`
- Modify: `src/main/java/com/feiniao/airobot/event/listener/AiCustomerServiceMdUploadedListener.java`
- Test: `src/test/java/com/feiniao/airobot/service/CustomerServiceImplTest.java`

**Interfaces:**
- Produces: `Response<?> mergeFile(MergeFileReqVO request)`
- `MergeFileReqVO` exposes a nonblank, 32-character hexadecimal `fileMd5`.
- `AiCustomerServiceMdUploadedListener.vectorizing` uses
  `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`.

- [ ] **Step 1: Write failing merge tests**

```java
@Test
void mergeRejectsIncompleteChunkSetAndRestoresUploading() {
    String md5 = "0123456789abcdef0123456789abcdef";
    AiCustomerServiceFileStorageDO storage = uploadingStorage(md5);
    when(storageMapper.selectByMd5(md5)).thenReturn(storage);
    when(storageMapper.update(any(), any())).thenReturn(1);
    when(chunkStorage.mergeChunks(md5, "guide.md", 2))
            .thenThrow(new BizException(ResponseCodeEnum.CHUNK_INCOMPLETE));

    BizException error = assertThrows(BizException.class,
            () -> service.mergeFile(MergeFileReqVO.builder().fileMd5(md5).build()));

    assertEquals(ResponseCodeEnum.CHUNK_INCOMPLETE.getErrorCode(), error.getErrorCode());
    verify(storageMapper, atLeast(2)).update(any(), any());
    verify(eventPublisher, never()).publishEvent(any());
}

@Test
void successfulMergePublishesFileAndDeletesChunkRecords() {
    String md5 = "0123456789abcdef0123456789abcdef";
    Path finalPath = storageDirectory.resolve("merged-guide.md");
    when(storageMapper.selectByMd5(md5)).thenReturn(uploadingStorage(md5));
    when(storageMapper.update(any(), any())).thenReturn(1);
    when(chunkStorage.mergeChunks(md5, "guide.md", 2)).thenReturn(finalPath);

    service.mergeFile(MergeFileReqVO.builder().fileMd5(md5).build());

    verify(storageMapper, atLeast(2)).update(any(), any());
    verify(chunkMapper).deleteByFileMd5(md5);
    verify(chunkStorage).deleteChunks(md5);
    verify(eventPublisher).publishEvent(
            argThat((AiCustomerServiceMdUploadedEvent event) ->
                    event.getId().equals(41L)
                            && event.getFilePath().equals(finalPath.toString())));
}
```

- [ ] **Step 2: Run the merge tests and verify they fail**

```powershell
& $mvnCommand -q -Dtest=CustomerServiceImplTest test
```

Expected: compilation failure because merge behavior does not exist.

- [ ] **Step 3: Implement merge claiming and recovery**

Annotate `mergeFile` with `@Transactional(rollbackFor = Exception.class)`.

Load the record by MD5. Return success without writing when the path exists and
the status is `PENDING`, `VECTORIZING`, `COMPLETED`, or `FAILED`.

Atomically claim `UPLOADING -> MERGING` with a conditional update. If another
request owns `MERGING`, throw `FILE_MERGE_IN_PROGRESS`.

On successful `chunkStorage.mergeChunks`:

- Update final path, file size, `uploadedChunks = totalChunks`,
  `status = PENDING`, and timestamps.
- Delete all chunk rows through `deleteByFileMd5`.
- Delete the chunk directory.
- Publish `AiCustomerServiceMdUploadedEvent` with storage ID, final path, and
  metadata containing `fileMd5` and `originalFileName`.

On failure:

- Remove the final file if a later database step failed.
- Restore `UPLOADING` for missing chunks and I/O errors.
- For MD5 mismatch, also delete chunk rows/files and reset
  `uploadedChunks = 0`.
- Re-throw `BizException` unchanged; wrap other I/O failures as
  `UPLOAD_FILE_FAILED`.

Change the listener to `@TransactionalEventListener` using `AFTER_COMMIT` so
the async handler cannot observe uncommitted metadata.

- [ ] **Step 4: Run service tests**

```powershell
& $mvnCommand -q -Dtest=CustomerServiceImplTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the task**

```powershell
git add src/main/java/com/feiniao/airobot/model/vo/customerService/MergeFileReqVO.java `
  src/main/java/com/feiniao/airobot/service/CustomerService.java `
  src/main/java/com/feiniao/airobot/service/impl/CustomerServiceImpl.java `
  src/main/java/com/feiniao/airobot/event/listener/AiCustomerServiceMdUploadedListener.java `
  src/test/java/com/feiniao/airobot/service/CustomerServiceImplTest.java
git commit -m "feat: merge verified file chunks"
```

### Task 5: HTTP Endpoints and Multipart Configuration

**Files:**
- Modify: `src/main/java/com/feiniao/airobot/controller/AiCustomerServiceController.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/feiniao/airobot/controller/AiCustomerServiceControllerTest.java`

**Interfaces:**
- Produces: `POST /customer-service/file/chunk/upload`
- Produces: `POST /customer-service/file/merge`
- Consumes: `CustomerService.uploadChunk(UploadChunkReqVO)` and
  `CustomerService.mergeFile(MergeFileReqVO)`.

- [ ] **Step 1: Write failing controller tests**

```java
@Test
void uploadChunkBuildsMultipartRequestAndDelegates() {
    MockMultipartFile chunk = new MockMultipartFile("file", "chunk".getBytes());
    Response<?> success = Response.success();
    when(customerService.uploadChunk(any(UploadChunkReqVO.class))).thenReturn(success);

    Response<?> result = controller.uploadChunk(
            chunk,
            "0123456789abcdef0123456789abcdef",
            "guide.md",
            11L,
            0,
            2);

    assertSame(success, result);
    ArgumentCaptor<UploadChunkReqVO> captor =
            ArgumentCaptor.forClass(UploadChunkReqVO.class);
    verify(customerService).uploadChunk(captor.capture());
    assertEquals("guide.md", captor.getValue().getFileName());
    assertSame(chunk, captor.getValue().getChunk());
}

@Test
void mergeDelegatesToService() {
    MergeFileReqVO request = MergeFileReqVO.builder()
            .fileMd5("0123456789abcdef0123456789abcdef")
            .build();
    Response<?> success = Response.success();
    when(customerService.mergeFile(request)).thenReturn(success);

    assertSame(success, controller.mergeFile(request));
}
```

- [ ] **Step 2: Run the controller tests and verify they fail**

```powershell
& $mvnCommand -q -Dtest=AiCustomerServiceControllerTest test
```

Expected: compilation failure because the controller methods do not exist.

- [ ] **Step 3: Implement the endpoints and limits**

Add:

```java
@PostMapping(value = "/file/chunk/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public Response<?> uploadChunk(
        @RequestPart("file") MultipartFile file,
        @RequestParam String fileMd5,
        @RequestParam String fileName,
        @RequestParam Long fileSize,
        @RequestParam Integer chunkNumber,
        @RequestParam Integer totalChunks) {
    return customerService.uploadChunk(UploadChunkReqVO.builder()
            .fileMd5(fileMd5)
            .fileName(fileName)
            .fileSize(fileSize)
            .chunkNumber(chunkNumber)
            .totalChunks(totalChunks)
            .chunk(file)
            .build());
}

@PostMapping("/file/merge")
public Response<?> mergeFile(@RequestBody @Validated MergeFileReqVO request) {
    return customerService.mergeFile(request);
}
```

Add to `application.yml`:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

- [ ] **Step 4: Run controller tests**

```powershell
& $mvnCommand -q -Dtest=AiCustomerServiceControllerTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the task**

```powershell
git add src/main/java/com/feiniao/airobot/controller/AiCustomerServiceController.java `
  src/main/resources/application.yml `
  src/test/java/com/feiniao/airobot/controller/AiCustomerServiceControllerTest.java
git commit -m "feat: expose chunk upload and merge APIs"
```

### Task 6: Check and Delete Lifecycle Cleanup

**Files:**
- Modify: `src/main/java/com/feiniao/airobot/service/impl/CustomerServiceImpl.java`
- Test: `src/test/java/com/feiniao/airobot/service/CustomerServiceImplTest.java`

**Interfaces:**
- `checkFile` returns `needUpload = false` for `MERGING`.
- `deleteMarkdownFile` calls `deleteByFileMd5` and `deleteChunks`.

- [ ] **Step 1: Write failing lifecycle tests**

```java
@Test
void checkFileDoesNotRequestChunksWhileMerging() {
    String md5 = "0123456789abcdef0123456789abcdef";
    when(storageMapper.selectByMd5(md5)).thenReturn(
            AiCustomerServiceFileStorageDO.builder()
                    .fileMd5(md5)
                    .status(AiCustomerServiceFileStatusEnum.MERGING.getCode())
                    .build());

    Response<CheckFileRspVO> response = service.checkFile(
            CheckFileReqVO.builder().fileMd5(md5).build());

    assertTrue(response.getData().getExists());
    assertFalse(response.getData().getNeedUpload());
}

@Test
void deletingFileAlsoDeletesRemainingChunks() throws Exception {
    String md5 = "0123456789abcdef0123456789abcdef";
    Path finalFile = Files.writeString(storageDirectory.resolve("guide.md"), "# guide");
    AiCustomerServiceFileStorageDO storage = AiCustomerServiceFileStorageDO.builder()
            .id(41L)
            .fileMd5(md5)
            .filePath(finalFile.toString())
            .status(AiCustomerServiceFileStatusEnum.COMPLETED.getCode())
            .build();
    when(storageMapper.selectById(41L)).thenReturn(storage);

    service.deleteMarkdownFile(DeleteMarkdownFileReqVO.builder().id(41L).build());

    verify(chunkMapper).deleteByFileMd5(md5);
    verify(chunkStorage).deleteChunks(md5);
    assertFalse(Files.exists(finalFile));
}
```

- [ ] **Step 2: Run the lifecycle tests and verify they fail**

```powershell
& $mvnCommand -q -Dtest=CustomerServiceImplTest test
```

Expected: the merge check and chunk cleanup assertions fail.

- [ ] **Step 3: Implement lifecycle cleanup**

Change the check rule so only `UPLOADING` returns chunk indexes and
`needUpload = true`. `MERGING` returns `exists = true`, `needUpload = false`,
and an empty list.

After deleting the file storage row, call `deleteByFileMd5` and
`chunkStorage.deleteChunks`. Log chunk cleanup failures without failing the
already-committed final-file deletion.

- [ ] **Step 4: Run the service tests**

```powershell
& $mvnCommand -q -Dtest=CustomerServiceImplTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the task**

```powershell
git add src/main/java/com/feiniao/airobot/service/impl/CustomerServiceImpl.java `
  src/test/java/com/feiniao/airobot/service/CustomerServiceImplTest.java
git commit -m "feat: clean chunks across file lifecycle"
```

### Task 7: Frontend Chunk Utilities and API Client

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Create: `frontend/src/utils/chunkUpload.js`
- Modify: `frontend/src/api/customerService.js`
- Test: `frontend/tests/chunkUpload.test.js`

**Interfaces:**
- Produces constants `CHUNK_SIZE = 5 * 1024 * 1024` and
  `MAX_CONCURRENT_UPLOADS = 3`.
- Produces: `getChunkCount(fileSize)`
- Produces: `getMissingChunkNumbers(totalChunks, uploadedChunks)`
- Produces: `runWithConcurrency(items, limit, worker, onSettled)`
- Produces: `withRetries(task, retries = 2)`
- Produces: `calculateUploadPercent(completed, total)`
- Produces: `createUploadProgress()`
- Produces API functions `checkFile`, `uploadFileChunk`, and `mergeFile`.

- [ ] **Step 1: Install the MD5 dependency**

```powershell
Set-Location frontend
npm install spark-md5@3.0.2
Set-Location ..
```

- [ ] **Step 2: Write failing frontend helper tests**

```javascript
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  CHUNK_SIZE,
  calculateUploadPercent,
  getChunkCount,
  getMissingChunkNumbers,
  runWithConcurrency,
  withRetries,
} from '../src/utils/chunkUpload.js'

test('chunk count handles exact multiples and a final partial chunk', () => {
  assert.equal(getChunkCount(0), 0)
  assert.equal(getChunkCount(CHUNK_SIZE), 1)
  assert.equal(getChunkCount(CHUNK_SIZE + 1), 2)
})

test('missing chunk calculation skips uploaded indexes', () => {
  assert.deepEqual(getMissingChunkNumbers(5, [0, 2, 4]), [1, 3])
})

test('concurrency helper never exceeds its limit', async () => {
  let active = 0
  let maximum = 0
  const settled = []
  await runWithConcurrency([1, 2, 3, 4, 5], 2, async (value) => {
    active += 1
    maximum = Math.max(maximum, active)
    await new Promise((resolve) => setTimeout(resolve, 2))
    active -= 1
  }, (value) => settled.push(value))

  assert.equal(maximum, 2)
  assert.deepEqual(settled.sort((left, right) => left - right), [1, 2, 3, 4, 5])
})

test('retry helper retries twice before succeeding', async () => {
  let attempts = 0
  const result = await withRetries(async () => {
    attempts += 1
    if (attempts < 3) throw new Error('retry')
    return 'ok'
  })

  assert.equal(result, 'ok')
  assert.equal(attempts, 3)
})

test('upload percent is bounded', () => {
  assert.equal(calculateUploadPercent(0, 0), 100)
  assert.equal(calculateUploadPercent(1, 4), 25)
  assert.equal(calculateUploadPercent(5, 4), 100)
})
```

- [ ] **Step 3: Update the API functions**

```javascript
export function checkFile(fileMd5) {
  return axios.post('/customer-service/file/check', { fileMd5 })
}

export function uploadFileChunk(formData, onUploadProgress) {
  return axios.post('/customer-service/file/chunk/upload', formData, {
    timeout: 60_000,
    onUploadProgress,
  })
}

export function mergeFile(fileMd5) {
  return axios.post('/customer-service/file/merge', { fileMd5 }, {
    timeout: 0,
  })
}
```

Implement the pure helpers without importing axios. `runWithConcurrency` must
start at most `limit` workers, advance through the input in shared index order,
and call `onSettled(value)` as each item completes.

Implement the session progress object with stable snapshot fields:

```javascript
export function createUploadProgress() {
  const state = {
    stage: 'idle',
    percent: 0,
    completedChunks: 0,
    totalChunks: 0,
  }

  return {
    setStage(stage, percent = 0, completedChunks = 0, totalChunks = 0) {
      Object.assign(state, { stage, percent, completedChunks, totalChunks })
    },
    snapshot() {
      return { ...state }
    },
  }
}
```

- [ ] **Step 4: Run frontend helper tests**

```powershell
Set-Location frontend
npm test
Set-Location ..
```

Expected: all existing and new Node tests pass.

- [ ] **Step 5: Commit the task**

```powershell
git add frontend/package.json frontend/package-lock.json `
  frontend/src/utils/chunkUpload.js frontend/src/api/customerService.js `
  frontend/tests/chunkUpload.test.js
git commit -m "feat: add chunk upload client helpers"
```

### Task 8: MD5 Worker and Vue Upload Flow

**Files:**
- Create: `frontend/src/workers/fileMd5.worker.js`
- Create: `frontend/src/utils/fileMd5.js`
- Modify: `frontend/src/views/CustomerServiceChatPage.vue`
- Test: `frontend/tests/uploadSession.test.js`

**Interfaces:**
- Produces: `calculateFileMd5(file, onProgress): Promise<string>`.
- The worker receives `{ file, sliceSize }` and posts `{ type: 'progress',
  percent }` and `{ type: 'done', md5 }`.
- The page consumes `checkFile`, `uploadFileChunk`, `mergeFile`,
  `getChunkCount`, `getMissingChunkNumbers`, `runWithConcurrency`, and
  `withRetries`.

- [ ] **Step 1: Add a failing session-state test**

Create `frontend/tests/uploadSession.test.js`:

```javascript
import test from 'node:test'
import assert from 'node:assert/strict'

import { createUploadProgress } from '../src/utils/chunkUpload.js'

test('upload progress separates hashing, uploading, and merging', () => {
  const progress = createUploadProgress()
  progress.setStage('hashing', 40)
  progress.setStage('uploading', 25, 1, 4)
  progress.setStage('merging')

  assert.deepEqual(progress.snapshot(), {
    stage: 'merging',
    percent: 100,
    completedChunks: 1,
    totalChunks: 4,
  })
})
```

- [ ] **Step 2: Run the test and verify it fails**

```powershell
Set-Location frontend
node --test tests/uploadSession.test.js
Set-Location ..
```

Expected: import failure because `createUploadProgress` is missing.

- [ ] **Step 3: Implement the worker and page flow**

The worker reads the file in 2 MiB slices, appends each `ArrayBuffer` to
`SparkMD5.ArrayBuffer`, posts progress after each slice, and posts the final
lowercase MD5.

`calculateFileMd5` creates a Vite module worker:

```javascript
new Worker(new URL('../workers/fileMd5.worker.js', import.meta.url), {
  type: 'module',
})
```

It resolves on `done`, rejects on worker error, and terminates in both paths.

Update `CustomerServiceChatPage.vue` so `handleFileSelect` performs:

1. Validate `.md/.markdown` and reject an empty file.
2. Calculate MD5 and show hashing progress.
3. Call `checkFile`.
4. Return success immediately when the server reports the merged file exists
   and no upload is needed.
5. Calculate `totalChunks` and upload only
   `getMissingChunkNumbers(totalChunks, uploadedChunks)`.
6. Build multipart data with `file`, `fileMd5`, `fileName`, `fileSize`,
   `chunkNumber`, and `totalChunks`.
7. Wrap each chunk request in `withRetries`.
8. Upload with `runWithConcurrency(..., 3, ...)` and update progress after each
   settled chunk.
9. Call `mergeFile`, show the merge stage, reload the file list, and reset
   progress in `finally`.

Add a compact `a-progress` block below the upload controls showing:

- `正在计算文件 MD5`
- `正在上传分片 x / y`
- `正在合并文件`

Use `Math.min(file.size, (chunkNumber + 1) * CHUNK_SIZE)` as the slice end.
Reset the hidden input after reading the file.

- [ ] **Step 4: Update frontend status display**

Correct the page status map to:

```javascript
const statusMap = {
  0: { label: '上传中', color: 'default' },
  1: { label: '待处理', color: 'processing' },
  2: { label: '向量化中', color: 'processing' },
  3: { label: '已完成', color: 'success' },
  4: { label: '失败', color: 'error' },
  5: { label: '合并中', color: 'processing' },
}
```

Allow deletion only for status `3` and `4`.

- [ ] **Step 5: Run frontend tests and production build**

```powershell
Set-Location frontend
npm test
npm run build
Set-Location ..
```

Expected: all tests pass and Vite exits with code `0`.

- [ ] **Step 6: Commit the task**

```powershell
git add frontend/src/workers/fileMd5.worker.js `
  frontend/src/utils/fileMd5.js `
  frontend/src/views/CustomerServiceChatPage.vue `
  frontend/tests/uploadSession.test.js `
  frontend/src/utils/chunkUpload.js
git commit -m "feat: add resumable Markdown upload UI"
```

### Task 9: Full Verification and Handoff

**Files:**
- Verify only; modify files only to fix a failing check.

**Interfaces:**
- Consumes every interface produced by Tasks 1-8.

- [ ] **Step 1: Run all backend tests**

```powershell
& $mvnCommand -q test
```

Expected: exit code `0` with no failing tests.

- [ ] **Step 2: Run all frontend tests**

```powershell
Set-Location frontend
npm test
Set-Location ..
```

Expected: exit code `0` with no failing tests.

- [ ] **Step 3: Build the frontend**

```powershell
Set-Location frontend
npm run build
Set-Location ..
```

Expected: exit code `0`.

- [ ] **Step 4: Inspect the final scope**

```powershell
git status --short
git diff --check
rg -n "chunk/upload|file/merge|MERGING|FILE_MD5_MISMATCH|calculateFileMd5" src frontend docs
```

Expected: the new endpoints, status, recovery code, worker flow, SQL script,
and tests are present; no whitespace errors exist.

- [ ] **Step 5: Report any environment-only gap**

If PostgreSQL is reachable, apply
`docs/sql/2026-11-26-large-file-chunk-upload.sql` before an end-to-end upload
test. If it is not reachable, report that schema application remains an
external deployment step; do not claim a database-backed runtime upload passed.
