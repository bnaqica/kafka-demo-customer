package com.kafkademo.customer.config;

/** The {@code @PreAuthorize} rules used by the controllers, in terms of Keycloak realm roles. */
public final class ApiRoles {

    public static final String ADMIN = "hasRole('customer-admin')";
    public static final String READ = "hasAnyRole('customer-admin', 'customer-viewer')";

    private ApiRoles() {}
}
