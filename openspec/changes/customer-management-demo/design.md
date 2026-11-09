# Design

## Context

See proposal.md for motivation. The service has one `customer` table, `CustomerServiceImpl` publishes directly, and `SecurityConfig` has a permit-all `local` chain. Project rules require a CRUD controller for every table except `audit.api_audit`, repositories called only from their CRUD service, builder mapping, and no logic in controllers.

## Goals / Non-Goals

**Goals:**
- A single event per changing call, classified from a real before/after comparison.
- Keycloak roles enforced the same way locally as elsewhere.

**Non-Goals:**
- A transactional outbox (see Risks).
- Optimistic locking; the last write wins.
- Expiring old idempotency keys.
- Checking that `state` is a real US state; the format is validated only.

## Data model

```
Schema: kafka_demo_customer

+-------------------------------+         +-----------------------------+
| customer                      |         | customer_phone              |
+-------------------------------+ 1     * +-----------------------------+
| PK id             UUID        |---------| PK id          UUID         |
|    first_name     VARCHAR     |         | FK customer_id UUID CASCADE |
|    middle_name    VARCHAR NULL|         |    type        VARCHAR      |
|    last_name      VARCHAR     |         |    number      VARCHAR      |
|    date_of_birth  DATE        |         | UQ (customer_id,type,number)|
|    gender         VARCHAR     |         +-----------------------------+
| UQ lower(email)   VARCHAR     |
|    street         VARCHAR     |         +-----------------------------+
|    apartment      VARCHAR NULL|         | idempotency_key             |
|    city           VARCHAR     |         +-----------------------------+
|    state          CHAR(2)     |         | PK key             VARCHAR  |
|    zip            VARCHAR     |         |    request_hash    VARCHAR  |
|    created_at     TIMESTAMPTZ |         |    response_status INT      |
|    updated_at     TIMESTAMPTZ |         |    response_body   JSONB    |
+-------------------------------+         |    customer_id     UUID     |
                                          |    created_at   TIMESTAMPTZ |
Schema: audit                             +-----------------------------+
  api_audit (unchanged)
```

- `address` is an embedded value on `customer`, because it is one-to-one and a separate table would need its own CRUD API.
- `idempotency_key.customer_id` is not a foreign key, because a key must outlive a deleted customer.
- `response_status` and `response_body` are stored because the original result must be returned even after the record is gone, and a customer id alone can't identify a phone response.

## Decisions

### One action service owns every change

```
CustomerController   CustomerPhoneController   IdempotencyKeyController
       | writes              | writes                  |
       v                     v                         |
  CustomerChangeService (action, one transaction)      |
    idempotency, before/after comparison, publish      |
       |            |                 |                |
       v            v                 v                v
  CustomerService  CustomerPhoneService  IdempotencyKeyService
       |            |                 |
       v            v                 v
  CustomerRepo   CustomerPhoneRepo   IdempotencyKeyRepo
```

Reads go straight to the CRUD interfaces. `Customer` has no `@OneToMany` to phones, so `CustomerRepository` can't write the phone table and the layering rule holds. Publishing moves from `CustomerServiceImpl` to `CustomerChangeServiceImpl`. Alternative considered: JPA cascade from `Customer`, rejected because it writes phones outside their CRUD service.

### Classifying a PUT

A pure function in `processors` takes before and after state and returns the event type and `changes`. Areas are profile (name, date of birth, gender, email), address and phones. Phones are compared as a set of (type, number), so changing a number on a PUT is one removal plus one addition; with the same type that is `customer-phone-change`. Pure code makes the full matrix cheap to unit test.

### Idempotency

The key row is inserted first in the same transaction as the change, and its response is written at the end. A rollback removes it, so failures don't consume a key. A concurrent request with the same key waits on the primary key; `CustomerChangeServiceImpl` catches the conflict outside the failed transaction and returns the stored response. `request_hash` covers method, path and body. Alternative considered: treating email uniqueness as the idempotency check, rejected because a repeat would return 409 instead of the original result, and it can't cover phones.

### Security

One secured chain applies in every profile. A `@Profile("local")` chain with a higher order matches only the Swagger paths and permits them. `realm_access.roles` is mapped to `ROLE_*` authorities, and rules are declared with `@PreAuthorize` on controller methods, which is part of the contract and keeps controllers logic-free. springdoc gets a bearer scheme for the Authorize button. This departs from the `spring-boot-initialization` default, which turns authentication off in `local`.

### Seed data

`db/seed/R__seed_customers.sql` is on the Flyway path only in `application-local.yml`. It uses fixed UUIDs and `ON CONFLICT DO NOTHING`. It is repeatable rather than versioned, so a high seed version can't block later migrations.

### Event contract

`CustomerChangeEvent` in `dto.event` replaces `CustomerCreatedEvent`. Event types use kebab-case, replacing the `UPPER_SNAKE_CASE` convention in `docs/kafka-events.md`.

## Risks / Trade-offs

- [A crash between commit and publish loses the event] → Accepted for a demo; listed under known gaps in `docs/architecture.md`.
- [Deleting an idempotency key allows a repeat to create again] → Limited to `customer-admin`, and the email-uniqueness check still applies while the customer exists.
- [`response_body` stores customer data a second time] → Same database and schema as the customer.
- [Keycloak down blocks local API calls] → Documented in the README; the Swagger pages still load.

## Migration Plan

1. Apply the `local-docker` changes and recreate Keycloak so the realm is imported, and recreate Kafka topics.
2. `V3__expand_customer.sql` deletes existing customers, adds the new columns and creates `customer_phone` and `idempotency_key`. V1 and V2 are not edited.
3. Roll out with the email and analytics changes, since `customer-events` is no longer published to.

Rollback: restore the previous code and reset the local database; the migration is not reversible because it deletes demo rows.
