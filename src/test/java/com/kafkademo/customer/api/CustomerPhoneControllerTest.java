package com.kafkademo.customer.api;

import static com.kafkademo.customer.TestTokens.admin;
import static com.kafkademo.customer.TestTokens.viewer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.kafkademo.customer.config.SecurityConfig;
import com.kafkademo.customer.dto.IdempotentResult;
import com.kafkademo.customer.dto.response.PhoneResponse;
import com.kafkademo.customer.entity.PhoneType;
import com.kafkademo.customer.exception.CustomerNotFoundException;
import com.kafkademo.customer.exception.DuplicatePhoneException;
import com.kafkademo.customer.exception.PhoneNotFoundException;
import com.kafkademo.customer.service.CustomerChangeService;
import com.kafkademo.customer.service.CustomerPhoneService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(CustomerPhoneController.class)
@Import(SecurityConfig.class)
class CustomerPhoneControllerTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID PHONE = UUID.randomUUID();
    private static final String PATH = "/api/customers/" + CUSTOMER + "/phones";
    private static final String BODY = """
            {"type":"CELL","number":"555-0100"}
            """;
    private static final PhoneResponse RESPONSE = new PhoneResponse(PHONE, PhoneType.CELL, "555-0100");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerPhoneService phoneService;

    @MockitoBean
    private CustomerChangeService changeService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void addReturns201WithItsId() throws Exception {
        when(changeService.addPhone(eq("key-1"), eq(CUSTOMER), any()))
                .thenReturn(new IdempotentResult<>(201, RESPONSE));

        mockMvc.perform(addRequest().with(admin()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", PATH + "/" + PHONE))
                .andExpect(jsonPath("$.id").value(PHONE.toString()));
    }

    @Test
    void addWithoutIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(post(PATH)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addRejectsAnInvalidType() throws Exception {
        mockMvc.perform(post(PATH)
                        .with(admin())
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("CELL", "FAX")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addToUnknownCustomerReturns404() throws Exception {
        when(changeService.addPhone(eq("key-1"), eq(CUSTOMER), any()))
                .thenThrow(new CustomerNotFoundException(CUSTOMER));

        mockMvc.perform(addRequest().with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void addingADuplicateReturns409() throws Exception {
        when(changeService.addPhone(eq("key-1"), eq(CUSTOMER), any()))
                .thenThrow(new DuplicatePhoneException(PhoneType.CELL, "555-0100"));

        mockMvc.perform(addRequest().with(admin())).andExpect(status().isConflict());
    }

    @Test
    void viewerCannotAdd() throws Exception {
        mockMvc.perform(addRequest().with(viewer())).andExpect(status().isForbidden());

        verify(changeService, never()).addPhone(any(), any(), any());
    }

    @Test
    void viewerCanList() throws Exception {
        when(phoneService.findByCustomer(CUSTOMER)).thenReturn(List.of(RESPONSE));

        mockMvc.perform(get(PATH).with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].number").value("555-0100"));
    }

    @Test
    void viewerCanGet() throws Exception {
        when(phoneService.get(CUSTOMER, PHONE)).thenReturn(RESPONSE);

        mockMvc.perform(get(PATH + "/" + PHONE).with(viewer())).andExpect(status().isOk());
    }

    @Test
    void phoneOfAnotherCustomerReturns404() throws Exception {
        when(phoneService.get(CUSTOMER, PHONE)).thenThrow(new PhoneNotFoundException(CUSTOMER, PHONE));

        mockMvc.perform(get(PATH + "/" + PHONE).with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void updateGoesThroughTheChangeService() throws Exception {
        when(changeService.updatePhone(eq(CUSTOMER), eq(PHONE), any())).thenReturn(RESPONSE);

        mockMvc.perform(put(PATH + "/" + PHONE)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void viewerCannotUpdate() throws Exception {
        mockMvc.perform(put(PATH + "/" + PHONE)
                        .with(viewer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete(PATH + "/" + PHONE).with(admin())).andExpect(status().isNoContent());

        verify(changeService).deletePhone(CUSTOMER, PHONE);
    }

    @Test
    void viewerCannotDelete() throws Exception {
        mockMvc.perform(delete(PATH + "/" + PHONE).with(viewer())).andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder addRequest() {
        return post(PATH)
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY);
    }
}
