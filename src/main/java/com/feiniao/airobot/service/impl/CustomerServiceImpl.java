package com.feiniao.airobot.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.feiniao.airobot.domain.dos.AiCustomerServiceFileStorageDO;
import com.feiniao.airobot.domain.dos.FileChunkInfoDO;
import com.feiniao.airobot.domain.mapper.AiCustomerServiceFileStorageMapper;
import com.feiniao.airobot.domain.mapper.FileChunkInfoMapper;
import com.feiniao.airobot.enums.AiCustomerServiceFileStatusEnum;
import com.feiniao.airobot.enums.ResponseCodeEnum;
import com.feiniao.airobot.event.AiCustomerServiceMdUploadedEvent;
import com.feiniao.airobot.exception.BizException;
import com.feiniao.airobot.model.vo.chat.PageRspVO;
import com.feiniao.airobot.model.vo.customerService.*;
import com.feiniao.airobot.service.CustomerService;
import com.feiniao.airobot.utils.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @Description: AI 客服
 **/
@Service
@Slf4j
public class CustomerServiceImpl  implements CustomerService {
    private static final long CHUNK_SIZE = 5L * 1024 * 1024;
    private static final Pattern MD5_PATTERN = Pattern.compile("^[0-9a-fA-F]{32}$");

    @Resource
    private AiCustomerServiceFileStorageMapper aiCustomerServiceFileStorageMapper;

    @Resource
    private VectorStore vectorStore;

    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    @Resource
    private FileChunkInfoMapper fileChunkInfoMapper;

    @Resource
    private LocalFileChunkStorage chunkStorage;

    /**
     * 检查文件是否存在
     *
     * @param checkFileReqVO
     * @return
     */
    @Override
    public Response<CheckFileRspVO> checkFile(CheckFileReqVO checkFileReqVO) {
        String fileMd5 = checkFileReqVO.getFileMd5();
        // 查询对应 MD5 值的文件记录是否已经存在
        AiCustomerServiceFileStorageDO fileStorageDO = aiCustomerServiceFileStorageMapper
                .selectByMd5(fileMd5);

        // 文件记录不存在，需要上传
        if (Objects.isNull(fileStorageDO)) {
            return Response.success(CheckFileRspVO.builder()
                    .exists(false)
                    .needUpload(true)
                    .build());
        }

        // 若文件记录已存在
        Integer status = fileStorageDO.getStatus();
        AiCustomerServiceFileStatusEnum statusEnum = AiCustomerServiceFileStatusEnum.codeOf(status);

        // 判断当前处理状态
        // 文件已完整上传，支持秒传
        if (!Objects.equals(statusEnum, AiCustomerServiceFileStatusEnum.UPLOADING)) {
            return Response.success(CheckFileRspVO.builder()
                    .exists(true)
                    .needUpload(false)
                    .build());
        }

        // 文件正在上传中，返回已上传的分片序号
        List<FileChunkInfoDO> chunks = fileChunkInfoMapper.selectChunkedList(fileMd5);
        List<Integer> uploadedChunks = chunks.stream()
                .map(FileChunkInfoDO::getChunkNumber)
                .toList();

        return Response.success(CheckFileRspVO.builder()
                .exists(true)
                .needUpload(true)
                .uploadedChunks(uploadedChunks)
                .build());
    }

