package com.feiniao.airobot.service.impl;

import com.feiniao.airobot.model.dto.SearchResultDTO;
import com.feiniao.airobot.service.SearXNGService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 通过 SearXNG JSON API 获取联网搜索结果。
 */
@Service
@Slf4j
public class SearXNGServiceImpl implements SearXNGService {

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final String searxngUrl;
    private final int count;

    public SearXNGServiceImpl(
            OkHttpClient okHttpClient,
            ObjectMapper objectMapper,
            @Value("${searxng.url}") String searxngUrl,
            @Value("${searxng.count:10}") int count) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.searxngUrl = searxngUrl;
        this.count = count;
    }

    @Override
    public List<SearchResultDTO> search(String query) {
        HttpUrl requestUrl = HttpUrl.get(searxngUrl).newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("format", "json")
                .build();
        Request request = new Request.Builder().url(requestUrl).get().build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("## SearXNG 请求失败，状态码: {}", response.code());
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body().string());
            JsonNode results = root.path("results");
            List<SearchResultDTO> searchResults = new ArrayList<>();
            for (JsonNode node : results) {
                searchResults.add(SearchResultDTO.builder()
                        .url(node.path("url").asString(""))
                        .score(node.path("score").asDouble(0.0))
                        .build());
            }

            return searchResults.stream()
                    .filter(result -> !result.getUrl().isBlank())
                    .sorted(Comparator.comparingDouble(SearchResultDTO::getScore).reversed())
                    .limit(count)
                    .toList();
        } catch (Exception exception) {
            log.error("## SearXNG 搜索异常", exception);
            return List.of();
        }
    }
}
