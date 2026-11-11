package com.kafkademo.customer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Requests authenticated as each realm role, with the authorities {@code SecurityConfig} derives from a token. */
public final class TestTokens {

    private TestTokens() {}

    public static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_customer-admin"));
    }

    public static RequestPostProcessor viewer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_customer-viewer"));
    }

    /** A decoded token shaped like Keycloak's, for a stubbed {@code JwtDecoder}; {@code username} may be null. */
    public static Jwt keycloakToken(String username, String... realmRoles) {
        Jwt.Builder token = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(username == null ? "service-account" : username)
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2999-01-01T00:00:00Z"))
                .claim("realm_access", Map.of("roles", List.of(realmRoles)));
        if (username != null) {
            token.claim("preferred_username", username);
        }
        return token.build();
    }
}
