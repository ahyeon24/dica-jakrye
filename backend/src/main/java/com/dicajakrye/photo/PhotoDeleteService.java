package com.dicajakrye.photo;

import com.dicajakrye.common.ApiException;
import com.dicajakrye.common.ErrorCode;
import com.dicajakrye.storage.ImageStorage;
import com.dicajakrye.storage.ImageStorage.Visibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 작례 삭제 (API.md 5장, ERD.md 1장)
 * - DB는 지우지 않고 deleted_at을 채운다 (신고 기록이 작례를 참조하므로). 화면과 API에서는 바로 사라진다.
 * - 파일(보기용·썸네일·원본)은 실제로 지운다.
 * - DB를 먼저 바꾸고 파일을 지운다. 파일 삭제가 실패해도 작례는 이미 안 보이고, 남은 파일은 정리 배치가 지운다.
 */
@Service
public class PhotoDeleteService {

    private static final Logger log = LoggerFactory.getLogger(PhotoDeleteService.class);

    private final JdbcClient jdbc;
    private final ImageStorage storage;
    private final TransactionTemplate tx;

    public PhotoDeleteService(JdbcClient jdbc, ImageStorage storage, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.tx = tx;
    }

    record Target(long id, Long uploaderId, String displayKey, String thumbnailKey, String originalKey) {
    }

    /** 본인이 올린 작례만. Commons 작례는 올린 사람이 없으므로 여기서는 지울 수 없다 (관리자 처리) */
    public void deleteOwn(long photoId, long userId) {
        Target t = tx.execute(status -> {
            Target found = jdbc.sql("""
                            SELECT id, uploader_id, display_key, thumbnail_key, original_key
                              FROM photo WHERE id = :id AND deleted_at IS NULL
                               FOR UPDATE
                            """)
                    .param("id", photoId)
                    .query(Target.class)
                    .optional()
                    .orElseThrow(() -> new ApiException(ErrorCode.PHOTO_NOT_FOUND));
            if (found.uploaderId() == null || found.uploaderId() != userId) {
                throw new ApiException(ErrorCode.NOT_PHOTO_OWNER);
            }
            jdbc.sql("UPDATE photo SET deleted_at = now(), updated_at = now() WHERE id = :id")
                    .param("id", photoId)
                    .update();
            return found;
        });
        deleteFiles(t);
    }

    /**
     * 관리자 삭제 (신고 처리, Commons 저작권자 요청). 올린 사람 확인을 하지 않는다.
     */
    public void deleteAsAdmin(long photoId) {
        Target t = tx.execute(status -> markDeleted(photoId));
        deleteFiles(t);
    }

    Target markDeleted(long photoId) {
        Target found = jdbc.sql("""
                        SELECT id, uploader_id, display_key, thumbnail_key, original_key
                          FROM photo WHERE id = :id AND deleted_at IS NULL
                           FOR UPDATE
                        """)
                .param("id", photoId)
                .query(Target.class)
                .optional()
                .orElseThrow(() -> new ApiException(ErrorCode.PHOTO_NOT_FOUND));
        jdbc.sql("UPDATE photo SET deleted_at = now(), updated_at = now() WHERE id = :id")
                .param("id", photoId)
                .update();
        return found;
    }

    private void deleteFiles(Target t) {
        try {
            storage.delete(Visibility.PUBLIC, t.displayKey());
            storage.delete(Visibility.PUBLIC, t.thumbnailKey());
            if (t.originalKey() != null) {
                storage.delete(Visibility.PRIVATE, t.originalKey());
            }
        } catch (RuntimeException e) {
            log.warn("작례 {} 파일 삭제 실패 (정리 배치가 지움): {}", t.id(), e.toString());
        }
    }
}
