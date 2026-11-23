package com.feiniao.airobot.service;

import com.feiniao.airobot.model.vo.chat.ChatHistoryRspVO;
import com.feiniao.airobot.model.vo.chat.ChatMessageRspVO;
import com.feiniao.airobot.model.vo.chat.PageReqVO;
import com.feiniao.airobot.model.vo.chat.PageRspVO;
import com.feiniao.airobot.model.vo.chat.RenameChatReqVO;

/**
 * 历史会话查询与管理能力。
 */
public interface
ChatHistoryService {

    /**
     * 分页查询历史对话。
     */
    PageRspVO<ChatHistoryRspVO> listChatHistory(PageReqVO request);

    /**
     * 分页查询指定对话的历史消息。
     */
    PageRspVO<ChatMessageRspVO> listChatMessages(String chatUuid, PageReqVO request);

    /**
     * 重命名指定对话。
     */
    ChatHistoryRspVO renameChat(String chatUuid, RenameChatReqVO request);

    /**
     * 物理删除对话及其全部消息。
     */
    void deleteChat(String chatUuid);
}
