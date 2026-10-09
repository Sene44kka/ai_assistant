package ru.skripov.ai_assistant.dbeaver_proxy.config.open_ai.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum InputType {

    MESSAGE("message"),
    FUNCTION_CALL("function_call"),
    FUNCTION_CALL_OUTPUT("function_call_output");

    private final String value;

    InputType(String value) {
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
    public static InputType fromValue(String value) {
        if (value == null) return null;
        for (InputType role : values()) {
            if (role.value.equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown input type: " + value);
    }

    public boolean isMessage() {
        return this == MESSAGE;
    }

    public boolean isFunctionCall() {
        return this == FUNCTION_CALL;
    }

    public boolean isFunctionCallOutput() {
        return this == FUNCTION_CALL_OUTPUT;
    }
}
