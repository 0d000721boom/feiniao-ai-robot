package com.feiniao.airobot.service.impl;

import com.feiniao.airobot.enums.ResponseCodeEnum;
import com.feiniao.airobot.exception.BizException;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 本地文件分片的保存、合并与清理。
 */
@Component
public class LocalFileChunkStorage {

    private static final Pattern MD5_PATTERN = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final int BUFFER_SIZE = 64 * 1024;

    private final Path storageRoot;

    public LocalFileChunkStorage(
            @Value("${customer-service.md-storage-path}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public void saveChunk(String fileMd5, int chunkNumber, MultipartFile chunk) {
        validateMd5(fileMd5);
        if (chunkNumber < 0) {
            throw new BizException(ResponseCodeEnum.CHUNK_PARAM_INVALID);
        }
        if (chunk == null || chunk.isEmpty()) {
            throw new BizException(ResponseCodeEnum.UPLOAD_FILE_CANT_EMPTY);
        }

        Path chunkPath = chunkPath(fileMd5, chunkNumber);
        try {
            Files.createDirectories(chunkPath.getParent());
            try (InputStream input = chunk.getInputStream()) {
                Files.copy(input, chunkPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BizException(ResponseCodeEnum.UPLOAD_FILE_FAILED);
        }
    }

    public boolean chunkExists(String fileMd5, int chunkNumber) {
        validateMd5(fileMd5);
        return chunkNumber >= 0 && Files.isRegularFile(chunkPath(fileMd5, chunkNumber));
    }

    public Path mergeChunks(String fileMd5, String originalFileName, int totalChunks) {
        validateMd5(fileMd5);
        if (totalChunks <= 0) {
            throw new BizException(ResponseCodeEnum.CHUNK_PARAM_INVALID);
        }

        Path temporaryFile = storageRoot.resolve(
                ".merge-" + fileMd5 + "-" + UUID.randomUUID() + ".part");
        Path finalFile = null;
        try {
            Files.createDirectories(storageRoot);
            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (OutputStream output = Files.newOutputStream(
                    temporaryFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                for (int chunkNumber = 0; chunkNumber < totalChunks; chunkNumber++) {
                    Path chunkPath = chunkPath(fileMd5, chunkNumber);
                    if (!Files.isRegularFile(chunkPath)) {
                        throw new BizException(ResponseCodeEnum.CHUNK_INCOMPLETE);
                    }
                    try (InputStream input = Files.newInputStream(chunkPath)) {
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            digest.update(buffer, 0, read);
                            output.write(buffer, 0, read);
                        }
                    }
                }
            }

            String actualMd5 = HexFormat.of().formatHex(digest.digest());
            if (!actualMd5.equalsIgnoreCase(fileMd5)) {
                throw new BizException(ResponseCodeEnum.FILE_MD5_MISMATCH);
            }

            String safeFileName = FilenameUtils.getName(originalFileName);
            finalFile = storageRoot.resolve(UUID.randomUUID() + "-" + safeFileName);
            moveAtomically(temporaryFile, finalFile);
            return finalFile;
        } catch (BizException e) {
            deleteIfExists(temporaryFile);
            deleteIfExists(finalFile);
            throw e;
        } catch (IOException | NoSuchAlgorithmException e) {
            deleteIfExists(temporaryFile);
            deleteIfExists(finalFile);
            throw new BizException(ResponseCodeEnum.UPLOAD_FILE_FAILED);
        }
    }

    public void deleteChunks(String fileMd5) {
        validateMd5(fileMd5);
        Path chunkDirectory = chunkDirectory(fileMd5);
        if (!Files.exists(chunkDirectory)) {
            return;
        }

        try (var paths = Files.walk(chunkDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new BizException(ResponseCodeEnum.UPLOAD_FILE_FAILED);
        }
    }

    public void deleteFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        Path path = Path.of(filePath).toAbsolutePath().normalize();
        if (!path.startsWith(storageRoot)) {
            throw new BizException(ResponseCodeEnum.FILE_NAME_INVALID);
        }
        deleteIfExists(path);
    }

    private Path chunkDirectory(String fileMd5) {
        return storageRoot.resolve(".chunks").resolve(fileMd5).normalize();
    }

    private Path chunkPath(String fileMd5, int chunkNumber) {
        return chunkDirectory(fileMd5).resolve(chunkNumber + ".part").normalize();
    }

    private void validateMd5(String fileMd5) {
        if (fileMd5 == null || !MD5_PATTERN.matcher(fileMd5).matches()) {
            throw new BizException(ResponseCodeEnum.FILE_MD5_INVALID);
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The caller reports the primary operation; a stale temp file can be cleaned later.
        }
    }
}
