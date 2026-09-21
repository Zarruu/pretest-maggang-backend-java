CREATE TABLE carts (
    id UUID PRIMARY KEY, customer_id UUID NOT NULL UNIQUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE cart_items (
    id UUID PRIMARY KEY, cart_id UUID NOT NULL REFERENCES carts(id), variant_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 1000),
    note VARCHAR(500) NOT NULL DEFAULT '', version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(cart_id,variant_id)
);
