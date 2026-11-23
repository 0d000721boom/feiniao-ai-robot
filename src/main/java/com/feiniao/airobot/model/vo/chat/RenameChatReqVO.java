package com.feiniao.airobot.model.vo.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重命名对话请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenameChatReqVO {

    @NotBlank(message = "对话标题不能为空")
    private String title;
}
