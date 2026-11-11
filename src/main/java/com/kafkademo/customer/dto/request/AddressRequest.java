package com.kafkademo.customer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 255) String street,
        @Size(max = 50) String apartment,
        @NotBlank @Size(max = 100) String city,

        @NotNull @Pattern(regexp = "[A-Z]{2}", message = "must be a two-letter US state code")
        String state,

        @NotNull @Pattern(regexp = "\\d{5}(-\\d{4})?", message = "must be 5 digits or ZIP+4")
        String zip) {

    AddressRequest normalized() {
        return new AddressRequest(street.trim(), CustomerRequest.blankToNull(apartment), city.trim(), state, zip);
    }
}
