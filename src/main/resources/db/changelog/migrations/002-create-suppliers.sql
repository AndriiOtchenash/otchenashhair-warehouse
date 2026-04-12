-- liquibase formatted sql

-- changeset andrii:002-create-suppliers
CREATE TABLE suppliers (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    contact_info VARCHAR(255),
    notes        TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
