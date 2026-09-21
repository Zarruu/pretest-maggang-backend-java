-- Local development only. Run as a PostgreSQL administrator on a fresh instance.
CREATE USER catalog_user PASSWORD 'catalog_local';
CREATE USER cart_user PASSWORD 'cart_local';
CREATE USER transaction_user PASSWORD 'transaction_local';
CREATE DATABASE catalog_db OWNER catalog_user;
CREATE DATABASE cart_db OWNER cart_user;
CREATE DATABASE transaction_db OWNER transaction_user;
REVOKE CONNECT ON DATABASE catalog_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE cart_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE transaction_db FROM PUBLIC;
GRANT CONNECT ON DATABASE catalog_db TO catalog_user;
GRANT CONNECT ON DATABASE cart_db TO cart_user;
GRANT CONNECT ON DATABASE transaction_db TO transaction_user;
