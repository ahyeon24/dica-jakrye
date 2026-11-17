package com.dicajakrye.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * DB에는 R2 객체 키만 저장하고, 응답할 때 공개 이미지 주소(img.{도메인})를 붙인다. (docs/ERD.md 1장)
 * 원본(비공개 버킷) 키는 절대 주소로 만들지 않는다.
 */
@Component
public class ImageUrls {

    private final String baseUrl;

    public ImageUrls(@Value("${app.image.base-url}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    public String publicUrl(String key) {
        return key == null ? null : baseUrl + key;
    }
}
