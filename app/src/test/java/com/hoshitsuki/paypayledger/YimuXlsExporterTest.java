package com.hoshitsuki.paypayledger;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import jxl.NumberCell;
import jxl.Sheet;
import jxl.Workbook;

import static org.junit.Assert.assertEquals;

public class YimuXlsExporterTest {
    @Test
    public void createsReadableYimuWorkbookWithDateAndTimeInOneCell() throws Exception {
        byte[] bytes = YimuXlsExporter.create(Arrays.asList(
                new YimuXlsExporter.Entry(
                        "台湾料理 吉香楼",
                        "2026-07-27",
                        "19:14",
                        900,
                        "食品餐饮",
                        "日常正餐")));

        Workbook workbook = Workbook.getWorkbook(new ByteArrayInputStream(bytes));
        try {
            Sheet sheet = workbook.getSheet(0);
            assertEquals("日期", sheet.getCell(0, 0).getContents());
            assertEquals("2026-07-27 19:14", sheet.getCell(0, 1).getContents());
            assertEquals("支出", sheet.getCell(1, 1).getContents());
            assertEquals(900.0, ((NumberCell) sheet.getCell(2, 1)).getValue(), 0.0);
            assertEquals("食品餐饮", sheet.getCell(3, 1).getContents());
            assertEquals("日常正餐", sheet.getCell(4, 1).getContents());
            assertEquals("日常账本", sheet.getCell(5, 1).getContents());
            assertEquals("PayPay", sheet.getCell(6, 1).getContents());
            assertEquals("台湾料理 吉香楼", sheet.getCell(7, 1).getContents());
            assertEquals(10, sheet.getColumns());
            assertEquals(2, sheet.getRows());
        } finally {
            workbook.close();
        }
    }
}
