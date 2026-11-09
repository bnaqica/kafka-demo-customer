package com.kafkademo.customer.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload published to the {@code customer-events} topic. See docs/kafka-events.md for the contract.
 */
public record CustomerCreatedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID customerId,
        String firstName,
        String lastName,
        String email) {

    public static final String TYPE = "CUSTOMER_CREATED";
}
