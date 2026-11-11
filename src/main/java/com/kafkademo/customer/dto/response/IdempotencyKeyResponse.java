package com.kafkademo.customer.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import tools.jackson.databind.JsonNode;

@Builder
public record IdempotencyKeyResponse(
        String key,
        String requestHash,
        int responseStatus,
        JsonNode responseBody,
        UUID customerId,
        Instant createdAt) {}
