package com.kafkademo.customer.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void offersABearerTokenForTheAuthorizeButton() {
        OpenAPI api = new OpenApiConfig().openApi();

        SecurityScheme scheme = api.getComponents().getSecuritySchemes().get("bearer");
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(api.getSecurity())
                .singleElement()
                .satisfies(item -> assertThat(item).containsKey("bearer"));
    }
}
