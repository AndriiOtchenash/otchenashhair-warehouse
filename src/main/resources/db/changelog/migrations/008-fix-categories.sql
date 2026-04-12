-- liquibase formatted sql

-- changeset andrii:008-fix-categories
DELETE FROM categories;
ALTER SEQUENCE categories_id_seq RESTART WITH 1;
INSERT INTO categories (name, sort_order) VALUES
    ('Трихологія', 1),
    ('Косметика', 2),
    ('Витратні матеріали', 3),
    ('Інше', 4);
