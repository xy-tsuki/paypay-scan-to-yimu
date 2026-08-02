package com.hoshitsuki.paypayledger;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OcrTextParserTest {
    @Test
    public void toleratesCommonAmountOcrErrors() {
        assertEquals(Integer.valueOf(12985), OcrTextParser.extractAmount("12,985円"));
        assertEquals(Integer.valueOf(1040), OcrTextParser.extractAmount("1,O4O 円"));
        assertEquals(Integer.valueOf(6380), OcrTextParser.extractAmount("６，３８０￥"));
        assertNull(OcrTextParser.extractAmount("0円"));
    }

    @Test
    public void parsesJapaneseAndSeparatorDates() {
        OcrTextParser.DateTime japanese =
                OcrTextParser.extractDateTime("2026年4月25日 21時30分");
        assertEquals("2026-04-25", japanese.date);
        assertEquals("21:30", japanese.time);

        OcrTextParser.DateTime tolerant =
                OcrTextParser.extractDateTime("2026/O4/24 16:54");
        assertEquals("2026-04-24", tolerant.date);
        assertEquals("16:54", tolerant.time);
    }

    @Test
    public void rejectsImpossibleDateOrTime() {
        assertNull(OcrTextParser.extractDateTime("2026年2月31日 21時30分"));
        assertNull(OcrTextParser.extractDateTime("2026年4月25日 25時30分"));
    }
}
