package com.dicajakrye.photo;

import com.dicajakrye.common.ApiException;
import com.dicajakrye.common.Cursor;
import com.dicajakrye.common.ErrorCode;
import com.dicajakrye.common.ImageUrls;
import com.dicajakrye.common.PageResponse;
import com.dicajakrye.photo.PhotoQueries.PhotoListRow;
import com.dicajakrye.photo.PhotoSummaryResponse.CameraRef;
import java.util.List;
import java.util.function.BiFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 여러 기종이 섞인 작례 목록 (홈 최근 작례, 프로필 피드). 페이지 규칙은 기종 목록과 같다 (API.md 1.5) */
@Service
public class PhotoListService {

    public static final int DEFAULT_PAGE_SIZE = 24;
    public static final int MAX_PAGE_SIZE = 48;

    private final PhotoQueries queries;
    private final ImageUrls imageUrls;

    public PhotoListService(PhotoQueries queries, ImageUrls imageUrls) {
        this.queries = queries;
        this.imageUrls = imageUrls;
    }

    @Transactional(readOnly = true)
    public PageResponse<PhotoSummaryResponse> recent(String cursor, int size) {
        return page(cursor, size, queries::recentUploads);
    }

    @Transactional(readOnly = true)
    public PageResponse<PhotoSummaryResponse> byUploader(long uploaderId, String cursor, int size) {
        return page(cursor, size, (c, limit) -> queries.byUploader(uploaderId, c, limit));
    }

    private PageResponse<PhotoSummaryResponse> page(String cursorValue, int size,
                                                    BiFunction<Cursor, Integer, List<PhotoListRow>> fetch) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        List<PhotoListRow> rows = fetch.apply(Cursor.decode(cursorValue), size + 1); // 하나 더 받아 다음 페이지 확인
        boolean hasNext = rows.size() > size;
        List<PhotoListRow> page = hasNext ? rows.subList(0, size) : rows;
        List<PhotoSummaryResponse> items = page.stream()
                .map(r -> new PhotoSummaryResponse(r.id(), r.source(), imageUrls.publicUrl(r.thumbnailKey()),
                        r.width(), r.height(), r.flashFired(), r.isOriginalSize(),
                        r.cameraSlug() == null ? null : new CameraRef(r.cameraSlug(), r.cameraName(), r.cameraNameKo()),
                        r.credit()))
                .toList();
        String next = hasNext ? Cursor.of(page.getLast().createdAt().toInstant(), page.getLast().id()).encode() : null;
        return new PageResponse<>(items, next);
    }
}
