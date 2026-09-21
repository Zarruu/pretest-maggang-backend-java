CREATE TABLE stores (
    id UUID PRIMARY KEY, name VARCHAR(150) NOT NULL, city VARCHAR(100) NOT NULL
);
CREATE TABLE products (
    id UUID PRIMARY KEY, store_id UUID NOT NULL REFERENCES stores(id),
    name VARCHAR(200) NOT NULL, description TEXT NOT NULL, category VARCHAR(100) NOT NULL,
    image_url TEXT NOT NULL DEFAULT '', created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_products_store_created ON products(store_id, created_at DESC);
CREATE TABLE product_variants (
    id UUID PRIMARY KEY, product_id UUID NOT NULL REFERENCES products(id),
    sku VARCHAR(80) NOT NULL UNIQUE, variant_name VARCHAR(150) NOT NULL,
    price BIGINT NOT NULL CHECK(price BETWEEN 0 AND 1000000000000),
    stock INTEGER NOT NULL CHECK(stock >= 0)
);
CREATE INDEX idx_variants_product ON product_variants(product_id);
-- Technical retry ledger: stock may be deducted only once for each checkout.
CREATE TABLE stock_operations (
    checkout_id UUID PRIMARY KEY, request_json JSONB NOT NULL,
    result_json JSONB NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
