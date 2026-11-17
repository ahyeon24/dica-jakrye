package com.dicajakrye.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/** 세션에 로그인 상태를 저장한다. 세션은 DB(spring_session)에 있다. */
@Component
public class SessionLogin {

    private final SecurityContextRepository repository;

    public SessionLogin(SecurityContextRepository repository) {
        this.repository = repository;
    }

    public void login(long userId, String role, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
        repository.saveContext(context, request, response);
    }

    /** 소셜 로그인은 됐지만 아직 회원이 아닐 때: 로그인 상태를 비운다 */
    public void clear(HttpServletRequest request, HttpServletResponse response) {
        SecurityContext empty = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(empty);
        repository.saveContext(empty, request, response);
    }
}
