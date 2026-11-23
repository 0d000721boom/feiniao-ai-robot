package com.feiniao.airobot.model.vo.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 历史对话列表项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryRspVO {

    private String uuid;
    private String summary;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
