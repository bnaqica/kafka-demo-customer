package com.kafkademo.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/** The stored result of a POST, so a repeat of the same request returns it instead of changing anything. */
@Entity
@Table(name = "idempotency_key")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
@AllArgsConstructor
public class IdempotencyKey implements Persistable<String> {

    @Id
    private String key;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String responseBody;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** True only when reserving a key, so saving inserts and a taken key fails instead of being overwritten. */
    @Transient
    private boolean newKey;

    @Override
    public String getId() {
        return key;
    }

    @Override
    public boolean isNew() {
        return newKey;
    }
}
