package com.kafkademo.customer.service;

import com.kafkademo.customer.dto.CustomerChange;
import com.kafkademo.customer.dto.IdempotentResult;
import com.kafkademo.customer.dto.event.ChangedPhone;
import com.kafkademo.customer.dto.request.CustomerRequest;
import com.kafkademo.customer.dto.request.IdempotencyKeyRequest;
import com.kafkademo.customer.dto.request.PhoneRequest;
import com.kafkademo.customer.dto.response.CustomerResponse;
import com.kafkademo.customer.dto.response.IdempotencyKeyResponse;
import com.kafkademo.customer.dto.response.PhoneResponse;
import com.kafkademo.customer.exception.DuplicateIdempotencyKeyException;
import com.kafkademo.customer.exception.IdempotencyKeyMismatchException;
import com.kafkademo.customer.processors.CustomerChangeClassifier;
import com.kafkademo.customer.publisher.CustomerEventPublisher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Action service: the only place customers and phones are changed from. It changes data through the CRUD
 * interfaces, decides what changed and publishes one event for it.
 */
@RequiredArgsConstructor
@Service
public class CustomerChangeServiceImpl implements CustomerChangeService {

    private static final int CREATED = 201;

    private final CustomerService customers;
    private final CustomerPhoneService phones;
    private final IdempotencyKeyService keys;
    private final CustomerEventPublisher publisher;
    private final CustomerChangeClassifier classifier;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transaction;

    @Override
    public IdempotentResult<CustomerResponse> create(String idempotencyKey, CustomerRequest request) {
        return idempotent(
                idempotencyKey, "/api/customers", request, CustomerResponse.class, CustomerResponse::id, () -> {
                    CustomerRequest normalized = request.normalized();
                    UUID id = customers.create(normalized).id();
                    normalized.phones().forEach(phone -> phones.create(id, phone));
                    CustomerResponse created = customers.get(id);
                    publisher.publish(CustomerChange.addition(), created);
                    return created;
                });
    }

    @Override
    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        CustomerResponse before = customers.get(id);
        CustomerRequest normalized = request.normalized();
        Optional<CustomerChange> change = classifier.classify(before, normalized);
        if (change.isEmpty()) {
            return before;
        }
        if (classifier.customerFieldsChanged(before, normalized)) {
            customers.update(id, normalized);
        }
        syncPhones(id, before.phones(), normalized.phones());
        CustomerResponse after = customers.get(id);
        publisher.publish(change.get(), after);
        return after;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        CustomerResponse existing = customers.get(id);
        customers.delete(id);
        publisher.publish(CustomerChange.delete(), existing);
    }

    @Override
    public IdempotentResult<PhoneResponse> addPhone(String idempotencyKey, UUID customerId, PhoneRequest request) {
        String path = "/api/customers/" + customerId + "/phones";
        return idempotent(idempotencyKey, path, request, PhoneResponse.class, phone -> customerId, () -> {
            customers.get(customerId); // 404 for an unknown customer
            PhoneResponse created = phones.create(customerId, request.normalized());
            publisher.publish(CustomerChange.phoneAdd(changed(created)), customers.get(customerId));
            return created;
        });
    }

    @Override
    @Transactional
    public PhoneResponse updatePhone(UUID customerId, UUID phoneId, PhoneRequest request) {
        PhoneResponse before = phones.get(customerId, phoneId);
        PhoneRequest normalized = request.normalized();
        if (asRequest(before).equals(normalized)) {
            return before;
        }
        PhoneResponse updated = phones.update(customerId, phoneId, normalized);
        publisher.publish(CustomerChange.phoneChange(changed(before), changed(updated)), customers.get(customerId));
        return updated;
    }

    @Override
    @Transactional
    public void deletePhone(UUID customerId, UUID phoneId) {
        PhoneResponse existing = phones.get(customerId, phoneId);
        phones.delete(customerId, phoneId);
        publisher.publish(CustomerChange.phoneDelete(changed(existing)), customers.get(customerId));
    }

    private void syncPhones(UUID customerId, List<PhoneResponse> before, List<PhoneRequest> requested) {
        Set<PhoneRequest> existing =
                before.stream().map(CustomerChangeServiceImpl::asRequest).collect(Collectors.toSet());
        before.stream()
                .filter(phone -> !requested.contains(asRequest(phone)))
                .forEach(phone -> phones.delete(customerId, phone.id()));
        requested.stream()
                .filter(phone -> !existing.contains(phone))
                .forEach(phone -> phones.create(customerId, phone));
    }

    /**
     * Runs a POST at most once for its key. The key is inserted first in the same transaction, so a failure removes
     * it again, and a concurrent request with the same key waits on the primary key and then returns the stored result.
     */
    private <T> IdempotentResult<T> idempotent(
            String key,
            String path,
            Object body,
            Class<T> responseType,
            Function<T, UUID> customerId,
            Supplier<T> action) {
        String hash = hash("POST " + path + "\n" + jsonMapper.writeValueAsString(body));
        try {
            return transaction.execute(status -> keys.find(key)
                    .map(existing -> replay(existing, hash, responseType))
                    .orElseGet(() -> {
                        keys.create(new IdempotencyKeyRequest(key, hash, CREATED, jsonMapper.createObjectNode(), null));
                        T result = action.get();
                        keys.update(
                                key,
                                new IdempotencyKeyRequest(
                                        key, hash, CREATED, jsonMapper.valueToTree(result), customerId.apply(result)));
                        return new IdempotentResult<>(CREATED, result);
                    }));
        } catch (DataIntegrityViolationException | DuplicateIdempotencyKeyException ex) {
            return keys.find(key)
                    .map(existing -> replay(existing, hash, responseType))
                    .orElseThrow(() -> ex);
        }
    }

    private <T> IdempotentResult<T> replay(IdempotencyKeyResponse existing, String hash, Class<T> responseType) {
        if (!existing.requestHash().equals(hash)) {
            throw new IdempotencyKeyMismatchException(existing.key());
        }
        return new IdempotentResult<>(
                existing.responseStatus(), jsonMapper.treeToValue(existing.responseBody(), responseType));
    }

    private static PhoneRequest asRequest(PhoneResponse phone) {
        return new PhoneRequest(phone.type(), phone.number());
    }

    private static ChangedPhone changed(PhoneResponse phone) {
        return new ChangedPhone(phone.type(), phone.number());
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", ex);
        }
    }
}
