package com.dicajakrye.upload;

import static com.dicajakrye.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.dicajakrye.TestSessions;
import com.dicajakrye.TestcontainersConfiguration;
import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.util.FileSystemUtils;

/**
 * 작례 올리기 통합 테스트. 저장된 파일을 직접 열어 EXIF·GPS가 지워졌는지까지 확인한다.
 * 파일 저장과 DB 기록이 함께 일어나므로 @Transactional 대신 테스트마다 직접 정리한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class UploadApiTest {

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.storage.local.dir", () -> storageDir.toString());
        r.add("app.upload.max-per-hour", () -> "3");
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @SuppressWarnings("rawtypes")
    @Autowired
    SessionRepository sessions;

    @AfterEach
    void cleanUp() throws Exception {
        jdbc.sql("DELETE FROM photo WHERE source = 'UPLOAD'").update();
        jdbc.sql("DELETE FROM terms_agreement").update();
        jdbc.sql("DELETE FROM users").update();
        jdbc.sql("DELETE FROM spring_session").update();
        FileSystemUtils.deleteRecursively(storageDir.resolve("public"));
        FileSystemUtils.deleteRecursively(storageDir.resolve("private"));
    }

    @Test
    void 디카_사진을_올리면_기종을_찾고_공개_파일은_EXIF를_모두_지우고_원본은_GPS만_지운다() throws Exception {
        Cookie me = login("디카러버");
        // 세로로 찍은 400x300 사진 (Orientation 6), GPS 있음
        byte[] jpeg = UploadTestImages.digicamJpeg(400, 300, "Canon", "Canon DIGITAL IXUS 30", 6, true);

        MvcTestResult r = upload(me, jpeg, true);

        assertThat(r).hasStatus(201);
        assertThat(r).bodyJson().extractingPath("$.source").isEqualTo("UPLOAD");
        assertThat(r).bodyJson().extractingPath("$.camera.slug").isEqualTo("canon-ixus-30");
        assertThat(r).bodyJson().extractingPath("$.width").isEqualTo(300);   // 돌린 뒤 기준
        assertThat(r).bodyJson().extractingPath("$.height").isEqualTo(400);
        assertThat(r).bodyJson().extractingPath("$.isOriginalSize").isEqualTo(false); // 원래 2048x1536
        assertThat(r).bodyJson().extractingPath("$.exif.flashFired").isEqualTo(true);
        assertThat(r).bodyJson().extractingPath("$.exif.exposureTime").isEqualTo("1/60");
        assertThat(r).bodyJson().extractingPath("$.exif.takenAt").isEqualTo("2006-05-24T14:10:05");
        assertThat(r).bodyJson().extractingPath("$.uploader.nickname").isEqualTo("디카러버");
        assertThat(r).bodyJson().extractingPath("$.isMine").isEqualTo(true);

        var keys = jdbc.sql("SELECT display_key, thumbnail_key, original_key FROM photo WHERE source = 'UPLOAD'")
                .query((rs, i) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}).single();
        byte[] display = Files.readAllBytes(storageDir.resolve("public").resolve(keys[0]));
        byte[] thumb = Files.readAllBytes(storageDir.resolve("public").resolve(keys[1]));
        byte[] original = Files.readAllBytes(storageDir.resolve("private").resolve(keys[2]));

        // 공개 파일: EXIF 없음, 세로로 돌아가 있음
        assertThat(hasExif(display)).isFalse();
        assertThat(hasExif(thumb)).isFalse();
        BufferedImage shown = ImageIO.read(new ByteArrayInputStream(display));
        assertThat(shown.getWidth()).isEqualTo(300);
        assertThat(shown.getHeight()).isEqualTo(400);
        // 원본: GPS 없음, 기종 정보는 남음, 공개 저장소에는 없음
        Metadata meta = JpegMetadataReader.readMetadata(new ByteArrayInputStream(original));
        assertThat(meta.getFirstDirectoryOfType(GpsDirectory.class)).isNull();
        assertThat(meta.getFirstDirectoryOfType(ExifIFD0Directory.class).getString(ExifIFD0Directory.TAG_MODEL))
                .isEqualTo("Canon DIGITAL IXUS 30");
        assertThat(Files.exists(storageDir.resolve("public").resolve(keys[2]))).isFalse();
        // 저장 키는 서버가 만든 이름 (사용자 파일명 아님)
        assertThat(keys[0]).startsWith("uploads/display/").doesNotContain("my-photo");
    }

    @Test
    void 등록_안_된_기종은_미분류로_올라간다() {
        Cookie me = login("디카러버");
        byte[] jpeg = UploadTestImages.digicamJpeg(400, 300, "SAMSUNG", "Digimax V700", 1, false);

        MvcTestResult r = upload(me, jpeg, true);

        assertThat(r).hasStatus(201);
        assertThat(r).bodyJson().extractingPath("$.camera").isNull();
        assertThat(r).bodyJson().extractingPath("$.isOriginalSize").isNull();
        assertThat(jdbc.sql("SELECT exif_model FROM photo WHERE source = 'UPLOAD'").query(String.class).single())
                .isEqualTo("digimax v700");
    }

    @Test
    void 막아야_하는_파일들() {
        Cookie me = login("디카러버");

        assertThat(upload(me, UploadTestImages.plainJpeg(400, 300), true))
                .bodyJson().extractingPath("$.code").isEqualTo("EXIF_NOT_FOUND");
        assertThat(upload(me, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0}, true))
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_IMAGE");
        assertThat(upload(me, UploadTestImages.digicamJpeg(400, 300, "Canon", "Canon DIGITAL IXUS 30", 1, false), false))
                .bodyJson().extractingPath("$.code").isEqualTo("UPLOAD_POLICY_NOT_AGREED");
        MvcTestResult tooBig = upload(me, new byte[(int) UploadService.MAX_FILE_BYTES + 1], true);
        assertThat(tooBig).hasStatus(413);
        assertThat(tooBig).bodyJson().extractingPath("$.code").isEqualTo("FILE_TOO_LARGE");

        assertThat(jdbc.sql("SELECT count(*) FROM photo WHERE source = 'UPLOAD'").query(Integer.class).single()).isZero();
    }

    @Test
    void 로그인과_CSRF_토큰이_필요하다() {
        byte[] jpeg = UploadTestImages.digicamJpeg(400, 300, "Canon", "Canon DIGITAL IXUS 30", 1, false);
        MvcTestResult noLogin = mvc.post().uri("/api/photos").multipart()
                .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", jpeg))
                .param("agreedToUploadPolicy", "true").with(csrf()).exchange();
        assertThat(noLogin).hasStatus(401);

        MvcTestResult noCsrf = mvc.post().uri("/api/photos").multipart()
                .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", jpeg))
                .param("agreedToUploadPolicy", "true").cookie(login("디카러버")).exchange();
        assertThat(noCsrf).hasStatus(403);
    }

    @Test
    void 한_시간에_올릴_수_있는_장수를_넘으면_429() {
        Cookie me = login("디카러버");
        byte[] jpeg = UploadTestImages.digicamJpeg(200, 150, "Canon", "Canon DIGITAL IXUS 30", 1, false);
        for (int i = 0; i < 3; i++) {
            assertThat(upload(me, jpeg, true)).hasStatus(201);
        }
        // 지운 작례도 센다
        jdbc.sql("UPDATE photo SET deleted_at = now() WHERE source = 'UPLOAD'").update();

        MvcTestResult r = upload(me, jpeg, true);
        assertThat(r).hasStatus(429);
        assertThat(r).bodyJson().extractingPath("$.code").isEqualTo("UPLOAD_RATE_LIMITED");
    }

    @Test
    void 업로드_전_기종_미리보기() {
        Cookie me = login("디카러버");
        assertThat(mvc.get().uri("/api/cameras/exif-match").param("make", "Canon")
                .param("model", "Canon DIGITAL IXUS 30\0").cookie(me).exchange())
                .bodyJson().extractingPath("$.camera.slug").isEqualTo("canon-ixus-30");
        assertThat(mvc.get().uri("/api/cameras/exif-match").param("make", "Apple")
                .param("model", "iPhone 15").cookie(me).exchange())
                .bodyJson().extractingPath("$.matched").isEqualTo(false);
        assertThat(mvc.get().uri("/api/cameras/exif-match").param("make", "Canon")
                .param("model", "x").exchange()).hasStatus(401);
    }

    @Test
    void 내_작례를_지우면_화면에서_사라지고_파일도_지워진다() throws Exception {
        Cookie me = login("디카러버");
        long id = uploadedId(upload(me, UploadTestImages.digicamJpeg(400, 300, "Canon", "Canon DIGITAL IXUS 30", 1, false), true));
        var keys = jdbc.sql("SELECT display_key, thumbnail_key, original_key FROM photo WHERE id = :id").param("id", id)
                .query((rs, i) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}).single();

        assertThat(mvc.delete().uri("/api/photos/{id}", id).cookie(me).with(csrf()).exchange()).hasStatus(204);

        assertThat(mvc.get().uri("/api/photos/{id}", id).exchange()).hasStatus(404);
        assertThat(mvc.get().uri("/api/cameras/canon-ixus-30/photos").exchange())
                .bodyJson().extractingPath("$.items[?(@.id == " + id + ")]").asArray().isEmpty();
        assertThat(Files.exists(storageDir.resolve("public").resolve(keys[0]))).isFalse();
        assertThat(Files.exists(storageDir.resolve("public").resolve(keys[1]))).isFalse();
        assertThat(Files.exists(storageDir.resolve("private").resolve(keys[2]))).isFalse();
        // 신고 기록이 참조할 수 있어 행은 남기고 deleted_at만 채운다
        assertThat(jdbc.sql("SELECT deleted_at IS NOT NULL FROM photo WHERE id = :id").param("id", id)
                .query(Boolean.class).single()).isTrue();
        // 두 번 지우면 404
        assertThat(mvc.delete().uri("/api/photos/{id}", id).cookie(me).with(csrf()).exchange()).hasStatus(404);
    }

    @Test
    void 남의_작례와_Commons_작례는_지울_수_없다() {
        Cookie owner = login("디카러버");
        Cookie other = login("똑딱이");
        long id = uploadedId(upload(owner, UploadTestImages.digicamJpeg(400, 300, "Canon", "Canon DIGITAL IXUS 30", 1, false), true));

        MvcTestResult r = mvc.delete().uri("/api/photos/{id}", id).cookie(other).with(csrf()).exchange();
        assertThat(r).hasStatus(403);
        assertThat(r).bodyJson().extractingPath("$.code").isEqualTo("NOT_PHOTO_OWNER");
        assertThat(mvc.get().uri("/api/photos/{id}", id).exchange()).hasStatusOk();

        long commonsId = jdbc.sql("""
                        INSERT INTO photo (source, camera_id, display_key, thumbnail_key)
                        VALUES ('COMMONS', (SELECT id FROM camera WHERE slug = 'canon-ixus-30'), 'commons/display/1.jpg', 'commons/thumb/1.jpg')
                        RETURNING id
                        """).query(Long.class).single();
        try {
            assertThat(mvc.delete().uri("/api/photos/{id}", commonsId).cookie(owner).with(csrf()).exchange()).hasStatus(403);
        } finally {
            jdbc.sql("DELETE FROM photo WHERE id = :id").param("id", commonsId).update();
        }

        assertThat(mvc.delete().uri("/api/photos/{id}", id).with(csrf()).exchange()).hasStatus(401);
        assertThat(mvc.delete().uri("/api/photos/{id}", id).cookie(owner).exchange()).hasStatus(403); // CSRF 없음
    }

    // ---------- 도우미 ----------

    private Cookie login(String nickname) {
        long userId = jdbc.sql("INSERT INTO users (provider, provider_user_id, nickname) VALUES ('GOOGLE', :n, :n) RETURNING id")
                .param("n", nickname).query(Long.class).single();
        return TestSessions.loggedIn(sessions, userId);
    }

    private long uploadedId(MvcTestResult r) {
        assertThat(r).hasStatus(201);
        try {
            return Long.parseLong(com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.id").toString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult upload(Cookie session, byte[] file, boolean agreed) {
        return mvc.post().uri("/api/photos").multipart()
                .file(new MockMultipartFile("file", "../../my-photo.jpg", "image/jpeg", file))
                .param("agreedToUploadPolicy", String.valueOf(agreed))
                .cookie(session).with(csrf()).exchange();
    }

    /** JPEG 안에 EXIF(APP1 "Exif") 블록이 있는지 */
    private static boolean hasExif(byte[] jpeg) {
        for (int i = 0; i + 4 < jpeg.length; i++) {
            if (jpeg[i] == 'E' && jpeg[i + 1] == 'x' && jpeg[i + 2] == 'i' && jpeg[i + 3] == 'f' && jpeg[i + 4] == 0) {
                return true;
            }
        }
        return false;
    }
}
