-- liquibase formatted sql

-- changeset AndriiOtchenash:010-add-movement-cancel
ALTER TABLE stock_movements
    ADD COLUMN original_movement_id BIGINT REFERENCES stock_movements(id);
