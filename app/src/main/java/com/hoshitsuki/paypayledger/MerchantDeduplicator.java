package com.hoshitsuki.paypayledger;

final class MerchantDeduplicator {
    private MerchantDeduplicator() {
    }

    static boolean isLikelySame(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.length() == 0 || b.length() == 0) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }

        String shorter = a.length() <= b.length() ? a : b;
        String longer = a.length() <= b.length() ? b : a;
        if (shorter.length() >= 4
                && longer.contains(shorter)
                && shorter.length() * 2 >= longer.length()) {
            return true;
        }
        if (shorter.length() >= 5
                && sharedPrefixLength(a, b) >= 5
                && shorter.length() * 2 >= longer.length()) {
            return true;
        }

        float similarity = similarity(a, b);
        return shorter.length() >= 5
                ? similarity >= 0.80f
                : similarity >= 0.90f;
    }

    static String normalize(String value) {
        return MerchantRuleMatcher.normalize(value)
                .replaceAll("\\s+", "")
                .replaceAll("[^a-z0-9\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]+", "");
    }

    private static int sharedPrefixLength(String a, String b) {
        int max = Math.min(a.length(), b.length());
        int count = 0;
        while (count < max && a.charAt(count) == b.charAt(count)) {
            count++;
        }
        return count;
    }

    private static float similarity(String a, String b) {
        int common = longestCommonSubsequence(a, b);
        return (2.0f * common) / (a.length() + b.length());
    }

    private static int longestCommonSubsequence(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    current[j] = previous[j - 1] + 1;
                } else {
                    current[j] = Math.max(previous[j], current[j - 1]);
                }
            }
            int[] swap = previous;
            previous = current;
            current = swap;
            for (int j = 0; j < current.length; j++) {
                current[j] = 0;
            }
        }
        return previous[b.length()];
    }
}
