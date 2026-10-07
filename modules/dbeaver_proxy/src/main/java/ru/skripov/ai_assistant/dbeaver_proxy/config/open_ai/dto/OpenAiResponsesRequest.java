package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Запрос в формате OpenAI Responses API (новое API, использ. DBeaver 26+).
 * POST /v1/responses
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiResponsesRequest {
    private String model;
    private Object input;
    private Double temperature;
    private Boolean stream;
    private List<Object> tools;
    private String instructions;
    private String previousResponseId;
}
