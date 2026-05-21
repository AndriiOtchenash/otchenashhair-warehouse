--liquibase formatted sql

--changeset otchenash:022-appointment-service-link
ALTER TABLE appointments DROP COLUMN IF EXISTS appointment_type;
ALTER TABLE appointments ADD COLUMN service_id BIGINT REFERENCES services(id) ON DELETE SET NULL;
