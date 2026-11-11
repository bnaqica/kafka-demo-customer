package com.kafkademo.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A customer's address. One-to-one, so it is stored as columns on the customer table. */
@Embeddable
public record Address(
        @Column(nullable = false) String street,
        String apartment,
        @Column(nullable = false) String city,

        @Column(nullable = false) @JdbcTypeCode(SqlTypes.CHAR)
        String state,

        @Column(nullable = false) String zip) {}
