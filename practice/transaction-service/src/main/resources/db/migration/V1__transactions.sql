CREATE TABLE transactions (
    id UUID PRIMARY KEY, customer_id UUID NOT NULL, transaction_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','CREATED','FAILED')),
    total_amount BIGINT NOT NULL DEFAULT 0 CHECK(total_amount >= 0),
    idempotency_key VARCHAR(100) NOT NULL, request_json JSONB NOT NULL, cart_snapshot JSONB NOT NULL,
    cart_cleaned BOOLEAN NOT NULL DEFAULT false, failure_reason TEXT,
    UNIQUE(customer_id,idempotency_key)
);
-- Serialize outstanding checkouts per customer, including delayed cart cleanup.
CREATE UNIQUE INDEX idx_one_active_checkout ON transactions(customer_id)
    WHERE status='PENDING' OR (status='CREATED' AND cart_cleaned=false);
CREATE INDEX idx_transactions_customer_date ON transactions(customer_id,transaction_date DESC);
CREATE TABLE transaction_orders (
    id UUID PRIMARY KEY, transaction_id UUID NOT NULL REFERENCES transactions(id), store_id UUID NOT NULL,
    store_name_snapshot VARCHAR(150) NOT NULL, invoice_number VARCHAR(80) NOT NULL UNIQUE,
    recipient_name VARCHAR(150) NOT NULL, recipient_phone VARCHAR(30) NOT NULL,
    shipping_address TEXT NOT NULL, courier VARCHAR(30) NOT NULL,
    shipping_cost BIGINT NOT NULL CHECK(shipping_cost>=0), subtotal BIGINT NOT NULL CHECK(subtotal>=0),
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED', UNIQUE(transaction_id,store_id)
);
CREATE TABLE transaction_items (
    id UUID PRIMARY KEY, transaction_order_id UUID NOT NULL REFERENCES transaction_orders(id),
    variant_id UUID NOT NULL, product_name_snapshot VARCHAR(200) NOT NULL,
    variant_name_snapshot VARCHAR(150) NOT NULL, unit_price BIGINT NOT NULL CHECK(unit_price>=0),
    quantity INTEGER NOT NULL CHECK(quantity>0), line_total BIGINT NOT NULL CHECK(line_total>=0),
    note VARCHAR(500) NOT NULL, CHECK(line_total=unit_price*quantity)
);
CREATE INDEX idx_transaction_items_order ON transaction_items(transaction_order_id);
