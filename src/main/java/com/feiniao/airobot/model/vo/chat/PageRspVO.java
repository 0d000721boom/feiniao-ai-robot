package com.feiniao.airobot.model.vo.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 对外返回的稳定分页结构。
 *
 * <p>Hutool 的 PageResult 继承自 ArrayList，直接序列化容易丢失分页元数据，
 * 因此接口层使用该对象承载最终结果。</p>
 *
 * @param <T> 分页记录类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageRspVO<T> {

    private long pageNum;
    private long pageSize;
    private long total;
    private long totalPages;
    private List<T> records;
}
