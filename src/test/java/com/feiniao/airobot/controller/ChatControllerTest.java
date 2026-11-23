package com.feiniao.airobot.controller;

import com.feiniao.airobot.domain.dos.ChatMessageDO;
import com.feiniao.airobot.domain.mapper.ChatMapper;
import com.feiniao.airobot.domain.mapper.ChatMessageMapper;
import com.feiniao.airobot.model.vo.chat.AiChatReqVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerTest {

    @Test
    void streamChatAcceptsStreamAdvisor() {
        ChatController chatController = new ChatController();
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        ReflectionTestUtils.setField(chatController, "openAiChatModel", chatModel);

        AiChatReqVO request = AiChatReqVO.builder()
                .message("你用的什么模型")
                .modelName("qwen3-omni-flash")
                .temperature(0.7)
                .build();

        assertDoesNotThrow(() -> chatController.chat(request));
    }

    @Test
    void enablesForcedWebSearchWhenNetworkSearchIsTrue() {
        ChatController chatController = new ChatController();
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        ChatMessageMapper chatMessageMapper = mock(ChatMessageMapper.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        when(chatModel.stream(promptCaptor.capture())).thenReturn(Flux.empty());
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());
        ReflectionTestUtils.setField(chatController, "openAiChatModel", chatModel);
        ReflectionTestUtils.setField(chatController, "chatMessageMapper", chatMessageMapper);
        ReflectionTestUtils.setField(chatController, "transactionTemplate", transactionTemplate);

        AiChatReqVO request = AiChatReqVO.builder()
                .message("今天的实时新闻")
                .modelName("qwen-plus")
                .networkSearch(true)
                .build();

        chatController.chat(request).blockLast();

        OpenAiChatOptions options = (OpenAiChatOptions) promptCaptor.getValue().getOptions();
        assertNotNull(options.getExtraBody());
        assertEquals(true, options.getExtraBody().get("enable_search"));

        @SuppressWarnings("unchecked")
        Map<String, Object> searchOptions =
                (Map<String, Object>) options.getExtraBody().get("search_options");
        assertEquals(true, searchOptions.get("forced_search"));
        assertEquals(true, searchOptions.get("enable_source"));
    }

    @Test
    void doesNotEnableWebSearchWhenNetworkSearchIsFalse() {
        ChatController chatController = new ChatController();
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        ChatMessageMapper chatMessageMapper = mock(ChatMessageMapper.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        when(chatModel.stream(promptCaptor.capture())).thenReturn(Flux.empty());
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());
        ReflectionTestUtils.setField(chatController, "openAiChatModel", chatModel);
        ReflectionTestUtils.setField(chatController, "chatMessageMapper", chatMessageMapper);
        ReflectionTestUtils.setField(chatController, "transactionTemplate", transactionTemplate);

        AiChatReqVO request = AiChatReqVO.builder()
                .message("解释一下 Spring AI")
                .modelName("qwen-plus")
                .networkSearch(false)
                .build();

        chatController.chat(request).blockLast();

        OpenAiChatOptions options = (OpenAiChatOptions) promptCaptor.getValue().getOptions();
        assertNull(options.getExtraBody());
    }

    @Test
    void completedStreamRefreshesConversationUpdateTime() {
        ChatController chatController = new ChatController();
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        ChatMessageMapper chatMessageMapper = mock(ChatMessageMapper.class);
        ChatMapper chatMapper = mock(ChatMapper.class);
        TransactionTemplate transactionTemplate = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };

        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.empty());
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());
        when(chatMessageMapper.insert(any(ChatMessageDO.class))).thenReturn(1);
        when(chatMapper.update(isNull(), any())).thenReturn(1);
        ReflectionTestUtils.setField(chatController, "openAiChatModel", chatModel);
        ReflectionTestUtils.setField(chatController, "chatMessageMapper", chatMessageMapper);
        ReflectionTestUtils.setField(chatController, "chatMapper", chatMapper);
        ReflectionTestUtils.setField(chatController, "transactionTemplate", transactionTemplate);

        AiChatReqVO request = AiChatReqVO.builder()
                .message("更新会话活跃时间")
                .chatId("chat-1")
                .modelName("qwen-plus")
                .build();

        chatController.chat(request).blockLast();

        verify(chatMapper).update(isNull(), any());
    }
}
