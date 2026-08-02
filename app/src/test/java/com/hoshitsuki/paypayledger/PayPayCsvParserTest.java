package com.hoshitsuki.paypayledger;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class PayPayCsvParserTest {
    private static final String HEADER =
            "\uFEFF取引日,出金金額（円）,入金金額（円）,海外出金金額,通貨,変換レート（円）,利用国,"
                    + "取引内容,取引先,取引方法,支払い区分,利用者,取引番号\n";

    @Test
    public void importsCardAndCreditExpensesAndCreditCharge() throws Exception {
        String csv = HEADER
                + "2026/07/27 21:58:13,183,-,-,-,-,-,支払い,ローソン,PayPayカード VISA 0000,一回払い,本人,card-1\n"
                + "2026/07/27 20:56:04,-,\"27,574\",-,-,-,-,チャージ,PayPay,クレジット VISA 0000,-,-,charge-1\n"
                + "2026/07/27 19:14:24,900,-,-,-,-,-,支払い,台湾料理  吉香楼,クレジット VISA 0000,-,-,credit-1\n"
                + "2026/07/27 02:52:30,20,-,-,-,-,-,投資,ポイント運用,PayPayポイント,-,-,point-1\n"
                + "2026/07/26 20:23:39,272,-,-,-,-,-,支払い,コンビニ,PayPay残高,-,-,balance-1\n";

        PayPayCsvParser.Result result = PayPayCsvParser.parse(csv);

        assertEquals(3, result.records.size());
        assertEquals(2, result.ignoredMethod);
        assertEquals(0, result.ignoredNonExpense);

        PayPayCsvParser.Record card = result.records.get(0);
        assertEquals("ローソン", card.merchant);
        assertEquals("2026-07-27", card.date);
        assertEquals("21:58", card.time);
        assertEquals(183, card.amount);
        assertEquals("card-1", card.transactionId);

        PayPayCsvParser.Record charge = result.records.get(1);
        assertEquals("PayPay", charge.merchant);
        assertEquals(27574, charge.amount);

        PayPayCsvParser.Record credit = result.records.get(2);
        assertEquals("台湾料理 吉香楼", credit.merchant);
        assertEquals(900, credit.amount);
    }

    @Test
    public void ignoresCardRowsWithoutExpenseOrChargeAmount() throws Exception {
        String csv = HEADER
                + "2026/07/27 20:56:04,-,500,-,-,-,-,返金,PayPay,クレジット VISA 0000,-,-,refund-1\n";

        PayPayCsvParser.Result result = PayPayCsvParser.parse(csv);

        assertEquals(0, result.records.size());
        assertEquals(1, result.ignoredNonExpense);
    }

    @Test(expected = Exception.class)
    public void rejectsUnrelatedCsv() throws Exception {
        PayPayCsvParser.parse("date,amount\n2026-07-27,100\n");
    }

    @Test(expected = Exception.class)
    public void rejectsUnclosedCsvQuote() throws Exception {
        PayPayCsvParser.parse(HEADER + "\"2026/07/27 21:58:13,183");
    }

    @Test
    public void importsProvidedSampleWhenConfigured() throws Exception {
        String path = System.getProperty("paypay.sample.csv", "");
        assumeTrue(path.length() > 0 && Files.exists(Paths.get(path)));
        String csv = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);

        PayPayCsvParser.Result result = PayPayCsvParser.parse(csv);

        assertEquals(43, result.records.size());
        boolean foundCharge = false;
        for (PayPayCsvParser.Record record : result.records) {
            if ("PayPay".equals(record.merchant) && record.amount == 27574) {
                foundCharge = true;
            }
        }
        assertTrue(foundCharge);
    }
}
