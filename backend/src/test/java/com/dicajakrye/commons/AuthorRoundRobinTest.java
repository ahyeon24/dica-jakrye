package com.dicajakrye.commons;

import static org.assertj.core.api.Assertions.assertThat;

import com.dicajakrye.commons.CommonsFileFilter.Candidate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorRoundRobinTest {

    static Candidate c(String author, int n) {
        var f = new CommonsFile(n, "File:" + n, null, "image/jpeg", 2048, 1536, null, author, "CC0", null, Map.of());
        return new Candidate(f, author, "Canon", "X", null, null, null, null, null, null, null);
    }

    @Test
    void 작가별로_돌아가며_고르고_작가당_제한을_지킨다() {
        List<Candidate> list = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            list.add(c("A", i));       // A가 30장으로 몰려 있음
        }
        list.add(c("B", 100));
        list.add(c("C", 101));

        List<Candidate> picked = AuthorRoundRobin.pick(list, Map.of(), 10, 100);
        assertThat(picked).hasSize(12); // A 10장 + B 1 + C 1
        assertThat(picked.subList(0, 3)).extracting(Candidate::author).containsExactly("A", "B", "C");
        assertThat(picked.stream().filter(x -> x.author().equals("A"))).hasSize(10);
    }

    @Test
    void 이미_저장된_장수까지_합쳐서_제한한다() {
        List<Candidate> list = List.of(c("A", 1), c("A", 2), c("A", 3), c("B", 4));
        List<Candidate> picked = AuthorRoundRobin.pick(list, Map.of("A", 9), 10, 100);
        assertThat(picked).extracting(Candidate::author).containsExactly("A", "B");
    }

    @Test
    void 남은_자리만큼만_고른다() {
        List<Candidate> list = List.of(c("A", 1), c("B", 2), c("C", 3), c("A", 4));
        assertThat(AuthorRoundRobin.pick(list, Map.of(), 10, 2)).hasSize(2);
    }
}
