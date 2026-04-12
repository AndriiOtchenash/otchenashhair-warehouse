-- liquibase formatted sql

-- changeset andrii:006-create-stock-movements
CREATE TABLE stock_movements (
    id            BIGSERIAL PRIMARY KEY,
    product_id    BIGINT        NOT NULL REFERENCES products(id),
    stock_item_id BIGINT        REFERENCES stock_items(id),
    movement_type VARCHAR(20)   NOT NULL,
    quantity      NUMERIC(10,3) NOT NULL,
    unit_price    NUMERIC(10,2),
    supplier_id   BIGINT        REFERENCES suppliers(id),
    client_id     BIGINT        REFERENCES clients(id),
    performed_by  BIGINT        REFERENCES users(id),
    notes         TEXT,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_movement_quantity CHECK (quantity > 0)
);

-- changeset andrii:006-create-stock-movements-idx
CREATE INDEX idx_movements_product_id ON stock_movements(product_id);
CREATE INDEX idx_movements_type       ON stock_movements(movement_type);
CREATE INDEX idx_movements_created_at ON stock_movements(created_at);
