# CLAUDE.md — kafka-demo-customer

Owns customer data in Postgres and publishes one event to `customer-change-event` for every API call that changes a customer or phone.

One of four repos in the Kafka Demo system (customer, email, analytics, ui). Local infrastructure lives
in `../../local-docker`. Full picture: `docs/architecture.md`. Event contracts: `docs/kafka-events.md`.

## Never commit

Claude does not commit, push, merge, rebase or open PRs in this repo — the developer reviews and commits
every change. A hook enforces this. See `.claude/rules/git-workflow.md`.

## Commands

```bash
./gradlew build              # compile, test, coverage report, formatting check (Docker must be running)
./gradlew test               # tests only (Docker must be running)
./gradlew spotlessApply      # format the code
./gradlew bootRun            # run against local-docker (profile `local`)
./gradlew jibDockerBuild     # container image into the local Docker daemon
```

## Stack

Java 25 (Gradle toolchain), Spring Boot 4.1 (Jackson 3 — `tools.jackson.*`, not `com.fasterxml.jackson.*`),
Spring Kafka, Postgres, Flyway, JUnit, Testcontainers, Spring Security (JWT), springdoc, Spotless, JaCoCo, Lombok, SLF4J, Jib. Port 8081.

## Layout

```
src/main/java/com/kafkademo/customer/
  KafkaDemoCustomerApplication.java
  api/          REST controllers
  service/      business logic
  repository/   Spring Data repositories
  dto/event/    objects sent via Kafka events
  dto/response/ objects returned by the API
  dto/request/  request bodies received by the API
  entity/       JPA entities
  processors/   additional processing that can't be done in a service
  listener/     Kafka listeners
  publisher/    Kafka publishers
  config/       Spring configuration and @ConfigurationProperties
  exception/    @RestControllerAdvice, custom exceptions, REST client error handling
  client/       API calls to third parties
src/main/resources/
  application.yml          defaults; values from environment variables
  application-local.yml    local-docker settings (profile `local`)
  db/migration/            Flyway SQL migrations
src/test/java/...          tests mirror the main package layout
src/test/resources/
```

## Conventions

- Constructor injection only, generated with Lombok `@RequiredArgsConstructor`; no field `@Autowired`.
- Log with Lombok `@Slf4j` (SLF4J, `{}` placeholders); never `System.out`.
- Request/response DTOs and Kafka events are Java `record`s.
- Inject `java.time.Clock` instead of calling `Instant.now()` directly, so tests can fix time.
- Kafka values are JSON strings serialized with the injected `JsonMapper`; no type headers.
- Config lives in `application.yml` with `${ENV_VAR}` placeholders; local values only in `application-local.yml`.
- Migrations: SQL files in `src/main/resources/db/migration` (`V<n>__<description>.sql`), applied by Spring Boot's Flyway auto-configuration on startup. Hibernate runs with `ddl-auto: validate`, so the entity must match the migrated schema. Never edit a migration that has been applied — add a new version.
- Tests mirror the main package. Unit-test services with Mockito; use `@WebMvcTest` for controllers.
- Database tests run against real PostgreSQL through Testcontainers: `@Import(TestcontainersConfiguration.class)`.
  Don't use an embedded database.
- Every endpoint needs a JWT in every profile, except the health check. The `local` profile only opens the Swagger pages.
- Every change to a customer or phone goes through `CustomerChangeService`, which publishes exactly one event.

## Workflow

Features start with `/opsx:propose`, not `superpowers:brainstorming`. `/opsx:apply` builds them with
`superpowers:test-driven-development`, and code review uses `superpowers:requesting-code-review`.
Finish with `/opsx:archive` after the developer has reviewed. See `.claude/rules/development-workflow.md`.

## More rules

`.claude/rules/` holds the detailed rules (git workflow, development workflow, design principles, package structure, service architecture, Java/Spring, Kafka contracts).
`.claude/agents/` holds review subagents. OpenSpec commands are under `/opsx:*`.
Superpowers is enabled in `.claude/settings.json`; skip any of its steps that commit.
