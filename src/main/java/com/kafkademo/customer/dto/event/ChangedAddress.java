package com.kafkademo.customer.dto.event;

public record ChangedAddress(String street, String apartment, String city, String state, String zip) {}
