package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.enums.Role;

import java.util.List;

/**
 * Запрос в формате Ollama Chat API (POST /api/chat).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OllamaChatRequest {

    private String model;
    private List<Message> messages;
    private boolean stream;
    private Boolean think; //Включать ли размышление для модели
    private Options options;
    private List<OllamaTool> tools;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private Role role;
        private String content;
        @JsonProperty("tool_calls")
        private List<OllamaToolCall> toolCalls;
        @JsonProperty("tool_call_id")
        private String toolCallId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Options {
        private Double temperature;

        @JsonProperty("num_ctx")
        private Integer numCtx;
    }
}
