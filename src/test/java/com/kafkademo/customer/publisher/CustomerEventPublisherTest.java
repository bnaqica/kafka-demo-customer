package com.kafkademo.customer.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kafkademo.customer.CustomerFixtures;
import com.kafkademo.customer.config.KafkaTopicsProperties;
import com.kafkademo.customer.dto.CustomerChange;
import com.kafkademo.customer.dto.event.ChangedPhone;
import com.kafkademo.customer.dto.response.CustomerResponse;
import com.kafkademo.customer.entity.PhoneType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class CustomerEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final UUID ID = UUID.randomUUID();
    private static final CustomerResponse CUSTOMER = CustomerFixtures.response(ID, List.of());

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private CustomerEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new CustomerEventPublisher(
                kafkaTemplate,
                MAPPER,
                new KafkaTopicsProperties("customer-change-event"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void additionCarriesTheRecipientFilterFieldsAndEmptyChanges() {
        JsonNode event = publishAndCapture(CustomerChange.addition());

        assertThat(event.path("eventId").asString()).isNotBlank();
        assertThat(event.path("eventType").asString()).isEqualTo("customer-addition");
        assertThat(event.path("occurredAt").asString()).isEqualTo("2026-01-01T10:00:00Z");
        assertThat(event.path("customerId").asString()).isEqualTo(ID.toString());
        assertThat(event.path("firstName").asString()).isEqualTo("Ada");
        assertThat(event.path("lastName").asString()).isEqualTo("Lovelace");
        assertThat(event.path("email").asString()).isEqualTo("ada@example.com");
        assertThat(event.path("state").asString()).isEqualTo("NY");
        assertThat(event.path("changes").isEmpty()).isTrue();
    }

    @Test
    void neverIncludesDateOfBirth() {
        assertThat(publishAndCapture(CustomerChange.addition()).has("dateOfBirth"))
                .isFalse();
    }

    @Test
    void deleteUsesItsOwnTypeAndCarriesTheRecipient() {
        JsonNode event = publishAndCapture(CustomerChange.delete());

        assertThat(event.path("eventType").asString()).isEqualTo("customer-delete");
        assertThat(event.path("email").asString()).isEqualTo("ada@example.com");
    }

    @Test
    void phoneChangeCarriesPreviousAndNewPhone() {
        JsonNode event = publishAndCapture(CustomerChange.phoneChange(
                new ChangedPhone(PhoneType.CELL, "555-0100"), new ChangedPhone(PhoneType.CELL, "555-0111")));

        assertThat(event.path("eventType").asString()).isEqualTo("customer-phone-change");
        assertThat(event.path("changes").path("previousPhone").path("number").asString())
                .isEqualTo("555-0100");
        assertThat(event.path("changes").path("phone").path("type").asString()).isEqualTo("CELL");
    }

    @Test
    void waitsForTheCommitWhenATransactionIsActive() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publish(CustomerChange.addition(), CUSTOMER);
            verify(kafkaTemplate, never()).send(any(), any(), any());

            when(kafkaTemplate.send(any(), any(), any())).thenReturn(new CompletableFuture<>());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(kafkaTemplate).send(eq("customer-change-event"), eq(ID.toString()), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void aFailedSendDoesNotFailACommittedRequest() {
        when(kafkaTemplate.send(any(), any(), any())).thenThrow(new IllegalStateException("producer closed"));

        assertThatCode(() -> publisher.publish(CustomerChange.addition(), CUSTOMER))
                .doesNotThrowAnyException();
    }

    private JsonNode publishAndCapture(CustomerChange change) {
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(new CompletableFuture<>());

        publisher.publish(change, CUSTOMER);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("customer-change-event"), eq(ID.toString()), payload.capture());
        return MAPPER.readTree(payload.getValue());
    }
}
