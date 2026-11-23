package com.feiniao.airobot.service.impl;

import com.feiniao.airobot.domain.dos.ChatDO;
import com.feiniao.airobot.domain.mapper.ChatMapper;
import com.feiniao.airobot.model.vo.chat.NewChatReqVO;
import com.feiniao.airobot.model.vo.chat.NewChatRspVO;
import com.feiniao.airobot.service.ChatService;
import com.feiniao.airobot.utils.Response;
import com.feiniao.airobot.utils.StringUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @Description: 对话
 **/
@Service
@Slf4j
public class ChatServiceImpl implements ChatService {

    @Resource
    private ChatMapper chatMapper;

    /**
     * 新建对话
     *
     * @param newChatReqVO
     * @return
     */
    @Override
    public Response<NewChatRspVO> newChat(NewChatReqVO newChatReqVO) {
        // 用户发送消息
        String message = newChatReqVO.getMessage();

        //生成对话uuid
        String uuid = UUID.randomUUID().toString();

        //对话摘要
        String summary = StringUtil.truncate(message,20);

        //存储到数据库
        chatMapper.insert(ChatDO.builder()
                .summary(summary)
                .uuid(uuid)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build());
        //返回前端
        return Response.success(NewChatRspVO.builder()
                        .uuid(uuid)
                        .summary(summary)
                        .build());
    }
}


