package com.kafkademo.customer.exception;

public class DuplicateIdempotencyKeyException extends RuntimeException {

    public DuplicateIdempotencyKeyException(String key) {
        super("Idempotency key " + key + " already exists");
    }
}
