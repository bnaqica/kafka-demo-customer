# customer-api-access Specification

## Purpose

Defines who can call the customer API, in every profile, and how denied requests are recorded in the audit schema.

## Requirements

### Requirement: Token required in every profile
Every endpoint except the health check SHALL require a valid `kafka-demo` realm bearer token issued for the `kafka-demo-api` audience in every profile, including `local`. In the `local` profile only, the Swagger pages SHALL be open.

#### Scenario: No token
- **WHEN** a request without a token calls a customer endpoint in any profile
- **THEN** the API responds 401

#### Scenario: Token for another audience
- **WHEN** a request calls a customer endpoint with a valid realm token whose audience does not include `kafka-demo-api`
- **THEN** the API responds 401

#### Scenario: Swagger in local
- **WHEN** the Swagger page is requested without a token in the `local` profile
- **THEN** the page is returned

#### Scenario: Swagger elsewhere
- **WHEN** the API docs are requested in a profile other than `local`
- **THEN** they are not served

### Requirement: Reads allowed for both roles
GET endpoints for customers and phones SHALL be allowed for `customer-admin` and `customer-viewer`.

#### Scenario: Viewer lists customers
- **WHEN** a `customer-viewer` calls `GET /api/customers`
- **THEN** the API responds 200

### Requirement: Writes limited to customer-admin
POST, PUT and DELETE on customers and phones, and every idempotency key endpoint, SHALL require `customer-admin`.

#### Scenario: Viewer tries to delete
- **WHEN** a `customer-viewer` calls `DELETE /api/customers/{id}`
- **THEN** the API responds 403 and the customer still exists

#### Scenario: Viewer reads idempotency keys
- **WHEN** a `customer-viewer` calls `GET /api/idempotency-keys`
- **THEN** the API responds 403

#### Scenario: Admin creates a customer
- **WHEN** a `customer-admin` posts a valid customer
- **THEN** the API responds 201

### Requirement: Denied writes are audited
Every POST, PUT and DELETE SHALL write an audit row, including those denied for a missing token or role, recording the API, action, payload, person, status and success.

#### Scenario: Viewer denied
- **WHEN** a `customer-viewer` attempts a POST
- **THEN** an audit row records that user, status 403 and success false

#### Scenario: Anonymous denied
- **WHEN** a POST arrives without a token
- **THEN** an audit row records `anonymous`, status 401 and success false
