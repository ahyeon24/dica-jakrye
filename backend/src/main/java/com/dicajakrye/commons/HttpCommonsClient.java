package com.dicajakrye.commons;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wikimedia Commons API 클라이언트. 위키미디어 이용 규칙을 지킨다. (docs/ARCHITECTURE.md 3.3)
 * - User-Agent에 서비스 이름과 연락처
 * - 한 번에 하나씩 순서대로, 요청 사이 대기 (API·다운로드 따로)
 * - maxlag=5: 위키미디어 서버가 바쁘면 거절되고, 잠시 쉬었다 다시 보낸다
 * - 실패하면 대기 시간을 늘려 가며 최대 4번 시도
 */
@Component
public class HttpCommonsClient implements CommonsClient {

    private static final Logger log = LoggerFactory.getLogger(HttpCommonsClient.class);
    private static final int MAX_ATTEMPTS = 4;
    private static final Duration BACKOFF = Duration.ofSeconds(5);
    private static final Duration MAX_WAIT = Duration.ofMinutes(2);

    private final CommonsProperties props;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private long lastApiCall;
    private long lastDownload;

    public HttpCommonsClient(CommonsProperties props, JsonMapper json) {
        this.props = props;
        this.json = json;
    }

    // ---------- 분류의 파일 목록 ----------

    @Override
    public List<CommonsFile> listCategoryFiles(String category, int max) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action", "query");
        params.put("generator", "categorymembers");
        params.put("gcmtitle", "Category:Taken with " + category);
        params.put("gcmtype", "file");
        params.put("gcmlimit", "50");
        params.put("prop", "imageinfo");
        params.put("iiprop", "url|size|mime|extmetadata|commonmetadata");
        params.put("iiurlwidth", "1600");
        params.put("iiextmetadatafilter", "Artist|LicenseShortName|LicenseUrl");

