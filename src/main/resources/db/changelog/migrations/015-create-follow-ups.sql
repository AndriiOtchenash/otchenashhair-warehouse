--liquibase formatted sql

--changeset otchenash:015-create-follow-ups
CREATE TABLE follow_ups (
    id         BIGSERIAL PRIMARY KEY,
    client_id  BIGINT       NOT NULL REFERENCES clients(id),
    action     VARCHAR(20)  NOT NULL,
    due_date   DATE,
    note       VARCHAR(500),
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_follow_ups_client_id  ON follow_ups(client_id);
CREATE INDEX idx_follow_ups_created_at ON follow_ups(created_at DESC);
