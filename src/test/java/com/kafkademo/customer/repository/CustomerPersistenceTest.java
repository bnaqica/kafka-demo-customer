package com.kafkademo.customer.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kafkademo.customer.TestcontainersConfiguration;
import com.kafkademo.customer.entity.Address;
import com.kafkademo.customer.entity.Customer;
import com.kafkademo.customer.entity.CustomerPhone;
import com.kafkademo.customer.entity.Gender;
import com.kafkademo.customer.entity.IdempotencyKey;
import com.kafkademo.customer.entity.PhoneType;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CustomerPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private CustomerPhoneRepository phones;

    @Autowired
    private IdempotencyKeyRepository keys;

    @Autowired
    private EntityManager entityManager;

    @Test
    void storesEveryCustomerFieldWithItsAddress() {
        Customer saved = customers.saveAndFlush(customer("ada@example.com"));
        entityManager.clear();

        Customer found = customers.findById(saved.getId()).orElseThrow();

        assertThat(found.getMiddleName()).isEqualTo("King");
        assertThat(found.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 12, 10));
        assertThat(found.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(found.getAddress()).isEqualTo(new Address("1 Main St", "4B", "Albany", "NY", "12207"));
        assertThat(found.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void optionalFieldsMayBeEmpty() {
        Customer customer = Customer.builder()
                .id(UUID.randomUUID())
                .firstName("Alan")
                .lastName("Turing")
                .dateOfBirth(LocalDate.of(1990, 6, 23))
                .gender(Gender.MALE)
                .email("alan@example.com")
                .address(new Address("2 Oak Ave", null, "Reno", "NV", "89501"))
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();

        customers.saveAndFlush(customer);
        entityManager.clear();

        assertThat(customers.findById(customer.getId()).orElseThrow().getMiddleName())
                .isNull();
    }

    @Test
    void rejectsSameEmailInDifferentCase() {
        customers.saveAndFlush(customer("ada@example.com"));

        assertThatThrownBy(() -> customers.saveAndFlush(customer("ADA@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSamePhoneTwiceOnOneCustomer() {
        Customer owner = customers.saveAndFlush(customer("ada@example.com"));
        phones.saveAndFlush(phone(owner.getId(), PhoneType.CELL, "555-0100"));

        assertThatThrownBy(() -> phones.saveAndFlush(phone(owner.getId(), PhoneType.CELL, "555-0100")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameNumberWithDifferentType() {
        Customer owner = customers.saveAndFlush(customer("ada@example.com"));
        phones.saveAndFlush(phone(owner.getId(), PhoneType.CELL, "555-0100"));
        phones.saveAndFlush(phone(owner.getId(), PhoneType.HOME, "555-0100"));

        assertThat(phones.findByCustomerId(owner.getId())).hasSize(2);
    }

    @Test
    void deletingCustomerDeletesItsPhones() {
        Customer owner = customers.saveAndFlush(customer("ada@example.com"));
        phones.saveAndFlush(phone(owner.getId(), PhoneType.CELL, "555-0100"));
        entityManager.clear();

        customers.deleteById(owner.getId());
        customers.flush();
        entityManager.clear();

        assertThat(phones.findByCustomerId(owner.getId())).isEmpty();
    }

    @Test
    void storesIdempotencyKeyThatOutlivesTheCustomer() {
        keys.saveAndFlush(IdempotencyKey.builder()
                .key("key-1")
                .requestHash("hash")
                .responseStatus(201)
                .responseBody("{\"id\":\"x\"}")
                .customerId(UUID.randomUUID())
                .createdAt(NOW)
                .build());
        entityManager.clear();

        IdempotencyKey found = keys.findById("key-1").orElseThrow();

        assertThat(found.getResponseStatus()).isEqualTo(201);
        assertThat(found.getResponseBody()).contains("\"id\"");
    }

    @Test
    void reservingAKeyTwiceFailsRatherThanOverwriting() {
        keys.saveAndFlush(newKey("taken", "first"));
        entityManager.clear();

        assertThatThrownBy(() -> keys.saveAndFlush(newKey("taken", "second")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static IdempotencyKey newKey(String key, String hash) {
        return IdempotencyKey.builder()
                .key(key)
                .requestHash(hash)
                .responseStatus(201)
                .responseBody("{}")
                .createdAt(NOW)
                .newKey(true)
                .build();
    }

    private static Customer customer(String email) {
        return Customer.builder()
                .id(UUID.randomUUID())
                .firstName("Ada")
                .middleName("King")
                .lastName("Lovelace")
                .dateOfBirth(LocalDate.of(1990, 12, 10))
                .gender(Gender.FEMALE)
                .email(email)
                .address(new Address("1 Main St", "4B", "Albany", "NY", "12207"))
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private static CustomerPhone phone(UUID customerId, PhoneType type, String number) {
        return CustomerPhone.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .type(type)
                .number(number)
                .build();
    }
}
