package com.kafkademo.customer.dto.response;

import com.kafkademo.customer.entity.Gender;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record CustomerResponse(
        UUID id,
        String firstName,
        String middleName,
        String lastName,
        LocalDate dateOfBirth,
        Gender gender,
        String email,
        AddressResponse address,
        List<PhoneResponse> phones,
        Instant createdAt,
        Instant updatedAt) {}
