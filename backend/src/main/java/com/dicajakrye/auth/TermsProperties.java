package com.dicajakrye.auth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 현재 시행 중인 약관 버전 (ERD.md 3.10). 모두 필수 동의 */
@ConfigurationProperties(prefix = "app.terms")
public record TermsProperties(String termsOfService, String privacyCollection) {

    public record Terms(String type, String version, String url) {
    }

    public List<Terms> current() {
        return List.of(
                new Terms("TERMS_OF_SERVICE", termsOfService, "/terms/" + termsOfService),
                new Terms("PRIVACY_COLLECTION", privacyCollection, "/privacy-consent/" + privacyCollection));
    }
}
