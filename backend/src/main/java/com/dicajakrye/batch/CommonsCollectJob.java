package com.dicajakrye.batch;

import com.dicajakrye.commons.CommonsClient.CommonsUnavailableException;
import com.dicajakrye.commons.CommonsCollector;
import com.dicajakrye.commons.CommonsCollector.Result;
import com.dicajakrye.commons.CommonsProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Commons 수집 배치. (docs/ARCHITECTURE.md 3.3, 3.7)
 * - 수집한 지 오래된 기종부터 한 번에 camerasPerRun개
 * - 기종 하나 끝날 때마다 camera.commons_collected_at 갱신 → 중간에 끊겨도 다음 실행에서 이어 감
 * - 같은 배치가 겹쳐 돌지 않게 막는다 (서버 1대 기준. 2대 이상이면 ShedLock 등 필요: ARCHITECTURE.md 3.9)
 *
 * 실행 방법
 * - 정해진 시간: app.batch.commons-collect.cron (기본 "-" = 끔)
 * - 앱 시작 시 한 번: app.batch.run-on-startup=commons-collect (로컬 개발용)
 * - 특정 기종만: app.batch.commons-collect.only-slugs=canon-ixus-30,sony-dsc-t1
 */
@Component
public class CommonsCollectJob {

    private static final Logger log = LoggerFactory.getLogger(CommonsCollectJob.class);

    private final CommonsCollector collector;
    private final JdbcClient jdbc;
    private final CommonsProperties props;
    private final List<String> onlySlugs;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CommonsCollectJob(CommonsCollector collector, JdbcClient jdbc, CommonsProperties props,
                             @Value("${app.batch.commons-collect.only-slugs:}") List<String> onlySlugs) {
        this.collector = collector;
        this.jdbc = jdbc;
        this.props = props;
        this.onlySlugs = onlySlugs.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    @Scheduled(cron = "${app.batch.commons-collect.cron:-}", zone = "Asia/Seoul")
    public void scheduled() {
        run();
    }

    /** @return 처리한 기종별 결과. 이미 실행 중이면 빈 목록 */
    public List<Result> run() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Commons 수집 배치가 이미 실행 중이라 건너뜀");
            return List.of();
        }
        long started = System.currentTimeMillis();
        List<Result> results = new ArrayList<>();
        try {
            List<Long> cameraIds = targets();
            log.info("Commons 수집 시작: 기종 {}개", cameraIds.size());
            for (Long id : cameraIds) {
                try {
                    results.add(collector.collect(id));
                } catch (CommonsUnavailableException e) {
                    log.error("Commons 응답 없음, 이번 실행 중단: {}", e.getMessage());
                    break;
                } catch (RuntimeException e) {
                    log.error("Commons 수집 실패 (camera {})", id, e);
                }
            }
        } finally {
            running.set(false);
            int saved = results.stream().mapToInt(Result::saved).sum();
            int failed = results.stream().mapToInt(Result::failed).sum();
            log.info("Commons 수집 끝: 기종 {}개, 저장 {}장, 실패 {}장, {}초",
                    results.size(), saved, failed, (System.currentTimeMillis() - started) / 1000);
        }
        return results;
    }

    private List<Long> targets() {
        if (!onlySlugs.isEmpty()) {
            return jdbc.sql("SELECT id FROM camera WHERE slug IN (:slugs) ORDER BY id")
                    .param("slugs", onlySlugs).query(Long.class).list();
        }
        return jdbc.sql("""
                        SELECT c.id FROM camera c
                         WHERE EXISTS (SELECT 1 FROM camera_source_category s WHERE s.camera_id = c.id)
                         ORDER BY c.commons_collected_at NULLS FIRST, c.id
                         LIMIT :n
                        """)
                .param("n", props.camerasPerRun()).query(Long.class).list();
    }
}
