--liquibase formatted sql

--changeset andriiOtchenash:020-create-gift-certificates
CREATE TABLE gift_certificates (
    id                  BIGSERIAL    PRIMARY KEY,
    code                VARCHAR(20)  NOT NULL UNIQUE,

    purchaser_client_id BIGINT       REFERENCES clients(id) ON DELETE SET NULL,
    purchaser_name      VARCHAR(200),
    purchaser_phone     VARCHAR(50),

    recipient_client_id BIGINT       REFERENCES clients(id) ON DELETE SET NULL,
    recipient_name      VARCHAR(200),
    recipient_phone     VARCHAR(50),

    service_id          BIGINT       REFERENCES services(id) ON DELETE SET NULL,
    service_name        VARCHAR(200) NOT NULL,

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    notes               VARCHAR(2000),

    expires_at          DATE         NOT NULL,
    issued_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    redeemed_at         TIMESTAMP,
    cancelled_at        TIMESTAMP
);

CREATE INDEX idx_gift_certificates_purchaser ON gift_certificates(purchaser_client_id);
CREATE INDEX idx_gift_certificates_recipient ON gift_certificates(recipient_client_id);
CREATE INDEX idx_gift_certificates_status    ON gift_certificates(status);
