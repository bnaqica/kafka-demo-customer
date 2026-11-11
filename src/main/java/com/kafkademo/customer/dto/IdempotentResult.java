package com.kafkademo.customer.dto;

/** The outcome of a POST, either new or returned from an earlier request with the same key. */
public record IdempotentResult<T>(int status, T body) {}
