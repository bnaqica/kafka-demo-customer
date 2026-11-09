# Proposal

## Why

The Kafka Demo needs to show Keycloak role-based access, Postgres and MongoDB persistence, and event-driven reactions to every customer change. Today a customer is only a name and email, only creation publishes an event, and any authenticated caller can change data.

## What Changes

- **BREAKING** Customer gains `middleName`, `dateOfBirth`, `gender`, `address` and a list of `phones`. Create and update requests must supply the new required fields.
- Add a CRUD API for phones under a customer, and a CRUD API for idempotency keys, so every table has a CRUD controller.
- **BREAKING** `POST` for customers and phones requires an `Idempotency-Key` header.
- **BREAKING** The `customer-events` topic and `CUSTOMER_CREATED` are replaced by the `customer-change-event` topic and seven event types. Every call that actually changes data publishes exactly one event.
- **BREAKING** Writes require `customer-admin`; reads require `customer-admin` or `customer-viewer`.
- **BREAKING** The `local` profile requires Keycloak tokens. Only the Swagger pages stay open there.
- Add local-only seed data and an ERD document.
- Update `local-docker`: fix the realm file and role descriptions, and replace the Kafka topics.

## Capabilities

### New Capabilities

- `customer-records`: storing, validating, listing and changing customers, including local seed data.
- `customer-phones`: managing a customer's phone numbers.
- `customer-idempotency`: making POST requests safe to repeat, and managing stored idempotency keys.
- `customer-change-publishing`: which event is published for which change, and what it carries.
- `customer-api-access`: token and role requirements, and auditing of denied requests.

### Modified Capabilities

None. `openspec/specs/` is empty.

## Impact

- **Code:** customer entity, service layer, controllers, DTOs, publisher, `SecurityConfig`, Flyway migrations, tests and docs in this repo.
- **`local-docker`:** `keycloak/realms/kafka-demo-realm.json`, `README.md`, `kafka/create-topics.sh`. The Keycloak realm must be re-imported.
- **Event consumers:** `kafka-demo-email` and `kafka-demo-analytics` must move to `customer-change-event` in their own `customer-management-demo` changes. `docs/kafka-events.md` changes identically in all three service repos.
- **`kafka-demo-ui`:** must log in, send tokens, send `Idempotency-Key`, and use the new customer shape.
- **Running locally:** Keycloak must be running to call the API.
