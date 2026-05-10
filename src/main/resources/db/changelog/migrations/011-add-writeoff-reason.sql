-- liquibase formatted sql

-- changeset AndriiOtchenash:011-add-writeoff-reason
ALTER TABLE stock_movements ADD COLUMN write_off_reason VARCHAR(30);
