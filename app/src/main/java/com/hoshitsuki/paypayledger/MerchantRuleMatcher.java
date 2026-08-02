package com.hoshitsuki.paypayledger;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class MerchantRuleMatcher {
    private MerchantRuleMatcher() {
    }

    static CategoryRuleStore.RuleGroup match(
            List<CategoryRuleStore.RuleGroup> groups,
            String merchant) {
        String normalizedMerchant = normalize(merchant);
        String compactMerchant = compact(normalizedMerchant);
        if (compactMerchant.length() == 0) {
            return null;
        }

        CategoryRuleStore.RuleGroup bestGroup = null;
        int bestScore = -1;
        for (CategoryRuleStore.RuleGroup group : groups) {
            for (String keyword : group.keywords) {
                String normalizedKeyword = normalize(keyword);
                String compactKeyword = compact(normalizedKeyword);
                if (compactKeyword.length() == 0) {
                    continue;
                }
                int score = score(
                        normalizedMerchant,
                        compactMerchant,
                        normalizedKeyword,
                        compactKeyword);
                if (score > bestScore) {
                    bestScore = score;
                    bestGroup = group;
                }
            }
        }
        return bestGroup;
    }

    private static int score(
            String normalizedMerchant,
            String compactMerchant,
            String normalizedKeyword,
            String compactKeyword) {
        if (compactMerchant.equals(compactKeyword)) {
            return 1_000_000 + compactKeyword.length();
        }

        if (isAsciiWord(compactKeyword) && compactKeyword.length() <= 2) {
            Pattern token = Pattern.compile(
                    "(^|\\s)" + Pattern.quote(normalizedKeyword) + "($|\\s)");
            return token.matcher(normalizedMerchant).find()
                    ? 100_000 + compactKeyword.length()
                    : -1;
        }

        if (compactKeyword.length() == 1) {
            return -1;
        }
        return compactMerchant.contains(compactKeyword)
                ? 100_000 + compactKeyword.length()
                : -1;
    }

    static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[・＊*／/\\\\()（）\\[\\]【】.,，。:：_-]+", " ")
                .replaceAll("\\bpaypay\\b", " ")
                .replaceAll("\\bvisa\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", "");
    }

    private static boolean isAsciiWord(String value) {
        return value.matches("[a-z0-9]+");
    }
}
