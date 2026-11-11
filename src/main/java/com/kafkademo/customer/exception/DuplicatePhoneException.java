package com.kafkademo.customer.exception;

import com.kafkademo.customer.entity.PhoneType;

public class DuplicatePhoneException extends RuntimeException {

    public DuplicatePhoneException(PhoneType type, String number) {
        super("Customer already has " + type + " phone " + number);
    }
}
