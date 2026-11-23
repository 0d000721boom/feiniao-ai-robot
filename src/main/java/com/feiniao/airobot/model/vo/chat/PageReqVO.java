package com.feiniao.airobot.model.vo.chat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 通用分页请求参数。
 */
@Data
public class PageReqVO {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于 1")
    private Long pageNum = 1L;

    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量不能小于 1")
    @Max(value = 100, message = "每页数量不能超过 100")
    private Long pageSize = 20L;
}
