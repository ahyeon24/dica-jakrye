package com.dicajakrye.commons;

import com.dicajakrye.commons.CommonsFileFilter.Candidate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 작가별로 돌아가며 한 장씩, 작가당 최대 N장까지 고른다. (docs/ARCHITECTURE.md 3.3)
 * 한 작가 사진이 몰리면 카메라가 아니라 그 사람의 습관이 카메라 특성처럼 보이기 때문이다.
 */
public final class AuthorRoundRobin {

    private AuthorRoundRobin() {
    }

    /**
     * @param candidates      조건을 통과한 후보 (받은 순서)
     * @param existingByAuthor 이미 저장된 작가별 장수 (다시 실행해도 제한을 지키려고)
     * @param perAuthor        작가당 최대 장수
     * @param slots            이번에 더 넣을 수 있는 장수
     */
    public static List<Candidate> pick(List<Candidate> candidates, Map<String, Integer> existingByAuthor,
                                       int perAuthor, int slots) {
        Map<String, List<Candidate>> byAuthor = new LinkedHashMap<>();
        for (Candidate c : candidates) {
            byAuthor.computeIfAbsent(c.author(), k -> new ArrayList<>()).add(c);
        }
        List<Candidate> chosen = new ArrayList<>();
        for (int round = 0; chosen.size() < slots; round++) {
            boolean added = false;
            for (Map.Entry<String, List<Candidate>> e : byAuthor.entrySet()) {
                int already = existingByAuthor.getOrDefault(e.getKey(), 0);
                if (round < e.getValue().size() && already + round < perAuthor && chosen.size() < slots) {
                    chosen.add(e.getValue().get(round));
                    added = true;
                }
            }
            if (!added) {
                break;
            }
        }
        return chosen;
    }
}
