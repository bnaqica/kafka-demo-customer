package com.kafkademo.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kafkademo.customer.dto.request.CustomerRequest;
import com.kafkademo.customer.dto.response.CustomerResponse;
import com.kafkademo.customer.entity.Customer;
import com.kafkademo.customer.exception.CustomerNotFoundException;
import com.kafkademo.customer.exception.DuplicateEmailException;
import com.kafkademo.customer.publisher.CustomerEventPublisher;
import com.kafkademo.customer.repository.CustomerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    private CustomerEventPublisher eventPublisher;

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerServiceImpl(repository, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createSavesNormalizedCustomerAndPublishesEvent() {
        when(repository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(repository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse created = service.create(new CustomerRequest(" Ada ", "Lovelace", "Ada@Example.com"));

        assertThat(created.id()).isNotNull();
        assertThat(created.firstName()).isEqualTo("Ada");
        assertThat(created.email()).isEqualTo("ada@example.com");
        assertThat(created.createdAt()).isEqualTo(NOW);
        verify(eventPublisher).publishCustomerCreated(any(Customer.class));
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(repository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CustomerRequest("Ada", "Lovelace", "ada@example.com")))
                .isInstanceOf(DuplicateEmailException.class);
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishCustomerCreated(any());
    }

    @Test
    void updateKeepsIdAndCreatedAt() {
        when(repository.findById(ID)).thenReturn(Optional.of(existing()));
        when(repository.existsByEmailIgnoreCaseAndIdNot("grace@example.com", ID))
                .thenReturn(false);
        when(repository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse updated = service.update(ID, new CustomerRequest("Grace", "Hopper", "Grace@Example.com"));

        assertThat(updated.id()).isEqualTo(ID);
        assertThat(updated.firstName()).isEqualTo("Grace");
        assertThat(updated.email()).isEqualTo("grace@example.com");
        assertThat(updated.createdAt()).isEqualTo(CREATED);
    }

    @Test
    void updateRejectsEmailOwnedByAnotherCustomer() {
        when(repository.findById(ID)).thenReturn(Optional.of(existing()));
        when(repository.existsByEmailIgnoreCaseAndIdNot("grace@example.com", ID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(ID, new CustomerRequest("Grace", "Hopper", "grace@example.com")))
                .isInstanceOf(DuplicateEmailException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deleteRemovesExistingCustomer() {
        Customer customer = existing();
        when(repository.findById(ID)).thenReturn(Optional.of(customer));

        service.delete(ID);

        verify(repository).delete(customer);
    }

    @Test
    void deleteUnknownCustomerThrowsNotFound() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(ID)).isInstanceOf(CustomerNotFoundException.class);
    }

    private static Customer existing() {
        return new Customer(ID, "Ada", "Lovelace", "ada@example.com", CREATED);
    }
}
