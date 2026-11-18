package com.dicajakrye.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** 응답을 늦추지 않아도 되는 일(신고 알림 메일)을 따로 처리한다. 실행기는 Spring Boot 기본(applicationTaskExecutor) */
@Configuration
@EnableAsync
public class AsyncConfig {
}
