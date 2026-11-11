# Entity relationship diagram

kafka-demo-customer stores its data in PostgreSQL. It never uses the `public` schema.

```mermaid
erDiagram
    customer ||--o{ customer_phone : has

    customer {
        uuid id PK
        varchar first_name
        varchar middle_name "nullable"
        varchar last_name
        date date_of_birth
        varchar gender "MALE, FEMALE, NON_BINARY, PREFER_NOT_TO_SAY"
        varchar email UK "unique on lower(email)"
        varchar street
        varchar apartment "nullable"
        varchar city
        char state "2-letter US code"
        varchar zip
        timestamptz created_at
        timestamptz updated_at
    }

    customer_phone {
        uuid id PK
        uuid customer_id FK "cascades on customer delete"
        varchar type "HOME, WORK, CELL"
        varchar number "unique per customer with type"
    }

    idempotency_key {
        varchar key PK
        varchar request_hash "SHA-256 of method, path and body"
        int response_status
        jsonb response_body
        uuid customer_id "not a foreign key"
        timestamptz created_at
    }

    api_audit {
        uuid id PK
        varchar api_path
        varchar action
        text request_payload "nullable"
        varchar performed_by
        int response_status
        boolean success
        timestamptz occurred_at
    }
```

| Table | Schema | Purpose |
|---|---|---|
| `customer` | `kafka_demo_customer` | Customers. The address is stored as columns because it is one-to-one. |
| `customer_phone` | `kafka_demo_customer` | A customer's phones. |
| `idempotency_key` | `kafka_demo_customer` | Stored results of POST requests. `customer_id` is not a foreign key because a key must outlive a deleted customer. |
| `api_audit` | `audit` | One row for every POST, PUT and DELETE, including denied ones. Not related to the other tables. |
