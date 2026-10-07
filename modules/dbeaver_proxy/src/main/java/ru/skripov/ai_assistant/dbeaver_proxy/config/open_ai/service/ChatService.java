package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto.OpenAiChatRequest;
import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto.OpenAiChatResponse;
import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto.OpenAiResponsesRequest;
import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto.OpenAiResponsesResponse;
import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.provider.AiProvider;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatService {
    private final Map<String, AiProvider> providers;
    private final String defaultProvider;

    public ChatService(List<AiProvider> providerList,
                       @Value("${proxy.default-provider}") String defaultProvider) {
        this.providers = providerList.stream().collect(Collectors.toMap(AiProvider::name, Function.identity()));
        this.defaultProvider = defaultProvider;

        log.info("Registered AI providers: {}", providers.keySet());
        log.info("Default provider: {}", defaultProvider);
    }

    public OpenAiChatResponse chat(OpenAiChatRequest request, String providerName) {
        AiProvider provider = resolve(providerName);
        log.debug("Using provider '{}' for chat/completions", provider.name());
        return provider.chat(request);
    }

    public OpenAiResponsesResponse responses(OpenAiResponsesRequest request, String providerName) {
        AiProvider provider = resolve(providerName);
        log.debug("Using provider '{}' for responses", provider.name());
        return provider.responses(request);
    }

    private AiProvider resolve(String providerName) {
        String name = (providerName == null || providerName.isBlank()) ? defaultProvider : providerName;
        AiProvider provider = providers.get(name);

        if (provider == null) {
            throw new IllegalArgumentException("Unknown provider: " + name + ". Available: " + providers.keySet());
        }
        return provider;
    }
}
