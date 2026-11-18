package com.dicajakrye.auth;

import com.dicajakrye.common.ErrorCode;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import com.dicajakrye.upload.UploadProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 로그인·세션·CSRF·CORS (API.md 1.2~1.3, SECURITY.md 1·4·6·7·10장)
 * - 로그인: 구글·카카오 (Spring Security OAuth2 Client가 state 검증까지 처리)
 * - 로그인 유지: 세션 쿠키. 세션은 DB(spring_session)에 저장
 * - CSRF: XSRF-TOKEN 쿠키 → 프론트가 X-XSRF-TOKEN 헤더로 돌려보냄
 */
@Configuration
@EnableConfigurationProperties({TermsProperties.class, UploadProperties.class})
public class SecurityConfig {

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository,
                                            OAuthLoginSuccessHandler successHandler,
                                            @Value("${app.front-url}") String frontUrl,
                                            @Value("${app.cookie-domain:}") String cookieDomain) throws Exception {
        http
                .securityContext(c -> c.securityContextRepository(contextRepository))
                .csrf(c -> c.spa().csrfTokenRepository(csrfTokenRepository(cookieDomain)))
                .cors(Customizer.withDefaults())
                // 로그인 안 한 방문자의 요청마다 세션(DB 행)이 생기지 않게 한다
                .requestCache(c -> c.disable())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/me", "/api/me/**", "/api/cameras/exif-match").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/photos").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/photos/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/photos/*/reports").authenticated()
                        // 관리자 여부는 컨트롤러에서 요청마다 DB로 확인한다 (AdminCheck)
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> SecurityErrors.write(res, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, ex) -> SecurityErrors.write(res,
                                ex instanceof CsrfException ? ErrorCode.CSRF_TOKEN_INVALID : ErrorCode.FORBIDDEN)))
                .oauth2Login(o -> o
                        .loginPage("/login") // 스프링 기본 로그인 화면을 만들지 않는다 (로그인 화면은 프론트)
                        .authorizedClientRepository(new NoopAuthorizedClientRepository())
                        .successHandler(successHandler)
                        .failureHandler((req, res, ex) -> res.sendRedirect(trimSlash(frontUrl) + "/login?error=oauth")))
                .logout(l -> l
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .addFilterBefore(new RedirectCaptureFilter(), OAuth2AuthorizationRequestRedirectFilter.class);
        return http.build();
    }

    /**
     * XSRF-TOKEN 쿠키 (JS에서 읽어야 하므로 HttpOnly 아님).
     * 운영에서는 프론트({도메인})와 API(api.{도메인})가 같은 쿠키를 보도록 Domain=.{도메인}으로 내려준다.
     * 업로드는 브라우저가 API로 바로 보내기 때문이다.
     */
    private static CookieCsrfTokenRepository csrfTokenRepository(String cookieDomain) {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookieCustomizer(c -> {
            c.sameSite("Lax");
            if (!cookieDomain.isBlank()) {
                c.domain(cookieDomain).secure(true);
            }
        });
        return repo;
    }

    /** 우리 프론트 주소만 허용한다. 쿠키를 쓰므로 * 는 쓰지 않는다 (SECURITY.md 10장) */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
