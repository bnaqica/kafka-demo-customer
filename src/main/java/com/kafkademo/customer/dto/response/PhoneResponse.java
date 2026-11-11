package com.kafkademo.customer.dto.response;

import com.kafkademo.customer.entity.PhoneType;
import java.util.UUID;

public record PhoneResponse(UUID id, PhoneType type, String number) {}
