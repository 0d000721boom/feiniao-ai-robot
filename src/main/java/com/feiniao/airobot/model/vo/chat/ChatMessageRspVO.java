package com.feiniao.airobot.model.vo.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 历史消息列表项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRspVO {

    private Long id;
    private String chatUuid;
    private String role;
    private String content;
    private LocalDateTime createTime;
}
