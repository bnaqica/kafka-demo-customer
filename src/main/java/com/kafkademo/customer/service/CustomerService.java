package com.kafkademo.customer.service;

import com.kafkademo.customer.dto.request.CustomerRequest;
import com.kafkademo.customer.dto.response.CustomerResponse;
import java.util.List;
import java.util.UUID;

public interface CustomerService {

    CustomerResponse create(CustomerRequest request);

    List<CustomerResponse> findAll();

    CustomerResponse get(UUID id);

    CustomerResponse update(UUID id, CustomerRequest request);

    void delete(UUID id);
}
