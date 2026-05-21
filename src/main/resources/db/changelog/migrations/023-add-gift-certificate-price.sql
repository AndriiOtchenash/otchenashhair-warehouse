--liquibase formatted sql

--changeset otchenash:023-add-gift-certificate-price
ALTER TABLE gift_certificates
    ADD COLUMN price NUMERIC(10, 2) NOT NULL DEFAULT 0;
