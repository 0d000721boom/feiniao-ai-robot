package com.feiniao.airobot.service.impl;

import cn.hutool.core.util.PageUtil;
import cn.hutool.db.PageResult;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.feiniao.airobot.service.ChatHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 历史会话服务实现。
 */
@Service
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private final ChatMapper chatMapper;
    private final ChatMessageMapper chatMessageMapper;

    public ChatHistoryServiceImpl(ChatMapper chatMapper, ChatMessageMapper chatMessageMapper) {
        this.chatMapper = chatMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    @Override
    public PageRspVO<ChatHistoryRspVO> listChatHistory(PageReqVO request) {
        long pageNum = request.getPageNum();
        long pageSize = request.getPageSize();
        Page<ChatDO> databasePage = chatMapper.selectPage(
                new Page<>(pageNum, pageSize),
                Wrappers.<ChatDO>lambdaQuery()
                        .orderByDesc(ChatDO::getUpdateTime)
                        .orderByDesc(ChatDO::getId)
        );
        List<ChatHistoryRspVO> records = databasePage.getRecords().stream()
                .map(this::toHistoryRsp)
                .toList();
        return toPageRsp(pageNum, pageSize, databasePage.getTotal(), records);
    }

    @Override
    public PageRspVO<ChatMessageRspVO> listChatMessages(String chatUuid, PageReqVO request) {
        requireChat(chatUuid);

        long pageNum = request.getPageNum();
        long pageSize = request.getPageSize();
        Page<ChatMessageDO> databasePage = chatMessageMapper.selectPage(
                new Page<>(pageNum, pageSize),
                Wrappers.<ChatMessageDO>lambdaQuery()
                        .eq(ChatMessageDO::getChatUuid, chatUuid)
                        .orderByDesc(ChatMessageDO::getCreateTime)
                        .orderByDesc(ChatMessageDO::getId)
        );

        // 数据库先取最新一页，接口页内再按时间正序返回。
        List<ChatMessageDO> chronologicalMessages = new ArrayList<>(databasePage.getRecords());
        Collections.reverse(chronologicalMessages);
        List<ChatMessageRspVO> records = chronologicalMessages.stream()
                .map(this::toMessageRsp)
                .toList();
        return toPageRsp(pageNum, pageSize, databasePage.getTotal(), records);
    }

    @Override
    public ChatHistoryRspVO renameChat(String chatUuid, RenameChatReqVO request) {
        String title = normalizeTitle(request.getTitle());
        ChatDO chat = requireChat(chatUuid);
        LocalDateTime updateTime = LocalDateTime.now();

        int updatedRows = chatMapper.update(null, Wrappers.<ChatDO>lambdaUpdate()
                .eq(ChatDO::getUuid, chatUuid)
                .set(ChatDO::getSummary, title)
                .set(ChatDO::getUpdateTime, updateTime));
        if (updatedRows != 1) {
            throw new BizException(ResponseCodeEnum.CHAT_NOT_FOUND);
        }

        chat.setSummary(title);
        chat.setUpdateTime(updateTime);
        return toHistoryRsp(chat);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChat(String chatUuid) {
        requireChat(chatUuid);

        // 先删除消息，再删除会话；两步处于同一事务中。
        chatMessageMapper.delete(Wrappers.<ChatMessageDO>lambdaQuery()
                .eq(ChatMessageDO::getChatUuid, chatUuid));
        int deletedRows = chatMapper.delete(Wrappers.<ChatDO>lambdaQuery()
                .eq(ChatDO::getUuid, chatUuid));
        if (deletedRows != 1) {
            throw new BizException(ResponseCodeEnum.CHAT_NOT_FOUND);
        }
    }

    /**
     * 标题按 Unicode 字符计数，避免 emoji 等多字节字符被错误截断。
     */
    private String normalizeTitle(String rawTitle) {
        String title = rawTitle == null ? "" : rawTitle.trim();
        int length = title.codePointCount(0, title.length());
        if (length < 1 || length > 50) {
            throw new BizException(ResponseCodeEnum.CHAT_TITLE_INVALID);
        }
        return title;
    }

    private ChatDO requireChat(String chatUuid) {
        ChatDO chat = chatMapper.selectOne(Wrappers.<ChatDO>lambdaQuery()
                .eq(ChatDO::getUuid, chatUuid));
        if (chat == null) {
            throw new BizException(ResponseCodeEnum.CHAT_NOT_FOUND);
        }
        return chat;
    }

    private ChatHistoryRspVO toHistoryRsp(ChatDO chat) {
        return ChatHistoryRspVO.builder()
                .uuid(chat.getUuid())
                .summary(chat.getSummary())
                .createTime(chat.getCreateTime())
                .updateTime(chat.getUpdateTime())
                .build();
    }

    private ChatMessageRspVO toMessageRsp(ChatMessageDO message) {
        return ChatMessageRspVO.builder()
                .id(message.getId())
                .chatUuid(message.getChatUuid())
                .role(message.getRole())
                .content(message.getContent())
                .reasoning(message.getReasoningContent())
                .createTime(message.getCreateTime())
                .build();
    }

    /**
     * Hutool 负责分页元数据计算，最终转换为不会丢失元数据的接口 VO。
     */
    private <T> PageRspVO<T> toPageRsp(long pageNum, long pageSize, long total, List<T> records) {
        PageResult<T> hutoolPage = new PageResult<>((int) pageNum, (int) pageSize);
        hutoolPage.setTotal((int) Math.min(total, Integer.MAX_VALUE));
        hutoolPage.setTotalPage(PageUtil.totalPage(total, (int) pageSize));
        hutoolPage.addAll(records);

        return PageRspVO.<T>builder()
                .pageNum(hutoolPage.getPage())
                .pageSize(hutoolPage.getPageSize())
                .total(hutoolPage.getTotal())
                .totalPages(hutoolPage.getTotalPage())
                .records(List.copyOf(hutoolPage))
                .build();
    }
}
