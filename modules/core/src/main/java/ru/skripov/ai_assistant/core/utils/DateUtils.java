package ru.skripov.ai_assistant.core.utils;

public class DateUtils {

    public static String getMonthString(Integer durationMonth) {
        // Определяем последние цифры для склонения
        int lastDigit = durationMonth % 10;
        int lastTwoDigits = durationMonth % 100;

        // Определяем, как правильно склонять слово "месяц"
        if (lastDigit == 1 && lastTwoDigits != 11) {
            return durationMonth + " месяц";
        }

        if (lastDigit >= 2 && lastDigit <= 4 && (lastTwoDigits < 12 || lastTwoDigits > 14)) {
            return durationMonth + " месяца";
        }

        return durationMonth + " месяцев";
    }
}

