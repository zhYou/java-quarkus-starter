CREATE TABLE orders (
 customer_id uuid NOT NULL,
 id uuid NOT NULL,
 description varchar(200) NOT NULL CHECK (length(btrim(description)) BETWEEN 1 AND 200),
 created_at timestamptz NOT NULL,
 PRIMARY KEY (customer_id,id)
);
CREATE INDEX orders_customer_created ON orders(customer_id,created_at,id);
