---
paths:
  - "src/**/dto/event/**"
  - "src/**/listener/**"
  - "src/**/publisher/**"
  - "src/main/resources/application*.yml"
  - "docs/kafka-events.md"
---

# Kafka event contracts

`docs/kafka-events.md` is the contract, and it is duplicated in every service repo.

- Keep the envelope (`eventId`, `eventType`, `occurredAt`, `customerId`) on every event.
- Additive changes only. Never rename or remove a field; for breaking changes add a new `eventType`.
- Consumers must be idempotent on `eventId` and must ignore unknown `eventType`s and unknown fields.
- Topics are created in `local-docker/kafka/create-topics.sh` (auto-create is off). A new topic needs
  an entry there plus its `-dlq`.
- When you change a contract, update `docs/kafka-events.md` here **and** tell the developer which
  other repos need the same doc change and code change. Don't edit other repos unless asked.
