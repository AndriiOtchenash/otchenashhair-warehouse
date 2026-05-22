--liquibase formatted sql
--changeset AndriiOtchenash:024-add-product-recommended-price

ALTER TABLE products ADD COLUMN recommended_price NUMERIC(10,2);
