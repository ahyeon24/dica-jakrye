package com.dicajakrye.camera;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface CameraRepository extends Repository<Camera, Long> {

    @Query("select c from Camera c join fetch c.brand where c.slug = :slug")
    Optional<Camera> findBySlugWithBrand(String slug);
}
