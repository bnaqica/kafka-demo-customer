package com.kafkademo.customer.service;

import com.kafkademo.customer.dto.request.IdempotencyKeyRequest;
import com.kafkademo.customer.dto.response.IdempotencyKeyResponse;
import java.util.List;
import java.util.Optional;

/** CRUD for the idempotency_key table. */
public interface IdempotencyKeyService {

    IdempotencyKeyResponse create(IdempotencyKeyRequest request);

    List<IdempotencyKeyResponse> findAll();

    Optional<IdempotencyKeyResponse> find(String key);

    IdempotencyKeyResponse get(String key);

    IdempotencyKeyResponse update(String key, IdempotencyKeyRequest request);

    void delete(String key);
}
