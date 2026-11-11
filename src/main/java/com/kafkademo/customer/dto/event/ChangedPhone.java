package com.kafkademo.customer.dto.event;

import com.kafkademo.customer.entity.PhoneType;

public record ChangedPhone(PhoneType type, String number) {}
