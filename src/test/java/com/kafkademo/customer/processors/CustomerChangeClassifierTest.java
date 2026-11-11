package com.kafkademo.customer.processors;

import static com.kafkademo.customer.CustomerFixtures.phoneResponse;
import static org.assertj.core.api.Assertions.assertThat;

import com.kafkademo.customer.CustomerFixtures;
import com.kafkademo.customer.dto.CustomerChange;
import com.kafkademo.customer.dto.event.ChangedAddress;
import com.kafkademo.customer.dto.event.ChangedPhone;
import com.kafkademo.customer.dto.event.CustomerEventType;
import com.kafkademo.customer.dto.request.AddressRequest;
import com.kafkademo.customer.dto.request.CustomerRequest;
import com.kafkademo.customer.dto.request.PhoneRequest;
import com.kafkademo.customer.dto.response.CustomerResponse;
import com.kafkademo.customer.dto.response.PhoneResponse;
import com.kafkademo.customer.entity.Gender;
import com.kafkademo.customer.entity.PhoneType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerChangeClassifierTest {

    private static final PhoneResponse CELL = phoneResponse(PhoneType.CELL, "555-0100");
    private static final PhoneRequest CELL_REQUEST = new PhoneRequest(PhoneType.CELL, "555-0100");
    private static final AddressRequest NEW_ADDRESS = new AddressRequest("9 Elm St", null, "Reno", "NV", "89501");

    private final CustomerChangeClassifier classifier = new CustomerChangeClassifier();
    private final CustomerResponse before = CustomerFixtures.response(UUID.randomUUID(), List.of(CELL));

    @Test
    void identicalValuesAreNoChange() {
        assertThat(classifier.classify(before, request("Lovelace", CustomerFixtures.address(), List.of(CELL_REQUEST))))
                .isEmpty();
    }

    @Test
    void addressOnlyIsAnAddressChange() {
        CustomerChange change = change(request("Lovelace", NEW_ADDRESS, List.of(CELL_REQUEST)));

        assertThat(change.type()).isEqualTo(CustomerEventType.ADDRESS_CHANGE);
        assertThat(change.changes().address()).isEqualTo(new ChangedAddress("9 Elm St", null, "Reno", "NV", "89501"));
    }

    @Test
    void oneAddedPhoneIsAPhoneAdd() {
        PhoneRequest added = new PhoneRequest(PhoneType.HOME, "555-0199");

        CustomerChange change = change(phones(CELL_REQUEST, added));

        assertThat(change.type()).isEqualTo(CustomerEventType.PHONE_ADD);
        assertThat(change.changes().phone()).isEqualTo(new ChangedPhone(PhoneType.HOME, "555-0199"));
    }

    @Test
    void oneRemovedPhoneIsAPhoneDelete() {
        CustomerChange change = change(phones());

        assertThat(change.type()).isEqualTo(CustomerEventType.PHONE_DELETE);
        assertThat(change.changes().phone()).isEqualTo(new ChangedPhone(PhoneType.CELL, "555-0100"));
    }

    @Test
    void onePhoneReplacedBySameTypeIsAPhoneChange() {
        CustomerChange change = change(phones(new PhoneRequest(PhoneType.CELL, "555-0111")));

        assertThat(change.type()).isEqualTo(CustomerEventType.PHONE_CHANGE);
        assertThat(change.changes().previousPhone()).isEqualTo(new ChangedPhone(PhoneType.CELL, "555-0100"));
        assertThat(change.changes().phone()).isEqualTo(new ChangedPhone(PhoneType.CELL, "555-0111"));
    }

    @Test
    void onePhoneReplacedByAnotherTypeIsACustomerChange() {
        CustomerChange change = change(phones(new PhoneRequest(PhoneType.HOME, "555-0111")));

        assertThat(change.type()).isEqualTo(CustomerEventType.CHANGE);
        assertThat(change.changes().fields()).containsExactly("phones");
    }

    @Test
    void twoAddedPhonesIsACustomerChange() {
        CustomerChange change = change(phones(
                CELL_REQUEST,
                new PhoneRequest(PhoneType.HOME, "555-0199"),
                new PhoneRequest(PhoneType.WORK, "555-0188")));

        assertThat(change.type()).isEqualTo(CustomerEventType.CHANGE);
        assertThat(change.changes().fields()).containsExactly("phones");
    }

    @Test
    void reorderedPhonesAreNoChange() {
        PhoneResponse home = phoneResponse(PhoneType.HOME, "555-0199");
        CustomerResponse twoPhones = CustomerFixtures.response(UUID.randomUUID(), List.of(CELL, home));
        CustomerRequest request = phones(new PhoneRequest(PhoneType.HOME, "555-0199"), CELL_REQUEST);

        assertThat(classifier.classify(twoPhones, request)).isEmpty();
    }

    @Test
    void severalAreasIsACustomerChangeListingEach() {
        CustomerChange change = change(request("Byron", NEW_ADDRESS, List.of(CELL_REQUEST)));

        assertThat(change.type()).isEqualTo(CustomerEventType.CHANGE);
        assertThat(change.changes().fields()).containsExactly("lastName", "address");
    }

    @Test
    void addressAndPhoneTogetherIsACustomerChange() {
        CustomerChange change = change(request("Lovelace", NEW_ADDRESS, List.of()));

        assertThat(change.type()).isEqualTo(CustomerEventType.CHANGE);
        assertThat(change.changes().fields()).containsExactly("address", "phones");
    }

    @Test
    void everyProfileFieldIsDetected() {
        CustomerRequest request = new CustomerRequest(
                "Augusta",
                null,
                "Byron",
                LocalDate.of(1991, 1, 1),
                Gender.PREFER_NOT_TO_SAY,
                "new@example.com",
                CustomerFixtures.address(),
                List.of(CELL_REQUEST));

        assertThat(change(request).changes().fields())
                .containsExactly("firstName", "middleName", "lastName", "dateOfBirth", "gender", "email");
    }

    @Test
    void customerFieldsChangedIsFalseWhenOnlyPhonesDiffer() {
        assertThat(classifier.customerFieldsChanged(before, phones())).isFalse();
    }

    @Test
    void customerFieldsChangedIsTrueForAnAddressChange() {
        assertThat(classifier.customerFieldsChanged(before, request("Lovelace", NEW_ADDRESS, List.of(CELL_REQUEST))))
                .isTrue();
    }

    private CustomerChange change(CustomerRequest request) {
        Optional<CustomerChange> change = classifier.classify(before, request);
        assertThat(change).isPresent();
        return change.orElseThrow();
    }

    private static CustomerRequest phones(PhoneRequest... phones) {
        return request("Lovelace", CustomerFixtures.address(), List.of(phones));
    }

    private static CustomerRequest request(String lastName, AddressRequest address, List<PhoneRequest> phones) {
        return new CustomerRequest(
                "Ada", "King", lastName, LocalDate.of(1990, 12, 10), Gender.FEMALE, "ada@example.com", address, phones);
    }
}
