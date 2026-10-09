package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaChatResponse {

    private String model;

    @com.fasterxml.jackson.annotation.JsonProperty("created_at")
    private String createdAt;

    private Message message;
    private boolean done;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String role;
        private String content;

        @JsonProperty("tool_calls")
        private List<OllamaToolCall> toolCalls;

        @JsonProperty("thinking")
        private String thinking;
    }
}
