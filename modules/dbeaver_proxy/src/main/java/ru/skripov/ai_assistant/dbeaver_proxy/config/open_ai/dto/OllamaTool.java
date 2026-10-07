package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * Инструмент в формате Ollama.
 * Формат: {type: "function", function: {name, description, parameters}}
 */
@Data
@AllArgsConstructor
public class OllamaTool {

    private String type;      // "function"
    private Function function;

    @Data
    @AllArgsConstructor
    public static class Function {
        private String name;
        private String description;
        private Map<String, Object> parameters;
    }
}
