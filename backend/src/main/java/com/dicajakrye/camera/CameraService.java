package com.dicajakrye.camera;

import com.dicajakrye.camera.CameraQueries.AliasMatchRow;
import com.dicajakrye.camera.CameraQueries.CameraSummaryRow;
import com.dicajakrye.camera.CameraQueries.PhotoCountsRow;
import com.dicajakrye.camera.CameraResponses.BrandRef;
import com.dicajakrye.camera.CameraResponses.CameraDetail;
import com.dicajakrye.camera.CameraResponses.SearchItem;
import com.dicajakrye.camera.CameraResponses.SearchResult;
import com.dicajakrye.common.ApiException;
import com.dicajakrye.common.Cursor;
import com.dicajakrye.common.ErrorCode;
import com.dicajakrye.common.ImageUrls;
import com.dicajakrye.common.PageResponse;
import com.dicajakrye.common.TextNormalizer;
import com.dicajakrye.photo.PhotoQueries;
import com.dicajakrye.photo.PhotoQueries.PhotoSummaryRow;
import com.dicajakrye.photo.PhotoSummaryResponse;
import com.dicajakrye.photo.PhotoSummaryResponse.CameraRef;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CameraService {

    public static final int MAX_QUERY_LENGTH = 50;
    public static final int DEFAULT_PAGE_SIZE = 24;
    public static final int MAX_PAGE_SIZE = 48;

    private final CameraRepository cameras;
    private final CameraQueries cameraQueries;
    private final PhotoQueries photoQueries;
    private final ImageUrls imageUrls;

    public CameraService(CameraRepository cameras, CameraQueries cameraQueries, PhotoQueries photoQueries,
                         ImageUrls imageUrls) {
        this.cameras = cameras;
        this.cameraQueries = cameraQueries;
        this.photoQueries = photoQueries;
        this.imageUrls = imageUrls;
    }

    // ---------- 검색 ----------

    /** 일치 정도. 숫자가 작을수록 앞에 온다 */
    private enum Match { EXACT, PREFIX, CONTAINS, BRAND }

    private record Hit(long cameraId, Match match, String alias, String normalized) {
    }

    /**
     * 기종 검색. 대표 이름·한글 이름·별칭을 같은 정규화 규칙으로 비교한다.
     * 정렬: 정확히 일치 > 앞부분 일치 > 포함 > 브랜드 이름 일치, 같으면 작례 많은 순, 이름 순
     */
    public SearchResult search(String q, int limit) {
        if (q == null || q.isBlank() || q.length() > MAX_QUERY_LENGTH || limit < 1 || limit > 50) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        String nq = TextNormalizer.alias(q);
        if (nq.isEmpty()) {
            return new SearchResult(List.of());
        }

        Map<Long, Hit> best = new HashMap<>();
        for (AliasMatchRow row : cameraQueries.aliasesContaining(nq)) {
            Match m = row.normalized().equals(nq) ? Match.EXACT
                    : row.normalized().startsWith(nq) ? Match.PREFIX
                    : Match.CONTAINS;
            best.merge(row.cameraId(), new Hit(row.cameraId(), m, row.alias(), row.normalized()),
                    (a, b) -> b.match().ordinal() < a.match().ordinal() ? b : a);
        }
        for (Long id : cameraQueries.cameraIdsByBrandName(nq)) {
            best.putIfAbsent(id, new Hit(id, Match.BRAND, null, null));
        }
        if (best.isEmpty()) {
            return new SearchResult(List.of());
        }

        Map<Long, CameraSummaryRow> summaries = cameraQueries.summariesByIds(best.keySet()).stream()
                .collect(Collectors.toMap(CameraSummaryRow::id, Function.identity()));

        List<SearchItem> items = best.values().stream()
                .filter(h -> summaries.containsKey(h.cameraId()))
                .sorted(Comparator.<Hit>comparingInt(h -> h.match().ordinal())
                        .thenComparing(h -> -summaries.get(h.cameraId()).photoCount())
                        .thenComparing(h -> summaries.get(h.cameraId()).name()))
                .limit(limit)
                .map(h -> {
                    CameraSummaryRow s = summaries.get(h.cameraId());
                    return new SearchItem(s.slug(), s.name(), s.nameKo(), s.brandName(),
                            matchedAlias(h, s), s.photoCount(), imageUrls.publicUrl(s.coverThumbnailKey()));
                })
                .toList();
        return new SearchResult(items);
    }

    /** 대표 이름이나 한글 이름으로 찾았으면 null (화면에 "○○로 찾음"을 띄우지 않는다) */
    private static String matchedAlias(Hit h, CameraSummaryRow s) {
        if (h.alias() == null) {
            return null;
        }
        boolean isOwnName = h.normalized().equals(TextNormalizer.alias(s.name()))
                || (s.nameKo() != null && h.normalized().equals(TextNormalizer.alias(s.nameKo())));
        return isOwnName ? null : h.alias();
    }

    // ---------- 기종 정보 ----------

    public CameraDetail detail(String slug) {
        Camera c = findCamera(slug);
        String ownName = TextNormalizer.alias(c.getName());
        String ownKo = c.getNameKo() == null ? null : TextNormalizer.alias(c.getNameKo());
        List<String> aliases = cameraQueries.aliases(c.getId()).stream()
                .filter(a -> {
                    String n = TextNormalizer.alias(a);
                    return !n.equals(ownName) && !Objects.equals(n, ownKo);
                })
                .toList();
        PhotoCountsRow counts = cameraQueries.photoCounts(c.getId());
        return new CameraDetail(c.getSlug(), c.getName(), c.getNameKo(),
                new BrandRef(c.getBrand().getSlug(), c.getBrand().getName(), c.getBrand().getNameKo()),
                c.getReleaseYear(), c.getSensorType(), aliases,
                counts.total(), counts.flash(), counts.originalSize(), c.nativeResolution());
    }

    // ---------- 기종 작례 목록 ----------

    public PageResponse<PhotoSummaryResponse> photos(String slug, Boolean flash, boolean originalOnly,
                                                     String cursorValue, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        Camera c = findCamera(slug);
        Cursor cursor = Cursor.decode(cursorValue);

        List<PhotoSummaryRow> rows = photoQueries.byCamera(c.getId(), flash, originalOnly, cursor, size + 1);
        boolean hasNext = rows.size() > size;
        List<PhotoSummaryRow> page = hasNext ? rows.subList(0, size) : rows;

        CameraRef ref = new CameraRef(c.getSlug(), c.getName(), c.getNameKo());
        List<PhotoSummaryResponse> items = page.stream()
                .map(r -> new PhotoSummaryResponse(r.id(), r.source(), imageUrls.publicUrl(r.thumbnailKey()),
                        r.width(), r.height(), r.flashFired(), r.isOriginalSize(), ref, r.credit()))
                .toList();

        String next = null;
        if (hasNext) {
            PhotoSummaryRow last = page.getLast();
            next = Cursor.of(last.createdAt().toInstant(), last.id()).encode();
        }
        return new PageResponse<>(items, next);
    }

    private Camera findCamera(String slug) {
        return cameras.findBySlugWithBrand(slug).orElseThrow(() -> new ApiException(ErrorCode.CAMERA_NOT_FOUND));
    }
}
