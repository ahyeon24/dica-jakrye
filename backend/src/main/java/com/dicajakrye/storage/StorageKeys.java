package com.dicajakrye.storage;

import java.util.regex.Pattern;

/**
 * 저장 키 규칙. 키는 서버가 만들고, 사용자가 보낸 파일명은 쓰지 않는다. (docs/SECURITY.md 5장)
 * 소문자·숫자와 / . _ - 만 허용하고, ".."이나 "/"로 시작하는 키는 거부한다. (경로 조작 방지)
 */
public final class StorageKeys {

    private static final Pattern ALLOWED = Pattern.compile("[a-z0-9][a-z0-9/._-]{0,250}");

    private StorageKeys() {
    }

    public static String commonsDisplay(long commonsPageId) {
        return "commons/display/" + commonsPageId + ".jpg";
    }

    public static String commonsThumbnail(long commonsPageId) {
        return "commons/thumb/" + commonsPageId + ".jpg";
    }

    /** 사용자 업로드: 파일 이름은 서버가 만든 무작위 id (추측해서 다른 파일을 찾을 수 없게) */
    public static String uploadDisplay(String fileId) {
        return "uploads/display/" + fileId + ".jpg";
    }

    public static String uploadThumbnail(String fileId) {
        return "uploads/thumb/" + fileId + ".jpg";
    }

    /** 비공개 저장소의 원본 (GPS만 지운 파일) */
    public static String uploadOriginal(String fileId) {
        return "originals/" + fileId + ".jpg";
    }

    public static String requireValid(String key) {
        if (key == null || !ALLOWED.matcher(key).matches() || key.contains("..") || key.contains("//")) {
            throw new IllegalArgumentException("허용되지 않는 저장 키: " + key);
        }
        return key;
    }
}
