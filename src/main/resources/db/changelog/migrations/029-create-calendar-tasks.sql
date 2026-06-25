--liquibase formatted sql

--changeset otchenash:029-create-calendar-tasks
CREATE TABLE calendar_tasks (
    id             BIGSERIAL    PRIMARY KEY,
    task_date      DATE         NOT NULL,
    client_id      BIGINT       REFERENCES clients(id)      ON DELETE SET NULL,
    appointment_id BIGINT       REFERENCES appointments(id) ON DELETE SET NULL,
    text           VARCHAR(500) NOT NULL,
    is_done        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_calendar_tasks_date   ON calendar_tasks(task_date);
CREATE INDEX idx_calendar_tasks_client ON calendar_tasks(client_id);
