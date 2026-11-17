package com.dicajakrye.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로컬 개발용 저장소. {dir}/public, {dir}/private 폴더에 파일로 저장한다.
 * 공개 파일은 local 프로필에서 /dev-images/** 로 볼 수 있다. (DevImageConfig)
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalImageStorage implements ImageStorage {

    private final Path root;

    public LocalImageStorage(@Value("${app.storage.local.dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    public Path publicRoot() {
        return root.resolve("public");
    }

    @Override
    public void put(Visibility visibility, String key, byte[] data, String contentType) {
        Path target = resolve(visibility, key);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(tmp, data);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(Visibility visibility, String key) {
        try {
            Files.deleteIfExists(resolve(visibility, key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean exists(Visibility visibility, String key) {
        return Files.exists(resolve(visibility, key));
    }

    private Path resolve(Visibility visibility, String key) {
        StorageKeys.requireValid(key);
        Path base = root.resolve(visibility == Visibility.PUBLIC ? "public" : "private");
        Path p = base.resolve(key).normalize();
        if (!p.startsWith(base)) {
            throw new IllegalArgumentException("저장소 밖 경로: " + key);
        }
        return p;
    }
}
