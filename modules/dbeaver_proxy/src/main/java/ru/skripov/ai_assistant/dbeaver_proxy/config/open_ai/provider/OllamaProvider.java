package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class OllamaProvider implements AiProvider {
    private static final String PROVIDER_NAME = "ollama";

    @Value("${ollama.url}")
    private String ollamaUrl;

    @Value("${ollama.default-model}")
    private String defaultModel;

    private final WebClient.Builder webClientBuilder;

    public OllamaProvider(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public OpenAiChatResponse chat(OpenAiChatRequest request) {
        List<OllamaChatRequest.Message> ollamaMessages = request.getMessages().stream()
                .map(m -> new OllamaChatRequest.Message(m.getRole(), m.getContent()))
                .toList();

        String answer = callOllama(ollamaMessages, request.getTemperature(), null, false);

        return buildChatResponse(answer);
    }

    @Override
    public OpenAiResponsesResponse responses(OpenAiResponsesRequest request) {
        List<OllamaChatRequest.Message> ollamaMessages = convertResponsesToOllamaMessages(request);
        List<OllamaTool> ollamaTools = convertTools(request.getTools());

        String answer = callOllama(ollamaMessages, request.getTemperature(), ollamaTools, request.getStream());

        return buildResponsesResponse(answer);
    }

    private String callOllama(List<OllamaChatRequest.Message> messages,
                              Double temperature,
                              List<OllamaTool> tools,
                              boolean stream) {
        OllamaChatRequest.Options options = new OllamaChatRequest.Options();
        options.setTemperature(temperature != null ? temperature : 0.1);

        OllamaChatRequest ollamaRequest = new OllamaChatRequest(defaultModel, messages, stream, false, options, tools);

        log.info("→ Ollama: POST {}/api/chat, model={}, messages={}, tools={}, temp={}, think=false",
                ollamaUrl, defaultModel, messages.size(),
                tools != null ? tools.size() : 0,
                options.getTemperature());

        OllamaChatResponse response = webClientBuilder
                .baseUrl(ollamaUrl)
                .build()
                .post()
                .uri("/api/chat")
                .bodyValue(ollamaRequest)
                .retrieve()
                .bodyToMono(OllamaChatResponse.class)
                .timeout(Duration.ofMinutes(5))
                .block();

        if (response == null || response.getMessage() == null) {
            throw new IllegalStateException("Empty response from Ollama");
        }

        OllamaChatResponse.Message msg = response.getMessage();
        log.info("← Ollama: content={} chars, tool_calls={}",
                msg.getContent() != null ? msg.getContent().length() : 0,
                msg.getToolCalls() != null ? msg.getToolCalls().size() : 0);

        if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
            for (OllamaToolCall tc : msg.getToolCalls()) {
                log.info("   tool_call: name={}, args={}",
                        tc.getFunction().getName(),
                        tc.getFunction().getArguments());
            }
        }

        return msg.getContent();
    }

    private List<OllamaChatRequest.Message> convertResponsesToOllamaMessages(OpenAiResponsesRequest request) {
        List<OllamaChatRequest.Message> messages = new ArrayList<>();

        // instructions → system (если есть отдельным полем)
        if (request.getInstructions() != null && !request.getInstructions().isBlank()) {
            messages.add(new OllamaChatRequest.Message("system", request.getInstructions()));
        }

        Object input = request.getInput();
        if (input instanceof String s) {
            messages.add(new OllamaChatRequest.Message("user", s));
            return messages;
        }

        if (input instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) continue;

                String role = map.get("role") != null ? map.get("role").toString() : "user";
                Object content = map.get("content");

                String text = extractText(content);

                if (text != null && !text.isBlank()) {
                    messages.add(new OllamaChatRequest.Message(role, text));
                }
            }
        }

        return messages;
    }

    /**
     * OpenAiResponsesRequest.tools (формат Responses API / Chat Completions) → List<OllamaTool> (формат Ollama).
     */
    private List<OllamaTool> convertTools(List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }

        List<OllamaTool> result = new ArrayList<>();

        for (Tool tool : tools) {
            if (tool.getName() == null || tool.getName().isBlank()) {
                log.warn("Skipping tool without name: {}", tool);
                continue;
            }

            OllamaTool.Function function = new OllamaTool.Function(
                    tool.getName(),
                    tool.getDescription(),
                    tool.getParameters()
            );

            result.add(new OllamaTool("function", function));
        }

        log.info("→ Converted {} tools to Ollama format", result.size());
        return result;
    }


    private String extractText(Object content) {
        if (content == null) {
            return null;
        }

        if (content instanceof String s) {
            return s;
        }

        if (content instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object block : list) {
                if (block instanceof Map<?, ?> bm) {
                    Object text = bm.get("text");
                    if (text != null) {
                        if (!sb.isEmpty()) sb.append("\n");
                        sb.append(text);
                    }
                }
            }
            return sb.toString();
        }

        if (content instanceof Map<?, ?> map) {
            Object text = map.get("text");
            return text != null ? text.toString() : map.toString();
        }

        return content.toString();
    }

    private OpenAiChatResponse buildChatResponse(String answer) {
        return new OpenAiChatResponse(
                "chatcmpl-" + UUID.randomUUID(),
                "chat.completion",
                System.currentTimeMillis() / 1000,
                defaultModel,
                List.of(new OpenAiChatResponse.Choice(
                        0,
                        new OpenAiChatResponse.Message("assistant", answer),
                        "stop"
                )),
                new OpenAiChatResponse.Usage(0, 0, 0)
        );
    }

    private OpenAiResponsesResponse buildResponsesResponse(String answer) {
        OpenAiResponsesResponse.ContentBlock contentBlock =
                new OpenAiResponsesResponse.ContentBlock("output_text", answer, List.of());

        OpenAiResponsesResponse.OutputItem outputItem =
                new OpenAiResponsesResponse.OutputItem(
                        "message",
                        "msg_" + UUID.randomUUID().toString().replace("-", ""),
                        "assistant",
                        "completed",
                        List.of(contentBlock)
                );

        return new OpenAiResponsesResponse(
                "resp_" + UUID.randomUUID().toString().replace("-", ""),
                "response",
                "completed",
                defaultModel,
                System.currentTimeMillis() / 1000,
                List.of(outputItem),
                new OpenAiResponsesResponse.Usage(0, 0, 0)
        );
    }
}
