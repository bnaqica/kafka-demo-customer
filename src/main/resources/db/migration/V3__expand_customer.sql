-- Existing rows are demo data with no values for the new required columns.
DELETE FROM customer;

ALTER TABLE customer
    ADD COLUMN middle_name   VARCHAR(100),
    ADD COLUMN date_of_birth DATE         NOT NULL,
    ADD COLUMN gender        VARCHAR(20)  NOT NULL,
    ADD COLUMN street        VARCHAR(255) NOT NULL,
    ADD COLUMN apartment     VARCHAR(50),
    ADD COLUMN city          VARCHAR(100) NOT NULL,
    ADD COLUMN state         CHAR(2)      NOT NULL,
    ADD COLUMN zip           VARCHAR(10)  NOT NULL,
    ADD COLUMN updated_at    TIMESTAMPTZ  NOT NULL;

CREATE TABLE customer_phone
(
    id          UUID PRIMARY KEY,
    customer_id UUID        NOT NULL REFERENCES customer (id) ON DELETE CASCADE,
    type        VARCHAR(10) NOT NULL,
    number      VARCHAR(20) NOT NULL,
    CONSTRAINT ux_customer_phone UNIQUE (customer_id, type, number)
);

-- customer_id is deliberately not a foreign key: a key must outlive a deleted customer.
CREATE TABLE idempotency_key
(
    key             VARCHAR(255) PRIMARY KEY,
    request_hash    VARCHAR(64)  NOT NULL,
    response_status INTEGER      NOT NULL,
    response_body   JSONB        NOT NULL,
    customer_id     UUID,
    created_at      TIMESTAMPTZ  NOT NULL
);
