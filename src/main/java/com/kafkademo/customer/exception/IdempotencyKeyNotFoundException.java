package com.kafkademo.customer.exception;

public class IdempotencyKeyNotFoundException extends RuntimeException {

    public IdempotencyKeyNotFoundException(String key) {
        super("Idempotency key " + key + " not found");
    }
}
