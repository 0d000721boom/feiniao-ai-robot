package com.feiniao.airobot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feiniao.airobot.domain.dos.AiCustomerServiceFileStorageDO;
import com.feiniao.airobot.domain.dos.FileChunkInfoDO;
import com.feiniao.airobot.domain.mapper.AiCustomerServiceFileStorageMapper;
import com.feiniao.airobot.domain.mapper.FileChunkInfoMapper;
import com.feiniao.airobot.enums.AiCustomerServiceFileStatusEnum;
import com.feiniao.airobot.enums.ResponseCodeEnum;
import com.feiniao.airobot.event.AiCustomerServiceMdUploadedEvent;
import com.feiniao.airobot.exception.BizException;
import com.feiniao.airobot.model.vo.chat.PageRspVO;
import com.feiniao.airobot.model.vo.customerService.FindMarkdownFilePageListReqVO;
import com.feiniao.airobot.model.vo.customerService.FindMarkdownFilePageListRspVO;
import com.feiniao.airobot.model.vo.customerService.MergeFileReqVO;
import com.feiniao.airobot.model.vo.customerService.UploadChunkReqVO;
import com.feiniao.airobot.model.vo.customerService.UpdateMarkdownFileReqVO;
import com.feiniao.airobot.service.impl.CustomerServiceImpl;
import com.feiniao.airobot.service.impl.LocalFileChunkStorage;
import com.feiniao.airobot.utils.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private AiCustomerServiceFileStorageMapper storageMapper;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private FileChunkInfoMapper chunkMapper;

    @Mock
    private LocalFileChunkStorage chunkStorage;

    @InjectMocks
    private CustomerServiceImpl service;

    @TempDir
    private Path storageDirectory;

    @Test
    void findMarkdownFilePageListMapsStorageRecordsToResponse() {
        AiCustomerServiceFileStorageDO storage = AiCustomerServiceFileStorageDO.builder()
                .id(7L)
                .fileName("faq.md")
                .fileSize(2048L)
                .status(2)
                .createTime(LocalDateTime.of(2026, 11, 21, 10, 0))
                .updateTime(LocalDateTime.of(2026, 11, 21, 10, 1))
                .remark("常见问题")
                .build();
        Page<AiCustomerServiceFileStorageDO> databasePage = new Page<>(2, 10);
        databasePage.setTotal(21);
        databasePage.setRecords(List.of(storage));
        when(storageMapper.selectPageList(2L, 10L)).thenReturn(databasePage);

        FindMarkdownFilePageListReqVO request = new FindMarkdownFilePageListReqVO();
        request.setCurrent(2L);
        request.setSize(10L);

        PageRspVO<FindMarkdownFilePageListRspVO> result =
                service.findMarkdownFilePageList(request);

        assertEquals(2L, result.getPageNum());
        assertEquals(10L, result.getPageSize());
        assertEquals(21L, result.getTotal());
        assertEquals(3L, result.getTotalPages());
        assertEquals("faq.md", result.getRecords().getFirst().getOriginalFileName());
        assertEquals("2 KB", result.getRecords().getFirst().getFileSize());
    }

    @Test
    void updateMarkdownFileChangesRemarkForExistingRecord() {
        AiCustomerServiceFileStorageDO storage = AiCustomerServiceFileStorageDO.builder()
                .id(7L)
                .fileName("faq.md")
                .build();
        when(storageMapper.selectById(7L)).thenReturn(storage);
        when(storageMapper.updateById(any(AiCustomerServiceFileStorageDO.class))).thenReturn(1);
        ArgumentCaptor<AiCustomerServiceFileStorageDO> captor =
                ArgumentCaptor.forClass(AiCustomerServiceFileStorageDO.class);

        Response<?> response = service.updateMarkdownFile(
                UpdateMarkdownFileReqVO.builder().id(7L).remark("更新后的备注").build());

        verify(storageMapper).updateById(captor.capture());
        assertEquals("更新后的备注", captor.getValue().getRemark());
        assertTrue(response.isSuccess());
    }

    @Test
    void firstChunkCreatesUploadingRecordAndStoresChunk() {
        String md5 = "0123456789abcdef0123456789abcdef";
        when(storageMapper.selectByMd5(md5)).thenReturn(null);
        when(chunkMapper.selectByFileMd5AndChunkNumber(md5, 0)).thenReturn(null);
        when(chunkMapper.insert(any(FileChunkInfoDO.class))).thenReturn(1);
        when(chunkMapper.selectChunkedList(md5)).thenReturn(List.of(
                FileChunkInfoDO.builder().fileMd5(md5).chunkNumber(0).build()));
        when(chunkStorage.getChunkPath(md5, 0))
                .thenReturn(storageDirectory.resolve("0.part"));
        when(storageMapper.updateById(any(AiCustomerServiceFileStorageDO.class))).thenReturn(1);
        doAnswer(invocation -> {
            AiCustomerServiceFileStorageDO row = invocation.getArgument(0);
            row.setId(41L);
            return 1;
        }).when(storageMapper).insert(any(AiCustomerServiceFileStorageDO.class));

        Response<?> response = service.uploadChunk(uploadRequest(md5, 0));

        assertTrue(response.isSuccess());
        verify(chunkStorage).saveChunk(eq(md5), eq(0), any(org.springframework.web.multipart.MultipartFile.class));
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

    @Test
    void mergeRejectsIncompleteChunkSetAndRestoresUploading() {
        String md5 = "0123456789abcdef0123456789abcdef";
        when(storageMapper.selectByMd5(md5)).thenReturn(uploadingStorage(md5));
        when(storageMapper.update(any(), any())).thenReturn(1);
        when(storageMapper.updateById(any(AiCustomerServiceFileStorageDO.class))).thenReturn(1);
        when(chunkStorage.mergeChunks(md5, "guide.md", 2))
                .thenThrow(new BizException(ResponseCodeEnum.CHUNK_INCOMPLETE));

        BizException error = assertThrows(BizException.class,
                () -> service.mergeFile(MergeFileReqVO.builder().fileMd5(md5).build()));

        assertEquals(ResponseCodeEnum.CHUNK_INCOMPLETE.getErrorCode(), error.getErrorCode());
        verify(storageMapper).update(any(), any());
        verify(storageMapper).updateById(any(AiCustomerServiceFileStorageDO.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void successfulMergePublishesFileAndDeletesChunkRecords() {
        String md5 = "0123456789abcdef0123456789abcdef";
        Path finalPath = storageDirectory.resolve("merged-guide.md");
        when(storageMapper.selectByMd5(md5)).thenReturn(uploadingStorage(md5));
        when(storageMapper.update(any(), any())).thenReturn(1);
        when(storageMapper.updateById(any(AiCustomerServiceFileStorageDO.class))).thenReturn(1);
        when(chunkStorage.mergeChunks(md5, "guide.md", 2)).thenReturn(finalPath);

        service.mergeFile(MergeFileReqVO.builder().fileMd5(md5).build());

        verify(storageMapper).update(any(), any());
        verify(storageMapper).updateById(any(AiCustomerServiceFileStorageDO.class));
        verify(chunkMapper).deleteByFileMd5(md5);
        verify(chunkStorage).deleteChunks(md5);
        verify(eventPublisher).publishEvent(
                argThat((AiCustomerServiceMdUploadedEvent event) ->
                        event.getId().equals(41L)
                                && event.getFilePath().equals(finalPath.toString())));
    }

    private UploadChunkReqVO uploadRequest(String md5, int chunkNumber) {
        return UploadChunkReqVO.builder()
                .fileMd5(md5)
                .fileName("guide.md")
                .fileSize(5L * 1024 * 1024 + 1)
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
                .fileSize(5L * 1024 * 1024 + 1)
                .totalChunks(2)
                .uploadedChunks(1)
                .status(AiCustomerServiceFileStatusEnum.UPLOADING.getCode())
                .build();
    }
}
