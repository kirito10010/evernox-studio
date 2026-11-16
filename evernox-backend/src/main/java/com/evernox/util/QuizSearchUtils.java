package com.evernox.util;

/**
 * 题库文本匹配工具（忍者测验 / 喵问答共用）。
 *
 * 题库搜索要容忍错别字、多字、少字：
 *  - 先做「全串编辑距离」：整句整体相似度；
 *  - 再做「近似子串匹配」：把用户输入当成模式，在题目里找最接近的一段
 *    （用户只记得题目里的一小段时，整串距离会失真，这一步能把这种情况救回来）；
 *  - 任取较高分作为最终相似度。
 */
public final class QuizSearchUtils {

    /** 参与计算的输入最长长度，避免长文本把搜索拖慢 */
    private static final int MAX_QUERY_LEN = 80;
    private static final int MAX_TEXT_LEN = 200;
    /** 太短的串不做子串匹配，否则一个字会命中所有题目 */
    private static final int MIN_SUBSTRING_LEN = 3;

    private QuizSearchUtils() {
    }

    /** 去标点/空白、全角转半角、转小写，仅保留中文/字母/数字 */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0xFF01 && c <= 0xFF5E) {
                c = (char) (c - 0xFEE0);
            }
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /** 相似度 0~1，入参必须是 normalize 之后的字符串 */
    public static double similarity(String query, String text) {
        if (query == null || text == null) {
            return 0.0;
        }
        if (query.equals(text)) {
            return 1.0;
        }
        if (query.isEmpty() || text.isEmpty()) {
            return 0.0;
        }
        if (text.contains(query) || query.contains(text)) {
            return 1.0;
        }
        String q = truncate(query, MAX_QUERY_LEN);
        String t = truncate(text, MAX_TEXT_LEN);

        int full = levenshtein(q, t);
        double fullScore = 1.0 - (double) full / Math.max(q.length(), t.length());
        double subScore = 0.0;
        int shortLen = Math.min(q.length(), t.length());
        if (shortLen >= MIN_SUBSTRING_LEN) {
            String pattern = q.length() <= t.length() ? q : t;
            String target = q.length() <= t.length() ? t : q;
            int dist = bestSubstringDistance(pattern, target);
            subScore = 1.0 - (double) dist / pattern.length();
        }
        return Math.max(fullScore, subScore);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= b.length(); j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }

    /**
     * 近似子串匹配（Sellers 算法）：pattern 与 target 任意子串的最小编辑距离。
     * 首行全 0 允许跳过 target 前缀，最后取末行最小值允许跳过后缀。
     */
    private static int bestSubstringDistance(String pattern, String target) {
        int m = pattern.length();
        int n = target.length();
        int[] prev = new int[n + 1];
        int[] cur = new int[n + 1];
        for (int j = 0; j <= n; j++) {
            prev[j] = 0;
        }
        for (int i = 1; i <= m; i++) {
            cur[0] = i;
            char cp = pattern.charAt(i - 1);
            for (int j = 1; j <= n; j++) {
                int cost = cp == target.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        int min = Integer.MAX_VALUE;
        for (int j = 0; j <= n; j++) {
            min = Math.min(min, prev[j]);
        }
        return min;
    }
}
