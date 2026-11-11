package com.kafkademo.customer.exception;

import java.util.UUID;

public class PhoneNotFoundException extends RuntimeException {

    public PhoneNotFoundException(UUID customerId, UUID phoneId) {
        super("Phone " + phoneId + " not found for customer " + customerId);
    }
}
