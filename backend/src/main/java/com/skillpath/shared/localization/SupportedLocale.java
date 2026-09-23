package com.skillpath.shared.localization;

import java.util.List;
import java.util.Locale;

public enum SupportedLocale {
    VIETNAMESE("vi-VN"),
    ENGLISH("en");

    private static final List<Locale> SUPPORTED = List.of(
            Locale.forLanguageTag(VIETNAMESE.tag), Locale.forLanguageTag(ENGLISH.tag));

    private final String tag;

    SupportedLocale(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }

    public boolean requiresTranslation() {
        return this != ENGLISH;
    }

    public static SupportedLocale resolve(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return ENGLISH;
        }
        try {
            Locale match = Locale.lookup(Locale.LanguageRange.parse(acceptLanguage), SUPPORTED);
            if (match == null) {
                return ENGLISH;
            }
            return match.toLanguageTag().equalsIgnoreCase(VIETNAMESE.tag)
                    ? VIETNAMESE
                    : ENGLISH;
        } catch (IllegalArgumentException exception) {
            return ENGLISH;
        }
    }
}
