package com.feiniao.airobot.service;

import com.feiniao.airobot.model.dto.SearchResultDTO;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 并发抓取搜索结果页面正文。
 */
public interface SearchResultContentFetcherService {

    CompletableFuture<List<SearchResultDTO>> batchFetch(
            List<SearchResultDTO> searchResults, long timeout, TimeUnit unit);
}
