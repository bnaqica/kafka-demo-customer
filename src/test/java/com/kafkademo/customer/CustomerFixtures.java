package com.kafkademo.customer;

import com.kafkademo.customer.dto.request.AddressRequest;
import com.kafkademo.customer.dto.request.CustomerRequest;
import com.kafkademo.customer.dto.request.PhoneRequest;
import com.kafkademo.customer.dto.response.AddressResponse;
import com.kafkademo.customer.dto.response.CustomerResponse;
import com.kafkademo.customer.dto.response.PhoneResponse;
import com.kafkademo.customer.entity.Gender;
import com.kafkademo.customer.entity.PhoneType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Shared valid customers, so a test only spells out the values it is about. */
public final class CustomerFixtures {

    public static final Instant CREATED = Instant.parse("2025-06-01T10:00:00Z");
    public static final String VALID_BODY = """
            {"firstName":"Ada","middleName":"King","lastName":"Lovelace","dateOfBirth":"1990-12-10",
             "gender":"FEMALE","email":"ada@example.com",
             "address":{"street":"1 Main St","apartment":"4B","city":"Albany","state":"NY","zip":"12207"},
             "phones":[{"type":"CELL","number":"555-0100"}]}
            """;

    private CustomerFixtures() {}

    public static AddressRequest address() {
        return new AddressRequest("1 Main St", "4B", "Albany", "NY", "12207");
    }

    public static PhoneRequest cell() {
        return new PhoneRequest(PhoneType.CELL, "555-0100");
    }

    public static CustomerRequest request() {
        return request(List.of(cell()));
    }

    public static CustomerRequest request(List<PhoneRequest> phones) {
        return new CustomerRequest(
                "Ada",
                "King",
                "Lovelace",
                LocalDate.of(1990, 12, 10),
                Gender.FEMALE,
                "ada@example.com",
                address(),
                phones);
    }

    public static CustomerResponse response(UUID id, List<PhoneResponse> phones) {
        return CustomerResponse.builder()
                .id(id)
                .firstName("Ada")
                .middleName("King")
                .lastName("Lovelace")
                .dateOfBirth(LocalDate.of(1990, 12, 10))
                .gender(Gender.FEMALE)
                .email("ada@example.com")
                .address(new AddressResponse("1 Main St", "4B", "Albany", "NY", "12207"))
                .phones(phones)
                .createdAt(CREATED)
                .updatedAt(CREATED)
                .build();
    }

    public static PhoneResponse phoneResponse(PhoneType type, String number) {
        return new PhoneResponse(UUID.randomUUID(), type, number);
    }
}
