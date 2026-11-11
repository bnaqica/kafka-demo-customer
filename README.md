# kafka-demo-customer

Owns customer data in Postgres and publishes one event to `customer-change-event` for every API call that changes a customer or phone.

Part of the Kafka Demo system — see [docs/architecture.md](docs/architecture.md).

| | |
|---|---|
| Stack | Java 25, Spring Boot 4.1, Gradle 9.9 (wrapper), Lombok, SLF4J (Logback), Jib |
| Data store | Postgres, schema managed by Flyway |
| Port | 8081 |
| Produces | `customer-change-event` (seven event types, see [docs/kafka-events.md](docs/kafka-events.md)) |
| Consumes | — |

## Prerequisites

- Docker, with the [local-docker](../../local-docker) stacks running (the tests start their own containers, so Docker must be running for them too)
- A JDK 17+ to run Gradle. The Java 25 toolchain is downloaded automatically by Gradle
  (foojay resolver) if you don't have it.
- In your IDE, the Lombok plugin with annotation processing enabled (IntelliJ bundles the plugin;
  VS Code's Java extension supports Lombok out of the box). Gradle builds need nothing extra.

## Run locally

Start the local-docker `postgres` and `kafka` stacks, then:

```bash
./gradlew bootRun
```

`bootRun` uses the `local` profile (`src/main/resources/application-local.yml`), which points at
local-docker. Override with `./gradlew bootRun -Dspring.profiles.active=<profile>`.
It also loads 12 sample customers. Swagger UI is at <http://localhost:8081/swagger-ui.html> in this profile; use its
Authorize button with a Keycloak access token. Keycloak must be running, because every API call needs a token.

Try it, with a token from the Keycloak section of the local-docker README:

```bash
curl -i -X POST localhost:8081/api/customers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Ada","lastName":"Lovelace","dateOfBirth":"1990-12-10","gender":"FEMALE","email":"ada@example.com",
       "address":{"street":"1 Main St","city":"Albany","state":"NY","zip":"12207"},
       "phones":[{"type":"CELL","number":"555-0100"}]}'
```

## API

Every endpoint except `/actuator/health` needs a Keycloak bearer token in the `Authorization` header in every profile,
and returns 401 without one. To get a token, see the Keycloak section of the local-docker README. POST, PUT and DELETE
need the `customer-admin` role and return 403 otherwise; GET on customers and phones also allows `customer-viewer`.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/customers` | Create a customer. Needs `Idempotency-Key`. 201 + `Location`; 400 if invalid; 409 if the email exists; 422 if the key was used for a different request. |
| `GET` | `/api/customers` | All customers with phones, newest first. |
| `GET` | `/api/customers/{id}` | One customer; 404 if unknown. |
| `PUT` | `/api/customers/{id}` | Replace a customer and its phones. 404 if unknown; 409 if the email belongs to another customer. |
| `DELETE` | `/api/customers/{id}` | Delete a customer and its phones. 204; 404 if unknown. |
| `POST` | `/api/customers/{id}/phones` | Add a phone. Needs `Idempotency-Key`. 201; 404 if the customer is unknown; 409 if it's a duplicate. |
| `GET` | `/api/customers/{id}/phones` | A customer's phones; empty if the customer is unknown. |
| `GET` | `/api/customers/{id}/phones/{phoneId}` | One phone; 404 if unknown or owned by another customer. |
| `PUT` | `/api/customers/{id}/phones/{phoneId}` | Update a phone. 404 if unknown; 409 if it's a duplicate. |
| `DELETE` | `/api/customers/{id}/phones/{phoneId}` | Delete a phone. 204; 404 if unknown. |
| `POST` | `/api/idempotency-keys` | Create a stored key (`customer-admin` only, like every endpoint here). 409 if it exists. |
| `GET` | `/api/idempotency-keys` | All stored keys. |
| `GET` | `/api/idempotency-keys/{key}` | One key; 404 if unknown. |
| `PUT` | `/api/idempotency-keys/{key}` | Update a key. |
| `DELETE` | `/api/idempotency-keys/{key}` | Delete a key, so a later POST with it is treated as new. 204. |
| `GET` | `/actuator/health` | Health check. |

A POST repeated with the same key, method, path and body returns the original status and body, changes nothing and
publishes nothing, even if the record has since been deleted.

Request body for a customer: `firstName`, `lastName`, `dateOfBirth`, `gender` (`MALE`, `FEMALE`, `NON_BINARY`,
`PREFER_NOT_TO_SAY`), `email` and `address` (`street`, `city`, `state`, `zip`) are required; `middleName`,
`address.apartment` and `phones` are optional. A phone has `type` (`HOME`, `WORK`, `CELL`) and `number`.

## Test and format

```bash
./gradlew build
```

The build runs the tests, writes a coverage report to `build/reports/jacoco/test/html/index.html`, and fails if
the code isn't formatted. To format it:

```bash
./gradlew spotlessApply
```

## Build a container (Jib)

```bash
./gradlew jibDockerBuild     # build into your local Docker daemon as kafka-demo-customer:latest
IMAGE_REGISTRY=ghcr.io/my-org ./gradlew jib     # build and push to that registry
```

The image needs these environment variables (the `local` profile is not active in the container):

| Variable | Purpose | Local value |
|---|---|---|
| `DB_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/kafka_demo_customer_db` |
| `DB_USERNAME` | DB user | `kafka_demo_customer_local` |
| `DB_PASSWORD` | DB password | `local` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | `localhost:9092` |
| `JWT_ISSUER_URI` | Issuer of accepted tokens | `http://localhost:8180/realms/kafka-demo` |

From a container, reach the local-docker databases via `host.docker.internal`. Kafka advertises
`localhost:9092`, which doesn't work from inside a container, so join the Kafka compose network and use
`kafka:29092`:

```bash
docker run --rm --network kafka_default -p 8081:8081 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/kafka_demo_customer_db \
  -e DB_USERNAME=kafka_demo_customer_local -e DB_PASSWORD=local \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:29092 \
  kafka-demo-customer:latest
```

## Database migrations

SQL files in `src/main/resources/db/migration` (`V<n>__<description>.sql`), applied by Spring Boot's Flyway auto-configuration on startup. Hibernate runs with `ddl-auto: validate`, so the entity must match the migrated schema.

## Database schemas and audit

Nothing uses the `public` schema. The `kafka_demo_customer` schema holds the service's tables and the Flyway history,
and the `audit` schema holds `audit.api_audit`. Every POST, PUT and DELETE is recorded there with the API called, the
action, the request payload, the person from the token (`anonymous` if there is no valid token), the response status and
whether it succeeded. Failed requests are recorded too, including ones rejected by security.

## Documentation

- [docs/architecture.md](docs/architecture.md) — where this service fits, design decisions, known gaps
- [docs/erd.md](docs/erd.md) — tables and relationships
- [docs/kafka-events.md](docs/kafka-events.md) — event contracts shared across services
- [docs/local-development.md](docs/local-development.md) — local setup and troubleshooting
- [openspec/](openspec/) — spec-driven change proposals (OpenSpec)

## Contributing

Claude Code is configured for this repo (`CLAUDE.md`, `.claude/`). Claude never commits: review the
working tree and commit yourself.
