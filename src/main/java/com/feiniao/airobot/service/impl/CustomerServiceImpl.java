package com.feiniao.airobot.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feiniao.airobot.domain.dos.AiCustomerServiceMdStorageDO;
import com.feiniao.airobot.domain.mapper.AiCustomerServiceMdStorageMapper;
import com.feiniao.airobot.enums.AiCustomerServiceMdStatusEnum;
import com.feiniao.airobot.enums.ResponseCodeEnum;
import com.feiniao.airobot.event.AiCustomerServiceMdUploadedEvent;
import com.feiniao.airobot.exception.BizException;
import com.feiniao.airobot.model.vo.chat.PageRspVO;
import com.feiniao.airobot.model.vo.customerService.DeleteMarkdownFileReqVO;
import com.feiniao.airobot.model.vo.customerService.FindMarkdownFilePageListReqVO;
import com.feiniao.airobot.model.vo.customerService.FindMarkdownFilePageListRspVO;
import com.feiniao.airobot.model.vo.customerService.UpdateMarkdownFileReqVO;
import com.feiniao.airobot.service.CustomerService;
import com.feiniao.airobot.utils.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @Description: AI 客服
 **/
@Service
@Slf4j
public class CustomerServiceImpl  implements CustomerService {
    @Value("${customer-service.md-storage-path}")
    private String mdStoragePath;

    @Resource
    private AiCustomerServiceMdStorageMapper aiCustomerServiceMdStorageMapper;

    @Resource
    private VectorStore vectorStore;

    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public Response<?> uploadMarkdownFile(MultipartFile file) {
        // 校验文件不能为空
        if (file == null || file.isEmpty()) {
            throw new BizException(ResponseCodeEnum.UPLOAD_FILE_CANT_EMPTY);
        }

        // 仅保留文件名本身，防止目录穿越到配置的存储目录之外。
        String originalFilename = StringUtils.trimToEmpty(
                FilenameUtils.getName(file.getOriginalFilename()));

        // 验证文件类型，仅支持 Markdown
        if (StringUtils.isBlank(originalFilename) || !isMarkdownFile(originalFilename)) {
            throw new BizException(ResponseCodeEnum.ONLY_SUPPORT_MARKDOWN);
        }

        try {
            // 重新生成文件名 (防止文件名冲突导致覆盖)
            String newFilename = UUID.randomUUID().toString() + "-" + originalFilename;

            // 构建存储路径
            Path storageDirectory = Paths.get(mdStoragePath);
            Path targetPath = storageDirectory.resolve(newFilename);

            // 确保目录存在
            Files.createDirectories(storageDirectory);

            // 保存文件
            file.transferTo(targetPath);

            // 记录操作日志
            log.info("## Markdown 问答文件存储成功, 文件名：{} -> 存储路径：{}", originalFilename, targetPath);

            // 存储入库
            AiCustomerServiceMdStorageDO storage = AiCustomerServiceMdStorageDO.builder()
                    .originalFileName(originalFilename)
                    .newFileName(newFilename)
                    .filePath(targetPath.toString())
                    .fileSize(file.getSize())
                    .status(AiCustomerServiceMdStatusEnum.PENDING.getCode())
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            if (aiCustomerServiceMdStorageMapper.insert(storage) != 1) {
                throw new BizException(ResponseCodeEnum.UPLOAD_FILE_FAILED);
            }

            // 上传接口只负责落库，向量化由异步事件监听器完成。
            applicationEventPublisher.publishEvent(AiCustomerServiceMdUploadedEvent.builder()
                    .id(storage.getId())
                    .filePath(targetPath.toString())
                    .metadatas(Map.of(
                            "mdStorageId", storage.getId(),
                            "originalFileName", originalFilename
                    ))
                    .build());

            return Response.success();

        } catch (IOException e) {
            log.error("## Markdown 问答文件上传失败：{}", originalFilename, e);
            throw new BizException(ResponseCodeEnum.UPLOAD_FILE_FAILED);
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
        AiCustomerServiceMdStorageDO aiCustomerServiceMdStorageDO = aiCustomerServiceMdStorageMapper.selectById(id);

        // 若记录不存在
        if (Objects.isNull(aiCustomerServiceMdStorageDO)) {
            throw new BizException(ResponseCodeEnum.MARKDOWN_FILE_NOT_FOUND);
        }

        // 正在处理中的文件，无法删除
        AiCustomerServiceMdStatusEnum statusEnum = AiCustomerServiceMdStatusEnum.codeOf(aiCustomerServiceMdStorageDO.getStatus());
        if (Objects.equals(statusEnum, AiCustomerServiceMdStatusEnum.PENDING) // 待向量化
                || Objects.equals(statusEnum, AiCustomerServiceMdStatusEnum.VECTORIZING)) { // 向量化中...
            throw new BizException(ResponseCodeEnum.MARKDOWN_FILE_CANT_DELETE);
        }

        // 删除文件表记录
        aiCustomerServiceMdStorageMapper.deleteById(id);

        // 删除向量化数据
        vectorStore.delete(String.format("mdStorageId == %s", id));

        // 删除本地文件
        String filePath = aiCustomerServiceMdStorageDO.getFilePath();
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            log.error("## Markdown 问答文件删除失败：", e);
        }

        return Response.success();
    }

    @Override
    public Response<?> updateMarkdownFile(UpdateMarkdownFileReqVO request) {
        if (aiCustomerServiceMdStorageMapper.selectById(request.getId()) == null) {
            throw new BizException(ResponseCodeEnum.MARKDOWN_FILE_NOT_FOUND);
        }

        int updatedRows = aiCustomerServiceMdStorageMapper.updateById(
                AiCustomerServiceMdStorageDO.builder()
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
        Page<AiCustomerServiceMdStorageDO> databasePage =
                aiCustomerServiceMdStorageMapper.selectPageList(
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
            AiCustomerServiceMdStorageDO storage) {
        return FindMarkdownFilePageListRspVO.builder()
                .id(storage.getId())
                .originalFileName(storage.getOriginalFileName())
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
