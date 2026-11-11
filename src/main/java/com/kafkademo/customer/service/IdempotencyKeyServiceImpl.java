package com.kafkademo.customer.service;

import com.kafkademo.customer.dto.request.IdempotencyKeyRequest;
import com.kafkademo.customer.dto.response.IdempotencyKeyResponse;
import com.kafkademo.customer.entity.IdempotencyKey;
import com.kafkademo.customer.exception.DuplicateIdempotencyKeyException;
import com.kafkademo.customer.exception.IdempotencyKeyNotFoundException;
import com.kafkademo.customer.repository.IdempotencyKeyRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
@Service
public class IdempotencyKeyServiceImpl implements IdempotencyKeyService {

    private final IdempotencyKeyRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Override
    @Transactional
    public IdempotencyKeyResponse create(IdempotencyKeyRequest request) {
        try {
            // Flushed now so a concurrent request with this key waits here, before any other work
            return toResponse(repository.saveAndFlush(toEntity(request.key(), request, Instant.now(clock), true)));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateIdempotencyKeyException(request.key());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<IdempotencyKeyResponse> findAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdempotencyKeyResponse> find(String key) {
        return repository.findById(key).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public IdempotencyKeyResponse get(String key) {
        return toResponse(load(key));
    }

    @Override
    @Transactional
    public IdempotencyKeyResponse update(String key, IdempotencyKeyRequest request) {
        return toResponse(repository.save(toEntity(key, request, load(key).getCreatedAt(), false)));
    }

    @Override
    @Transactional
    public void delete(String key) {
        repository.delete(load(key));
    }

    private IdempotencyKey load(String key) {
        return repository.findById(key).orElseThrow(() -> new IdempotencyKeyNotFoundException(key));
    }

    private static IdempotencyKey toEntity(
            String key, IdempotencyKeyRequest request, Instant createdAt, boolean newKey) {
        return IdempotencyKey.builder()
                .key(key)
                .requestHash(request.requestHash())
                .responseStatus(request.responseStatus())
                .responseBody(request.responseBody().toString())
                .customerId(request.customerId())
                .createdAt(createdAt)
                .newKey(newKey)
                .build();
    }

    private IdempotencyKeyResponse toResponse(IdempotencyKey key) {
        return IdempotencyKeyResponse.builder()
                .key(key.getKey())
                .requestHash(key.getRequestHash())
                .responseStatus(key.getResponseStatus())
                .responseBody(jsonMapper.readTree(key.getResponseBody()))
                .customerId(key.getCustomerId())
                .createdAt(key.getCreatedAt())
                .build();
    }
}
