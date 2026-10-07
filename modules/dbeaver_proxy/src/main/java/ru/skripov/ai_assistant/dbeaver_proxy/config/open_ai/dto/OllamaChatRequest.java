package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private Options options;
    private Boolean think; //Включать ли размышление для модели

    public OllamaChatRequest(String model, List<Message> messages, boolean stream) {
        this.model = model;
        this.messages = messages;
        this.stream = stream;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
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
