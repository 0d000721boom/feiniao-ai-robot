package com.feiniao.airobot.service.impl;

import com.feiniao.airobot.model.dto.SearchResultDTO;
import com.feiniao.airobot.service.SearchResultContentFetcherService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 使用独立线程池并发抓取并清洗搜索结果页面。
 */
@Service
@Slf4j
public class SearchResultContentFetcherServiceImpl implements SearchResultContentFetcherService {

    @Resource(name = "resultProcessingExecutor")
    private ThreadPoolTaskExecutor processingExecutor;

    @Resource(name = "httpRequestExecutor")
    private ThreadPoolTaskExecutor httpExecutor;

    @Resource
    private OkHttpClient okHttpClient;

    @Override
    public CompletableFuture<List<SearchResultDTO>> batchFetch(
            List<SearchResultDTO> searchResults, long timeout, TimeUnit unit) {
        List<CompletableFuture<SearchResultDTO>> futures = searchResults.stream()
                .map(result -> fetchContent(result, timeout, unit))
                .toList();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApplyAsync(ignored -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList(), processingExecutor);
    }

    private CompletableFuture<SearchResultDTO> fetchContent(
            SearchResultDTO result, long timeout, TimeUnit unit) {
        return CompletableFuture.supplyAsync(() -> withContent(result, fetchHtml(result.getUrl())), httpExecutor)
                .completeOnTimeout(withContent(result, ""), timeout, unit)
                .exceptionally(exception -> {
                    log.warn("## 获取页面内容失败，URL: {}", result.getUrl(), exception);
                    return withContent(result, "");
                });
    }

    private String fetchHtml(String url) {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (compatible; FeiniaoAiRobot/1.0)")
                .get()
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return "";
            }
            return Jsoup.parse(response.body().string()).text();
        } catch (Exception exception) {
            log.warn("## 页面抓取失败，URL: {}", url, exception);
            return "";
        }
    }

    private SearchResultDTO withContent(SearchResultDTO result, String content) {
        return SearchResultDTO.builder()
                .url(result.getUrl())
                .score(result.getScore())
                .content(content)
                .build();
    }
}
