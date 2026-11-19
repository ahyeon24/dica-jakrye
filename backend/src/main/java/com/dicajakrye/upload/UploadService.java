package com.dicajakrye.upload;

import com.dicajakrye.common.ApiException;
import com.dicajakrye.common.ErrorCode;
import com.dicajakrye.image.ImageProcessor;
import com.dicajakrye.image.ImageProcessor.ImageRejectedException;
import com.dicajakrye.image.ImageProcessor.Processed;
import com.dicajakrye.photo.PhotoDetailResponse;
import com.dicajakrye.photo.PhotoService;
import com.dicajakrye.storage.ImageStorage;
import com.dicajakrye.storage.ImageStorage.Visibility;
import com.dicajakrye.storage.StorageKeys;
import com.dicajakrye.upload.CameraMatcher.Match;
import com.dicajakrye.upload.UploadPhotoWriter.NewPhoto;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 작례 올리기 (ARCHITECTURE.md 3.1, SECURITY.md 5장)
 * 1. 내용으로 JPEG인지 확인 → 헤더에서 크기 확인(4,000만 화소 초과 거부)
 * 2. EXIF 읽기 (제조사·모델 없으면 거부) → 기종 찾기: 모델명, 그다음 캐논 내부 번호 (없으면 미분류)
 * 3. 보기용·썸네일: 새로 인코딩해서 EXIF 전부 없음, 공개 저장소
 *    원본: GPS·XMP만 지움, 비공개 저장소
 * 4. DB 저장. 실패하면 올린 파일을 지운다
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);
    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final double ORIGINAL_SIZE_RATIO = 0.95;

    private final ExifReader exifReader;
    private final CameraMatcher cameraMatcher;
    private final ImageProcessor imageProcessor;
    private final GpsRemover gpsRemover;
    private final ImageStorage storage;
    private final UploadPhotoWriter writer;
    private final PhotoService photoService;
    private final UploadProperties props;
    /** 이 서버의 동시 처리 수 제한. 기억해야 하는 값이 아니라 서버 자원 보호용이라 서버마다 따로 둬도 된다 */
    private final Semaphore slots;

    public UploadService(ExifReader exifReader, CameraMatcher cameraMatcher, ImageProcessor imageProcessor,
                         GpsRemover gpsRemover, ImageStorage storage, UploadPhotoWriter writer,
                         PhotoService photoService, UploadProperties props) {
        this.exifReader = exifReader;
        this.cameraMatcher = cameraMatcher;
        this.imageProcessor = imageProcessor;
        this.gpsRemover = gpsRemover;
        this.storage = storage;
        this.writer = writer;
        this.photoService = photoService;
        this.props = props;
        this.slots = new Semaphore(Math.max(1, props.maxConcurrent()), true);
    }

    public PhotoDetailResponse upload(long userId, byte[] file, boolean agreedToUploadPolicy) {
        if (!agreedToUploadPolicy) {
            throw new ApiException(ErrorCode.UPLOAD_POLICY_NOT_AGREED);
        }
        if (file == null || file.length == 0) {
            throw new ApiException(ErrorCode.INVALID_IMAGE);
        }
        if (file.length > MAX_FILE_BYTES) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE);
        }
        if (writer.recentUploadCount(userId) >= props.maxPerHour()) {
            throw new ApiException(ErrorCode.UPLOAD_RATE_LIMITED);
        }
        acquireSlot();
        try {
            long photoId = process(userId, file);
            return photoService.detail(photoId, userId);
        } finally {
            slots.release();
        }
    }

    private long process(long userId, byte[] file) {
        if (!isJpeg(file)) {
            throw new ApiException(ErrorCode.INVALID_IMAGE);
        }
        int[] size;
        try {
            size = ImageProcessor.readSize(file);
        } catch (ImageRejectedException e) {
            throw new ApiException(ErrorCode.INVALID_IMAGE);
        }
        if ((long) size[0] * size[1] > ImageProcessor.MAX_PIXELS) {
            throw new ApiException(ErrorCode.IMAGE_DIMENSIONS_TOO_LARGE);
        }
        ExifInfo exif = exifReader.read(file).orElseThrow(() -> new ApiException(ErrorCode.EXIF_NOT_FOUND));
        Match camera = cameraMatcher.find(exif.makeRaw(), exif.modelRaw(), exif.makerModelId()).orElse(null);

        Processed processed;
        byte[] original;
        try {
            processed = imageProcessor.process(file, exif.orientation());
            original = gpsRemover.strip(file);
        } catch (ImageRejectedException | IllegalStateException e) {
            throw new ApiException(ErrorCode.INVALID_IMAGE);
        }

        String fileId = UUID.randomUUID().toString();
        String displayKey = StorageKeys.uploadDisplay(fileId);
        String thumbKey = StorageKeys.uploadThumbnail(fileId);
        String originalKey = StorageKeys.uploadOriginal(fileId);
        try {
            storage.put(Visibility.PRIVATE, originalKey, original, "image/jpeg");
            storage.put(Visibility.PUBLIC, displayKey, processed.display(), "image/jpeg");
            storage.put(Visibility.PUBLIC, thumbKey, processed.thumbnail(), "image/jpeg");
            return writer.insert(new NewPhoto(userId, camera == null ? null : camera.cameraId(), exif,
                    processed.sourceWidth(), processed.sourceHeight(),
                    isOriginalSize(camera, processed.sourceWidth(), processed.sourceHeight()),
                    originalKey, displayKey, thumbKey));
        } catch (RuntimeException e) {
            // 저장 도중 실패하면 이미 올린 파일을 남기지 않는다
            storage.delete(Visibility.PRIVATE, originalKey);
            storage.delete(Visibility.PUBLIC, displayKey);
            storage.delete(Visibility.PUBLIC, thumbKey);
            throw e;
        }
    }

    /** 긴 변이 카메라 원래 해상도 긴 변의 95% 이상이면 원본 크기. 기종이나 해상도를 모르면 null */
    static Boolean isOriginalSize(Match camera, int width, int height) {
        if (camera == null || camera.nativeLongSide() == null) {
            return null;
        }
        return Math.max(width, height) >= camera.nativeLongSide() * ORIGINAL_SIZE_RATIO;
    }

    /** 확장자·Content-Type이 아니라 파일 앞부분(FF D8 FF)으로 JPEG인지 본다 */
    static boolean isJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private void acquireSlot() {
        try {
            if (!slots.tryAcquire(30, TimeUnit.SECONDS)) {
                log.warn("업로드 대기 시간 초과 (동시 처리 {}개)", props.maxConcurrent());
                throw new ApiException(ErrorCode.UPLOAD_BUSY);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.UPLOAD_BUSY);
        }
    }
}
