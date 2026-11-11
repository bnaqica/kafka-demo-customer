# Kafka event contracts

All events are JSON strings (UTF-8) with a `String` key. Topics are created by
`local-docker/kafka` (auto-create is off), 3 partitions each, and each has a
`<topic>-dlq` dead-letter topic.

## Envelope

Every event carries these fields. `kafka-demo-analytics` reads only the
envelope, so new event types are saved without code changes.

| Field | Type | Notes |
|---|---|---|
| `eventId` | UUID | Unique per event. Consumers use it to ignore redeliveries. |
| `eventType` | string | Lowercase kebab-case, e.g. `customer-addition` |
| `occurredAt` | ISO-8601 instant | When the producer created the event (UTC) |
| `customerId` | UUID | The customer the event is about |

## `customer-change-event`

Producer: **kafka-demo-customer**. Key: `customerId`, so events for one customer stay in order.
Consumers: kafka-demo-email (`kafka-demo-email` group), kafka-demo-analytics (`kafka-demo-analytics` group).

Every API call that actually changes a customer or phone publishes exactly one event, after the database commit.
A call that changes nothing or fails publishes nothing. Seed data publishes nothing.

Besides the envelope, every event has the fields below, as they are after the change, or as they were for
`customer-delete`. Date of birth is never included.

| Field | Type | Notes |
|---|---|---|
| `firstName` | string | |
| `lastName` | string | |
| `email` | string | The address to write to. After an email change, the new address. |
| `state` | string | Two-letter US code |
| `changes` | object | Depends on the type |

### Event types

| `eventType` | Published when | `changes` |
|---|---|---|
| `customer-addition` | POST customer, even with phones | `{}` |
| `customer-change` | PUT customer changing profile fields or several areas | `{"fields": [...]}` |
| `customer-address-change` | PUT customer changing only the address | `{"address": {...}}` |
| `customer-phone-add` | POST phone, or PUT customer adding exactly one phone | `{"phone": {...}}` |
| `customer-phone-change` | PUT phone, or PUT customer replacing one phone with one of the same type | `{"previousPhone": {...}, "phone": {...}}` |
| `customer-phone-delete` | DELETE phone, or PUT customer removing exactly one phone | `{"phone": {...}}` |
| `customer-delete` | DELETE customer, even with phones | `{}` |

`fields` holds any of `firstName`, `middleName`, `lastName`, `dateOfBirth`, `gender`, `email`, `address`, `phones`.
`address` has `street`, `apartment` (may be null), `city`, `state`, `zip`. A phone has `type` (`HOME`, `WORK`, `CELL`) and `number`.

### Example

```json
{
  "eventId": "0e15a972-ae98-429a-a35f-0b61251f0481",
  "eventType": "customer-phone-add",
  "occurredAt": "2026-11-09T18:04:18.720578Z",
  "customerId": "1c02b30d-233c-44ea-a4bf-aa491c81dee9",
  "firstName": "Ada",
  "lastName": "Lovelace",
  "email": "ada@example.com",
  "state": "NY",
  "changes": { "phone": { "type": "CELL", "number": "555-0100" } }
}
```

## Delivery and failure handling

- Delivery is **at-least-once**. Every consumer must be idempotent on `eventId`, and saves each incoming event in
  MongoDB (email: `incoming_event`; analytics: `analytics_event`, where `eventId` is the document `_id`).
- Consumers retry a failing record 3 times, 1 s apart, then publish it to
  `<topic>-dlq` with `kafka_dlt-exception-*` headers describing the failure.
  Malformed JSON and missing envelope fields skip the retries.
- kafka-demo-email ignores `eventType`s it doesn't handle.

## Changing a contract

This file is duplicated in each service repo's `docs/`. When a contract changes:

1. Add fields; don't rename or remove them. Consumers must tolerate unknown fields.
2. For a breaking change, publish a new `eventType` (e.g. `customer-addition-v2`) and migrate consumers first.
3. Update `docs/kafka-events.md` in **every** service repo in the same change.
