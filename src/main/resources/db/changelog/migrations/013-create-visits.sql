-- liquibase formatted sql

-- changeset AndriiOtchenash:013-create-visits
CREATE TABLE visits (
    id               BIGSERIAL PRIMARY KEY,
    client_id        BIGINT NOT NULL REFERENCES clients(id),
    visit_date       DATE NOT NULL,
    complaint        TEXT,
    scalp_condition  TEXT,
    recommendations  TEXT,
    next_visit_date  DATE,
    notes            TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_visits_client_id ON visits(client_id);
