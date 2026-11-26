package com.feiniao.airobot.controller;

import com.feiniao.airobot.model.vo.chat.PageRspVO;
import com.feiniao.airobot.model.vo.customerService.FindMarkdownFilePageListReqVO;
import com.feiniao.airobot.model.vo.customerService.FindMarkdownFilePageListRspVO;
import com.feiniao.airobot.model.vo.customerService.MergeFileReqVO;
import com.feiniao.airobot.model.vo.customerService.UploadChunkReqVO;
import com.feiniao.airobot.service.CustomerService;
import com.feiniao.airobot.utils.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCustomerServiceControllerTest {

    @Mock
    private CustomerService customerService;

    @InjectMocks
    private AiCustomerServiceController controller;

    @Test
    void findMarkdownFilePageListReturnsServicePage() {
        FindMarkdownFilePageListReqVO request = new FindMarkdownFilePageListReqVO();
        PageRspVO<FindMarkdownFilePageListRspVO> page =
                PageRspVO.<FindMarkdownFilePageListRspVO>builder()
                        .pageNum(1)
                        .pageSize(10)
                        .total(0)
                        .totalPages(0)
                        .records(List.of())
                        .build();
        when(customerService.findMarkdownFilePageList(request)).thenReturn(page);

        PageRspVO<FindMarkdownFilePageListRspVO> result =
                controller.findMarkdownFilePageList(request);

        assertSame(page, result);
    }

    @Test
    void uploadChunkBuildsMultipartRequestAndDelegates() {
        MockMultipartFile chunk = new MockMultipartFile("file", "chunk".getBytes());
        Response<Object> success = Response.success();
        doReturn(success).when(customerService).uploadChunk(any(UploadChunkReqVO.class));

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
        Response<Object> success = Response.success();
        doReturn(success).when(customerService).mergeFile(request);

        assertSame(success, controller.mergeFile(request));
    }
}
