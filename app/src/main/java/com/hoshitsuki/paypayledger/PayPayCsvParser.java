package com.hoshitsuki.paypayledger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PayPayCsvParser {
    static Result parse(String csv) throws Exception {
        List<String[]> rows = parseCsv(csv == null ? "" : csv.replace("\uFEFF", ""));
        if (rows.isEmpty()) {
            throw new Exception("CSV 为空");
        }

        Map<String, Integer> columns = headerMap(rows.get(0));
        int dateColumn = requireColumn(columns, "取引日");
        int outgoingAmountColumn = requireColumn(columns, "出金金額（円）");
        int incomingAmountColumn = requireColumn(columns, "入金金額（円）");
        int contentColumn = requireColumn(columns, "取引内容");
        int merchantColumn = requireColumn(columns, "取引先");
        int methodColumn = requireColumn(columns, "取引方法");
        int transactionIdColumn = requireColumn(columns, "取引番号");

        ArrayList<Record> records = new ArrayList<Record>();
        int ignoredMethod = 0;
        int ignoredNonExpense = 0;
        int invalid = 0;
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            String method = cell(row, methodColumn).trim();
            if (!isSupportedMethod(method)) {
                ignoredMethod++;
                continue;
            }

            String transactionContent = cell(row, contentColumn).trim();
            Integer amount = parseAmount(cell(row, outgoingAmountColumn));
            if ((amount == null || amount <= 0) && transactionContent.contains("チャージ")) {
                amount = parseAmount(cell(row, incomingAmountColumn));
            }
            if (amount == null || amount <= 0) {
                ignoredNonExpense++;
                continue;
            }

            String[] dateTime = parseDateTime(cell(row, dateColumn));
            String merchant = cell(row, merchantColumn).trim().replaceAll("\\s+", " ");
            if (dateTime == null || merchant.length() == 0 || "-".equals(merchant)) {
                invalid++;
                continue;
            }
            records.add(new Record(
                    merchant,
                    dateTime[0],
                    dateTime[1],
                    amount,
                    cell(row, transactionIdColumn).trim()));
        }
        return new Result(records, ignoredMethod, ignoredNonExpense, invalid);
    }

    private static boolean isSupportedMethod(String method) {
        return method.contains("カード") || method.contains("クレジット");
    }

    private static Integer parseAmount(String raw) {
        String value = raw == null ? "" : raw.trim()
                .replace(",", "")
                .replace("，", "")
                .replace(" ", "");
        if (value.length() == 0 || "-".equals(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String[] parseDateTime(String raw) {
        String value = raw == null ? "" : raw.trim();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(20\\d{2})/(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2})(?::\\d{1,2})?")
                .matcher(value);
        if (!matcher.find()) {
            return null;
        }
        return new String[]{
                String.format(java.util.Locale.US, "%04d-%02d-%02d",
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3))),
                String.format(java.util.Locale.US, "%02d:%02d",
                        Integer.parseInt(matcher.group(4)),
                        Integer.parseInt(matcher.group(5)))
        };
    }

    private static Map<String, Integer> headerMap(String[] header) {
        LinkedHashMap<String, Integer> columns = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < header.length; i++) {
            columns.put(header[i].trim().replace("\uFEFF", ""), i);
        }
        return columns;
    }

    private static int requireColumn(Map<String, Integer> columns, String name) throws Exception {
        Integer index = columns.get(name);
        if (index == null) {
            throw new Exception("不是 PayPay 交易履历 CSV，缺少“" + name + "”列");
        }
        return index;
    }

    private static String cell(String[] row, int index) {
        return index >= 0 && index < row.length ? row[index] : "";
    }

    private static List<String[]> parseCsv(String source) throws Exception {
        ArrayList<String[]> rows = new ArrayList<String[]>();
        ArrayList<String> row = new ArrayList<String>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < source.length() && source.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row.toArray(new String[row.size()]));
                row = new ArrayList<String>();
            } else if (c != '\r') {
                cell.append(c);
            }
        }
        if (quoted) {
            throw new Exception("CSV 引号没有正确闭合");
        }
        row.add(cell.toString());
        boolean hasData = false;
        for (String value : row) {
            if (value.length() > 0) {
                hasData = true;
                break;
            }
        }
        if (hasData) {
            rows.add(row.toArray(new String[row.size()]));
        }
        return rows;
    }

    static final class Record {
        final String merchant;
        final String date;
        final String time;
        final int amount;
        final String transactionId;

        Record(String merchant, String date, String time, int amount, String transactionId) {
            this.merchant = merchant;
            this.date = date;
            this.time = time;
            this.amount = amount;
            this.transactionId = transactionId;
        }
    }

    static final class Result {
        final List<Record> records;
        final int ignoredMethod;
        final int ignoredNonExpense;
        final int invalid;

        Result(List<Record> records, int ignoredMethod, int ignoredNonExpense, int invalid) {
            this.records = records;
            this.ignoredMethod = ignoredMethod;
            this.ignoredNonExpense = ignoredNonExpense;
            this.invalid = invalid;
        }
    }
}
