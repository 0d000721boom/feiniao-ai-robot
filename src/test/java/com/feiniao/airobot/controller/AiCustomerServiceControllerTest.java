package com.feiniao.airobot.controller;

import com.feiniao.airobot.model.vo.chat.PageRspVO;
import com.feiniao.airobot.model.vo.customerService.FindMarkdownFilePageListReqVO;
import com.feiniao.airobot.model.vo.customerService.FindMarkdownFilePageListRspVO;
import com.feiniao.airobot.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
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
}
