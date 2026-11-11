package com.kafkademo.customer.dto.event;

import java.time.Instant;
import java.util.UUID;

/** Payload published to the {@code customer-change-event} topic. See docs/kafka-events.md for the contract. */
public record CustomerChangeEvent(
        UUID eventId,
        CustomerEventType eventType,
        Instant occurredAt,
        UUID customerId,
        String firstName,
        String lastName,
        String email,
        String state,
        CustomerChanges changes) {}
