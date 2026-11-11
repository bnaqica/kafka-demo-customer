package com.kafkademo.customer.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kafkademo.customer.entity.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

/** Body of the create and update customer APIs. */
public record CustomerRequest(
        @NotBlank @Size(max = 100) String firstName,
        @Size(max = 100) String middleName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull @Past LocalDate dateOfBirth,
        @NotNull Gender gender,
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull @Valid AddressRequest address,
        List<@Valid @NotNull PhoneRequest> phones) {

    public CustomerRequest {
        phones = phones == null ? List.of() : phones;
    }

    /** The form everything is compared and stored in, so whitespace or email case alone is never a change. */
    public CustomerRequest normalized() {
        return new CustomerRequest(
                firstName.trim(),
                blankToNull(middleName),
                lastName.trim(),
                dateOfBirth,
                gender,
                email.trim().toLowerCase(),
                address.normalized(),
                phones.stream().map(PhoneRequest::normalized).toList());
    }

    @JsonIgnore
    @AssertTrue(message = "must not list the same type and number twice")
    public boolean isPhonesDistinct() {
        if (phones.stream().anyMatch(phone -> phone == null || phone.number() == null)) {
            return true; // reported by the field constraints
        }
        return new HashSet<>(phones.stream().map(PhoneRequest::normalized).toList()).size() == phones.size();
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
