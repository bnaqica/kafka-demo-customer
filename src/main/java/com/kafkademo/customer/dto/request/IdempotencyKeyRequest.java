package com.kafkademo.customer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record IdempotencyKeyRequest(
        @NotBlank @Size(max = 255) String key,
        @NotBlank @Size(max = 64) String requestHash,
        @NotNull Integer responseStatus,
        @NotNull JsonNode responseBody,
        UUID customerId) {}
