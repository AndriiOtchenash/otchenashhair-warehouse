-- liquibase formatted sql

-- changeset AndriiOtchenash:017-create-appointments
CREATE TABLE appointments (
    id               BIGSERIAL PRIMARY KEY,
    client_id        BIGINT,
    guest_name       VARCHAR(150),
    guest_phone      VARCHAR(50),
    start_at         TIMESTAMP NOT NULL,
    end_at           TIMESTAMP NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    appointment_type VARCHAR(30),
    notes            TEXT,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL,
    CONSTRAINT fk_appointments_client FOREIGN KEY (client_id) REFERENCES clients(id),
    CONSTRAINT chk_appointments_client_or_guest
        CHECK (client_id IS NOT NULL OR NULLIF(TRIM(guest_name), '') IS NOT NULL)
);

CREATE INDEX idx_appointments_start_at   ON appointments(start_at);
CREATE INDEX idx_appointments_client_id  ON appointments(client_id);
