# Tasks

## 1. Local infrastructure (local-docker)

- [ ] 1.1 In `keycloak/realms/kafka-demo-realm.json`, remove the undefined `viewer` role from `kafkaDemoAdmin` and correct the role descriptions (`customer-admin`: full customer access and emails; `customer-viewer`: read customers and analytics); verify the file parses and every user role is defined
- [ ] 1.2 Update the Keycloak section of `local-docker/README.md` with the current users and roles and how to re-import the realm; verify a documented token command returns a token containing the expected role
- [ ] 1.3 In `kafka/create-topics.sh`, replace `customer-events`, `email-events` and their `-dlq` topics with `customer-change-event` and `customer-change-event-dlq`; verify with the Kafka topic list after `docker compose up kafka-setup`

## 2. Data model

- [ ] 2.1 Add `V3__expand_customer.sql` per design.md and verify with the Testcontainers startup test
- [ ] 2.2 Add the `Gender` and `PhoneType` enums, embedded `Address`, expanded `Customer`, and `CustomerPhone` and `IdempotencyKey` entities with repositories; verify with `@DataJpaTest` tests against Testcontainers, including the case-insensitive email and duplicate phone constraints
- [ ] 2.3 Add `db/seed/R__seed_customers.sql` and the `local`-only Flyway location; verify the seed loads under `local` and not under the default test profile
- [ ] 2.4 Write `docs/erd.md` with a Mermaid ERD matching design.md and link it from `docs/README.md`; verify it renders and every migrated column appears

## 3. CRUD services and APIs

- [ ] 3.1 Expand `CustomerRequest` and `CustomerResponse` with validation, and update `CustomerService`/`CustomerServiceImpl`; verify with unit tests and controller tests for each validation rule in `customer-records`
- [ ] 3.2 Add `CustomerPhoneService` with its implementation, DTOs and `CustomerPhoneController`; verify with unit and controller tests for each `customer-phones` scenario
- [ ] 3.3 Add `IdempotencyKeyService` with its implementation, DTOs and `IdempotencyKeyController`; verify with unit and controller tests for key CRUD
- [ ] 3.4 Add exceptions and `GlobalExceptionHandler` mappings for missing or mismatched idempotency keys and duplicate phones; verify the 400, 409 and 422 responses in controller tests
- [ ] 3.5 Update the API table in `README.md` and verify each listed endpoint exists in the OpenAPI output

## 4. Change events

- [ ] 4.1 Add `CustomerChangeEvent` and replace `CustomerCreatedEvent`; rename the configured topic to `customer-change-event`; verify the JSON matches the spec in a serialization test
- [ ] 4.2 Add the change classifier in `processors`; verify with a unit test for every `customer-change-publishing` scenario
- [ ] 4.3 Add `CustomerChangeService` and its implementation with idempotency, comparison and after-commit publishing, and route controller writes through it; verify with unit tests covering retries, a 422 mismatch, no-op PUTs, a rollback publishing nothing, and only CRUD implementations injecting repositories
- [ ] 4.4 Rewrite `docs/kafka-events.md` for the new topic, types and naming convention, and record the lost-event gap in `docs/architecture.md`; verify all seven types are documented with an example

## 5. Security

- [ ] 5.1 Update `SecurityConfig` per design.md with the role converter and local Swagger chain; update `SecurityConfigTests`; verify 401 without a token and open Swagger only under `local`
- [ ] 5.2 Add `@PreAuthorize` rules to all controllers and role-specific tokens in controller tests; verify each `customer-api-access` role scenario
- [ ] 5.3 Add a test that denied writes produce audit rows with status 403 and success false; verify it passes through the audit filter against Testcontainers
- [ ] 5.4 Add the springdoc bearer scheme and update the README's run and auth sections; verify the Authorize button appears under `local`

## 6. Integration

- [ ] 6.1 Run `./gradlew spotlessApply build` and verify it passes
- [ ] 6.2 Against `local-docker` and real tokens for both users, verify each event type appears on `customer-change-event`, a repeated POST publishes once, and a no-op PUT publishes nothing
- [ ] 6.3 Leave all changes uncommitted for developer review and verify with `git status` that nothing was committed