    @Override
    public Response<?> uploadChunk(UploadChunkReqVO request) {
        validateUploadChunk(request);
        String fileMd5 = request.getFileMd5().toLowerCase(Locale.ROOT);
        String fileName = FilenameUtils.getName(request.getFileName().trim());

        AiCustomerServiceFileStorageDO storage = aiCustomerServiceFileStorageMapper.selectByMd5(fileMd5);
        if (storage == null) {
            storage = AiCustomerServiceFileStorageDO.builder()
                    .fileMd5(fileMd5)
                    .fileName(fileName)
                    .fileSize(request.getFileSize())
                    .totalChunks(request.getTotalChunks())
                    .uploadedChunks(0)
                    .status(AiCustomerServiceFileStatusEnum.UPLOADING.getCode())
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            try {
                if (aiCustomerServiceFileStorageMapper.insert(storage) != 1) {
                    throw new BizException(ResponseCodeEnum.UPLOAD_FILE_FAILED);
                }
            } catch (DuplicateKeyException e) {
                storage = aiCustomerServiceFileStorageMapper.selectByMd5(fileMd5);
                if (storage == null) {
                    throw new BizException(ResponseCodeEnum.UPLOAD_FILE_FAILED);
                }
            }
        }
        if (storage != null) {
            if (AiCustomerServiceFileStatusEnum.FAILED.getCode().equals(storage.getStatus())
                    || AiCustomerServiceFileStatusEnum.PENDING.getCode().equals(storage.getStatus())
                    || AiCustomerServiceFileStatusEnum.VECTORIZING.getCode().equals(storage.getStatus())
                    || AiCustomerServiceFileStatusEnum.COMPLETED.getCode().equals(storage.getStatus())) {
                return Response.success();
            }
            if (!AiCustomerServiceFileStatusEnum.UPLOADING.getCode().equals(storage.getStatus())) {
                throw new BizException(ResponseCodeEnum.FILE_MERGE_IN_PROGRESS);
            }
            if (!Objects.equals(storage.getFileName(), fileName)
                    || !Objects.equals(storage.getFileSize(), request.getFileSize())
                    || !Objects.equals(storage.getTotalChunks(), request.getTotalChunks())) {
                throw new BizException(ResponseCodeEnum.CHUNK_METADATA_MISMATCH);
            }
        }

        int chunkNumber = request.getChunkNumber();
        FileChunkInfoDO existingChunk = fileChunkInfoMapper
                .selectByFileMd5AndChunkNumber(fileMd5, chunkNumber);
        if (existingChunk != null) {
            if (chunkStorage.chunkExists(fileMd5, chunkNumber)) {
                return Response.success();
            }
            fileChunkInfoMapper.deleteById(existingChunk.getId());
        }

        try {
            chunkStorage.saveChunk(fileMd5, chunkNumber, request.getChunk());
            FileChunkInfoDO chunkInfo = FileChunkInfoDO.builder()
                    .fileMd5(fileMd5)
                    .chunkNumber(chunkNumber)
                    .chunkPath(chunkStorage.getChunkPath(fileMd5, chunkNumber).toString())
                    .chunkSize(request.getChunk().getSize())
                    .createTime(LocalDateTime.now())
                    .build();
            if (fileChunkInfoMapper.insert(chunkInfo) != 1) {
                throw new BizException(ResponseCodeEnum.UPLOAD_FILE_FAILED);
            }
        } catch (RuntimeException e) {
            chunkStorage.deleteChunk(fileMd5, chunkNumber);
            throw e;
        }

        aiCustomerServiceFileStorageMapper.updateById(
                AiCustomerServiceFileStorageDO.builder()
                        .id(storage.getId())
                        .uploadedChunks(fileChunkInfoMapper.selectChunkedList(fileMd5).size())
                        .updateTime(LocalDateTime.now())
                        .build());
        return Response.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<?> mergeFile(MergeFileReqVO request) {
        String fileMd5 = validateMergeRequest(request);
        AiCustomerServiceFileStorageDO storage = aiCustomerServiceFileStorageMapper.selectByMd5(fileMd5);
        if (storage == null) {
            throw new BizException(ResponseCodeEnum.FILE_UPLOAD_RECORD_NOT_FOUND);
        }

        AiCustomerServiceFileStatusEnum status = AiCustomerServiceFileStatusEnum.codeOf(storage.getStatus());
        if (Objects.equals(status, AiCustomerServiceFileStatusEnum.MERGING)) {
            throw new BizException(ResponseCodeEnum.FILE_MERGE_IN_PROGRESS);
        }
        if (Objects.equals(status, AiCustomerServiceFileStatusEnum.PENDING)
                || Objects.equals(status, AiCustomerServiceFileStatusEnum.VECTORIZING)
                || Objects.equals(status, AiCustomerServiceFileStatusEnum.COMPLETED)
                || Objects.equals(status, AiCustomerServiceFileStatusEnum.FAILED)) {
            return Response.success();
        }
        if (!Objects.equals(status, AiCustomerServiceFileStatusEnum.UPLOADING)) {
            throw new BizException(ResponseCodeEnum.FILE_UPLOAD_RECORD_NOT_FOUND);
        }

        int claimed = aiCustomerServiceFileStorageMapper.update(
                null,
                new UpdateWrapper<AiCustomerServiceFileStorageDO>()
                        .eq("id", storage.getId())
                        .eq("status", AiCustomerServiceFileStatusEnum.UPLOADING.getCode())
                        .set("status", AiCustomerServiceFileStatusEnum.MERGING.getCode())
                        .set("update_time", LocalDateTime.now()));
        if (claimed != 1) {
            throw new BizException(ResponseCodeEnum.FILE_MERGE_IN_PROGRESS);
        }

        Path finalPath = null;
        try {
            finalPath = chunkStorage.mergeChunks(
                    fileMd5, storage.getFileName(), storage.getTotalChunks());
            int updated = aiCustomerServiceFileStorageMapper.updateById(
                    AiCustomerServiceFileStorageDO.builder()
                            .id(storage.getId())
                            .filePath(finalPath.toString())
                            .fileSize(storage.getFileSize())
                            .uploadedChunks(storage.getTotalChunks())
                            .status(AiCustomerServiceFileStatusEnum.PENDING.getCode())
                            .updateTime(LocalDateTime.now())
                            .build());
            if (updated != 1) {
                throw new BizException(ResponseCodeEnum.UPLOAD_FILE_FAILED);
            }

            fileChunkInfoMapper.deleteByFileMd5(fileMd5);
            chunkStorage.deleteChunks(fileMd5);
            applicationEventPublisher.publishEvent(AiCustomerServiceMdUploadedEvent.builder()
                    .id(storage.getId())
                    .filePath(finalPath.toString())
                    .metadatas(Map.of(
                            "mdStorageId", storage.getId(),
                            "fileMd5", fileMd5,
                            "originalFileName", storage.getFileName()))
                    .build());
            return Response.success();
        } catch (BizException e) {
            cleanupFailedMerge(storage, fileMd5, finalPath, e);
            throw e;
        } catch (RuntimeException e) {
            cleanupFailedMerge(storage, fileMd5, finalPath,
                    new BizException(ResponseCodeEnum.UPLOAD_FILE_FAILED));
            throw new BizException(ResponseCodeEnum.UPLOAD_FILE_FAILED);
        }
    }

    private String validateMergeRequest(MergeFileReqVO request) {
        if (request == null
                || request.getFileMd5() == null
                || !MD5_PATTERN.matcher(request.getFileMd5()).matches()) {
            throw new BizException(ResponseCodeEnum.FILE_MD5_INVALID);
        }
        return request.getFileMd5().toLowerCase(Locale.ROOT);
    }

    private void cleanupFailedMerge(
            AiCustomerServiceFileStorageDO storage,
            String fileMd5,
            Path finalPath,
            BizException cause) {
        if (finalPath != null) {
            try {
                chunkStorage.deleteFile(finalPath.toString());
            } catch (RuntimeException ignored) {
                log.warn("## 清理合并失败文件失败: {}", finalPath);
            }
        }

        if (Objects.equals(cause.getErrorCode(), ResponseCodeEnum.FILE_MD5_MISMATCH.getErrorCode())) {
            try {
                fileChunkInfoMapper.deleteByFileMd5(fileMd5);
                chunkStorage.deleteChunks(fileMd5);
            } catch (RuntimeException cleanupError) {
                log.warn("## 清理 MD5 校验失败的分片失败: {}", fileMd5, cleanupError);
            }
        }

        aiCustomerServiceFileStorageMapper.updateById(
                AiCustomerServiceFileStorageDO.builder()
                        .id(storage.getId())
                        .uploadedChunks(
                                Objects.equals(cause.getErrorCode(),
                                        ResponseCodeEnum.FILE_MD5_MISMATCH.getErrorCode())
                                        ? 0
                                        : storage.getUploadedChunks())
                        .status(AiCustomerServiceFileStatusEnum.UPLOADING.getCode())
                        .updateTime(LocalDateTime.now())
                        .build());
    }

    private void validateUploadChunk(UploadChunkReqVO request) {
        if (request == null
                || request.getChunk() == null
                || request.getChunk().isEmpty()
                || request.getFileSize() == null
                || request.getFileSize() <= 0
                || request.getChunkNumber() == null
                || request.getTotalChunks() == null) {
            throw new BizException(ResponseCodeEnum.CHUNK_PARAM_INVALID);
        }
        if (request.getFileMd5() == null
                || !MD5_PATTERN.matcher(request.getFileMd5()).matches()) {
            throw new BizException(ResponseCodeEnum.FILE_MD5_INVALID);
        }
        String fileName = request.getFileName();
        if (StringUtils.isBlank(fileName) || !isMarkdownFile(FilenameUtils.getName(fileName))) {
            throw new BizException(ResponseCodeEnum.FILE_NAME_INVALID);
        }
        if (request.getTotalChunks() <= 0
                || request.getChunkNumber() < 0
                || request.getChunkNumber() >= request.getTotalChunks()
                || request.getChunk().getSize() > CHUNK_SIZE) {
            throw new BizException(ResponseCodeEnum.CHUNK_PARAM_INVALID);
        }
    }

    /**
     * 删除 Markdown 问答文件
     *
     * @param deleteMarkdownFileReqVO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<?> deleteMarkdownFile(DeleteMarkdownFileReqVO deleteMarkdownFileReqVO) {
        // 文件记录 ID
        Long id = deleteMarkdownFileReqVO.getId();

        // 查询该文件记录
        AiCustomerServiceFileStorageDO AiCustomerServiceFileStorageDO = aiCustomerServiceFileStorageMapper.selectById(id);

        // 若记录不存在
        if (Objects.isNull(AiCustomerServiceFileStorageDO)) {
            throw new BizException(ResponseCodeEnum.MARKDOWN_FILE_NOT_FOUND);
        }

        // 正在处理中的文件，无法删除
        AiCustomerServiceFileStatusEnum statusEnum = AiCustomerServiceFileStatusEnum.codeOf(AiCustomerServiceFileStorageDO.getStatus());
        if (Objects.equals(statusEnum, AiCustomerServiceFileStatusEnum.PENDING) // 待向量化
                || Objects.equals(statusEnum, AiCustomerServiceFileStatusEnum.VECTORIZING)) { // 向量化中...
            throw new BizException(ResponseCodeEnum.MARKDOWN_FILE_CANT_DELETE);
        }

        // 删除文件表记录
        aiCustomerServiceFileStorageMapper.deleteById(id);

        // 清理可能残留的分片记录和分片文件
        try {
            fileChunkInfoMapper.deleteByFileMd5(AiCustomerServiceFileStorageDO.getFileMd5());
            chunkStorage.deleteChunks(AiCustomerServiceFileStorageDO.getFileMd5());
        } catch (RuntimeException e) {
            log.warn("## 文件分片清理失败: {}", AiCustomerServiceFileStorageDO.getFileMd5(), e);
        }

        // 删除向量化数据
        vectorStore.delete(String.format("mdStorageId == %s", id));

        // 删除本地文件
        String filePath = AiCustomerServiceFileStorageDO.getFilePath();
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            log.error("## Markdown 问答文件删除失败：", e);
        }

        return Response.success();
    }

    @Override
    public Response<?> updateMarkdownFile(UpdateMarkdownFileReqVO request) {
        if (aiCustomerServiceFileStorageMapper.selectById(request.getId()) == null) {
            throw new BizException(ResponseCodeEnum.MARKDOWN_FILE_NOT_FOUND);
        }

        int updatedRows = aiCustomerServiceFileStorageMapper.updateById(
                AiCustomerServiceFileStorageDO.builder()
                        .id(request.getId())
                        .remark(request.getRemark())
                        .updateTime(LocalDateTime.now())
                        .build());
        if (updatedRows != 1) {
            throw new BizException(ResponseCodeEnum.MARKDOWN_FILE_NOT_FOUND);
        }
        return Response.success();
    }

    @Override
    public PageRspVO<FindMarkdownFilePageListRspVO> findMarkdownFilePageList(
            FindMarkdownFilePageListReqVO request) {
        Page<AiCustomerServiceFileStorageDO> databasePage =
                aiCustomerServiceFileStorageMapper.selectPageList(
                        request.getCurrent(), request.getSize());

        List<FindMarkdownFilePageListRspVO> records = databasePage.getRecords().stream()
                .map(this::toPageListResponse)
                .toList();

        return PageRspVO.<FindMarkdownFilePageListRspVO>builder()
                .pageNum(databasePage.getCurrent())
                .pageSize(databasePage.getSize())
                .total(databasePage.getTotal())
                .totalPages(databasePage.getPages())
                .records(records)
                .build();
    }

    private FindMarkdownFilePageListRspVO toPageListResponse(
            AiCustomerServiceFileStorageDO storage) {
        return FindMarkdownFilePageListRspVO.builder()
                .id(storage.getId())
                .originalFileName(storage.getFileName())
                .fileSize(storage.getFileSize() == null
                        ? null
                        : FileUtils.byteCountToDisplaySize(storage.getFileSize()))
                .status(storage.getStatus())
                .createTime(storage.getCreateTime())
                .updateTime(storage.getUpdateTime())
                .remark(storage.getRemark())
                .build();
    }

    /**
     * 验证文件是否为 Markdown 格式
     */
    private boolean isMarkdownFile(String filename) {
        if (StringUtils.isBlank(filename)) {
            return false;
        }

        // 获取文件扩展名
        String extension = FilenameUtils.getExtension(filename);
        return "md".equalsIgnoreCase(extension);
    }
}
