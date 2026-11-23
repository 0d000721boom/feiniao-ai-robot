package com.feiniao.airobot.controller;

import com.feiniao.airobot.model.vo.chat.ChatHistoryRspVO;
import com.feiniao.airobot.model.vo.chat.ChatMessageRspVO;
import com.feiniao.airobot.model.vo.chat.PageReqVO;
import com.feiniao.airobot.model.vo.chat.PageRspVO;
import com.feiniao.airobot.model.vo.chat.RenameChatReqVO;
import com.feiniao.airobot.service.ChatHistoryService;
import com.feiniao.airobot.utils.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatHistoryControllerTest {

    @Mock
    private ChatHistoryService chatHistoryService;

    @InjectMocks
    private ChatHistoryController controller;

    @Test
    void listHistoryReturnsServicePage() {
        PageRspVO<ChatHistoryRspVO> page = pageResponse();
        when(chatHistoryService.listChatHistory(org.mockito.ArgumentMatchers.any()))
                .thenReturn(page);

        Response<PageRspVO<ChatHistoryRspVO>> response =
                controller.listHistory(new PageReqVO());

        assertSame(page, response.getData());
    }

    @Test
    void listMessagesReturnsServicePage() {
        PageRspVO<ChatMessageRspVO> page = PageRspVO.<ChatMessageRspVO>builder()
                .pageNum(1)
                .pageSize(20)
                .total(1)
                .totalPages(1)
                .records(List.of())
                .build();
        PageReqVO request = new PageReqVO();
        when(chatHistoryService.listChatMessages("chat-1", request)).thenReturn(page);

        Response<PageRspVO<ChatMessageRspVO>> response =
                controller.listMessages("chat-1", request);

        assertSame(page, response.getData());
    }

    @Test
    void renameReturnsUpdatedConversation() {
        ChatHistoryRspVO history = ChatHistoryRspVO.builder()
                .uuid("chat-1")
                .summary("新标题")
                .build();
        RenameChatReqVO request = new RenameChatReqVO("新标题");
        when(chatHistoryService.renameChat("chat-1", request)).thenReturn(history);

        Response<ChatHistoryRspVO> response =
                controller.rename("chat-1", request);

        assertSame(history, response.getData());
    }

    @Test
    void deleteReturnsSuccessfulEmptyResponse() {
        Response<Void> response = controller.delete("chat-1");

        verify(chatHistoryService).deleteChat("chat-1");
        assertTrue(response.isSuccess());
    }

    private PageRspVO<ChatHistoryRspVO> pageResponse() {
        return PageRspVO.<ChatHistoryRspVO>builder()
                .pageNum(1)
                .pageSize(20)
                .total(1)
                .totalPages(1)
                .records(List.of())
                .build();
    }
}
