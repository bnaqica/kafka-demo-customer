package com.kafkademo.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kafkademo.customer.CustomerFixtures;
import com.kafkademo.customer.dto.response.CustomerResponse;
import com.kafkademo.customer.dto.response.PhoneResponse;
import com.kafkademo.customer.entity.Address;
import com.kafkademo.customer.entity.Customer;
import com.kafkademo.customer.entity.Gender;
import com.kafkademo.customer.entity.PhoneType;
import com.kafkademo.customer.exception.CustomerNotFoundException;
import com.kafkademo.customer.exception.DuplicateEmailException;
import com.kafkademo.customer.repository.CustomerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant CREATED = Instant.parse("2025-06-01T10:00:00Z");
    private static final UUID ID = UUID.randomUUID();

    @Mock
    private CustomerRepository repository;

    @Mock
    private CustomerPhoneService phoneService;

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerServiceImpl(repository, phoneService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createStoresEveryFieldOfTheNormalizedRequest() {
        when(repository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(repository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse created = service.create(CustomerFixtures.request());

        assertThat(created.id()).isNotNull();
        assertThat(created.middleName()).isEqualTo("King");
        assertThat(created.dateOfBirth()).isEqualTo(LocalDate.of(1990, 12, 10));
        assertThat(created.gender()).isEqualTo(Gender.FEMALE);
        assertThat(created.address().state()).isEqualTo("NY");
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(created.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(repository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(CustomerFixtures.request()))
                .isInstanceOf(DuplicateEmailException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void findAllReturnsNewestFirstWithEachCustomersPhones() {
        PhoneResponse phone = CustomerFixtures.phoneResponse(PhoneType.CELL, "555-0100");
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(existing()));
        when(phoneService.findByCustomers(List.of(ID))).thenReturn(Map.of(ID, List.of(phone)));

        assertThat(service.findAll())
                .singleElement()
                .satisfies(customer -> assertThat(customer.phones()).containsExactly(phone));
    }

    @Test
    void findAllGivesAnEmptyListToACustomerWithoutPhones() {
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(existing()));
        when(phoneService.findByCustomers(List.of(ID))).thenReturn(Map.of());

        assertThat(service.findAll().getFirst().phones()).isEmpty();
    }

    @Test
    void getIncludesPhones() {
        PhoneResponse phone = CustomerFixtures.phoneResponse(PhoneType.HOME, "555-0199");
        when(repository.findById(ID)).thenReturn(Optional.of(existing()));
        when(phoneService.findByCustomer(ID)).thenReturn(List.of(phone));

        assertThat(service.get(ID).phones()).containsExactly(phone);
    }

    @Test
    void getUnknownCustomerThrows() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ID)).isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void updateKeepsCreatedAtAndSetsUpdatedAt() {
        when(repository.findById(ID)).thenReturn(Optional.of(existing()));
        when(repository.existsByEmailIgnoreCaseAndIdNot("ada@example.com", ID)).thenReturn(false);
        when(repository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse updated = service.update(ID, CustomerFixtures.request());

        assertThat(updated.createdAt()).isEqualTo(CREATED);
        assertThat(updated.updatedAt()).isEqualTo(NOW);
        assertThat(updated.address().city()).isEqualTo("Albany");
    }

    @Test
    void updateRejectsAnEmailOwnedByAnotherCustomer() {
        when(repository.findById(ID)).thenReturn(Optional.of(existing()));
        when(repository.existsByEmailIgnoreCaseAndIdNot("ada@example.com", ID)).thenReturn(true);

        assertThatThrownBy(() -> service.update(ID, CustomerFixtures.request()))
                .isInstanceOf(DuplicateEmailException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void deleteRemovesTheCustomer() {
        Customer customer = existing();
        when(repository.findById(ID)).thenReturn(Optional.of(customer));

        service.delete(ID);

        verify(repository).delete(customer);
    }

    @Test
    void deleteUnknownCustomerThrows() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(ID)).isInstanceOf(CustomerNotFoundException.class);
    }

    private static Customer existing() {
        return Customer.builder()
                .id(ID)
                .firstName("Ada")
                .lastName("Lovelace")
                .dateOfBirth(LocalDate.of(1990, 12, 10))
                .gender(Gender.FEMALE)
                .email("ada@example.com")
                .address(new Address("1 Main St", null, "Albany", "NY", "12207"))
                .createdAt(CREATED)
                .updatedAt(CREATED)
                .build();
    }
}
