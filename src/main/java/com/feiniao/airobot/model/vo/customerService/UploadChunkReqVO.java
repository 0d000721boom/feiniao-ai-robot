package com.feiniao.airobot.model.vo.customerService;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadChunkReqVO {

    private String fileMd5;
    private String fileName;
    private Long fileSize;
    private Integer chunkNumber;
    private Integer totalChunks;
    private MultipartFile chunk;
}
