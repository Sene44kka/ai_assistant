package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Запрос в формате OpenAI Chat Completions API.
 * DBeaver шлёт именно такой.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiChatRequest {

    private String model; //Имя модели
    private List<Message> messages;
    private Double temperature;
    private Boolean stream;
    private List<Object> tools;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String role;      // "system" | "user" | "assistant" | "tool"
        private String content;
    }
}
