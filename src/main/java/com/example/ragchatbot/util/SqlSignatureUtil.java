package com.example.ragchatbot.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utility helpers for normalizing SQL so structurally similar queries share the
 * same signature regardless of literal values or whitespace.
 */
public final class SqlSignatureUtil {

    private static final Pattern STRING_LITERAL = Pattern.compile("(?s)'([^']|'')*'");
    private static final Pattern DOUBLE_QUOTE_LITERAL = Pattern.compile("(?s)\"([^\"]|\"\")*\"");
    private static final Pattern NUMERIC_LITERAL = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");
    private static final Pattern EXTRA_WHITESPACE = Pattern.compile("\\s+");

    private SqlSignatureUtil() {
    }

    /**
     * Normalizes SQL text by stripping literals and collapsing whitespace.
     *
     * @param sql Raw SQL text
     * @return Normalized signature safe for matching, or null if input empty
     */
    public static String normalize(String sql) {
        if (sql == null) {
            return null;
        }

        String trimmed = sql.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // Replace string and double-quoted literals with placeholders
        String withoutStrings = STRING_LITERAL.matcher(trimmed).replaceAll("''");
        withoutStrings = DOUBLE_QUOTE_LITERAL.matcher(withoutStrings).replaceAll("\"\"");

        // Replace numbers with a single token
        String withoutNumbers = NUMERIC_LITERAL.matcher(withoutStrings).replaceAll("#");

        // Collapse whitespace and upper-case for consistency
        String collapsed = EXTRA_WHITESPACE.matcher(withoutNumbers).replaceAll(" ").trim();
        return collapsed.toUpperCase(Locale.ENGLISH);
    }
}

