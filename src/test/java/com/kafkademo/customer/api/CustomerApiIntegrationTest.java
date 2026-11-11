package com.kafkademo.customer.api;

import static com.kafkademo.customer.CustomerFixtures.VALID_BODY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kafkademo.customer.TestTokens;
import com.kafkademo.customer.TestcontainersConfiguration;
import com.kafkademo.customer.entity.ApiAudit;
import com.kafkademo.customer.repository.ApiAuditRepository;
import com.kafkademo.customer.repository.CustomerPhoneRepository;
import com.kafkademo.customer.repository.CustomerRepository;
import com.kafkademo.customer.repository.IdempotencyKeyRepository;
import com.kafkademo.customer.service.IdempotencyKeyService;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Real database, real transactions, real audit filter; only Kafka and the token signature are replaced. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerApiIntegrationTest {

    private static final String ADMIN = "Bearer admin";
    private static final String VIEWER = "Bearer viewer";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private CustomerPhoneRepository phones;

    @Autowired
    private IdempotencyKeyRepository keys;

    @Autowired
    private ApiAuditRepository audits;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoSpyBean
    private IdempotencyKeyService keyService;

    @BeforeEach
    void setUp() {
        audits.deleteAll();
        keys.deleteAll();
        customers.deleteAll();
        clearInvocations(kafkaTemplate);
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(new CompletableFuture<>());
        when(jwtDecoder.decode("admin")).thenReturn(TestTokens.keycloakToken("admin.user", "customer-admin"));
        when(jwtDecoder.decode("viewer")).thenReturn(TestTokens.keycloakToken("viewer.user", "customer-viewer"));
    }

    @Test
    void createStoresEverythingAndPublishesOnce() throws Exception {
        JsonNode created = create("key-1", VALID_BODY);

        assertThat(created.path("phones").size()).isEqualTo(1);
        assertThat(customers.count()).isEqualTo(1);
        assertThat(phones.count()).isEqualTo(1);
        assertThat(publishedTypes()).containsExactly("customer-addition");
    }

    @Test
    void repeatingAPostReturnsTheSameResultAndPublishesOnce() throws Exception {
        JsonNode first = create("key-1", VALID_BODY);
        JsonNode second = create("key-1", VALID_BODY);

        assertThat(second).isEqualTo(first);
        assertThat(customers.count()).isEqualTo(1);
        verify(kafkaTemplate, times(1)).send(any(), any(), any());
    }

    @Test
    void repeatAfterTheCustomerIsDeletedReturnsTheOriginalWithoutRecreating() throws Exception {
        JsonNode first = create("key-1", VALID_BODY);
        mockMvc.perform(delete("/api/customers/" + first.path("id").asString()).header("Authorization", ADMIN))
                .andExpect(status().isNoContent());

        assertThat(create("key-1", VALID_BODY)).isEqualTo(first);
        assertThat(customers.count()).isZero();
    }

    @Test
    void differentBodyWithTheSameKeyIsRejected() throws Exception {
        create("key-1", VALID_BODY);

        mockMvc.perform(createRequest("key-1", VALID_BODY.replace("Ada", "Augusta")))
                .andExpect(status().isUnprocessableContent());
        assertThat(customers.count()).isEqualTo(1);
    }

    @Test
    void aFailedPostLeavesNoKeyAndPublishesNothing() throws Exception {
        JsonNode existing = create("key-1", VALID_BODY);
        clearInvocations(kafkaTemplate);

        mockMvc.perform(createRequest("key-2", VALID_BODY)).andExpect(status().isConflict());

        assertThat(keys.existsById("key-2")).isFalse();
        verify(kafkaTemplate, never()).send(any(), any(), any());

        mockMvc.perform(delete("/api/customers/" + existing.path("id").asString())
                        .header("Authorization", ADMIN))
                .andExpect(status().isNoContent());
        mockMvc.perform(createRequest("key-2", VALID_BODY)).andExpect(status().isCreated());
        assertThat(customers.count()).isEqualTo(1);
    }

    @Test
    void identicalPutPublishesNothing() throws Exception {
        JsonNode created = create("key-1", VALID_BODY);
        clearInvocations(kafkaTemplate);

        mockMvc.perform(updateRequest(created, VALID_BODY)).andExpect(status().isOk());

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void addressOnlyPutPublishesAnAddressChange() throws Exception {
        JsonNode created = create("key-1", VALID_BODY);
        clearInvocations(kafkaTemplate);

        mockMvc.perform(updateRequest(created, VALID_BODY.replace("Albany", "Troy")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.city").value("Troy"));

        assertThat(publishedTypes()).containsExactly("customer-address-change");
    }

    @Test
    void putThatAddsOnePhonePublishesAPhoneAdd() throws Exception {
        JsonNode created = create("key-1", VALID_BODY);
        clearInvocations(kafkaTemplate);
        String twoPhones = VALID_BODY.replace(
                "{\"type\":\"CELL\",\"number\":\"555-0100\"}",
                "{\"type\":\"CELL\",\"number\":\"555-0100\"},{\"type\":\"HOME\",\"number\":\"555-0199\"}");

        mockMvc.perform(updateRequest(created, twoPhones))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phones.length()").value(2));

        assertThat(publishedTypes()).containsExactly("customer-phone-add");
    }

    @Test
    void phonesApiPublishesForEachChange() throws Exception {
        JsonNode created = create("key-1", VALID_BODY);
        clearInvocations(kafkaTemplate);
        String path = "/api/customers/" + created.path("id").asString() + "/phones";

        MvcResult added = mockMvc.perform(post(path)
                        .header("Authorization", ADMIN)
                        .header("Idempotency-Key", "phone-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"WORK\",\"number\":\"555-0123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String phonePath = path + "/"
                + jsonMapper
                        .readTree(added.getResponse().getContentAsString())
                        .path("id")
                        .asString();

        mockMvc.perform(put(phonePath)
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"WORK\",\"number\":\"555-0124\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete(phonePath).header("Authorization", ADMIN)).andExpect(status().isNoContent());

        assertThat(publishedTypes())
                .containsExactly("customer-phone-add", "customer-phone-change", "customer-phone-delete");
    }

    @Test
    void deletingACustomerRemovesItsPhones() throws Exception {
        JsonNode created = create("key-1", VALID_BODY);
        clearInvocations(kafkaTemplate);

        mockMvc.perform(delete("/api/customers/" + created.path("id").asString())
                        .header("Authorization", ADMIN))
                .andExpect(status().isNoContent());

        assertThat(phones.count()).isZero();
        assertThat(publishedTypes()).containsExactly("customer-delete");
    }

    @Test
    void viewerCanRead() throws Exception {
        create("key-1", VALID_BODY);

        mockMvc.perform(get("/api/customers").header("Authorization", VIEWER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].address.state").value("NY"));
    }

    @Test
    void writeDeniedByRoleIsAudited() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .header("Authorization", VIEWER)
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        ApiAudit audit = audits.findAll().getFirst();
        assertThat(audit.getPerformedBy()).isEqualTo("viewer.user");
        assertThat(audit.getResponseStatus()).isEqualTo(403);
        assertThat(audit.isSuccess()).isFalse();
        assertThat(audit.getRequestPayload()).contains("ada@example.com");
        assertThat(customers.count()).isZero();
    }

    @Test
    void writeWithoutATokenIsAuditedAsAnonymous() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());

        ApiAudit audit = audits.findAll().getFirst();
        assertThat(audit.getPerformedBy()).isEqualTo("anonymous");
        assertThat(audit.getResponseStatus()).isEqualTo(401);
        assertThat(audit.isSuccess()).isFalse();
    }

    @Test
    void successfulWriteIsAuditedWithThePerson() throws Exception {
        create("key-1", VALID_BODY);

        ApiAudit audit = audits.findAll().getFirst();
        assertThat(audit.getPerformedBy()).isEqualTo("admin.user");
        assertThat(audit.isSuccess()).isTrue();
    }

    @Test
    void concurrentRepeatsOfOnePostCreateOneCustomerAndAgreeOnTheResult() throws Exception {
        List<MvcResult> results = concurrently(
                () -> mockMvc.perform(createRequest("race", VALID_BODY)).andReturn());

        assertThat(results)
                .allSatisfy(
                        result -> assertThat(result.getResponse().getStatus()).isEqualTo(201));
        assertThat(results.stream().map(CustomerApiIntegrationTest::body).distinct())
                .hasSize(1);
        assertThat(customers.count()).isEqualTo(1);
        verify(kafkaTemplate, times(1)).send(any(), any(), any());
    }

    @Test
    void concurrentPostsWithTheSameEmailGiveOneCreatedAndTheRestConflict() throws Exception {
        AtomicInteger key = new AtomicInteger();

        List<MvcResult> results =
                concurrently(() -> mockMvc.perform(createRequest("key-" + key.incrementAndGet(), VALID_BODY))
                        .andReturn());

        assertThat(results.stream().map(result -> result.getResponse().getStatus()))
                .containsOnly(201, 409)
                .containsOnlyOnce(201);
        assertThat(customers.count()).isEqualTo(1);
    }

    @Test
    void aRollbackAfterPublishingSendsNothingAndLeavesNothing() throws Exception {
        doThrow(new IllegalStateException("fails after publish"))
                .when(keyService)
                .update(eq("boom"), any());

        mockMvc.perform(createRequest("boom", VALID_BODY)).andExpect(status().isInternalServerError());

        verify(kafkaTemplate, never()).send(any(), any(), any());
        assertThat(customers.count()).isZero();
        assertThat(keys.existsById("boom")).isFalse();
    }

    @Test
    void lowercaseBearerSchemeIsStillAttributedToThePerson() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "bearer admin")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());

        assertThat(audits.findAll().getFirst().getPerformedBy()).isEqualTo("admin.user");
    }

    @Test
    void aTokenWithoutAUsernameIsAuditedByItsSubject() throws Exception {
        when(jwtDecoder.decode("service")).thenReturn(TestTokens.keycloakToken(null, "customer-admin"));

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer service")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());

        assertThat(audits.findAll().getFirst().getPerformedBy()).isEqualTo("service-account");
    }

    @Test
    void anOverlongPathIsStillAudited() throws Exception {
        mockMvc.perform(post("/api/" + "a".repeat(3000))).andExpect(status().isUnauthorized());

        assertThat(audits.findAll())
                .singleElement()
                .satisfies(audit -> assertThat(audit.getApiPath()).hasSize(2048));
    }

    private static String body(MvcResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static List<MvcResult> concurrently(Callable<MvcResult> request) throws Exception {
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return request.call();
                }));
            }
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private JsonNode create(String key, String body) throws Exception {
        MvcResult result = mockMvc.perform(createRequest(key, body))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder createRequest(String key, String body) {
        return post("/api/customers")
                .header("Authorization", ADMIN)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static MockHttpServletRequestBuilder updateRequest(JsonNode customer, String body) {
        return put("/api/customers/" + customer.path("id").asString())
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private List<String> publishedTypes() {
        ArgumentCaptor<String> payloads = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, org.mockito.Mockito.atLeastOnce())
                .send(eq("customer-change-event"), any(), payloads.capture());
        return payloads.getAllValues().stream()
                .map(payload -> jsonMapper.readTree(payload).path("eventType").asString())
                .toList();
    }
}
