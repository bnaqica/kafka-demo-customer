package com.kafkademo.customer.service;

import static com.kafkademo.customer.CustomerFixtures.phoneResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kafkademo.customer.CustomerFixtures;
import com.kafkademo.customer.dto.CustomerChange;
import com.kafkademo.customer.dto.IdempotentResult;
import com.kafkademo.customer.dto.event.CustomerEventType;
import com.kafkademo.customer.dto.request.CustomerRequest;
import com.kafkademo.customer.dto.request.IdempotencyKeyRequest;
import com.kafkademo.customer.dto.request.PhoneRequest;
import com.kafkademo.customer.dto.response.CustomerResponse;
import com.kafkademo.customer.dto.response.IdempotencyKeyResponse;
import com.kafkademo.customer.dto.response.PhoneResponse;
import com.kafkademo.customer.entity.PhoneType;
import com.kafkademo.customer.exception.DuplicateEmailException;
import com.kafkademo.customer.exception.DuplicateIdempotencyKeyException;
import com.kafkademo.customer.exception.IdempotencyKeyMismatchException;
import com.kafkademo.customer.processors.CustomerChangeClassifier;
import com.kafkademo.customer.publisher.CustomerEventPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerChangeServiceImplTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final UUID ID = UUID.randomUUID();
    private static final UUID PHONE_ID = UUID.randomUUID();

    @Mock
    private CustomerService customers;

    @Mock
    private CustomerPhoneService phones;

    @Mock
    private IdempotencyKeyService keys;

    @Mock
    private CustomerEventPublisher publisher;

    private CustomerChangeService service;

    @BeforeEach
    void setUp() {
        service = new CustomerChangeServiceImpl(
                customers,
                phones,
                keys,
                publisher,
                new CustomerChangeClassifier(),
                MAPPER,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    @Test
    void createStoresCustomerAndPhonesAndPublishesOneAddition() {
        CustomerResponse stored = CustomerFixtures.response(ID, List.of());
        CustomerResponse withPhones = CustomerFixtures.response(ID, List.of(phoneResponse(PhoneType.CELL, "555-0100")));
        when(keys.find("key")).thenReturn(Optional.empty());
        when(customers.create(any())).thenReturn(stored);
        when(customers.get(ID)).thenReturn(withPhones);

        IdempotentResult<CustomerResponse> result = service.create("key", CustomerFixtures.request());

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body()).isEqualTo(withPhones);
        verify(phones).create(ID, CustomerFixtures.cell());
        assertPublished(CustomerEventType.ADDITION, withPhones);
    }

    @Test
    void createReservesTheKeyBeforeChangingAnything() {
        when(keys.find("key")).thenReturn(Optional.empty());
        when(customers.create(any())).thenReturn(CustomerFixtures.response(ID, List.of()));
        when(customers.get(ID)).thenReturn(CustomerFixtures.response(ID, List.of()));

        service.create("key", CustomerFixtures.request(List.of()));

        InOrder order = inOrder(keys, customers);
        order.verify(keys).create(any());
        order.verify(customers).create(any());
        order.verify(keys).update(eq("key"), any());
    }

    @Test
    void createStoresTheResponseAgainstTheKey() {
        CustomerResponse created = CustomerFixtures.response(ID, List.of());
        when(keys.find("key")).thenReturn(Optional.empty());
        when(customers.create(any())).thenReturn(created);
        when(customers.get(ID)).thenReturn(created);

        service.create("key", CustomerFixtures.request(List.of()));

        ArgumentCaptor<IdempotencyKeyRequest> stored = ArgumentCaptor.forClass(IdempotencyKeyRequest.class);
        verify(keys).update(eq("key"), stored.capture());
        assertThat(stored.getValue().responseStatus()).isEqualTo(201);
        assertThat(stored.getValue().customerId()).isEqualTo(ID);
        assertThat(stored.getValue().responseBody().path("id").asString()).isEqualTo(ID.toString());
    }

    @Test
    void repeatedCreateReturnsTheStoredResultWithoutChangesOrEvents() {
        String hash = firstRequestHash();
        CustomerResponse original = CustomerFixtures.response(ID, List.of());
        when(keys.find("again")).thenReturn(Optional.of(stored("again", hash, original)));
        resetInteractionsOf();

        IdempotentResult<CustomerResponse> result = service.create("again", CustomerFixtures.request(List.of()));

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body()).isEqualTo(original);
        verify(keys, never()).create(any());
        verify(customers, never()).create(any());
        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void sameKeyWithDifferentBodyIsRejected() {
        when(keys.find("again"))
                .thenReturn(Optional.of(stored("again", "another-hash", CustomerFixtures.response(ID, List.of()))));

        assertThatThrownBy(() -> service.create("again", CustomerFixtures.request()))
                .isInstanceOf(IdempotencyKeyMismatchException.class);

        verifyNoInteractions(customers, publisher);
    }

    @Test
    void sameKeyOnADifferentPathIsRejected() {
        String customerHash = firstRequestHash();
        when(keys.find("again"))
                .thenReturn(Optional.of(stored("again", customerHash, CustomerFixtures.response(ID, List.of()))));

        assertThatThrownBy(() -> service.addPhone("again", ID, CustomerFixtures.cell()))
                .isInstanceOf(IdempotencyKeyMismatchException.class);
    }

    @Test
    void concurrentDuplicateReturnsTheWinnersResult() {
        String hash = firstRequestHash();
        CustomerResponse original = CustomerFixtures.response(ID, List.of());
        when(keys.find("race")).thenReturn(Optional.empty()).thenReturn(Optional.of(stored("race", hash, original)));
        when(keys.create(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThat(service.create("race", CustomerFixtures.request(List.of())).body())
                .isEqualTo(original);
        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void losingTheRaceAsADuplicateKeyStillReturnsTheWinnersResult() {
        String hash = firstRequestHash();
        CustomerResponse original = CustomerFixtures.response(ID, List.of());
        when(keys.find("race")).thenReturn(Optional.empty()).thenReturn(Optional.of(stored("race", hash, original)));
        when(keys.create(any())).thenThrow(new DuplicateIdempotencyKeyException("race"));

        assertThat(service.create("race", CustomerFixtures.request(List.of())).body())
                .isEqualTo(original);
    }

    @Test
    void constraintFailureUnrelatedToTheKeyIsNotHidden() {
        when(keys.find("key")).thenReturn(Optional.empty());
        when(customers.create(any())).thenThrow(new DataIntegrityViolationException("other"));

        assertThatThrownBy(() -> service.create("key", CustomerFixtures.request()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void failedCreatePublishesNothing() {
        when(keys.find("key")).thenReturn(Optional.empty());
        when(customers.create(any())).thenThrow(new DuplicateEmailException("ada@example.com"));

        assertThatThrownBy(() -> service.create("key", CustomerFixtures.request()))
                .isInstanceOf(DuplicateEmailException.class);

        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void updateWithIdenticalValuesChangesAndPublishesNothing() {
        CustomerResponse existing = CustomerFixtures.response(ID, List.of(phoneResponse(PhoneType.CELL, "555-0100")));
        when(customers.get(ID)).thenReturn(existing);

        assertThat(service.update(ID, CustomerFixtures.request())).isEqualTo(existing);

        verify(customers, never()).update(any(), any());
        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void updateOnlyTouchingPhonesLeavesTheCustomerRowAlone() {
        when(customers.get(ID))
                .thenReturn(CustomerFixtures.response(ID, List.of(phoneResponse(PhoneType.CELL, "555-0100"))));

        service.update(
                ID,
                CustomerFixtures.request(
                        List.of(CustomerFixtures.cell(), new PhoneRequest(PhoneType.HOME, "555-0199"))));

        verify(customers, never()).update(any(), any());
        verify(phones).create(ID, new PhoneRequest(PhoneType.HOME, "555-0199"));
    }

    @Test
    void updateRemovesPhonesLeftOutOfTheRequest() {
        PhoneResponse cell = phoneResponse(PhoneType.CELL, "555-0100");
        when(customers.get(ID)).thenReturn(CustomerFixtures.response(ID, List.of(cell)));

        service.update(ID, CustomerFixtures.request(List.of()));

        verify(phones).delete(ID, cell.id());
        verify(publisher).publish(any(), any());
    }

    @Test
    void updatePublishesTheClassifiedChangeWithTheStateAfterTheUpdate() {
        CustomerResponse before = CustomerFixtures.response(ID, List.of());
        CustomerResponse after = before.toBuilder().lastName("Byron").build();
        when(customers.get(ID)).thenReturn(before).thenReturn(after);
        CustomerRequest request = new CustomerRequest(
                "Ada",
                "King",
                "Byron",
                before.dateOfBirth(),
                before.gender(),
                before.email(),
                CustomerFixtures.address(),
                List.of());

        assertThat(service.update(ID, request)).isEqualTo(after);

        verify(customers).update(eq(ID), any());
        assertPublished(CustomerEventType.CHANGE, after);
    }

    @Test
    void deletePublishesWithTheCustomerAsItWas() {
        CustomerResponse existing = CustomerFixtures.response(ID, List.of());
        when(customers.get(ID)).thenReturn(existing);

        service.delete(ID);

        InOrder order = inOrder(customers, publisher);
        order.verify(customers).get(ID);
        order.verify(customers).delete(ID);
        assertPublished(CustomerEventType.DELETE, existing);
    }

    @Test
    void addPhonePublishesAPhoneAdd() {
        PhoneResponse created = new PhoneResponse(PHONE_ID, PhoneType.CELL, "555-0100");
        when(keys.find("key")).thenReturn(Optional.empty());
        when(customers.get(ID)).thenReturn(CustomerFixtures.response(ID, List.of(created)));
        when(phones.create(ID, CustomerFixtures.cell())).thenReturn(created);

        IdempotentResult<PhoneResponse> result = service.addPhone("key", ID, CustomerFixtures.cell());

        assertThat(result.body()).isEqualTo(created);
        assertPublished(CustomerEventType.PHONE_ADD, CustomerFixtures.response(ID, List.of(created)));
    }

    @Test
    void updatePhoneWithIdenticalValuesPublishesNothing() {
        PhoneResponse existing = new PhoneResponse(PHONE_ID, PhoneType.CELL, "555-0100");
        when(phones.get(ID, PHONE_ID)).thenReturn(existing);

        assertThat(service.updatePhone(ID, PHONE_ID, CustomerFixtures.cell())).isEqualTo(existing);

        verify(phones, never()).update(any(), any(), any());
        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void updatePhonePublishesAPhoneChange() {
        when(phones.get(ID, PHONE_ID)).thenReturn(new PhoneResponse(PHONE_ID, PhoneType.CELL, "555-0100"));
        PhoneRequest request = new PhoneRequest(PhoneType.CELL, "555-0111");
        when(phones.update(ID, PHONE_ID, request)).thenReturn(new PhoneResponse(PHONE_ID, PhoneType.CELL, "555-0111"));
        when(customers.get(ID)).thenReturn(CustomerFixtures.response(ID, List.of()));

        service.updatePhone(ID, PHONE_ID, request);

        assertPublished(CustomerEventType.PHONE_CHANGE, CustomerFixtures.response(ID, List.of()));
    }

    @Test
    void deletePhonePublishesAPhoneDelete() {
        when(phones.get(ID, PHONE_ID)).thenReturn(new PhoneResponse(PHONE_ID, PhoneType.CELL, "555-0100"));
        when(customers.get(ID)).thenReturn(CustomerFixtures.response(ID, List.of()));

        service.deletePhone(ID, PHONE_ID);

        verify(phones).delete(ID, PHONE_ID);
        assertPublished(CustomerEventType.PHONE_DELETE, CustomerFixtures.response(ID, List.of()));
    }

    private void assertPublished(CustomerEventType type, CustomerResponse customer) {
        ArgumentCaptor<CustomerChange> change = ArgumentCaptor.forClass(CustomerChange.class);
        verify(publisher).publish(change.capture(), eq(customer));
        assertThat(change.getValue().type()).isEqualTo(type);
    }

    /** Runs a first create and returns the request hash it reserved, so repeats can match it exactly. */
    private String firstRequestHash() {
        List<IdempotencyKeyRequest> reserved = new ArrayList<>();
        when(keys.find("first")).thenReturn(Optional.empty());
        when(keys.create(any())).thenAnswer(inv -> {
            reserved.add(inv.getArgument(0));
            return null;
        });
        when(customers.create(any())).thenReturn(CustomerFixtures.response(ID, List.of()));
        when(customers.get(ID)).thenReturn(CustomerFixtures.response(ID, List.of()));
        service.create("first", CustomerFixtures.request(List.of()));
        org.mockito.Mockito.clearInvocations(keys, customers, phones, publisher);
        return reserved.getFirst().requestHash();
    }

    private void resetInteractionsOf() {
        org.mockito.Mockito.clearInvocations(keys, customers, phones, publisher);
    }

    private static IdempotencyKeyResponse stored(String key, String hash, Object body) {
        return IdempotencyKeyResponse.builder()
                .key(key)
                .requestHash(hash)
                .responseStatus(201)
                .responseBody(MAPPER.valueToTree(body))
                .customerId(ID)
                .build();
    }
}
