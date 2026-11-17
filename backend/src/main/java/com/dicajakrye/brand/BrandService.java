package com.dicajakrye.brand;

import com.dicajakrye.brand.BrandResponses.BrandDetail;
import com.dicajakrye.brand.BrandResponses.BrandItem;
import com.dicajakrye.brand.BrandResponses.CameraItem;
import com.dicajakrye.camera.CameraQueries;
import com.dicajakrye.common.ApiException;
import com.dicajakrye.common.ErrorCode;
import com.dicajakrye.common.ImageUrls;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BrandService {

    private final BrandRepository brands;
    private final CameraQueries cameraQueries;
    private final JdbcClient jdbc;
    private final ImageUrls imageUrls;

    public BrandService(BrandRepository brands, CameraQueries cameraQueries, JdbcClient jdbc, ImageUrls imageUrls) {
        this.brands = brands;
        this.cameraQueries = cameraQueries;
        this.jdbc = jdbc;
        this.imageUrls = imageUrls;
    }

    /** 브랜드 목록: 기종이 많은 브랜드부터 */
    public List<BrandItem> list() {
        return jdbc.sql("""
                        SELECT b.slug, b.name, b.name_ko, count(c.id) AS camera_count
                          FROM brand b LEFT JOIN camera c ON c.brand_id = b.id
                         GROUP BY b.id
                         ORDER BY camera_count DESC, b.name
                        """)
                .query(BrandItem.class)
                .list();
    }

    /** 브랜드 페이지: 기종 수가 적어서(MVP 30개) 페이지네이션 없이 전부 */
    public BrandDetail detail(String slug) {
        Brand brand = brands.findBySlug(slug).orElseThrow(() -> new ApiException(ErrorCode.BRAND_NOT_FOUND));
        List<CameraItem> cameras = cameraQueries.summariesByBrand(brand.getId()).stream()
                .map(r -> new CameraItem(r.slug(), r.name(), r.nameKo(), r.releaseYear(), r.photoCount(),
                        imageUrls.publicUrl(r.coverThumbnailKey())))
                .toList();
        return new BrandDetail(brand.getSlug(), brand.getName(), brand.getNameKo(), cameras);
    }
}
