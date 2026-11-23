package com.feiniao.airobot.controller;

/*
* @Description:对话
* */

import com.feiniao.airobot.advisor.CustomChatMemoryAdvisor;
import com.feiniao.airobot.advisor.CustomStreamLoggerAndMessage2DBAdvisor;
import com.feiniao.airobot.aspect.ApiOperationLog;
import com.feiniao.airobot.domain.mapper.ChatMapper;
import com.feiniao.airobot.domain.mapper.ChatMessageMapper;
import com.feiniao.airobot.model.vo.chat.AIResponse;
import com.feiniao.airobot.model.vo.chat.AiChatReqVO;
import com.feiniao.airobot.model.vo.chat.NewChatReqVO;
import com.feiniao.airobot.service.ChatService;
import com.feiniao.airobot.utils.Response;
import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/chat")
@Slf4j
public class ChatController {

    @Resource
    private ChatService chatService;

    @Resource
    private OpenAiChatModel openAiChatModel;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    @Autowired
    private ChatMapper chatMapper;

    @PostMapping("/new")
    @ApiOperationLog(description = "新建对话")
    public Response<?> newChat(@RequestBody @Validated NewChatReqVO newChatReqVO) {

        return chatService.newChat(newChatReqVO);
    }

    /*
     * 流式对话
     * @return
     * */
    @PostMapping(value = "/completion", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperationLog(description = "流式对话")
    public Flux<AIResponse> chat(@RequestBody @Validated AiChatReqVO aiChatReqVO) {
        // 用户消息
        String userMessage = aiChatReqVO.getMessage();
        // 模型名称
        String modelName = aiChatReqVO.getModelName();
        // 温度值
        Double temperature = aiChatReqVO.getTemperature();

        OpenAiChatOptions.Builder chatOptionsBuilder = OpenAiChatOptions.builder()
                .model(modelName) // 模型名称
                .temperature(temperature); // 温度值

        if (Boolean.TRUE.equals(aiChatReqVO.getNetworkSearch())) {
            chatOptionsBuilder.extraBody(Map.of(
                    "enable_search", true,
                    "search_options", Map.of(
                            "forced_search", true,
                            "enable_source", true
                    )
            ));
        }

        // 动态设置调用的模型名称、温度值和联网搜索参数
        ChatClient.ChatClientRequestSpec chatClientRequestSpec = ChatClient.create(openAiChatModel)
                .prompt()
                .options(chatOptionsBuilder)
                .user(userMessage); // 用户提示词

        // Advisor 集合
        List<Advisor> advisors = Lists.newArrayList();
        // 添加自定义打印流式对话日志 Advisor
        advisors.add(new CustomStreamLoggerAndMessage2DBAdvisor(
                chatMessageMapper, chatMapper, aiChatReqVO, transactionTemplate));
        //添加自定义对话记忆
        advisors.add(new CustomChatMemoryAdvisor(chatMessageMapper, aiChatReqVO, 50));

        // 应用 Advisor 集合
        chatClientRequestSpec.advisors(advisors);

        int[] reasoningLen = {0};

        // 流式输出
        return chatClientRequestSpec
                .stream()
                .chatResponse()
                .mapNotNull(chatResponse -> {
                    if (chatResponse.getResult() == null) {
                        return null;
                    }

                    //获取AI的消息
                    AssistantMessage message = chatResponse.getResult().getOutput();

                    //获取正式回答
                    String text = message.getText();

                    //获取推理内容
                    String reasoningContent = message.getMetadata().get("reasoningContent").toString();

                    //若推理内容新增，则下发本帧增量
                    if (reasoningContent.length() > reasoningLen[0]) {
                        String delta = reasoningContent.substring(reasoningLen[0]);
                        reasoningLen[0] = reasoningContent.length();
                        return AIResponse.builder().reasoning(delta).build();
                    }

                    //正式回答
                    return AIResponse.builder().v(text).build(); // 构建返参 AIResponse

                });

    }
}
