package com.kafkademo.customer.api;

import static com.kafkademo.customer.TestTokens.admin;
import static com.kafkademo.customer.TestTokens.viewer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kafkademo.customer.config.SecurityConfig;
import com.kafkademo.customer.dto.response.IdempotencyKeyResponse;
import com.kafkademo.customer.exception.DuplicateIdempotencyKeyException;
import com.kafkademo.customer.exception.IdempotencyKeyNotFoundException;
import com.kafkademo.customer.service.IdempotencyKeyService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(IdempotencyKeyController.class)
@Import(SecurityConfig.class)
class IdempotencyKeyControllerTest {

    private static final String PATH = "/api/idempotency-keys";
    private static final String BODY = """
            {"key":"key-1","requestHash":"hash","responseStatus":201,"responseBody":{"a":1}}
            """;
    private static final IdempotencyKeyResponse RESPONSE = IdempotencyKeyResponse.builder()
            .key("key-1")
            .requestHash("hash")
            .responseStatus(201)
            .responseBody(JsonMapper.builder().build().readTree("{\"a\":1}"))
            .build();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdempotencyKeyService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void createReturns201() throws Exception {
        when(service.create(any())).thenReturn(RESPONSE);

        mockMvc.perform(post(PATH)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.responseBody.a").value(1));
    }

    @Test
    void createExistingKeyReturns409() throws Exception {
        when(service.create(any())).thenThrow(new DuplicateIdempotencyKeyException("key-1"));

        mockMvc.perform(post(PATH)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void listReturnsKeys() throws Exception {
        when(service.findAll()).thenReturn(List.of(RESPONSE));

        mockMvc.perform(get(PATH).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("key-1"));
    }

    @Test
    void getUnknownKeyReturns404() throws Exception {
        when(service.get("nope")).thenThrow(new IdempotencyKeyNotFoundException("nope"));

        mockMvc.perform(get(PATH + "/nope").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void updateReturnsTheKey() throws Exception {
        when(service.update(eq("key-1"), any())).thenReturn(RESPONSE);

        mockMvc.perform(put(PATH + "/key-1")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete(PATH + "/key-1").with(admin())).andExpect(status().isNoContent());

        verify(service).delete("key-1");
    }

    @Test
    void deleteUnknownKeyReturns404() throws Exception {
        doThrow(new IdempotencyKeyNotFoundException("nope")).when(service).delete("nope");

        mockMvc.perform(delete(PATH + "/nope").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void viewerCannotReadKeys() throws Exception {
        mockMvc.perform(get(PATH).with(viewer())).andExpect(status().isForbidden());
    }

    @Test
    void viewerCannotDeleteKeys() throws Exception {
        mockMvc.perform(delete(PATH + "/key-1").with(viewer())).andExpect(status().isForbidden());
    }
}
