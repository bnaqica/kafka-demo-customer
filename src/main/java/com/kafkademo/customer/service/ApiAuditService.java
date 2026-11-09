package com.kafkademo.customer.service;

import com.kafkademo.customer.dto.ApiAuditEntry;

public interface ApiAuditService {

    void record(ApiAuditEntry entry);
}
