package com.feiniao.airobot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feiniao.airobot.domain.dos.AiCustomerServiceMdStorageDO;
import com.feiniao.airobot.domain.mapper.AiCustomerServiceMdStorageMapper;
import com.feiniao.airobot.event.AiCustomerServiceMdUploadedEvent;
import com.feiniao.airobot.model.vo.chat.PageRspVO;
import com.feiniao.airobot.model.vo.customerService.FindMarkdownFilePageListReqVO;
import com.feiniao.airobot.model.vo.customerService.FindMarkdownFilePageListRspVO;
import com.feiniao.airobot.model.vo.customerService.UpdateMarkdownFileReqVO;
import com.feiniao.airobot.service.impl.CustomerServiceImpl;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private AiCustomerServiceMdStorageMapper storageMapper;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CustomerServiceImpl service;

    @TempDir
    private Path storageDirectory;

    @Test
    void findMarkdownFilePageListMapsStorageRecordsToResponse() {
        AiCustomerServiceMdStorageDO storage = AiCustomerServiceMdStorageDO.builder()
                .id(7L)
                .originalFileName("faq.md")
                .fileSize(2048L)
                .status(2)
                .createTime(LocalDateTime.of(2026, 11, 21, 10, 0))
                .updateTime(LocalDateTime.of(2026, 11, 21, 10, 1))
                .remark("常见问题")
                .build();
        Page<AiCustomerServiceMdStorageDO> databasePage = new Page<>(2, 10);
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
        AiCustomerServiceMdStorageDO storage = AiCustomerServiceMdStorageDO.builder()
                .id(7L)
                .originalFileName("faq.md")
                .build();
        when(storageMapper.selectById(7L)).thenReturn(storage);
        when(storageMapper.updateById(any(AiCustomerServiceMdStorageDO.class))).thenReturn(1);
        ArgumentCaptor<AiCustomerServiceMdStorageDO> captor =
                ArgumentCaptor.forClass(AiCustomerServiceMdStorageDO.class);

        Response<?> response = service.updateMarkdownFile(
                UpdateMarkdownFileReqVO.builder().id(7L).remark("更新后的备注").build());

        verify(storageMapper).updateById(captor.capture());
        assertEquals("更新后的备注", captor.getValue().getRemark());
        assertTrue(response.isSuccess());
    }

    @Test
    void uploadMarkdownFilePublishesVectorizationEvent() {
        ReflectionTestUtils.setField(service, "mdStoragePath", storageDirectory.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "faq.md", "text/markdown", "# 常见问题".getBytes());
        doAnswer(invocation -> {
            AiCustomerServiceMdStorageDO storage = invocation.getArgument(0);
            storage.setId(42L);
            return 1;
        }).when(storageMapper).insert(any(AiCustomerServiceMdStorageDO.class));
        ArgumentCaptor<AiCustomerServiceMdUploadedEvent> eventCaptor =
                ArgumentCaptor.forClass(AiCustomerServiceMdUploadedEvent.class);

        service.uploadMarkdownFile(file);

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(42L, eventCaptor.getValue().getId());
        assertEquals("faq.md", eventCaptor.getValue().getMetadatas().get("originalFileName"));
    }

    @Test
    void uploadMarkdownFileCannotEscapeConfiguredStorageDirectory() {
        Path configuredDirectory = storageDirectory.resolve("knowledge");
        ReflectionTestUtils.setField(service, "mdStoragePath", configuredDirectory.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../../outside.md", "text/markdown", "# 越界文件".getBytes());
        doAnswer(invocation -> {
            AiCustomerServiceMdStorageDO storage = invocation.getArgument(0);
            storage.setId(43L);
            return 1;
        }).when(storageMapper).insert(any(AiCustomerServiceMdStorageDO.class));
        ArgumentCaptor<AiCustomerServiceMdUploadedEvent> eventCaptor =
                ArgumentCaptor.forClass(AiCustomerServiceMdUploadedEvent.class);

        service.uploadMarkdownFile(file);

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        Path storedPath = Path.of(eventCaptor.getValue().getFilePath()).toAbsolutePath().normalize();
        assertTrue(storedPath.startsWith(configuredDirectory.toAbsolutePath().normalize()));
        assertEquals("outside.md", eventCaptor.getValue().getMetadatas().get("originalFileName"));
    }
}
