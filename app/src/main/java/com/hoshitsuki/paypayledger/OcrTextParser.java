package com.hoshitsuki.paypayledger;

import java.text.Normalizer;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OcrTextParser {
    private OcrTextParser() {
    }

    static Integer extractAmount(String text) {
        String candidate = Normalizer.normalize(
                text == null ? "" : text,
                Normalizer.Form.NFKC);
        Matcher matcher = Pattern.compile(
                "([0-9Oo〇○][0-9Oo〇○,，.．\\s]{0,12})\\s*[円¥￥]")
                .matcher(candidate);
        if (!matcher.find()) {
            return null;
        }
        try {
            String digits = matcher.group(1)
                    .replace('O', '0')
                    .replace('o', '0')
                    .replace('〇', '0')
                    .replace('○', '0')
                    .replaceAll("[,，.．\\s]", "");
            if (digits.length() == 0 || digits.length() > 9) {
                return null;
            }
            int amount = Integer.parseInt(digits);
            return amount > 0 ? amount : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static DateTime extractDateTime(String text) {
        String candidate = Normalizer.normalize(
                text == null ? "" : text,
                Normalizer.Form.NFKC)
                .replace('O', '0')
                .replace('o', '0')
                .replace('〇', '0')
                .replace('○', '0')
                .replace('峙', '時')
                .replaceAll("\\s+", " ");
        Matcher matcher = Pattern.compile(
                "(20[0-9]{2})\\s*(?:年|[/.\\-])\\s*([0-9]{1,2})\\s*(?:月|[/.\\-])\\s*"
                        + "([0-9]{1,2})\\s*日?\\s*([0-9]{1,2})\\s*(?:時|:)\\s*"
                        + "([0-9]{1,2})\\s*分?")
                .matcher(candidate);
        if (!matcher.find()) {
            return null;
        }

        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));
        int hour = Integer.parseInt(matcher.group(4));
        int minute = Integer.parseInt(matcher.group(5));
        GregorianCalendar calendar =
                new GregorianCalendar(year, month - 1, day, hour, minute);
        calendar.setLenient(false);
        try {
            calendar.getTime();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return new DateTime(
                String.format(Locale.US, "%04d-%02d-%02d", year, month, day),
                String.format(Locale.US, "%02d:%02d", hour, minute));
    }

    static final class DateTime {
        final String date;
        final String time;

        DateTime(String date, String time) {
            this.date = date;
            this.time = time;
        }
    }
}
