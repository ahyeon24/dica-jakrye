import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.makernotes.CanonMakernoteDirectory;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Commons 사진에서 캐논 MakerNote의 Model ID(내부 기종 번호)를 읽는다.
 * 나라별 이름이 다른 사진들이 같은 기종인지 확인하는 데 쓴다. (docs/CAMERAS.md 2장)
 *
 * 입력: 탭으로 나눈 "표시용 이름<TAB>EXIF 모델명<TAB>파일 이름(File:…)" 목록, 최대 50줄
 * 실행 (README.md 참고): java -cp "$CP" ModelId.java samples.tsv
 *
 * 위키미디어 규칙: User-Agent에 연락처, 순차 요청, 다운로드 3초 간격.
 * EXIF는 파일 앞부분에 있으므로 Range 요청으로 앞 256KB만 받는다.
 */
public class ModelId {
    static final String UA = "dica-jakrye/0.1 (dicajakrye@gmail.com) java-http-client/21";

    public static void main(String[] args) throws Exception {
        List<String[]> rows = Files.readAllLines(Path.of(args[0])).stream()
                .filter(l -> !l.isBlank()).map(l -> l.split("\t")).toList();
        if (rows.size() > 50) {
            throw new IllegalArgumentException("한 번에 50줄까지 (API 한 번으로 주소를 받음)");
        }
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10)).build();

        // 1) 파일 주소: API 한 번으로
        String titles = String.join("|", rows.stream().map(r -> r[2]).toList());
        String api = "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2&maxlag=5"
                + "&prop=imageinfo&iiprop=url&titles=" + URLEncoder.encode(titles, StandardCharsets.UTF_8);
        String body = http.send(HttpRequest.newBuilder(URI.create(api)).header("User-Agent", UA).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        Map<String, String> urls = new HashMap<>();
        Matcher m = Pattern.compile("\"title\":\"(.*?)\".*?\"url\":\"(.*?)\"").matcher(body);
        while (m.find()) {
            urls.put(unescape(m.group(1)), unescape(m.group(2)));
        }

        // 2) 파일 앞부분을 받아 번호 읽기 (3초 간격)
        for (String[] r : rows) {
            String url = urls.get(r[2]);
            String result;
            if (url == null) {
                result = "주소 못 찾음 (파일 이름에 특수문자가 있으면 생길 수 있음)";
            } else {
                Thread.sleep(3000);
                HttpResponse<byte[]> res = http.send(HttpRequest.newBuilder(URI.create(url))
                                .header("User-Agent", UA).header("Range", "bytes=0-262143").build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                result = read(res.body()) + " (HTTP " + res.statusCode() + ")";
            }
            System.out.printf("%-20s %-30s %s%n", r[0], r[1], result);
        }
    }

    static String read(byte[] bytes) {
        try {
            Metadata md = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            CanonMakernoteDirectory d = md.getFirstDirectoryOfType(CanonMakernoteDirectory.class);
            if (d == null) {
                return "MakerNote 없음 (편집 프로그램을 거친 사진일 수 있음)";
            }
            if (!d.containsTag(CanonMakernoteDirectory.TAG_MODEL_ID)) {
                return "Model ID 없음";
            }
            return String.format("0x%08X", d.getLong(CanonMakernoteDirectory.TAG_MODEL_ID));
        } catch (Exception e) {
            return "읽기 실패: " + e.getClass().getSimpleName();
        }
    }

    static String unescape(String s) {
        return s.replace("\\/", "/").replace("\\\"", "\"");
    }
}
