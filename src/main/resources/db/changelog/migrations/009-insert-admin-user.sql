-- liquibase formatted sql
-- changeset andrii:009-insert-admin-user
INSERT INTO users (username, password, full_name, role, enabled)
VALUES (
   'admin',
   '$2a$10$XkqUmoqDiZTwolR50D/NDuD5Sbwg9oIEDFadxo7djSoc2//rsVPEO',
   'Administrator',
   'ROLE_ADMIN',
   true
);
