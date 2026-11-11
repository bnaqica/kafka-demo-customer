package com.kafkademo.customer.dto.event;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** The kinds of customer change published to {@code customer-change-event}. See docs/kafka-events.md. */
@Getter
@RequiredArgsConstructor
public enum CustomerEventType {
    ADDITION("customer-addition"),
    CHANGE("customer-change"),
    ADDRESS_CHANGE("customer-address-change"),
    PHONE_ADD("customer-phone-add"),
    PHONE_CHANGE("customer-phone-change"),
    PHONE_DELETE("customer-phone-delete"),
    DELETE("customer-delete");

    @JsonValue
    private final String wireName;
}
