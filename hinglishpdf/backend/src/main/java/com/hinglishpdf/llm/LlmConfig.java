package com.hinglishpdf.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    public LlmProvider llmProvider(
            @Value("${app.llm.provider:mock}") String provider,
            @Value("${app.llm.api-key:mock-key}") String apiKey,
            @Value("${app.llm.model:gemini-1.5-flash}") String model,
            @Value("${app.llm.base-url:https://api.openai.com/v1}") String baseUrl
    ) {
        String cleanProvider = (provider != null) ? provider.trim().toLowerCase() : "mock";
        boolean hasValidKey = apiKey != null
                && !apiKey.isBlank()
                && !apiKey.equalsIgnoreCase("mock-key")
                && !apiKey.equalsIgnoreCase("mock")
                && !apiKey.startsWith("your-")
                && !apiKey.contains("api-key-here");

        // 1. Google Gemini Provider
        if ("gemini".equals(cleanProvider) || "google".equals(cleanProvider)) {
            String geminiModel = (model != null && model.startsWith("gemini")) ? model : "gemini-3.8-flash";
            if (hasValidKey) {
                log.info("================================================================================");
                log.info(" ACTIVE LLM PROVIDER: GOOGLE GEMINI (Model: {})", geminiModel);
                log.info("================================================================================");
                return new GeminiLlmProvider(geminiModel, apiKey.trim());
            } else {
                log.warn("********************************************************************************");
                log.warn("* WARNING: LLM_PROVIDER is set to 'gemini' but LLM_API_KEY is empty or a placeholder!");
                log.warn("* Falling back to MOCK LLM Provider. Translations will be simulated.");
                log.warn("* Output PDFs will be explicitly watermarked with '[MOCK OUTPUT]'.");
                log.warn("********************************************************************************");
                return new MockLlmProvider();
            }
        }

        // 2. OpenAI Provider
        if ("openai".equals(cleanProvider)) {
            String openaiModel = (model != null && !model.startsWith("gemini")) ? model : "gpt-4o-mini";
            if (hasValidKey) {
                log.info("================================================================================");
                log.info(" ACTIVE LLM PROVIDER: OPENAI (Model: {}, BaseURL: {})", openaiModel, baseUrl);
                log.info("================================================================================");
                return new OpenAiLlmProvider(baseUrl, openaiModel, apiKey.trim());
            } else {
                log.warn("********************************************************************************");
                log.warn("* WARNING: LLM_PROVIDER is set to 'openai' but LLM_API_KEY is empty or a placeholder!");
                log.warn("* Falling back to MOCK LLM Provider. Translations will be simulated.");
                log.warn("* Output PDFs will be explicitly watermarked with '[MOCK OUTPUT]'.");
                log.warn("********************************************************************************");
                return new MockLlmProvider();
            }
        }

        log.warn("********************************************************************************");
        log.warn("* WARNING: Running with MOCK LLM provider (LLM_PROVIDER='{}')", cleanProvider);
        log.warn("* Translations are simulated rule-based replacements for offline testing.");
        log.warn("* Output PDFs will be explicitly watermarked with '[MOCK OUTPUT]'.");
        log.warn("********************************************************************************");
        return new MockLlmProvider();
    }
}
