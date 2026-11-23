package com.feiniao.airobot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.feiniao.airobot.ResponseCodeEnum;
import com.feiniao.airobot.domain.dos.ChatDO;
import com.feiniao.airobot.domain.dos.ChatMessageDO;
import com.feiniao.airobot.domain.mapper.ChatMapper;
import com.feiniao.airobot.domain.mapper.ChatMessageMapper;
import com.feiniao.airobot.exception.BizException;
import com.feiniao.airobot.model.vo.chat.ChatHistoryRspVO;
import com.feiniao.airobot.model.vo.chat.ChatMessageRspVO;
import com.feiniao.airobot.model.vo.chat.PageReqVO;
import com.feiniao.airobot.model.vo.chat.PageRspVO;
import com.feiniao.airobot.model.vo.chat.RenameChatReqVO;
import com.feiniao.airobot.service.impl.ChatHistoryServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceImplTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "test");
        TableInfoHelper.initTableInfo(assistant, ChatDO.class);
        TableInfoHelper.initTableInfo(assistant, ChatMessageDO.class);
    }

    @Mock
    private ChatMapper chatMapper;

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @InjectMocks
    private ChatHistoryServiceImpl service;

    @Test
    void listChatHistoryMapsMyBatisPageToHutoolMetadata() {
        ChatDO chat = ChatDO.builder()
                .id(1L)
                .uuid("chat-1")
                .summary("历史对话")
                .createTime(LocalDateTime.of(2026, 11, 18, 20, 0))
                .updateTime(LocalDateTime.of(2026, 11, 18, 21, 0))
                .build();
        Page<ChatDO> databasePage = new Page<>(1, 20);
        databasePage.setTotal(21);
        databasePage.setRecords(List.of(chat));
        when(chatMapper.selectPage(any(), any())).thenReturn(databasePage);

        PageRspVO<ChatHistoryRspVO> result =
                service.listChatHistory(new PageReqVO());

        assertEquals(1L, result.getPageNum());
        assertEquals(20L, result.getPageSize());
        assertEquals(21L, result.getTotal());
        assertEquals(2L, result.getTotalPages());
        assertEquals("chat-1", result.getRecords().getFirst().getUuid());
    }

    @Test
    void listChatMessagesReturnsNewestPageInChronologicalOrder() {
        ChatDO chat = ChatDO.builder()
                .id(1L)
                .uuid("chat-1")
                .summary("历史对话")
                .createTime(LocalDateTime.of(2026, 11, 18, 20, 0))
                .updateTime(LocalDateTime.of(2026, 11, 18, 21, 0))
                .build();
        ChatMessageDO newerMessage = ChatMessageDO.builder()
                .id(2L)
                .chatUuid(chat.getUuid())
                .role("assistant")
                .content("较新的消息")
                .createTime(LocalDateTime.of(2026, 11, 18, 21, 0))
                .build();
        ChatMessageDO olderMessage = ChatMessageDO.builder()
                .id(1L)
                .chatUuid(chat.getUuid())
                .role("user")
                .content("较早的消息")
                .createTime(LocalDateTime.of(2026, 11, 18, 20, 59))
                .build();
        Page<ChatMessageDO> databasePage = new Page<>(1, 20);
        databasePage.setTotal(3);
        databasePage.setRecords(List.of(newerMessage, olderMessage));
        when(chatMapper.selectOne(any())).thenReturn(chat);
        when(chatMessageMapper.selectPage(any(), any())).thenReturn(databasePage);

        PageRspVO<ChatMessageRspVO> result =
                service.listChatMessages(chat.getUuid(), new PageReqVO());

        assertEquals(olderMessage.getId(), result.getRecords().get(0).getId());
        assertEquals(newerMessage.getId(), result.getRecords().get(1).getId());
    }

    @Test
    void listChatMessagesRejectsMissingConversation() {
        when(chatMapper.selectOne(any())).thenReturn(null);

        BizException exception = assertThrows(
                BizException.class,
                () -> service.listChatMessages("missing-chat", new PageReqVO())
        );

        assertEquals(ResponseCodeEnum.CHAT_NOT_FOUND.getErrorCode(), exception.getErrorCode());
    }

    @Test
    void renameChatTrimsTitleAndRefreshesUpdateTime() {
        ChatDO chat = ChatDO.builder()
                .id(1L)
                .uuid("chat-1")
                .summary("旧标题")
                .createTime(LocalDateTime.of(2026, 11, 18, 20, 0))
                .updateTime(LocalDateTime.of(2026, 11, 18, 20, 30))
                .build();
        LocalDateTime originalUpdateTime = chat.getUpdateTime();
        when(chatMapper.selectOne(any())).thenReturn(chat);
        when(chatMapper.update(any(), any())).thenReturn(1);

        ChatHistoryRspVO result = service.renameChat(
                chat.getUuid(),
                new RenameChatReqVO("  new title  ")
        );

        assertEquals("new title", result.getSummary());
        assertTrue(result.getUpdateTime().isAfter(originalUpdateTime));
    }

    @Test
    void renameChatRejectsOverlongTitleAfterTrim() {
        RenameChatReqVO request = new RenameChatReqVO("x".repeat(51));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.renameChat("chat-1", request)
        );

        assertEquals(ResponseCodeEnum.CHAT_TITLE_INVALID.getErrorCode(), exception.getErrorCode());
    }

    @Test
    void deleteChatDeletesMessagesBeforeConversation() {
        ChatDO chat = ChatDO.builder()
                .id(1L)
                .uuid("chat-1")
                .summary("待删除对话")
                .build();
        when(chatMapper.selectOne(any())).thenReturn(chat);
        when(chatMessageMapper.delete(any())).thenReturn(2);
        when(chatMapper.delete(any())).thenReturn(1);

        service.deleteChat(chat.getUuid());

        InOrder deleteOrder = inOrder(chatMessageMapper, chatMapper);
        deleteOrder.verify(chatMessageMapper).delete(any());
        deleteOrder.verify(chatMapper).delete(any());
    }
}
