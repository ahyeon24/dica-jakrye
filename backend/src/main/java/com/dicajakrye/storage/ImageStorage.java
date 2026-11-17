package com.dicajakrye.storage;

/**
 * 이미지 파일 저장소. 로컬 개발은 폴더(LocalImageStorage), 운영은 Cloudflare R2.
 * 공개(PUBLIC): 썸네일·보기용, img.{도메인}으로 누구나 받을 수 있음
 * 비공개(PRIVATE): 사용자 업로드 원본, 외부 접근 불가 (docs/ARCHITECTURE.md 3.1)
 */
public interface ImageStorage {

    enum Visibility { PUBLIC, PRIVATE }

    void put(Visibility visibility, String key, byte[] data, String contentType);

    /** 없는 파일을 지워도 오류를 내지 않는다 */
    void delete(Visibility visibility, String key);

    boolean exists(Visibility visibility, String key);
}
