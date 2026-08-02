package com.hoshitsuki.paypayledger;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class BuiltinRuleCatalogTest {
    @Test
    public void classifiesRepresentativePayPayMerchantsFromAsset() throws Exception {
        List<CategoryRuleStore.RuleGroup> groups = loadGroups();

        assertGroup(groups, "台湾料理 吉香楼", "restaurant");
        assertGroup(groups, "モスのネット注文", "restaurant");
        assertGroup(groups, "マックスバリュエクスプレス今池駅南店", "retail_supermarket");
        assertGroup(groups, "イオンシネマ", "entertainment_movie");
        assertGroup(groups, "APPLE.COM/BILL", "subscription");
        assertGroup(groups, "モバイルSuica (Apple Pay)", "transport_train");
        assertGroup(groups, "ニトリ名古屋店", "retail_home_center");
    }

    private static void assertGroup(
            List<CategoryRuleStore.RuleGroup> groups,
            String merchant,
            String expectedGroup) {
        assertEquals(
                expectedGroup,
                MerchantRuleMatcher.match(groups, merchant).groupId);
    }

    private static List<CategoryRuleStore.RuleGroup> loadGroups() throws Exception {
        Path asset = Paths.get("app", "src", "main", "assets", "builtin_rule_groups.csv");
        if (!Files.exists(asset)) {
            asset = Paths.get("src", "main", "assets", "builtin_rule_groups.csv");
        }
        List<String> lines = Files.readAllLines(asset, StandardCharsets.UTF_8);
        Map<String, CategoryRuleStore.RuleGroup> groups =
                new LinkedHashMap<String, CategoryRuleStore.RuleGroup>();
        for (int i = 1; i < lines.size(); i++) {
            String[] cells = lines.get(i).split(",", 3);
            if (cells.length != 3) {
                continue;
            }
            CategoryRuleStore.RuleGroup group = groups.get(cells[0]);
            if (group == null) {
                group = new CategoryRuleStore.RuleGroup(
                        cells[0],
                        cells[1],
                        groups.size(),
                        false);
                groups.put(cells[0], group);
            }
            for (String keyword : cells[2].split("\\|")) {
                if (keyword.trim().length() > 0) {
                    group.keywords.add(keyword.trim());
                }
            }
        }
        return new ArrayList<CategoryRuleStore.RuleGroup>(groups.values());
    }
}
