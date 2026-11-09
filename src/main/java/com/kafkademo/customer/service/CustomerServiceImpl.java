package com.kafkademo.customer.service;

import com.kafkademo.customer.dto.request.CustomerRequest;
import com.kafkademo.customer.dto.response.CustomerResponse;
import com.kafkademo.customer.entity.Customer;
import com.kafkademo.customer.exception.CustomerNotFoundException;
import com.kafkademo.customer.exception.DuplicateEmailException;
import com.kafkademo.customer.publisher.CustomerEventPublisher;
import com.kafkademo.customer.repository.CustomerRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository repository;
    private final CustomerEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        String email = normalizeEmail(request);
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException(email);
        }
        Customer customer = repository.save(toEntity(UUID.randomUUID(), request, email, Instant.now(clock)));
        log.info("Created customer {}", customer.getId());
        // Published after commit, so consumers never see a customer that was rolled back
        eventPublisher.publishCustomerCreated(customer);
        return toResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> findAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Override
    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer existing = find(id);
        String email = normalizeEmail(request);
        if (repository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new DuplicateEmailException(email);
        }
        Customer updated = repository.save(toEntity(id, request, email, existing.getCreatedAt()));
        log.info("Updated customer {}", id);
        return toResponse(updated);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        repository.delete(find(id));
        log.info("Deleted customer {}", id);
    }

    private Customer find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    private static String normalizeEmail(CustomerRequest request) {
        return request.email().trim().toLowerCase();
    }

    private static Customer toEntity(UUID id, CustomerRequest request, String email, Instant createdAt) {
        return Customer.builder()
                .id(id)
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(email)
                .createdAt(createdAt)
                .build();
    }

    private CustomerResponse toResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .email(customer.getEmail())
                .createdAt(customer.getCreatedAt())
                .build();
    }
}
