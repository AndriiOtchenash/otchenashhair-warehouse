-- liquibase formatted sql

-- changeset AndriiOtchenash:019-create-services
CREATE TABLE services (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_services_active ON services(active);
