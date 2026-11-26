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
