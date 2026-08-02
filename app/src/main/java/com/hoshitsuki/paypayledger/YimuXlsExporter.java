package com.hoshitsuki.paypayledger;

import java.io.ByteArrayOutputStream;
import java.util.List;

import jxl.Workbook;
import jxl.write.Label;
import jxl.write.Number;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;

final class YimuXlsExporter {
    private YimuXlsExporter() {
    }

    static byte[] create(List<Entry> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        WritableWorkbook workbook = null;
        try {
            workbook = Workbook.createWorkbook(bytes);
            WritableSheet sheet = workbook.createSheet("Sheet1", 0);
            String[] headers = new String[]{
                    "日期", "收支类型", "金额", "类别", "二级分类",
                    "所属账本", "收支账户", "备注", "标签", "地址"
            };
            for (int i = 0; i < headers.length; i++) {
                sheet.addCell(new Label(i, 0, headers[i]));
            }
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                int row = i + 1;
                sheet.addCell(new Label(0, row, entry.date + " " + entry.time));
                sheet.addCell(new Label(1, row, "支出"));
                sheet.addCell(new Number(2, row, entry.amount));
                sheet.addCell(new Label(3, row, entry.major));
                sheet.addCell(new Label(4, row, entry.minor));
                sheet.addCell(new Label(5, row, "日常账本"));
                sheet.addCell(new Label(6, row, "PayPay"));
                sheet.addCell(new Label(7, row, entry.merchant));
                sheet.addCell(new Label(8, row, ""));
                sheet.addCell(new Label(9, row, ""));
            }
            workbook.write();
        } finally {
            if (workbook != null) {
                workbook.close();
            }
        }
        return bytes.toByteArray();
    }

    static final class Entry {
        final String merchant;
        final String date;
        final String time;
        final int amount;
        final String major;
        final String minor;

        Entry(
                String merchant,
                String date,
                String time,
                int amount,
                String major,
                String minor) {
            this.merchant = safe(merchant);
            this.date = safe(date);
            this.time = safe(time);
            this.amount = amount;
            this.major = safe(major);
            this.minor = safe(minor);
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }
}
