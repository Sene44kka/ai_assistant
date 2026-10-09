package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.provider;

import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto.OpenAiChatRequest;
import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto.OpenAiChatResponse;
import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto.OpenAiResponsesRequest;
import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto.OpenAiResponsesResponse;

import java.util.List;

/**
 * Провайдер, который умеет обработать OpenAI-совместимый запрос
 * и вернуть OpenAI-совместимый ответ.
 * <p>
 * Реализации: OllamaProvider, OpenAiProvider, CopilotProvider и т.д.
 */
public interface AiProvider {

    /** Уникальный идентификатор провайдера: "ollama", "openai", "copilot" */
    String name();

    OpenAiChatResponse chat(OpenAiChatRequest request);

    OpenAiResponsesResponse responses(OpenAiResponsesRequest request);

    List<String> listModels();
}
