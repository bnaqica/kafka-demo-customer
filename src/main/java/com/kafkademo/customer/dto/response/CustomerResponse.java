package com.kafkademo.customer.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CustomerResponse(UUID id, String firstName, String lastName, String email, Instant createdAt) {}
