# Local development

## 1. Start local-docker

From the `local-docker` repo:

```bash
(cd kafka && docker compose up -d)
(cd postgres && docker compose up -d)
(cd keycloak && docker compose up -d)
```

| Service | Address |
|---|---|
| Kafka | `localhost:9092` |
| Kafka UI | http://localhost:8085 |
| Postgres | `localhost:5432`, db `kafka_demo_customer_db`, user `kafka_demo_customer_local` / `local` |

The `customer-change-event` and `customer-change-event-dlq` topics are created by `kafka-setup`.

## 2. Run the service

```bash
./gradlew bootRun
```

It starts on port 8081, runs Flyway migrations, and (for consumers) joins its consumer group.

## 3. Run the whole system

Start all three services (each in its own terminal) and the UI (`npm run dev` in kafka-demo-ui), then
open http://localhost:5173 and sign in as `kafkaDemoAdmin` or `kafkaDemoViewer` (password `local`).

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Connection refused` to 5432 / 27017 / 9092 | The local-docker stack isn't running. |
| `Unknown topic` / producer timeout | Re-run `docker compose up kafka-setup` in `local-docker/kafka`. |
| Flyway checksum mismatch | An applied migration was edited. Revert it and add a new version, or recreate the local DB (`docker compose down && docker compose up -d`). |
| 401 from every API call | Keycloak isn't running, or the token expired. |
| Records landing in `*-dlq` | Inspect them in Kafka UI; the `kafka_dlt-exception-*` headers give the cause. |
| `The uuidRepresentation has not been specified` | `spring.mongodb.representation.uuid: standard` must be set (it is, in `application.yml`). |
| Gradle can't find Java 25 | Gradle downloads it via the foojay toolchain resolver; check your network/proxy. |
