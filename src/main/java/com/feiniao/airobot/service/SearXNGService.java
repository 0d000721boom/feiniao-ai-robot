package com.feiniao.airobot.service;

import com.feiniao.airobot.model.dto.SearchResultDTO;

import java.util.List;

/**
 * SearXNG 联网搜索服务。
 */
public interface SearXNGService {

    List<SearchResultDTO> search(String query);
}
