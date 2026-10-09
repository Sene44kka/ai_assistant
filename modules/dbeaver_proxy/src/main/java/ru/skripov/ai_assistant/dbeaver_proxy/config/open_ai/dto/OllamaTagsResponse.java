package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaTagsResponse {

    private List<ModelInfo> models;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelInfo {
        private String name;
        private String model;

        @JsonProperty("modified_at")
        private String modifiedAt;

        private long size;
        private String digest;
        private Details details;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Details {
            private String family;
            private String format;

            @JsonProperty("parameter_size")
            private String parameterSize;

            @JsonProperty("quantization_level")
            private String quantizationLevel;
        }
    }
}
