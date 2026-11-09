# Kafka event contracts

All events are JSON strings (UTF-8) with a `String` key. Topics are created by
`local-docker/kafka` (auto-create is off), 3 partitions each, and each has a
`<topic>-dlq` dead-letter topic.

## Envelope

Every event carries these fields. `kafka-demo-analytics` reads only the
envelope, so new event types are counted without code changes.

| Field | Type | Notes |
|---|---|---|
| `eventId` | UUID | Unique per event. Consumers use it to ignore redeliveries. |
| `eventType` | string | `UPPER_SNAKE_CASE`, e.g. `CUSTOMER_CREATED` |
| `occurredAt` | ISO-8601 instant | When the producer created the event (UTC) |
| `customerId` | UUID | The customer the event is about |

## `customer-events`

Producer: **kafka-demo-customer**. Key: `customerId`.
Consumers: kafka-demo-email (`kafka-demo-email` group), kafka-demo-analytics (`kafka-demo-analytics` group).

### `CUSTOMER_CREATED`

Published after the customer row is committed to Postgres.

```json
{
  "eventId": "0e15a972-ae98-429a-a35f-0b61251f0481",
  "eventType": "CUSTOMER_CREATED",
  "occurredAt": "2026-11-07T18:04:18.720578Z",
  "customerId": "1c02b30d-233c-44ea-a4bf-aa491c81dee9",
  "firstName": "Ada",
  "lastName": "Lovelace",
  "email": "ada@example.com"
}
```

## `email-events`

Producer: **kafka-demo-email**. Key: `customerId`.
Consumers: kafka-demo-analytics.

### `EMAIL_SENT`

```json
{
  "eventId": "6dde44f1-7abf-4a6c-b1bb-3902d50ce0f6",
  "eventType": "EMAIL_SENT",
  "occurredAt": "2026-11-07T18:11:10.588189Z",
  "emailId": "bd01585f-8343-4512-b4e3-229b9755d436",
  "customerId": "ca77df95-f94f-4682-93b7-b82f74cb808d",
  "to": "alan@example.com",
  "subject": "Welcome to Kafka Demo, Alan!"
}
```

## Delivery and failure handling

- Delivery is **at-least-once**. Every consumer must be idempotent on `eventId`
  (email: unique index on `sourceEventId`; analytics: `eventId` is the document `_id`).
- Consumers retry a failing record 3 times, 1 s apart, then publish it to
  `<topic>-dlq` with `kafka_dlt-exception-*` headers describing the failure.
  Malformed JSON and missing envelope fields skip the retries.
- Consumers ignore `eventType`s they don't handle.

## Changing a contract

This file is duplicated in each service repo's `docs/`. When a contract changes:

1. Add fields; don't rename or remove them. Consumers must tolerate unknown fields.
2. For a breaking change, publish a new `eventType` (e.g. `CUSTOMER_CREATED_V2`) and migrate consumers first.
3. Update `docs/kafka-events.md` in **every** service repo in the same change.
