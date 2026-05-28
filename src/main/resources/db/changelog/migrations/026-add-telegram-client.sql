    -- liquibase formatted sql

-- changeset otchenash:026-add-telegram-client
ALTER TABLE clients ADD COLUMN telegram_chat_id BIGINT;
ALTER TABLE clients ADD COLUMN telegram_link_token VARCHAR(64);
CREATE UNIQUE INDEX clients_telegram_link_token_uq ON clients(telegram_link_token)
    WHERE telegram_link_token IS NOT NULL;
