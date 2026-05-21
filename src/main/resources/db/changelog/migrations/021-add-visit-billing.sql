-- liquibase formatted sql

-- changeset AndriiOtchenash:021-add-visit-billing
ALTER TABLE visits
    ADD COLUMN service_id       BIGINT          REFERENCES services(id) ON DELETE SET NULL,
    ADD COLUMN price_at_time    NUMERIC(10,2),
    ADD COLUMN payment_method   VARCHAR(20),
    ADD COLUMN is_paid          BOOLEAN         NOT NULL DEFAULT FALSE,
    ADD COLUMN certificate_code VARCHAR(20);
