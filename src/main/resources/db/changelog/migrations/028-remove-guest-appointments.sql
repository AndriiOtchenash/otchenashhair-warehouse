-- Remove guest_name / guest_phone columns from appointments.
-- All appointments in this system are always linked to a real client.
-- PostgreSQL will automatically drop the CHECK constraint that references guest_name.

ALTER TABLE appointments
    DROP COLUMN IF EXISTS guest_name,
    DROP COLUMN IF EXISTS guest_phone;

ALTER TABLE appointments
    ALTER COLUMN client_id SET NOT NULL;
