package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto.*;
import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto.ollama.*;
import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.enums.OllamaRole;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class OllamaProvider implements AiProvider {
    private static final String PROVIDER_NAME = "ollama";
    private static final String AI_MODEL_TYPE_ANSWER = "function_call";
    private static final int LAST_USER_MESSAGES_COUNT = 5;

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
                .map(m -> new OllamaChatRequest.Message(OllamaRole.fromValue(m.getRole()), m.getContent(), null, null))
                .toList();
        String ollamaModel = Optional.ofNullable(request.getModel()).orElse(defaultModel);

        OllamaChatResponse.Message msg = callOllama(ollamaModel, ollamaMessages, request.getTemperature(), null);
        return buildChatResponse(msg.getContent());
    }

    @Override
    public OpenAiResponsesResponse responses(OpenAiResponsesRequest request) {
        List<OllamaChatRequest.Message> ollamaMessages = convertResponsesToOllamaMessages(request);
        List<OllamaTool> ollamaTools = convertTools(request.getTools());
        String ollamaModel = Optional.ofNullable(request.getModel()).orElse(defaultModel);

        OllamaChatResponse.Message msg = callOllama(ollamaModel, ollamaMessages, request.getTemperature(), ollamaTools);

        if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
            return buildFunctionCallResponse(msg.getToolCalls());
        }

        return buildResponsesResponse(msg.getContent());
    }

    private OllamaChatResponse.Message callOllama(String ollamaModel,
                                                  List<OllamaChatRequest.Message> messages,
                                                  Double temperature,
                                                  List<OllamaTool> tools) {
        OllamaChatRequest.Options options = new OllamaChatRequest.Options();
        options.setTemperature(temperature != null ? temperature : 0.1);

        OllamaChatRequest ollamaRequest = new OllamaChatRequest(
                ollamaModel, messages, false, false, options, tools
        );

        log.info("→ Ollama: POST {}/api/chat, model={}, messages={}, tools={}, temp={}",
                ollamaUrl, ollamaModel, messages.size(),
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
        if (!(input instanceof List<?> list)) {
            if (input instanceof String s) {
                all.add(new OllamaChatRequest.Message(OllamaRole.USER, s, null, null));
            }
            return all;
        }

        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }

            String type = map.get("type") != null ? map.get("type").toString() : "";

            //Это если наша ИИ модель шлет запрос в DBeaver, она делает это с этим типом
            if ("function_call".equals(type)) {
                String name = (String) map.get("name");
                String callId = (String) map.get("call_id");
                String arguments = (String) map.get("arguments");

                OllamaToolCall.Function fn = new OllamaToolCall.Function();
                fn.setName(name);
                fn.setArguments(parseArgs(arguments));

                OllamaToolCall tc = new OllamaToolCall();
                tc.setFunction(fn);
                tc.setId(callId);

                OllamaChatRequest.Message assistantMsg = new OllamaChatRequest.Message();
                assistantMsg.setRole(OllamaRole.ASSISTANT);
                assistantMsg.setContent("");
                assistantMsg.setToolCalls(List.of(tc));
                all.add(assistantMsg);

                log.info("→ Added assistant message with tool_call: {} ({})", name, callId);
                continue;
            }

            //Это когда DBeaver отвечает на тип function_call
            if ("function_call_output".equals(type)) {
                String callId = (String) map.get("call_id");
                String output = map.get("output") != null ? map.get("output").toString() : "";

                OllamaChatRequest.Message toolMsg = new OllamaChatRequest.Message();
                toolMsg.setRole(OllamaRole.TOOL);
                toolMsg.setContent(output);
                toolMsg.setToolCallId(callId);
                all.add(toolMsg);

                log.info("→ Added tool message: call_id={}, {} chars", callId, output.length());
                continue;
            }

            OllamaRole role = map.get("role") != null ? OllamaRole.fromValue(map.get("role").toString()) : OllamaRole.USER;
            String text = extractText(map.get("content"));

            if (text != null && !text.isBlank()) {
                if (OllamaRole.ASSISTANT == role && text.startsWith("db_") && text.contains("was completed")) {
                    continue;
                }
//                if ("system".equals(role)) {
//                    text = text +
//                            """
//                                    Instructions:
//                                    - You are the DBeaver AI assistant.
//                                    - Act as a database architect and SQL expert.
//                                    - **You have access to special tools to inspect the database schema. You MUST use these tools whenever you lack information about tables, columns, or indexes.**
//                                    - **NEVER write tool calls as plain text JSON inside the message content. ALWAYS use the native tool_calls API mechanism.**
//                                    - **If the user asks for table indexes and you don't know them, you MUST call the 'db_listTableIndexes' tool immediately.**
//                                    - Rely only on the provided schema information, do not make assumptions.
//                                    - Use the same language as the user.
//                                    - By default generate SQL queries according to user requests. Also answer to general database related questions.
//                                    - If user wants to see table data then show it in markdown table format by default.
//                                    - Stick strictly to SQL dialect syntax.
//                                    - Do not invent columns, tables, or data that aren't explicitly defined.
//                                    - Use "" to quote identifiers if needed.
//                                    - Use '' to quote strings.
//                                    - Joins and sub‑queries are allowed.
//                            """;
//                }
                all.add(new OllamaChatRequest.Message(role, text, null, null));
            }
        }

        log.info("→ Converted messages: {} in → {} out", list.size(), all.size());
        for (int i = 0; i < all.size(); i++) {
            OllamaChatRequest.Message m = all.get(i);
            String preview = Optional.ofNullable(m.getContent())
                    .map(it -> it.replace("\n", " ").trim())
                    .orElse("");
            log.info("   [{}] role={}, toolCalls={}, toolCallId={}, content={}",
                    i, m.getRole(),
                    m.getToolCalls() != null ? m.getToolCalls().size() : 0,
                    m.getToolCallId(),
                    preview);
        }

        return trimHistory(all);
    }

    private Map<String, Object> parseArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(argumentsJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Cannot parse args: {}", argumentsJson, e);
            return Map.of();
        }
    }

    private List<OllamaChatRequest.Message> trimHistory(List<OllamaChatRequest.Message> all) {
        if (all.isEmpty()) {
            return all;
        }

        List<OllamaChatRequest.Message> result = new ArrayList<>();
        all.stream()
                .filter(m -> OllamaRole.SYSTEM == m.getRole())
                .findFirst()
                .ifPresent(result::add);

        List<List<OllamaChatRequest.Message>> turns = splitIntoTurns(all);
        List<List<OllamaChatRequest.Message>> completeTurns = new ArrayList<>();
        for (int i = 0; i < turns.size(); i++) {
            List<OllamaChatRequest.Message> turn = turns.get(i);
            boolean isLast = (i == turns.size() - 1);
            boolean isComplete = isTurnComplete(turn);

            if (isComplete || isLast) {
                completeTurns.add(turn);
            } else {
                log.info("→ Skipping incomplete turn #{} (no assistant response)", i);
            }
        }

        int fromTurn = Math.max(0, completeTurns.size() - LAST_USER_MESSAGES_COUNT);
        for (int i = fromTurn; i < completeTurns.size(); i++) {
            result.addAll(completeTurns.get(i));
        }

        log.info("→ Trimmed history: {} → {} messages ({} turns total, {} complete, kept last {})", all.size(), result.size(), turns.size(), completeTurns.size(), LAST_USER_MESSAGES_COUNT);
        return result;
    }

    /**
     * Разбивает историю на "тёрны": каждый turn = user + все последующие assistant/tool
     * до следующего user.
     */
    private List<List<OllamaChatRequest.Message>> splitIntoTurns(List<OllamaChatRequest.Message> all) {
        List<List<OllamaChatRequest.Message>> turns = new ArrayList<>();
        List<OllamaChatRequest.Message> current = new ArrayList<>();

        for (OllamaChatRequest.Message m : all) {
            if (OllamaRole.SYSTEM == m.getRole()) {
                continue;
            }
            if (OllamaRole.USER == m.getRole() && !current.isEmpty()) {
                turns.add(current);
                current = new ArrayList<>();
            }
            current.add(m);
        }
        if (!current.isEmpty()) {
            turns.add(current);
        }
        return turns;
    }

    /**
     * Тёрн COMPLETE, если в нём есть хотя бы один assistant с непустым content
     * и без toolCalls (это финальный ответ модели).
     */
    private boolean isTurnComplete(List<OllamaChatRequest.Message> turn) {
        return turn.stream()
                .anyMatch(
                        m -> OllamaRole.ASSISTANT == m.getRole()
                                && (m.getToolCalls() == null || m.getToolCalls().isEmpty())
                                && m.getContent() != null
                                && !m.getContent().isBlank()
                );
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

    public List<String> listModels() {
        try {
            OllamaTagsResponse response = webClientBuilder
                    .baseUrl(ollamaUrl)
                    .build()
                    .get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(OllamaTagsResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response == null || response.getModels() == null) {
                log.warn("Ollama returned empty model list");
                return List.of();
            }

            List<String> names = response.getModels().stream()
                    .map(OllamaTagsResponse.ModelInfo::getName)
                    .toList();

            log.info("→ Ollama has {} models: {}", names.size(), names);
            return names;

        } catch (Exception e) {
            log.error("Failed to fetch models from Ollama: {}", e.getMessage());
            return List.of();
        }
    }
}
