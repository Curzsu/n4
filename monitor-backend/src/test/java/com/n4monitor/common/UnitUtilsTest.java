package com.n4monitor.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnitUtilsTest {

    @Test
    void testNormalizeUnitNullOrBlank() {
        assertThat(UnitUtils.normalizeUnit(null)).isNull();
        assertThat(UnitUtils.normalizeUnit("")).isNull();
        assertThat(UnitUtils.normalizeUnit("   ")).isNull();
    }

    @Test
    void testNormalizeUnitNormalUnits() {
        assertThat(UnitUtils.normalizeUnit("kW")).isEqualTo("kW");
        assertThat(UnitUtils.normalizeUnit("V")).isEqualTo("V");
        assertThat(UnitUtils.normalizeUnit("A")).isEqualTo("A");
        assertThat(UnitUtils.normalizeUnit("\u00B0C")).isEqualTo("\u00B0C");
    }

    @Test
    void testNormalizeUnitCorruptedDegreeCelsius() {
        // \u64B8C and \u63B3C represent the GBK decodes of UTF-8 degree sign bytes followed by 'C'
        assertThat(UnitUtils.normalizeUnit("\u64B8C")).isEqualTo("\u00B0C");
        assertThat(UnitUtils.normalizeUnit("\u63B3C")).isEqualTo("\u00B0C");
        assertThat(UnitUtils.normalizeUnit("?C")).isEqualTo("\u00B0C");
        assertThat(UnitUtils.normalizeUnit("\uFFFDC")).isEqualTo("\u00B0C");
        assertThat(UnitUtils.normalizeUnit(" \u64B8c ")).isEqualTo("\u00B0C");
        assertThat(UnitUtils.normalizeUnit(" \u63B3c ")).isEqualTo("\u00B0C");
    }
}
