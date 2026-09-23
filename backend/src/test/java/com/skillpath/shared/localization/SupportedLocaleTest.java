package com.skillpath.shared.localization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SupportedLocaleTest {

    @Test
    void resolvesSupportedAndWeightedLanguageRanges() {
        assertThat(SupportedLocale.resolve("vi-VN")).isEqualTo(SupportedLocale.VIETNAMESE);
        assertThat(SupportedLocale.resolve("en")).isEqualTo(SupportedLocale.ENGLISH);
        assertThat(SupportedLocale.resolve("en;q=0.4, vi-VN;q=0.9"))
                .isEqualTo(SupportedLocale.VIETNAMESE);
    }

    @Test
    void fallsBackToEnglishForAbsentMalformedOrUnsupportedValues() {
        assertThat(SupportedLocale.resolve(null)).isEqualTo(SupportedLocale.ENGLISH);
        assertThat(SupportedLocale.resolve("")).isEqualTo(SupportedLocale.ENGLISH);
        assertThat(SupportedLocale.resolve("not a valid range"))
                .isEqualTo(SupportedLocale.ENGLISH);
        assertThat(SupportedLocale.resolve("fr-FR"))
                .isEqualTo(SupportedLocale.ENGLISH);
    }
}
