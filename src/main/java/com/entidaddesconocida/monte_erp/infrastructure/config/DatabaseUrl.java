package com.entidaddesconocida.monte_erp.infrastructure.config;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Lightweight wrapper around a JDBC URL so the DI container can bind it
 * type-safely instead of using the raw {@link String} type.
 *
 * <p>Using a dedicated type prevents collisions when future components need
 * other {@code String} dependencies (API keys, file paths, etc.).</p>
 */
public record DatabaseUrl(String value) {

    private static final Set<String> SENSITIVE_PARAMETERS = Set.of(
            "user", "username", "password", "passwd", "pwd", "token", "secret",
            "credential", "access_token", "api_key", "clientcertificatekeystorepassword",
            "trustcertificatekeystorepassword");

    /** Returns whether the URL embeds authentication data in its authority or query. */
    public static boolean containsCredentials(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        int authorityStart = value.indexOf("//");
        if (authorityStart >= 0) {
            int authorityEnd = firstIndexAfter(value, authorityStart + 2, '/', '?', '#');
            if (value.substring(authorityStart + 2, authorityEnd).contains("@")) {
                return true;
            }
        }
        int queryStart = value.indexOf('?');
        return queryStart >= 0 && queryParameters(value.substring(queryStart + 1)).stream()
                .anyMatch(parameter -> isSensitive(keyOf(parameter)));
    }

    /** Returns the URL with embedded credentials redacted for user-visible text. */
    public String redacted() {
        if (value == null) {
            return "";
        }
        String redacted = redactAuthority(value);
        int queryStart = redacted.indexOf('?');
        if (queryStart < 0) {
            return redacted;
        }

        StringBuilder result = new StringBuilder(redacted.substring(0, queryStart + 1));
        for (String parameter : queryParameters(redacted.substring(queryStart + 1))) {
            String delimiter = "";
            String body = parameter;
            if (!body.isEmpty() && (body.charAt(body.length() - 1) == '&'
                    || body.charAt(body.length() - 1) == ';')) {
                delimiter = body.substring(body.length() - 1);
                body = body.substring(0, body.length() - 1);
            }
            int equals = body.indexOf('=');
            if (equals >= 0 && isSensitive(body.substring(0, equals))) {
                result.append(body, 0, equals + 1).append("***");
            } else {
                result.append(body);
            }
            result.append(delimiter);
        }
        return result.toString();
    }

    private static String redactAuthority(String value) {
        int authorityStart = value.indexOf("//");
        if (authorityStart < 0) {
            return value;
        }
        int authorityEnd = firstIndexAfter(value, authorityStart + 2, '/', '?', '#');
        int at = value.lastIndexOf('@', authorityEnd);
        if (at < authorityStart + 2) {
            return value;
        }
        return value.substring(0, authorityStart + 2) + "***@" + value.substring(at + 1);
    }

    private static java.util.List<String> queryParameters(String value) {
        return java.util.Arrays.asList(value.split("(?<=[&;])", -1));
    }

    private static String keyOf(String parameter) {
        String body = parameter;
        if (!body.isEmpty() && (body.charAt(body.length() - 1) == '&'
                || body.charAt(body.length() - 1) == ';')) {
            body = body.substring(0, body.length() - 1);
        }
        int equals = body.indexOf('=');
        return equals < 0 ? body : body.substring(0, equals);
    }

    private static boolean isSensitive(String rawKey) {
        String key;
        try {
            key = URLDecoder.decode(rawKey.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            key = rawKey;
        }
        return SENSITIVE_PARAMETERS.contains(key.toLowerCase(Locale.ROOT));
    }

    private static int firstIndexAfter(String value, int start, char... delimiters) {
        int end = value.length();
        for (char delimiter : delimiters) {
            int index = value.indexOf(delimiter, start);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        return end;
    }
}
