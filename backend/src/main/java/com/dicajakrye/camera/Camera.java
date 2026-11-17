package com.dicajakrye.camera;

import com.dicajakrye.brand.Brand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "camera")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Camera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "name_ko", length = 100)
    private String nameKo;

    @Column(nullable = false, length = 120)
    private String slug;

    @Column(name = "release_year")
    private Short releaseYear;

    /** CCD / CMOS */
    @Column(name = "sensor_type", length = 10)
    private String sensorType;

    @Column(name = "native_width")
    private Integer nativeWidth;

    @Column(name = "native_height")
    private Integer nativeHeight;

    @Column(name = "cover_photo_id")
    private Long coverPhotoId;

    @Column(name = "commons_collected_at")
    private Instant commonsCollectedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    /** 예: "2048×1536". 원래 해상도를 모르면 null (원본 크기 필터를 숨긴다) */
    public String nativeResolution() {
        if (nativeWidth == null || nativeHeight == null) {
            return null;
        }
        return nativeWidth + "×" + nativeHeight;
    }
}
