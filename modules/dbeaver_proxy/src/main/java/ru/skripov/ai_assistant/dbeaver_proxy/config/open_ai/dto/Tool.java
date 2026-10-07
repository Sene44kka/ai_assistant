package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * Инструмент (function), который может вызвать модель.
 * Формат совместим с OpenAI Responses API.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Tool {
    private String type;
    private String name;
    private String description;
    private Map<String, Object> parameters;

    /**
     * В Chat Completions API функция вложена в "function".
     * В Responses API — на верхнем уровне.
     * Обрабатываем оба варианта через @JsonProperty.
     */
    @JsonProperty("function")
    public void setFunction(Map<String, Object> function) {
        if (function == null) return;
        if (this.name == null && function.get("name") != null) {
            this.name = function.get("name").toString();
        }
        if (this.description == null && function.get("description") != null) {
            this.description = function.get("description").toString();
        }
        if (this.parameters == null && function.get("parameters") instanceof Map) {
            this.parameters = (Map<String, Object>) function.get("parameters");
        }
    }
}
