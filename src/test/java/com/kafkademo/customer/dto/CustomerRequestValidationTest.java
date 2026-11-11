package com.kafkademo.customer.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.kafkademo.customer.CustomerFixtures;
import com.kafkademo.customer.dto.request.AddressRequest;
import com.kafkademo.customer.dto.request.CustomerRequest;
import com.kafkademo.customer.dto.request.PhoneRequest;
import com.kafkademo.customer.entity.Gender;
import com.kafkademo.customer.entity.PhoneType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CustomerRequestValidationTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAValidCustomer() {
        assertThat(validator.validate(CustomerFixtures.request())).isEmpty();
    }

    @Test
    void acceptsNoOptionalFieldsAndNoPhones() {
        CustomerRequest request = new CustomerRequest(
                "Ada",
                null,
                "Lovelace",
                LocalDate.of(1990, 12, 10),
                Gender.FEMALE,
                "ada@example.com",
                new AddressRequest("1 Main St", null, "Albany", "NY", "12207"),
                null);

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.phones()).isEmpty();
    }

    @Test
    void rejectsMissingDateOfBirth() {
        assertThat(validator.validate(withDateOfBirth(null))).isNotEmpty();
    }

    @Test
    void rejectsFutureDateOfBirth() {
        assertThat(validator.validate(withDateOfBirth(LocalDate.now().plusDays(1))))
                .isNotEmpty();
    }

    @Test
    void rejectsMissingAddress() {
        CustomerRequest request = new CustomerRequest(
                "Ada", null, "Lovelace", LocalDate.of(1990, 12, 10), Gender.FEMALE, "ada@example.com", null, List.of());

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234", "123456", "12345-12", "abcde"})
    void rejectsInvalidZip(String zip) {
        assertThat(validator.validate(new AddressRequest("1 Main St", null, "Albany", "NY", zip)))
                .isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "12345-6789"})
    void acceptsValidZip(String zip) {
        assertThat(validator.validate(new AddressRequest("1 Main St", null, "Albany", "NY", zip)))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ny", "N", "NYC", "N1"})
    void rejectsInvalidState(String state) {
        assertThat(validator.validate(new AddressRequest("1 Main St", null, "Albany", state, "12207")))
                .isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456", "123456789012345678901", "555-CALL-ME", "       ", "   1   "})
    void rejectsInvalidPhoneNumber(String number) {
        assertThat(validator.validate(new PhoneRequest(PhoneType.CELL, number))).isNotEmpty();
    }

    @Test
    void rejectsTheSamePhoneListedTwice() {
        CustomerRequest request = CustomerFixtures.request(List.of(CustomerFixtures.cell(), CustomerFixtures.cell()));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void normalizedTrimsAndLowercasesEmailAndBlanksOptionalFields() {
        CustomerRequest request = new CustomerRequest(
                " Ada ",
                "  ",
                " Lovelace ",
                LocalDate.of(1990, 12, 10),
                Gender.FEMALE,
                " Ada@Example.COM ",
                new AddressRequest(" 1 Main St ", " ", " Albany ", "NY", "12207"),
                List.of(new PhoneRequest(PhoneType.CELL, " 555-0100 ")));

        CustomerRequest normalized = request.normalized();

        assertThat(normalized.firstName()).isEqualTo("Ada");
        assertThat(normalized.middleName()).isNull();
        assertThat(normalized.email()).isEqualTo("ada@example.com");
        assertThat(normalized.address().street()).isEqualTo("1 Main St");
        assertThat(normalized.address().apartment()).isNull();
        assertThat(normalized.phones().getFirst().number()).isEqualTo("555-0100");
    }

    private static CustomerRequest withDateOfBirth(LocalDate dateOfBirth) {
        return new CustomerRequest(
                "Ada",
                null,
                "Lovelace",
                dateOfBirth,
                Gender.FEMALE,
                "ada@example.com",
                CustomerFixtures.address(),
                List.of());
    }
}
