package com.dicajakrye.upload;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 업로드 사진에서 읽은 촬영 정보. 제조사·모델은 꼭 있고, 나머지는 없으면 null
 * makerModelId: 제조사 전용 영역의 내부 기종 번호 (지금은 캐논의 Canon Model ID만). 편집 프로그램을 거치면 없다
 */
public record ExifInfo(String makeRaw, String modelRaw, Boolean flashFired, LocalDateTime takenAt,
                       BigDecimal fNumber, String exposureTime, Integer iso, BigDecimal focalLength,
                       int orientation, Long makerModelId) {
}
