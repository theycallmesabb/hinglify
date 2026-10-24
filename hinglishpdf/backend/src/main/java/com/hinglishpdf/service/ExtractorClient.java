package com.hinglishpdf.service;

import com.hinglishpdf.dto.ExtractorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.time.Duration;

@Service
public class ExtractorClient {

    private static final Logger log = LoggerFactory.getLogger(ExtractorClient.class);
    private final RestClient restClient;
    private final String resolvedUrl;
    private final SimpleClientHttpRequestFactory requestFactory;

    public ExtractorClient(@Value("${app.extractor-url:http://localhost:8000}") String extractorUrl) {
        String url = (extractorUrl != null && !extractorUrl.isBlank()) ? extractorUrl.trim() : "http://localhost:8000";

        // If missing scheme (e.g. Render internal host "hinglify-extractor" or "hinglify-extractor.onrender.com")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (url.endsWith(".onrender.com")) {
                url = "https://" + url;
            } else {
                url = "http://" + url;
            }
        }

        // If internal host without port, append :8000
        String authority = url.substring(url.indexOf("://") + 3);
        if (!url.contains(".onrender.com") && !authority.contains(":")) {
            url = url + ":8000";
        }

        this.resolvedUrl = url;
        log.info("Initialized ExtractorClient with resolved absolute URL: {}", url);

        this.requestFactory = new SimpleClientHttpRequestFactory();
        this.requestFactory.setConnectTimeout(Duration.ofSeconds(45));
        this.requestFactory.setReadTimeout(Duration.ofSeconds(120));

        this.restClient = RestClient.builder()
                .baseUrl(url)
                .requestFactory(this.requestFactory)
                .build();
    }

    public ExtractorResponse extractText(Path filePath) {
        log.info("Sending file {} to extractor at {}", filePath, resolvedUrl);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(filePath.toFile()));

        Exception lastException = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                ExtractorResponse response = restClient.post()
                        .uri("/extract")
                        .body(body)
                        .retrieve()
                        .body(ExtractorResponse.class);

                log.info("Received extractor response: {} pages, {} chars",
                        response != null ? response.pageCount() : 0,
                        response != null ? response.totalChars() : 0);
                return response;
            } catch (Exception e) {
                lastException = e;
                log.warn("Attempt {} to reach extractor at {} failed: {}. Retrying in 3s...", attempt, resolvedUrl, e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {}
            }
        }

        // Automatic fallback to local extractor if primary external URL failed
        if (!resolvedUrl.contains("127.0.0.1") && !resolvedUrl.contains("localhost")) {
            log.info("External extractor at {} failed. Attempting local extractor fallback at http://127.0.0.1:8000/extract", resolvedUrl);
            try {
                RestClient localClient = RestClient.builder()
                        .baseUrl("http://127.0.0.1:8000")
                        .requestFactory(this.requestFactory)
                        .build();

                ExtractorResponse localResponse = localClient.post()
                        .uri("/extract")
                        .body(body)
                        .retrieve()
                        .body(ExtractorResponse.class);

                log.info("Local extractor fallback succeeded: {} pages, {} chars",
                        localResponse != null ? localResponse.pageCount() : 0,
                        localResponse != null ? localResponse.totalChars() : 0);
                return localResponse;
            } catch (Exception localEx) {
                log.error("Local extractor fallback also failed: {}", localEx.getMessage());
            }
        }

        if (lastException instanceof RuntimeException re) {
            throw re;
        }
        throw new RuntimeException("Extractor service unreachable after retries: " + (lastException != null ? lastException.getMessage() : "unknown error"));
    }
}
