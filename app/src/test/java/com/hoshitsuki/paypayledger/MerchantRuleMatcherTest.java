package com.hoshitsuki.paypayledger;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MerchantRuleMatcherTest {
    @Test
    public void matchesTwoCharacterJapaneseKeywordInsideMerchant() {
        List<CategoryRuleStore.RuleGroup> groups = groups(
                group("restaurant", 0, "料理"));

        assertEquals(
                "restaurant",
                MerchantRuleMatcher.match(groups, "台湾料理 吉香楼").groupId);
    }

    @Test
    public void prefersExactAndLongerKeywordOverEarlierGenericGroup() {
        List<CategoryRuleStore.RuleGroup> groups = groups(
                group("generic", 0, "ネット"),
                group("specific", 1, "モスのネット注文"));

        assertEquals(
                "specific",
                MerchantRuleMatcher.match(groups, "モスのネット注文").groupId);
    }

    @Test
    public void shortAsciiKeywordRequiresAWordBoundary() {
        List<CategoryRuleStore.RuleGroup> groups = groups(
                group("telecom", 0, "au"));

        assertEquals("telecom", MerchantRuleMatcher.match(groups, "au PAY").groupId);
        assertNull(MerchantRuleMatcher.match(groups, "Audi Store"));
    }

    @Test
    public void toleratesOcrSpacingInsideLongAsciiName() {
        List<CategoryRuleStore.RuleGroup> groups = groups(
                group("subscription", 0, "netflix"));

        assertEquals(
                "subscription",
                MerchantRuleMatcher.match(groups, "N E T F L I X PAYPAY").groupId);
    }

    private static CategoryRuleStore.RuleGroup group(
            String id,
            int order,
            String... keywords) {
        CategoryRuleStore.RuleGroup group =
                new CategoryRuleStore.RuleGroup(id, id, order, false);
        for (String keyword : keywords) {
            group.keywords.add(keyword);
        }
        return group;
    }

    private static List<CategoryRuleStore.RuleGroup> groups(
            CategoryRuleStore.RuleGroup... values) {
        ArrayList<CategoryRuleStore.RuleGroup> groups =
                new ArrayList<CategoryRuleStore.RuleGroup>();
        for (CategoryRuleStore.RuleGroup value : values) {
            groups.add(value);
        }
        return groups;
    }
}
