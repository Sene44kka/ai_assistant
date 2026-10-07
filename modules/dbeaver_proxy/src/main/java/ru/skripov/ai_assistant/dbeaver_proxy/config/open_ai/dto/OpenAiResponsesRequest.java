package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiResponsesRequest {
    private String model;
    private Object input;
    private Double temperature;
    private Boolean stream = false;
    private String instructions;

    @JsonProperty("previous_response_id")
    private String previousResponseId;

    private List<Tool> tools;
}
