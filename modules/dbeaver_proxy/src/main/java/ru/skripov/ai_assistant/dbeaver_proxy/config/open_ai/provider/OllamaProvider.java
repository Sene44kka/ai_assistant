package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        OllamaChatResponse.Message msg = callOllama(ollamaMessages, request.getTemperature(), null);
        return buildChatResponse(msg.getContent());
    }

    @Override
    public OpenAiResponsesResponse responses(OpenAiResponsesRequest request) {
        List<OllamaChatRequest.Message> ollamaMessages = convertResponsesToOllamaMessages(request);
        List<OllamaTool> ollamaTools = convertTools(request.getTools());

        OllamaChatResponse.Message msg = callOllama(ollamaMessages, request.getTemperature(), ollamaTools);

        if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
            return buildFunctionCallResponse(msg.getToolCalls());
        }

        // 👇 Иначе — обычный текст
        return buildResponsesResponse(msg.getContent());
    }

    private OllamaChatResponse.Message callOllama(List<OllamaChatRequest.Message> messages,
                                                  Double temperature,
                                                  List<OllamaTool> tools) {
        OllamaChatRequest.Options options = new OllamaChatRequest.Options();
        options.setTemperature(temperature != null ? temperature : 0.1);

        OllamaChatRequest ollamaRequest = new OllamaChatRequest(
                defaultModel, messages, false, false, options, tools
        );

        log.info("→ Ollama: POST {}/api/chat, model={}, messages={}, tools={}, temp={}",
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

        if (msg.getToolCalls() != null) {
            for (OllamaToolCall tc : msg.getToolCalls()) {
                log.info("   tool_call: name={}, args={}",
                        tc.getFunction().getName(), tc.getFunction().getArguments());
            }
        }

        return msg;
    }

    private List<OllamaChatRequest.Message> convertResponsesToOllamaMessages(OpenAiResponsesRequest request) {
        List<OllamaChatRequest.Message> all = new ArrayList<>();

        Object input = request.getInput();
        if (input instanceof String s) {
            all.add(new OllamaChatRequest.Message("user", s));
            return all;
        }

        if (!(input instanceof List<?> list)) {
            return all;
        }

        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;

            String type = map.get("type") != null ? map.get("type").toString() : "";

            // ─── function_call: пропускаем, модель сама знает, что вызвала ───
            if ("function_call".equals(type)) {
                continue;
            }

            // ─── function_call_output: результат tool → передаём как user ───
            if ("function_call_output".equals(type)) {
                String output = map.get("output") != null ? map.get("output").toString() : "";
                if (!output.isBlank()) {
                    all.add(new OllamaChatRequest.Message(
                            "user",
                            "[Tool result]\n" + output
                    ));
                }
                continue;
            }

            // ─── обычное message ───
            String role = map.get("role") != null ? map.get("role").toString() : "user";
            String text = extractText(map.get("content"));
            if (text != null && !text.isBlank()) {
                // ⚠️ Пропускаем assistant-сообщения с "was completed" —
                // они дублируют function_call_output
                if ("assistant".equals(role) && text.startsWith("db_") && text.contains("was completed")) {
                    continue;
                }
                all.add(new OllamaChatRequest.Message(role, text));
            }
        }

        // 👇 Фильтрация дублей: system + assistant + ПОСЛЕДНЕЕ user
        List<OllamaChatRequest.Message> result = new ArrayList<>();

        // 1. Первый system
        all.stream()
                .filter(m -> "system".equals(m.getRole()))
                .findFirst()
                .ifPresent(result::add);

        // 2. Все assistant
        all.stream()
                .filter(m -> "assistant".equals(m.getRole()))
                .forEach(result::add);

        // 3. Последнее user (мог быть function_call_output)
        for (int i = all.size() - 1; i >= 0; i--) {
            if ("user".equals(all.get(i).getRole())) {
                result.add(all.get(i));
                break;
            }
        }

        log.info("→ Converted messages: {} in → {} out", all.size(), result.size());
        return result;
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

    private OpenAiResponsesResponse buildFunctionCallResponse(List<OllamaToolCall> toolCalls) {
        List<Object> output = new ArrayList<>();

        for (OllamaToolCall tc : toolCalls) {
            String callId = "call_" + UUID.randomUUID().toString().replace("-", "");
            String fcId = "fc_" + UUID.randomUUID().toString().replace("-", "");

            String toolName = tc.getFunction().getName();
            Map<String, Object> args = tc.getFunction().getArguments();


            if (args == null || args.isEmpty()) {
                args = defaultArgsFor(toolName);
                log.info("→ Empty args from model, injecting defaults for {}: {}", toolName, args);
            }

            String argsJson;
            try {
                argsJson = objectMapper.writeValueAsString(args);
            } catch (Exception e) {
                log.warn("Cannot serialize tool args", e);
                argsJson = "{}";
            }

            OpenAiResponsesResponse.FunctionCallItem fc =
                    new OpenAiResponsesResponse.FunctionCallItem(
                            "function_call", fcId, callId, toolName, argsJson, "completed"
                    );

            output.add(fc);
            log.info("→ Returning function_call: name={}, callId={}, args={}", toolName, callId, argsJson);
        }

        return new OpenAiResponsesResponse(
                "resp_" + UUID.randomUUID().toString().replace("-", ""),
                "response",
                "completed",
                defaultModel,
                System.currentTimeMillis() / 1000,
                output,
                new OpenAiResponsesResponse.Usage(0, 0, 0)
        );
    }

    /**
     * Дефолтные аргументы для tools, которые DBeaver передаёт с пустым args.
     * Схема/каталог берётся из контекста, который DBeaver вкладывает в system-промпт.
     */
    private Map<String, Object> defaultArgsFor(String toolName) {
        return switch (toolName) {
            case "db_listTableNames", "db_listSchemaNames" ->
                    Map.of("schemaNames", "public");
            case "db_getTableDetails" ->
                    Map.of("tableNames", "");
            default -> Map.of();
        };
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
