package com.kafkademo.customer.service;

import com.kafkademo.customer.dto.IdempotentResult;
import com.kafkademo.customer.dto.request.CustomerRequest;
import com.kafkademo.customer.dto.request.PhoneRequest;
import com.kafkademo.customer.dto.response.CustomerResponse;
import com.kafkademo.customer.dto.response.PhoneResponse;
import java.util.UUID;

/** Every change to a customer or phone, each publishing at most one event. */
public interface CustomerChangeService {

    IdempotentResult<CustomerResponse> create(String idempotencyKey, CustomerRequest request);

    CustomerResponse update(UUID id, CustomerRequest request);

    void delete(UUID id);

    IdempotentResult<PhoneResponse> addPhone(String idempotencyKey, UUID customerId, PhoneRequest request);

    PhoneResponse updatePhone(UUID customerId, UUID phoneId, PhoneRequest request);

    void deletePhone(UUID customerId, UUID phoneId);
}
