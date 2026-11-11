package com.kafkademo.customer.service;

import com.kafkademo.customer.dto.request.PhoneRequest;
import com.kafkademo.customer.dto.response.PhoneResponse;
import com.kafkademo.customer.entity.CustomerPhone;
import com.kafkademo.customer.exception.DuplicatePhoneException;
import com.kafkademo.customer.exception.PhoneNotFoundException;
import com.kafkademo.customer.repository.CustomerPhoneRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class CustomerPhoneServiceImpl implements CustomerPhoneService {

    private final CustomerPhoneRepository repository;

    @Override
    @Transactional
    public PhoneResponse create(UUID customerId, PhoneRequest request) {
        PhoneRequest normalized = request.normalized();
        if (repository.existsByCustomerIdAndTypeAndNumber(customerId, normalized.type(), normalized.number())) {
            throw new DuplicatePhoneException(normalized.type(), normalized.number());
        }
        return toResponse(repository.save(toEntity(UUID.randomUUID(), customerId, normalized)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PhoneResponse> findByCustomer(UUID customerId) {
        return repository.findByCustomerId(customerId).stream()
                .map(CustomerPhoneServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<PhoneResponse>> findByCustomers(Collection<UUID> customerIds) {
        return repository.findByCustomerIdIn(customerIds).stream()
                .collect(Collectors.groupingBy(
                        CustomerPhone::getCustomerId,
                        Collectors.mapping(CustomerPhoneServiceImpl::toResponse, Collectors.toList())));
    }

    @Override
    @Transactional(readOnly = true)
    public PhoneResponse get(UUID customerId, UUID phoneId) {
        return toResponse(find(customerId, phoneId));
    }

    @Override
    @Transactional
    public PhoneResponse update(UUID customerId, UUID phoneId, PhoneRequest request) {
        find(customerId, phoneId);
        PhoneRequest normalized = request.normalized();
        if (repository.existsByCustomerIdAndTypeAndNumberAndIdNot(
                customerId, normalized.type(), normalized.number(), phoneId)) {
            throw new DuplicatePhoneException(normalized.type(), normalized.number());
        }
        return toResponse(repository.save(toEntity(phoneId, customerId, normalized)));
    }

    @Override
    @Transactional
    public void delete(UUID customerId, UUID phoneId) {
        repository.delete(find(customerId, phoneId));
    }

    private CustomerPhone find(UUID customerId, UUID phoneId) {
        return repository
                .findByIdAndCustomerId(phoneId, customerId)
                .orElseThrow(() -> new PhoneNotFoundException(customerId, phoneId));
    }

    private static CustomerPhone toEntity(UUID id, UUID customerId, PhoneRequest request) {
        return CustomerPhone.builder()
                .id(id)
                .customerId(customerId)
                .type(request.type())
                .number(request.number())
                .build();
    }

    private static PhoneResponse toResponse(CustomerPhone phone) {
        return new PhoneResponse(phone.getId(), phone.getType(), phone.getNumber());
    }
}
