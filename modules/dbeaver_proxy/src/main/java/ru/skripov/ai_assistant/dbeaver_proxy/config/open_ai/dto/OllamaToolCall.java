package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaToolCall {
    private String id;
    private Function function;
    private String type = "function";

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Function {
        private String name;
        private Map<String, Object> arguments;
    }
}
