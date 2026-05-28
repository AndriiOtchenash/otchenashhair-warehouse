-- liquibase formatted sql

-- changeset otchenash:027-add-appointment-reminders
ALTER TABLE appointments ADD COLUMN reminder_48h_sent_at TIMESTAMP;
ALTER TABLE appointments ADD COLUMN reminder_24h_sent_at TIMESTAMP;
ALTER TABLE appointments ADD COLUMN reminder_2h_sent_at  TIMESTAMP;
