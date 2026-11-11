package com.kafkademo.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kafkademo.customer.dto.request.IdempotencyKeyRequest;
import com.kafkademo.customer.dto.response.IdempotencyKeyResponse;
import com.kafkademo.customer.entity.IdempotencyKey;
import com.kafkademo.customer.exception.DuplicateIdempotencyKeyException;
import com.kafkademo.customer.exception.IdempotencyKeyNotFoundException;
import com.kafkademo.customer.repository.IdempotencyKeyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class IdempotencyKeyServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant CREATED = Instant.parse("2025-06-01T10:00:00Z");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final IdempotencyKeyRequest REQUEST =
            new IdempotencyKeyRequest("key-1", "hash", 201, MAPPER.readTree("{\"a\":1}"), UUID.randomUUID());

    @Mock
    private IdempotencyKeyRepository repository;

    private IdempotencyKeyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyKeyServiceImpl(repository, MAPPER, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createStoresTheKey() {
        when(repository.saveAndFlush(any(IdempotencyKey.class))).thenAnswer(inv -> inv.getArgument(0));

        IdempotencyKeyResponse created = service.create(REQUEST);

        assertThat(created.key()).isEqualTo("key-1");
        assertThat(created.responseBody().path("a").asInt()).isEqualTo(1);
        assertThat(created.createdAt()).isEqualTo(NOW);
    }

    @Test
    void createRejectsAnExistingKey() {
        when(repository.saveAndFlush(any(IdempotencyKey.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(REQUEST)).isInstanceOf(DuplicateIdempotencyKeyException.class);
    }

    @Test
    void findIsEmptyForAnUnknownKey() {
        when(repository.findById("nope")).thenReturn(Optional.empty());

        assertThat(service.find("nope")).isEmpty();
    }

    @Test
    void getUnknownKeyThrows() {
        when(repository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("nope")).isInstanceOf(IdempotencyKeyNotFoundException.class);
    }

    @Test
    void updateKeepsTheKeyAndCreatedAt() {
        when(repository.findById("key-1")).thenReturn(Optional.of(existing()));
        when(repository.save(any(IdempotencyKey.class))).thenAnswer(inv -> inv.getArgument(0));

        IdempotencyKeyResponse updated = service.update("key-1", REQUEST);

        assertThat(updated.createdAt()).isEqualTo(CREATED);
        assertThat(updated.requestHash()).isEqualTo("hash");
        assertThat(updated.responseStatus()).isEqualTo(201);
    }

    @Test
    void deleteRemovesTheKey() {
        IdempotencyKey key = existing();
        when(repository.findById("key-1")).thenReturn(Optional.of(key));

        service.delete("key-1");

        verify(repository).delete(key);
    }

    private static IdempotencyKey existing() {
        return IdempotencyKey.builder()
                .key("key-1")
                .requestHash("old")
                .responseStatus(200)
                .responseBody("{}")
                .createdAt(CREATED)
                .build();
    }
}
