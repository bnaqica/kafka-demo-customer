package com.kafkademo.customer.api;

import static com.kafkademo.customer.CustomerFixtures.VALID_BODY;
import static com.kafkademo.customer.TestTokens.admin;
import static com.kafkademo.customer.TestTokens.viewer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kafkademo.customer.CustomerFixtures;
import com.kafkademo.customer.TestTokens;
import com.kafkademo.customer.config.SecurityConfig;
import com.kafkademo.customer.dto.IdempotentResult;
import com.kafkademo.customer.dto.response.CustomerResponse;
import com.kafkademo.customer.exception.CustomerNotFoundException;
import com.kafkademo.customer.exception.DuplicateEmailException;
import com.kafkademo.customer.exception.IdempotencyKeyMismatchException;
import com.kafkademo.customer.service.CustomerChangeService;
import com.kafkademo.customer.service.CustomerService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(CustomerController.class)
@Import(SecurityConfig.class)
class CustomerControllerTest {

    private static final UUID ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private CustomerChangeService changeService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void createReturns201WithLocation() throws Exception {
        when(changeService.create(eq("key-1"), any())).thenReturn(new IdempotentResult<>(201, response()));

        mockMvc.perform(createRequest().with(admin()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/customers/" + ID))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.address.state").value("NY"));
    }

    @Test
    void createReturnsTheStoredStatusForARepeat() throws Exception {
        when(changeService.create(eq("key-1"), any())).thenReturn(new IdempotentResult<>(200, response()));

        mockMvc.perform(createRequest().with(admin())).andExpect(status().isOk());
    }

    @Test
    void createWithoutIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());

        verify(changeService, never()).create(any(), any());
    }

    @Test
    void createWithBlankIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .with(admin())
                        .header("Idempotency-Key", "  ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithOverlongIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .with(admin())
                        .header("Idempotency-Key", "k".repeat(256))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reusedKeyForADifferentRequestReturns422() throws Exception {
        when(changeService.create(eq("key-1"), any())).thenThrow(new IdempotencyKeyMismatchException("key-1"));

        mockMvc.perform(createRequest().with(admin())).andExpect(status().isUnprocessableContent());
    }

    @Test
    void aConcurrentDuplicateEmailReturns409() throws Exception {
        when(changeService.create(eq("key-1"), any()))
                .thenThrow(new DataIntegrityViolationException("violates unique constraint \"ux_customer_email\""));

        mockMvc.perform(createRequest().with(admin())).andExpect(status().isConflict());
    }

    @Test
    void aConcurrentDuplicatePhoneReturns409() throws Exception {
        when(changeService.create(eq("key-1"), any()))
                .thenThrow(new DataIntegrityViolationException("violates unique constraint \"ux_customer_phone\""));

        mockMvc.perform(createRequest().with(admin())).andExpect(status().isConflict());
    }

    @Test
    void aPhoneRacingACustomerDeleteReturns404() throws Exception {
        when(changeService.create(eq("key-1"), any()))
                .thenThrow(new DataIntegrityViolationException(
                        "violates foreign key constraint \"customer_phone_customer_id_fkey\""));

        mockMvc.perform(createRequest().with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void anUnrecognizedConstraintFailureStays500() throws Exception {
        when(changeService.create(eq("key-1"), any())).thenThrow(new DataIntegrityViolationException("other"));

        mockMvc.perform(createRequest().with(admin())).andExpect(status().isInternalServerError());
    }

    @Test
    void createRejectsAnInvalidZip() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .with(admin())
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("12207", "1234")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsAnUnknownGender() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .with(admin())
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("FEMALE", "OTHER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsAnUnknownPhoneType() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .with(admin())
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("CELL", "FAX")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithoutTokenReturns401() throws Exception {
        mockMvc.perform(createRequest()).andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCannotCreate() throws Exception {
        mockMvc.perform(createRequest().with(viewer())).andExpect(status().isForbidden());

        verify(changeService, never()).create(any(), any());
    }

    @Test
    void viewerCanListCustomers() throws Exception {
        when(customerService.findAll()).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/customers").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ID.toString()));
    }

    @Test
    void adminCanListCustomers() throws Exception {
        mockMvc.perform(get("/api/customers").with(admin())).andExpect(status().isOk());
    }

    @Test
    void viewerCanGetACustomer() throws Exception {
        when(customerService.get(ID)).thenReturn(response());

        mockMvc.perform(get("/api/customers/" + ID).with(viewer())).andExpect(status().isOk());
    }

    @Test
    void getUnknownCustomerReturns404() throws Exception {
        when(customerService.get(ID)).thenThrow(new CustomerNotFoundException(ID));

        mockMvc.perform(get("/api/customers/" + ID).with(viewer())).andExpect(status().isNotFound());
    }

    @Test
    void updateGoesThroughTheChangeService() throws Exception {
        when(changeService.update(eq(ID), any())).thenReturn(response());

        mockMvc.perform(updateRequest().with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));
    }

    @Test
    void updateWithDuplicateEmailReturns409() throws Exception {
        when(changeService.update(eq(ID), any())).thenThrow(new DuplicateEmailException("ada@example.com"));

        mockMvc.perform(updateRequest().with(admin())).andExpect(status().isConflict());
    }

    @Test
    void viewerCannotUpdate() throws Exception {
        mockMvc.perform(updateRequest().with(viewer())).andExpect(status().isForbidden());

        verify(changeService, never()).update(any(), any());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/customers/" + ID).with(admin())).andExpect(status().isNoContent());

        verify(changeService).delete(ID);
    }

    @Test
    void deleteUnknownCustomerReturns404() throws Exception {
        doThrow(new CustomerNotFoundException(ID)).when(changeService).delete(ID);

        mockMvc.perform(delete("/api/customers/" + ID).with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void viewerCannotDelete() throws Exception {
        mockMvc.perform(delete("/api/customers/" + ID).with(viewer())).andExpect(status().isForbidden());

        verify(changeService, never()).delete(any());
    }

    @Test
    void realmRolesFromARealTokenAllowAnAdmin() throws Exception {
        tokenWithRoles("customer-admin");

        mockMvc.perform(delete("/api/customers/" + ID).header("Authorization", "Bearer token"))
                .andExpect(status().isNoContent());
    }

    @Test
    void realmRolesFromARealTokenRestrictAViewer() throws Exception {
        tokenWithRoles("customer-viewer");

        mockMvc.perform(delete("/api/customers/" + ID).header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTokenWithNoRealmRoleIsForbidden() throws Exception {
        tokenWithRoles("offline_access");

        mockMvc.perform(get("/api/customers").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    private void tokenWithRoles(String... roles) {
        when(jwtDecoder.decode("token")).thenReturn(TestTokens.keycloakToken("user", roles));
    }

    private static MockHttpServletRequestBuilder createRequest() {
        return post("/api/customers")
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY);
    }

    private static MockHttpServletRequestBuilder updateRequest() {
        return put("/api/customers/" + ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY);
    }

    private static CustomerResponse response() {
        return CustomerFixtures.response(ID, List.of());
    }
}
