package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OllamaRole {

    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool");

    private final String value;

    OllamaRole(String value) {
        this.value = value;
    }

    /**
     * Значение для JSON-сериализации (lowercase).
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Парсинг из JSON (case-insensitive).
     */
    @JsonCreator
    public static OllamaRole fromValue(String value) {
        if (value == null) return null;
        for (OllamaRole role : values()) {
            if (role.value.equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }

    public boolean isSystem() {
        return this == SYSTEM;
    }

    public boolean isUser() {
        return this == USER;
    }

    public boolean isAssistant() {
        return this == ASSISTANT;
    }

    public boolean isTool() {
        return this == TOOL;
    }
}
