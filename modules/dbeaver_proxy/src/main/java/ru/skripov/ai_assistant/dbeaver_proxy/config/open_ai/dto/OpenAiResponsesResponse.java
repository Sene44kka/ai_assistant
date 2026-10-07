package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class OpenAiResponsesResponse {

    private String id;
    private String object;
    private String status;
    private String model;

    @JsonProperty("created_at")
    private long createdAt;

    private List<OutputItem> output;
    private Usage usage;

    @Data
    @AllArgsConstructor
    public static class OutputItem {
        private String type;
        private String id;
        private String role;
        private String status;
        private List<ContentBlock> content;
    }

    @Data
    @AllArgsConstructor
    public static class ContentBlock {
        private String type;
        private String text;
        private List<Object> annotations;
    }

    @Data
    @AllArgsConstructor
    public static class Usage {
        @JsonProperty("input_tokens")
        private int inputTokens;

        @JsonProperty("output_tokens")
        private int outputTokens;

        @JsonProperty("total_tokens")
        private int totalTokens;
    }
}
