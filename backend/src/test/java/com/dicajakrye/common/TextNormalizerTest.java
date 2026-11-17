package com.dicajakrye.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 시드 생성기(backend/scripts/gen_seed_cameras.py)의 NORMALIZE_EXAMPLES와 같은 예시.
 * 한쪽 규칙을 바꾸면 다른 쪽도 같이 바꾸고 이 테스트와 생성기 검사가 모두 통과해야 한다.
 */
class TextNormalizerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "'Canon DIGITAL IXUS 30\u0000\u0000' | 'canon digital ixus 30'",
            "'  <KENOX S630  / Samsung S630> ' | '<kenox s630 / samsung s630>'",
            "'OLYMPUS IMAGING CORP.' | 'olympus imaging corp.'",
    })
    void exif(String raw, String expected) {
        assertThat(TextNormalizer.exif(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "'PowerShot SD200' | 'powershotsd200'",
            "'Cyber-shot DSC-T1' | 'cybershotdsct1'",
            "'익서스 30' | '익서스30'",
    })
    void alias(String raw, String expected) {
        assertThat(TextNormalizer.alias(raw)).isEqualTo(expected);
    }
}
