package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OutputType {

    MESSAGE("message");

    private final String value;

    OutputType(String value) {
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
    public static OutputType fromValue(String value) {
        if (value == null) return null;
        for (OutputType role : values()) {
            if (role.value.equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown output type: " + value);
    }

    public boolean isMessage() {
        return this == MESSAGE;
    }
}
