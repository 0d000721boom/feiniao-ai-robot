package com.feiniao.airobot.controller;

import com.feiniao.airobot.aspect.ApiOperationLog;
import com.feiniao.airobot.model.vo.chat.ChatHistoryRspVO;
import com.feiniao.airobot.model.vo.chat.ChatMessageRspVO;
import com.feiniao.airobot.model.vo.chat.PageReqVO;
import com.feiniao.airobot.model.vo.chat.PageRspVO;
import com.feiniao.airobot.model.vo.chat.RenameChatReqVO;
import com.feiniao.airobot.service.ChatHistoryService;
import com.feiniao.airobot.utils.Response;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 历史会话查询与管理接口。
 */
@Validated
@RestController
@RequestMapping("/chat/history")
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    public ChatHistoryController(ChatHistoryService chatHistoryService) {
        this.chatHistoryService = chatHistoryService;
    }

    /**
     * 分页查询历史对话。
     */
    @GetMapping
    @ApiOperationLog(description = "查询历史对话")
    public Response<PageRspVO<ChatHistoryRspVO>> listHistory(
            @Valid @ModelAttribute PageReqVO request) {
        return Response.success(chatHistoryService.listChatHistory(request));
    }

    /**
     * 分页查询指定对话的历史消息。
     */
    @GetMapping("/{chatUuid}/messages")
    @ApiOperationLog(description = "查询历史消息")
    public Response<PageRspVO<ChatMessageRspVO>> listMessages(
            @PathVariable String chatUuid,
            @Valid @ModelAttribute PageReqVO request) {
        return Response.success(chatHistoryService.listChatMessages(chatUuid, request));
    }

    /**
     * 重命名指定对话。
     */
    @PatchMapping("/{chatUuid}/title")
    @ApiOperationLog(description = "重命名对话")
    public Response<ChatHistoryRspVO> rename(
            @PathVariable String chatUuid,
            @Valid @RequestBody RenameChatReqVO request) {
        return Response.success(chatHistoryService.renameChat(chatUuid, request));
    }

    /**
     * 物理删除指定对话及其全部消息。
     */
    @DeleteMapping("/{chatUuid}")
    @ApiOperationLog(description = "删除对话")
    public Response<Void> delete(@PathVariable String chatUuid) {
        chatHistoryService.deleteChat(chatUuid);
        return Response.success();
    }
}
