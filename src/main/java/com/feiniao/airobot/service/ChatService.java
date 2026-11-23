package com.feiniao.airobot.service;


import com.feiniao.airobot.model.vo.chat.NewChatReqVO;
import com.feiniao.airobot.model.vo.chat.NewChatRspVO;
import com.feiniao.airobot.utils.Response;

/**
 * @description: 对话
 **/
public interface ChatService {

    /**
     * 新建对话
     * @param newChatReqVO
     * @return
     */
    Response<NewChatRspVO> newChat(NewChatReqVO newChatReqVO);
}


