-- liquibase formatted sql

-- changeset andrii:003-create-clients
CREATE TABLE clients (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    phone      VARCHAR(20),
    notes      TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
