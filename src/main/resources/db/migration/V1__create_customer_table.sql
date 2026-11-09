CREATE TABLE customer
(
    id         UUID PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX ux_customer_email ON customer (LOWER(email));
CREATE INDEX ix_customer_created_at ON customer (created_at DESC);
