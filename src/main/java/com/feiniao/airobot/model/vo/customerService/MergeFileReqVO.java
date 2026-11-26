package com.feiniao.airobot.model.vo.customerService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergeFileReqVO {

    @NotBlank(message = "文件 MD5 不能为空")
    @Pattern(regexp = "^[0-9a-fA-F]{32}$", message = "文件 MD5 格式不正确")
    private String fileMd5;
}
