package com.kafkademo.customer.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kafkademo.customer.config.SecurityConfig;
import com.kafkademo.customer.dto.response.CustomerResponse;
import com.kafkademo.customer.exception.CustomerNotFoundException;
import com.kafkademo.customer.exception.DuplicateEmailException;
import com.kafkademo.customer.service.CustomerService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerController.class)
@Import(SecurityConfig.class)
class CustomerControllerTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String BODY = """
            {"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void createReturns201WithLocation() throws Exception {
        when(customerService.create(any())).thenReturn(response());

        mockMvc.perform(post("/api/customers")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/customers/" + ID))
                .andExpect(jsonPath("$.email").value("ada@example.com"));
    }

    @Test
    void createRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ada","lastName":"Lovelace","email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUnknownCustomerReturns404() throws Exception {
        when(customerService.get(ID)).thenThrow(new CustomerNotFoundException(ID));

        mockMvc.perform(get("/api/customers/" + ID).with(jwt())).andExpect(status().isNotFound());
    }

    @Test
    void updateReturnsCustomer() throws Exception {
        when(customerService.update(eq(ID), any())).thenReturn(response());

        mockMvc.perform(put("/api/customers/" + ID)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));
    }

    @Test
    void updateWithDuplicateEmailReturns409() throws Exception {
        when(customerService.update(eq(ID), any())).thenThrow(new DuplicateEmailException("ada@example.com"));

        mockMvc.perform(put("/api/customers/" + ID)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/customers/" + ID).with(jwt())).andExpect(status().isNoContent());

        verify(customerService).delete(ID);
    }

    @Test
    void deleteUnknownCustomerReturns404() throws Exception {
        doThrow(new CustomerNotFoundException(ID)).when(customerService).delete(ID);

        mockMvc.perform(delete("/api/customers/" + ID).with(jwt())).andExpect(status().isNotFound());
    }

    private static CustomerResponse response() {
        return new CustomerResponse(ID, "Ada", "Lovelace", "ada@example.com", Instant.parse("2026-01-01T10:00:00Z"));
    }
}
