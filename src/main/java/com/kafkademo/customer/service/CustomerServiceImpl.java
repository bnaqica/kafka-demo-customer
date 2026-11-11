package com.kafkademo.customer.service;

import com.kafkademo.customer.dto.request.CustomerRequest;
import com.kafkademo.customer.dto.response.AddressResponse;
import com.kafkademo.customer.dto.response.CustomerResponse;
import com.kafkademo.customer.dto.response.PhoneResponse;
import com.kafkademo.customer.entity.Address;
import com.kafkademo.customer.entity.Customer;
import com.kafkademo.customer.exception.CustomerNotFoundException;
import com.kafkademo.customer.exception.DuplicateEmailException;
import com.kafkademo.customer.repository.CustomerRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    private final CustomerPhoneService phoneService;
    private final Clock clock;

    @Override
    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        CustomerRequest normalized = request.normalized();
        if (repository.existsByEmailIgnoreCase(normalized.email())) {
            throw new DuplicateEmailException(normalized.email());
        }
        Instant now = Instant.now(clock);
        Customer customer = repository.save(toEntity(UUID.randomUUID(), normalized, now, now));
        log.info("Created customer {}", customer.getId());
        return toResponse(customer, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> findAll() {
        List<Customer> customers = repository.findAllByOrderByCreatedAtDesc();
        Map<UUID, List<PhoneResponse>> phones = phoneService.findByCustomers(
                customers.stream().map(Customer::getId).toList());
        return customers.stream()
                .map(customer -> toResponse(customer, phones.getOrDefault(customer.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse get(UUID id) {
        return toResponse(find(id), phoneService.findByCustomer(id));
    }

    @Override
    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer existing = find(id);
        CustomerRequest normalized = request.normalized();
        if (repository.existsByEmailIgnoreCaseAndIdNot(normalized.email(), id)) {
            throw new DuplicateEmailException(normalized.email());
        }
        Customer updated = repository.save(toEntity(id, normalized, existing.getCreatedAt(), Instant.now(clock)));
        log.info("Updated customer {}", id);
        return toResponse(updated, phoneService.findByCustomer(id));
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

    private static Customer toEntity(UUID id, CustomerRequest request, Instant createdAt, Instant updatedAt) {
        return Customer.builder()
                .id(id)
                .firstName(request.firstName())
                .middleName(request.middleName())
                .lastName(request.lastName())
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender())
                .email(request.email())
                .address(new Address(
                        request.address().street(),
                        request.address().apartment(),
                        request.address().city(),
                        request.address().state(),
                        request.address().zip()))
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    private static CustomerResponse toResponse(Customer customer, List<PhoneResponse> phones) {
        Address address = customer.getAddress();
        return CustomerResponse.builder()
                .id(customer.getId())
                .firstName(customer.getFirstName())
                .middleName(customer.getMiddleName())
                .lastName(customer.getLastName())
                .dateOfBirth(customer.getDateOfBirth())
                .gender(customer.getGender())
                .email(customer.getEmail())
                .address(new AddressResponse(
                        address.street(), address.apartment(), address.city(), address.state(), address.zip()))
                .phones(phones)
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }
}
