package com.dicajakrye.storage;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * local 프로필에서만: 로컬 저장소의 공개 이미지를 /dev-images/** 로 보여준다.
 * (app.image.base-url 기본값이 http://localhost:8080/dev-images)
 * 비공개 폴더는 연결하지 않는다. 운영에서는 R2 공개 버킷이 이 역할을 한다.
 */
@Configuration
@Profile("local")
public class DevImageConfig implements WebMvcConfigurer {

    private final LocalImageStorage storage;

    public DevImageConfig(LocalImageStorage storage) {
        this.storage = storage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/dev-images/**")
                .addResourceLocations(storage.publicRoot().toUri().toString() + "/");
    }
}
