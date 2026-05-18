--liquibase formatted sql

--changeset aotchenash:018-add-next-appointment-to-visits
ALTER TABLE visits ADD COLUMN next_appointment_id BIGINT;
ALTER TABLE visits ADD CONSTRAINT fk_visits_next_appointment
    FOREIGN KEY (next_appointment_id) REFERENCES appointments(id) ON DELETE SET NULL;
