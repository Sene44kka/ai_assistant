package ru.skripov.ai_assistant.core.utils;

public class PhoneNormalizer {
    /**
     * Приводит номера телефонов к единому формату
     * @param phone - номер телефона в любом формате
     * @return номер телефона в формате "+7XXXXXXXXXX"
     */
    public static String normalize(String phone) {
        if (phone == null) return null;

        // убираем все кроме цифр
        String digits = phone.replaceAll("\\D", "");

        // приводим к формату +7XXXXXXXXXX
        if (digits.startsWith("8")) {
            digits = "7" + digits.substring(1);
        }
        if (!digits.startsWith("7")) {
            digits = "7" + digits;
        }

        if (digits.length() != 11) {
            throw new IllegalArgumentException("Некорректный телефон после нормализации: " + phone);
        }

        return "+7" + digits.substring(1);
    }
}
