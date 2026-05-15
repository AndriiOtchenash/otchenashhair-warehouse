-- liquibase formatted sql

-- changeset AndriiOtchenash:016-add-product-deactivation
ALTER TABLE products ADD COLUMN deactivated_at TIMESTAMP;
ALTER TABLE products ADD COLUMN deactivation_reason VARCHAR(200);
