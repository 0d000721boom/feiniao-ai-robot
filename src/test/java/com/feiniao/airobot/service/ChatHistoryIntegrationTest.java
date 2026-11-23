package com.feiniao.airobot.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.feiniao.airobot.domain.dos.ChatDO;
import com.feiniao.airobot.domain.dos.ChatMessageDO;
import com.feiniao.airobot.domain.mapper.ChatMapper;
import com.feiniao.airobot.domain.mapper.ChatMessageMapper;
import com.feiniao.airobot.model.vo.chat.ChatHistoryRspVO;
import com.feiniao.airobot.model.vo.chat.ChatMessageRspVO;
import com.feiniao.airobot.model.vo.chat.PageReqVO;
import com.feiniao.airobot.model.vo.chat.PageRspVO;
import com.feiniao.airobot.model.vo.chat.RenameChatReqVO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ChatHistoryIntegrationTest {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ChatMapper chatMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Test
    void historyLifecycleUsesRealPostgreSql() {
        String chatUuid = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        chatMapper.insert(ChatDO.builder()
                .uuid(chatUuid)
                .summary("集成测试对话")
                .createTime(now.minusMinutes(2))
                .updateTime(now)
                .build());
        chatMessageMapper.insert(ChatMessageDO.builder()
                .chatUuid(chatUuid)
                .role("user")
                .content("较早消息")
                .createTime(now.minusMinutes(1))
                .build());
        chatMessageMapper.insert(ChatMessageDO.builder()
                .chatUuid(chatUuid)
                .role("assistant")
                .content("较新消息")
                .createTime(now)
                .build());

        PageReqVO pageRequest = new PageReqVO();
        pageRequest.setPageSize(100L);
        PageRspVO<ChatHistoryRspVO> historyPage =
                chatHistoryService.listChatHistory(pageRequest);
        PageRspVO<ChatMessageRspVO> messagePage =
                chatHistoryService.listChatMessages(chatUuid, pageRequest);

        assertTrue(historyPage.getRecords().stream()
                .anyMatch(history -> chatUuid.equals(history.getUuid())));
        assertEquals(List.of("较早消息", "较新消息"),
                messagePage.getRecords().stream().map(ChatMessageRspVO::getContent).toList());
        assertEquals(2L, messagePage.getTotal());

        ChatHistoryRspVO renamed =
                chatHistoryService.renameChat(chatUuid, new RenameChatReqVO("  重命名后的标题  "));
        assertEquals("重命名后的标题", renamed.getSummary());
        assertEquals("重命名后的标题", chatMapper.selectOne(Wrappers.<ChatDO>lambdaQuery()
                .eq(ChatDO::getUuid, chatUuid)).getSummary());

        chatHistoryService.deleteChat(chatUuid);

        assertEquals(0L, chatMapper.selectCount(Wrappers.<ChatDO>lambdaQuery()
                .eq(ChatDO::getUuid, chatUuid)));
        assertEquals(0L, chatMessageMapper.selectCount(Wrappers.<ChatMessageDO>lambdaQuery()
                .eq(ChatMessageDO::getChatUuid, chatUuid)));
    }
}
