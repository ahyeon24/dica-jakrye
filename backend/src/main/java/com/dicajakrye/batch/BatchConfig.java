package com.dicajakrye.batch;

import com.dicajakrye.commons.CommonsProperties;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 배치 실행 설정. (docs/ARCHITECTURE.md 3.7)
 * 배치 전용 스케줄러 스레드("batch-")를 따로 둬서 사용자 요청 처리와 섞이지 않게 한다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(CommonsProperties.class)
public class BatchConfig {

    private static final Logger log = LoggerFactory.getLogger(BatchConfig.class);

    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(1);
        s.setThreadNamePrefix("batch-");
        return s;
    }

    /** 로컬 개발용: app.batch.run-on-startup=commons-collect 이면 앱 시작 후 한 번 실행 */
    @Bean
    ApplicationRunner runBatchOnStartup(@Value("${app.batch.run-on-startup:}") String jobs,
                                        CommonsCollectJob commonsCollect) {
        return args -> {
            List<String> names = Arrays.stream(jobs.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
            if (names.contains("commons-collect")) {
                log.info("시작 시 배치 실행: commons-collect");
                commonsCollect.run();
            }
        };
    }
}
