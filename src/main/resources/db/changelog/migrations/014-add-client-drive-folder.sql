-- liquibase formatted sql

-- changeset AndriiOtchenash:014-add-client-drive-folder
ALTER TABLE clients
    ADD COLUMN drive_folder_url VARCHAR(500),
    ADD COLUMN drive_folder_id  VARCHAR(100);
