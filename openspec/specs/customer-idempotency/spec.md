# customer-idempotency Specification

## Purpose

Makes POST requests safe to repeat, so a retry neither creates a second record nor publishes a second event.

## Requirements

### Requirement: Idempotency key required
`POST /api/customers` and `POST /api/customers/{customerId}/phones` SHALL require an `Idempotency-Key` header of 1 to 255 characters.

#### Scenario: Missing header
- **WHEN** a POST is sent without the header
- **THEN** the API responds 400 and nothing is stored

### Requirement: Repeated request returns the original result
A successful POST SHALL store its key with its response. A repeat with the same key and same method, path and body SHALL return the stored status and body without changing data or publishing.

#### Scenario: Client retries
- **WHEN** the same POST with the same key is sent twice
- **THEN** both responses have the same status and body, one record exists and one event is published

#### Scenario: Concurrent requests with the same key
- **WHEN** two identical POSTs with the same key arrive at the same time
- **THEN** one is processed, the other waits for it and returns the same status and body, and one event is published

#### Scenario: Customer deleted before the retry
- **WHEN** a customer is deleted and its original POST is then repeated with the same key
- **THEN** the original response is returned and no customer is created

### Requirement: Key reused for a different request
A key SHALL only be valid for the request it was first used with.

#### Scenario: Different body
- **WHEN** a POST uses an existing key with a different method, path or body
- **THEN** the API responds 422 and nothing changes

### Requirement: Failed requests do not consume a key
A POST that fails SHALL NOT store its key.

#### Scenario: Retry after a conflict is resolved
- **WHEN** a POST fails with 409 and is later repeated with the same key after the conflict is gone
- **THEN** the request is processed as new

#### Scenario: Phone posted to an unknown customer
- **WHEN** a phone POST responds 404 because the customer does not exist
- **THEN** its key is not stored

### Requirement: Idempotency key CRUD
The API SHALL support `POST` and `GET` on `/api/idempotency-keys` and `GET`, `PUT` and `DELETE` on `/api/idempotency-keys/{key}`.

#### Scenario: Delete a key
- **WHEN** a key is deleted
- **THEN** the API responds 204 and a later POST with that key is treated as new

#### Scenario: Create an existing key
- **WHEN** a client posts a key that already exists to `/api/idempotency-keys`
- **THEN** the API responds 409

#### Scenario: Unknown key
- **WHEN** a client requests a key that does not exist
- **THEN** the API responds 404
