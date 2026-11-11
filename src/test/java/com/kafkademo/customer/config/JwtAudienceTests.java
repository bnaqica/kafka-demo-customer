package com.kafkademo.customer.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Uses the JwtDecoder Spring Boot builds from application.yml, with a test signing key in place of Keycloak's, to
 * check that tokens must be issued for the {@code kafka-demo-api} audience. A 404 means the request got past security.
 */
@WebMvcTest(useDefaultFilters = false)
@Import(SecurityConfig.class)
class JwtAudienceTests {

    private static final KeyPair KEYS = generateKeys();

    private final MockMvc mockMvc;

    JwtAudienceTests(@Autowired MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @DynamicPropertySource
    static void signingKey(DynamicPropertyRegistry registry) throws IOException {
        Path publicKey = Files.createTempFile("jwt-audience-test", ".pem");
        publicKey.toFile().deleteOnExit();
        Files.writeString(
                publicKey,
                "-----BEGIN PUBLIC KEY-----\n"
                        + Base64.getMimeEncoder()
                                .encodeToString(KEYS.getPublic().getEncoded())
                        + "\n-----END PUBLIC KEY-----\n");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "");
        registry.add("spring.security.oauth2.resourceserver.jwt.public-key-location", () -> "file:" + publicKey);
    }

    @Test
    void acceptsTokenForTheApiAudience() throws Exception {
        mockMvc.perform(get("/anything").header("Authorization", "Bearer " + token("kafka-demo-api")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsTokenForAnotherAudience() throws Exception {
        mockMvc.perform(get("/anything").header("Authorization", "Bearer " + token("account")))
                .andExpect(status().isUnauthorized());
    }

    private static String token(String audience) throws Exception {
        Instant now = Instant.now();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.RS256),
                new JWTClaimsSet.Builder()
                        .subject("test-user")
                        .audience(audience)
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(300)))
                        .build());
        jwt.sign(new RSASSASigner(KEYS.getPrivate()));
        return jwt.serialize();
    }

    private static KeyPair generateKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
