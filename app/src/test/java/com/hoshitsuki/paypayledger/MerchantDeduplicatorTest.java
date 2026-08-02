package com.hoshitsuki.paypayledger;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MerchantDeduplicatorTest {
    @Test
    public void mergesFullAndTruncatedOcrVersions() {
        assertTrue(MerchantDeduplicator.isLikelySame(
                "マックスバリュエクスプレス今池駅南店",
                "マックスバリュエクスプレス今池駅"));
    }

    @Test
    public void mergesOneCharacterOcrErrorInLongMerchant() {
        assertTrue(MerchantDeduplicator.isLikelySame(
                "イオンリテール",
                "イ才ンリテール"));
    }

    @Test
    public void doesNotMergeDifferentShortMerchants() {
        assertFalse(MerchantDeduplicator.isLikelySame("すき家", "松屋"));
        assertFalse(MerchantDeduplicator.isLikelySame("イオン", "イオンシネマ"));
    }

    @Test
    public void ignoresPayPayAndSpacingNoise() {
        assertTrue(MerchantDeduplicator.isLikelySame(
                "OPENAI * CHATGPT",
                "OPENAI CHATGPT PAYPAY"));
    }
}
