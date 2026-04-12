-- liquibase formatted sql

-- changeset andrii:007-create-categories
CREATE TABLE categories (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- changeset andrii:007-insert-default-categories-v2
DELETE FROM categories;
ALTER SEQUENCE categories_id_seq RESTART WITH 1;
INSERT INTO categories (name, sort_order) VALUES
    (N'Трихологія', 1),
    (N'Косметика', 2),
    (N'Витратні матеріали', 3),
    (N'Інше', 4);

-- changeset andrii:007-alter-products-category
ALTER TABLE products DROP COLUMN category;
ALTER TABLE products ADD COLUMN category_id BIGINT REFERENCES categories(id);
