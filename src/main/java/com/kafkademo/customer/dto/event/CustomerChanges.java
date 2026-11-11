package com.kafkademo.customer.dto.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** What changed. Which fields are present depends on the event type; see docs/kafka-events.md. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CustomerChanges(
        List<String> fields, ChangedAddress address, ChangedPhone phone, ChangedPhone previousPhone) {}
