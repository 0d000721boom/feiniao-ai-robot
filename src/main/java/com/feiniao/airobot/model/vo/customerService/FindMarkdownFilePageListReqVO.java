package com.feiniao.airobot.model.vo.customerService;

import com.feiniao.airobot.model.common.BasePageQuery;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * @Description: 查询 Markdown 问答文件列表
 **/
@Data
@NoArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class FindMarkdownFilePageListReqVO extends BasePageQuery {
}
