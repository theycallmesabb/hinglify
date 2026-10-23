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

        log.info("Initialized ExtractorClient with resolved absolute URL: {}", url);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(15));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .baseUrl(url)
                .requestFactory(requestFactory)
                .build();
    }

    public ExtractorResponse extractText(Path filePath) {
        log.info("Sending file {} to extractor", filePath);

        // Use standard LinkedMultiValueMap to avoid reactive-streams dependency issues
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(filePath.toFile()));

        ExtractorResponse response = restClient.post()
                .uri("/extract")
                .body(body)
                .retrieve()
                .body(ExtractorResponse.class);

        log.info("Received extractor response: {} pages, {} chars",
                response != null ? response.pageCount() : 0,
                response != null ? response.totalChars() : 0);

        return response;
    }
}
