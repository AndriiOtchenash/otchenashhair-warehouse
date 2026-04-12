-- liquibase formatted sql

-- changeset andrii:004-create-products
CREATE TABLE products (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    brand           VARCHAR(100),
    category        VARCHAR(50),
    barcode         VARCHAR(50) UNIQUE,
    unit            VARCHAR(10)   NOT NULL,
    unit_size       NUMERIC(10,3) NOT NULL DEFAULT 1,
    min_stock_level NUMERIC(10,3) NOT NULL DEFAULT 0,
    description     TEXT,
    image_url       VARCHAR(500),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- changeset andrii:004-create-products-idx
CREATE INDEX idx_products_barcode  ON products(barcode);
CREATE INDEX idx_products_category ON products(category);
