package com.dicajakrye.commons;

import java.util.List;

/** Wikimedia Commons 접근. 테스트에서는 가짜 구현으로 바꾼다. */
public interface CommonsClient {

    /** "Category:Taken with {category}" 분류의 파일을 최대 max개 */
    List<CommonsFile> listCategoryFiles(String category, int max);

    byte[] download(String url);

    /** 파일 페이지가 아직 있는지 (정리 배치용). 없어진 pageId만 돌려준다 */
    List<Long> missingPageIds(List<Long> pageIds);

    class CommonsUnavailableException extends RuntimeException {
        public CommonsUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
