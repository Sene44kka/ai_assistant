package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto.OpenAiChatRequest;
import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto.OpenAiChatResponse;
import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto.OpenAiResponsesRequest;
import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto.OpenAiResponsesResponse;
import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.service.ChatService;

import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.truncate;

@Slf4j
@RestController
@RequestMapping("/dbeaver_proxy")
public class OpenAiController {
    @Value("${ollama.default-model}")
    private String defaultModel;

    private final ChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/{provider}/chat/completions")
    public OpenAiChatResponse chatCompletions(
            @PathVariable("provider") String provider,
            @RequestBody OpenAiChatRequest request) {

        log.info("→ DBeaver: model={}, messages={}, provider={}",
                request.getModel(),
                request.getMessages() != null ? request.getMessages().size() : 0,
                provider);

        return chatService.chat(request, provider);
    }

    @PostMapping("/{provider}/responses")
    public OpenAiResponsesResponse responses(
            @PathVariable("provider") String provider,
            @RequestBody OpenAiResponsesRequest request) {

        log.info("══════════════════════════════════════════════════════");
        log.info("→ DBeaver [responses] received");
        log.info("  model:        {}", request.getModel());
        log.info("  instructions: {}", truncate(request.getInstructions(), 200));
        log.info("  input:        {}", truncate(String.valueOf(request.getInput()), 3000));
        log.info("  temperature:  {}", request.getTemperature());
        log.info("══════════════════════════════════════════════════════");

        OpenAiResponsesResponse response = chatService.responses(request, provider);

        try {
            log.info("← Response JSON: {}", objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            log.warn("Cannot serialize response", e);
        }

        return response;
    }

    /**
     * DBeaver пингует /models при инициализации AI —
     * по нему он проверяет, что endpoint живой и валидный.
     */
    @GetMapping("/{provider}/models")
    public Map<String, Object> models(@PathVariable("provider") String provider) {
        log.info("✅ DBeaver connected successfully! GET /dbeaver_proxy/models called.");
        List<String> modelNames = chatService.listModels(provider);

        if (modelNames.isEmpty()) {
            log.warn("No models from Ollama, falling back to default: {}", defaultModel);
            modelNames = List.of(defaultModel);
        }

        List<Map<String, Object>> data = modelNames.stream()
                .map(name -> Map.<String, Object>of(
                        "id", name,
                        "object", "model",
                        "created", System.currentTimeMillis() / 1000,
                        "owned_by", provider
                ))
                .toList();

        log.info("  Returning {} model(s): {}", data.size(), modelNames);

        return Map.of(
                "object", "list",
                "data", data
        );
    }
}
