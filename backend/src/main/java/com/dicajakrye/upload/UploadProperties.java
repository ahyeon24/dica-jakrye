package com.dicajakrye.upload;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param maxPerHour    한 사람이 1시간에 올릴 수 있는 장수 (API.md, 운영하며 조정)
 * @param maxConcurrent 이 서버에서 동시에 처리할 업로드 수. 2GB 서버에서 큰 사진 여러 장을 한꺼번에 열지 않게 (SECURITY.md 5장)
 */
@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(int maxPerHour, int maxConcurrent) {
}
