package com.kafkademo.customer.dto;

import lombok.Builder;

@Builder
public record ApiAuditEntry(
        String apiPath,
        String action,
        String requestPayload,
        String performedBy,
        int responseStatus,
        boolean success) {}
