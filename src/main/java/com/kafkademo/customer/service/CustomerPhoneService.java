package com.kafkademo.customer.service;

import com.kafkademo.customer.dto.request.PhoneRequest;
import com.kafkademo.customer.dto.response.PhoneResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** CRUD for the customer_phone table. */
public interface CustomerPhoneService {

    PhoneResponse create(UUID customerId, PhoneRequest request);

    List<PhoneResponse> findByCustomer(UUID customerId);

    Map<UUID, List<PhoneResponse>> findByCustomers(Collection<UUID> customerIds);

    PhoneResponse get(UUID customerId, UUID phoneId);

    PhoneResponse update(UUID customerId, UUID phoneId, PhoneRequest request);

    void delete(UUID customerId, UUID phoneId);
}