        List<CommonsFile> files = new ArrayList<>();
        while (files.size() < max) {
            JsonNode root = apiGet(params);
            for (JsonNode page : root.path("query").path("pages")) {
                CommonsFile f = toFile(page);
                if (f != null) {
                    files.add(f);
                }
            }
            JsonNode cont = root.path("continue");
            if (cont.isMissingNode() || cont.isEmpty()) {
                break;
            }
            cont.properties().forEach(e -> params.put(e.getKey(), e.getValue().asString()));
        }
        return files.size() > max ? files.subList(0, max) : files;
    }

    private CommonsFile toFile(JsonNode page) {
        JsonNode info = page.path("imageinfo").path(0);
        if (info.isMissingNode()) {
            return null;
        }
        JsonNode ext = info.path("extmetadata");
        String downloadUrl = info.hasNonNull("thumburl") ? info.get("thumburl").asString() : info.path("url").asString(null);
        return new CommonsFile(
                page.path("pageid").asLong(),
                page.path("title").asString(""),
                info.path("descriptionurl").asString(null),
                info.path("mime").asString(""),
                info.path("width").asInt(0),
                info.path("height").asInt(0),
                downloadUrl,
                ext.path("Artist").path("value").asString(null),
                ext.path("LicenseShortName").path("value").asString(null),
                ext.path("LicenseUrl").path("value").asString(null),
                scalarMetadata(info.path("commonmetadata")));
    }

    /** commonmetadata [{name, value}] 중 단순 값만 */
    private static Map<String, String> scalarMetadata(JsonNode list) {
        Map<String, String> m = new HashMap<>();
        for (JsonNode item : list) {
            JsonNode v = item.path("value");
            if (v.isValueNode() && !v.isNull()) {
                m.putIfAbsent(item.path("name").asString(""), v.asString());
            }
        }
        return m;
    }

    // ---------- 존재 확인 (정리 배치) ----------

    @Override
    public List<Long> missingPageIds(List<Long> pageIds) {
        List<Long> missing = new ArrayList<>();
        for (int i = 0; i < pageIds.size(); i += 50) {
            List<Long> chunk = pageIds.subList(i, Math.min(i + 50, pageIds.size()));
            Map<String, String> params = new LinkedHashMap<>();
            params.put("action", "query");
            params.put("pageids", chunk.stream().map(String::valueOf).collect(Collectors.joining("|")));
            JsonNode root = apiGet(params);
            for (JsonNode page : root.path("query").path("pages")) {
                if (page.path("missing").asBoolean(false)) {
                    missing.add(page.path("pageid").asLong());
                }
            }
        }
        return missing;
    }

    // ---------- HTTP ----------

    private JsonNode apiGet(Map<String, String> params) {
        Map<String, String> all = new LinkedHashMap<>(params);
        all.put("format", "json");
        all.put("formatversion", "2");
        all.put("maxlag", "5");
        URI uri = URI.create(props.apiUrl() + "?" + all.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&")));

        for (int attempt = 1; ; attempt++) {
            waitTurn(true);
            try {
                HttpResponse<String> res = http.send(request(uri), HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    JsonNode root = json.readTree(res.body());
                    if (!"maxlag".equals(root.path("error").path("code").asString(""))) {
                        return root;
                    }
                }
                retryOrFail(attempt, "API 응답 " + res.statusCode(), null, waitHint(res));
            } catch (IOException e) {
                retryOrFail(attempt, "API 요청 실패", e, 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CommonsUnavailableException("중단됨", e);
            }
        }
    }

    @Override
    public byte[] download(String url) {
        URI uri = URI.create(url);
        if (!uri.getHost().endsWith("wikimedia.org")) {
            throw new IllegalArgumentException("Commons 주소가 아님: " + url); // 엉뚱한 곳에서 받지 않는다
        }
        for (int attempt = 1; ; attempt++) {
            waitTurn(false);
            try {
                HttpResponse<byte[]> res = http.send(request(uri), HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() == 200) {
                    return res.body();
                }
                retryOrFail(attempt, "다운로드 응답 " + res.statusCode(), null, waitHint(res));
            } catch (IOException e) {
                retryOrFail(attempt, "다운로드 실패", e, 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CommonsUnavailableException("중단됨", e);
            }
        }
    }

    private HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", props.userAgent())
                .GET()
                .build();
    }

    /** 요청 사이 간격을 지킨다 (API와 다운로드를 따로 센다) */
    private synchronized void waitTurn(boolean api) {
        long gap = (api ? props.requestDelay() : props.downloadDelay()).toMillis();
        long last = api ? lastApiCall : lastDownload;
        long wait = last + gap - System.currentTimeMillis();
        if (wait > 0) {
            sleep(wait);
        }
        if (api) {
            lastApiCall = System.currentTimeMillis();
        } else {
            lastDownload = System.currentTimeMillis();
        }
    }

    /** 429(요청 너무 많음)면 Retry-After(초)를 따르고, 없으면 기본보다 3배 오래 쉰다 */
    private static long waitHint(HttpResponse<?> res) {
        if (res.statusCode() != 429) {
            return 0;
        }
        return res.headers().firstValue("Retry-After")
                .flatMap(v -> {
                    try {
                        return java.util.Optional.of(Long.parseLong(v.strip()) * 1000);
                    } catch (NumberFormatException e) {
                        return java.util.Optional.empty();
                    }
                })
                .orElse(-1L);
    }

    private static void retryOrFail(int attempt, String what, Exception cause, long hintMs) {
        if (attempt >= MAX_ATTEMPTS) {
            throw new CommonsUnavailableException(what + " (" + attempt + "번 시도)", cause);
        }
        if (hintMs > MAX_WAIT.toMillis()) {
            // 오래 기다리라고 하면(제한에 걸린 상태) 스레드를 붙잡고 기다리지 않고 이번 실행을 끝낸다. 다음 실행에서 이어 간다
            throw new CommonsUnavailableException(what + ": " + hintMs / 1000 + "초 뒤에 다시 오라고 함", cause);
        }
        long base = BACKOFF.toMillis() * attempt;
        long wait = hintMs > 0 ? Math.max(hintMs, base) : hintMs < 0 ? base * 3 : base;
        log.warn("Commons {}: {}ms 후 다시 시도 ({}/{})", what, wait, attempt, MAX_ATTEMPTS);
        sleep(wait);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CommonsUnavailableException("중단됨", e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
