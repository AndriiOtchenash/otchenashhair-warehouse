-- liquibase formatted sql

-- changeset AndriiOtchenash:012-create-scalp-photos
CREATE TABLE scalp_photos (
    id           BIGSERIAL PRIMARY KEY,
    client_id    BIGINT NOT NULL REFERENCES clients(id),
    drive_file_id VARCHAR(100),
    drive_url    VARCHAR(500) NOT NULL,
    taken_at     DATE NOT NULL,
    zone         VARCHAR(30) NOT NULL,
    notes        TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scalp_photos_client_id ON scalp_photos(client_id);
