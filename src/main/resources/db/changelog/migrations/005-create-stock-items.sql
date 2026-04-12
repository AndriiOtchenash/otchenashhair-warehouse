-- liquibase formatted sql

-- changeset andrii:005-create-stock-items
CREATE TABLE stock_items (
    id             BIGSERIAL PRIMARY KEY,
    product_id     BIGINT        NOT NULL REFERENCES products(id),
    quantity       NUMERIC(10,3) NOT NULL DEFAULT 0,
    purchase_price NUMERIC(10,2),
    expiry_date    DATE,
    batch_number   VARCHAR(100),
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_quantity_non_negative CHECK (quantity >= 0)
);

-- changeset andrii:005-create-stock-items-idx
CREATE INDEX idx_stock_items_product_id  ON stock_items(product_id);
CREATE INDEX idx_stock_items_expiry_date ON stock_items(expiry_date);
