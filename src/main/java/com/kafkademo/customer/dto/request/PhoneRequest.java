package com.kafkademo.customer.dto.request;

import com.kafkademo.customer.entity.PhoneType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PhoneRequest(
        @NotNull PhoneType type,

        @NotNull
        @Pattern(
                regexp = "\\s*[0-9+()-][0-9 +()-]{5,18}[0-9+()-]\\s*",
                message = "must be 7 to 20 digits, spaces or + - ( )")
        String number) {

    public PhoneRequest normalized() {
        return new PhoneRequest(type, number.trim());
    }
}
