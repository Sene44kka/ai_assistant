package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Вызов инструмента моделью.
 * Формат OpenAI Chat Completions:
 *   {id: "call_abc", type: "function", function: {name, arguments}}
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolCall {
    private String id;      //call_abc123
    private String type;    //function

    @JsonProperty("function")
    private FunctionCall function;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionCall {
        private String name;         // db_listTableNames
        private String arguments;    // JSON-строка: '{"schemaNames":"public"}'
    }
}
