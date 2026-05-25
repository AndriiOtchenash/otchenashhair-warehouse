-- liquibase formatted sql
-- changeset system:025

ALTER TABLE visits ADD COLUMN next_visit_skipped BOOLEAN NOT NULL DEFAULT FALSE;
