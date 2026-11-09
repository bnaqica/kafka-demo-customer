# kafka-demo-customer

Owns customer data in Postgres and publishes a `CUSTOMER_CREATED` event to `customer-events` for every new customer.

Part of the Kafka Demo system — see [docs/architecture.md](docs/architecture.md).

| | |
|---|---|
| Stack | Java 25, Spring Boot 4.1, Gradle 9.9 (wrapper), Lombok, SLF4J (Logback), Jib |
| Data store | Postgres, schema managed by Flyway |
| Port | 8081 |
| Produces | `customer-events` (`CUSTOMER_CREATED`) |
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
Swagger UI is at <http://localhost:8081/swagger-ui.html> in this profile.

Try it:

```bash
curl -i -X POST localhost:8081/api/customers \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com"}'
```

## API

Every endpoint except `/actuator/health` needs a Keycloak bearer token in the `Authorization` header, and returns 401
without one. Authentication is off in the `local` profile, which `./gradlew bootRun` uses by default. To get a token, see
the Keycloak section of the local-docker README.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/customers` | Create a customer. Body: `{"firstName","lastName","email"}`. 201 + `Location`; 400 on validation errors; 409 if the email exists. |
| `GET` | `/api/customers` | All customers, newest first. |
| `GET` | `/api/customers/{id}` | One customer; 404 if unknown. |
| `PUT` | `/api/customers/{id}` | Update a customer. Same body as create. 404 if unknown; 409 if the email belongs to another customer. |
| `DELETE` | `/api/customers/{id}` | Delete a customer. 204; 404 if unknown. |
| `GET` | `/actuator/health` | Health check. |

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
- [docs/kafka-events.md](docs/kafka-events.md) — event contracts shared across services
- [docs/local-development.md](docs/local-development.md) — local setup and troubleshooting
- [openspec/](openspec/) — spec-driven change proposals (OpenSpec)

## Contributing

Claude Code is configured for this repo (`CLAUDE.md`, `.claude/`). Claude never commits: review the
working tree and commit yourself.
