package com.dicajakrye.commons;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Commons 수집 설정. (application.yml app.commons)
 * 요청 예절과 수량 기준은 docs/ARCHITECTURE.md 3.3
 *
 * @param apiUrl          Commons API 주소
 * @param userAgent       위키미디어 규칙: 서비스 이름과 연락처를 넣는다
 * @param requestDelay    API 요청 사이 대기
 * @param downloadDelay   이미지 다운로드 사이 대기 (1초에 1장 이하)
 * @param poolSize        분류마다 후보로 받아 볼 최대 파일 수
 * @param perCamera       기종마다 최대 작례 수
 * @param perAuthor       기종마다 한 작가에게서 가져올 최대 장수
 * @param minLongSide     이보다 작으면 버린다 (px)
 * @param camerasPerRun   한 번 실행에 처리할 기종 수
 */
@ConfigurationProperties("app.commons")
public record CommonsProperties(
        String apiUrl,
        String userAgent,
        Duration requestDelay,
        Duration downloadDelay,
        int poolSize,
        int perCamera,
        int perAuthor,
        int minLongSide,
        int camerasPerRun
) {
}
